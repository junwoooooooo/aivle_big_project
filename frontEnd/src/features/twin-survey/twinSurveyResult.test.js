import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  formatDelta, formatInterval, normalizeTwinSurvey, taskTypeView, winnerView,
} from './twinSurveyResult.js';

/**
 * **AI 와 같은 골든 픽스처**를 읽는다 (`ai/tests/test_twin_golden.py` 가 같은 파일을 검증한다).
 * 두 층이 한 파일을 보므로 한쪽이 모양을 바꾸면 다른 쪽 테스트가 즉시 빨개진다.
 * 사본을 만들면 그 성질이 사라진다 — 그래서 복사하지 않고 저장소 경로로 읽는다.
 */
function fixture(name) {
  const here = dirname(fileURLToPath(import.meta.url));
  return JSON.parse(readFileSync(
    resolve(here, '../../../../ai/tests/fixtures/twin_survey', name), 'utf-8',
  ));
}

describe('normalizeTwinSurvey', () => {
  const result = normalizeTwinSurvey(fixture('survey.json'));

  it('쌍마다 유형과 그 근거 지위가 같이 온다', () => {
    expect(result.pairs).toHaveLength(2);
    expect(result.pairs[0].taskTypeView.label).toBe('명백한 우열형');
    expect(result.pairs[1].taskTypeView.label).toBe('가격형');
    expect(result.pairs[1].taskTypeView.standing).toContain('관문 통과가 아니다');
  });

  it('경계가 쌍마다 붙어 온다 — 값과 한 몸이다', () => {
    result.pairs.forEach((pair) => {
      expect(pair.caveats.length).toBeGreaterThan(0);
      expect(pair.caveatsMissing).toBe(false);
    });
    expect(result.warnings).toHaveLength(0);
  });

  it('필수 고지가 정규화를 통과해 살아남는다', () => {
    const notes = result.pairs[0].caveats.join(' / ');
    expect(notes).toContain('외적 타당성 시험 종합 미달');
    expect(notes).toContain('한국미디어패널조사(KISDI)');
    expect(notes).toContain('실존 인물 인터뷰가 아니다');
  });

  it('0단계 판정 전이므로 성적 미전이 문구가 붙어 있다', () => {
    const notes = result.pairs[0].caveats.join(' / ');
    expect(notes).toContain('성적 미전이');
  });

  it('측정 한계 이하인 쌍은 «못 잼» 으로 읽힌다 — «차이 없음» 이 아니다', () => {
    const price = result.pairs[1];
    expect(price.measurable).toBe(false);
    expect(price.winnerView.label).toContain('못 잼');
    expect(Math.abs(price.delta)).toBeLessThan(price.mde);
    expect(price.caveats.join(' ')).toContain('못 잼');
  });

  it('방향이 난 쌍은 이긴 쪽 이름을 준다', () => {
    const dominance = result.pairs[0];
    expect(dominance.measurable).toBe(true);
    expect(dominance.winner).toBe('X');
    expect(dominance.winnerLabel).toBe('신선 냉장');
  });

  it('응답자 갈래가 사람이 읽는 말로 펴진다 — 위치응답을 숨기지 않는다', () => {
    const keys = result.pairs[0].classes.map((item) => item.key);
    expect(keys).toContain('position_driven');
    const position = result.pairs[0].classes.find((item) => item.key === 'position_driven');
    expect(position.label).toContain('위치응답');
  });

  it('층별 실제 배분이 온다', () => {
    expect(result.sampling.drawn).toBe(100);
    expect(result.sampling.strata).toHaveLength(10);
    expect(result.sampling.hasShortCells).toBe(false);
  });
});

describe('크기 주장 금지', () => {
  const result = normalizeTwinSurvey(fixture('survey.json'));

  it('Δ 를 퍼센트로 바꾸지 않는다 — 그 순간 크기 주장이 된다', () => {
    result.pairs.forEach((pair) => {
      expect(pair.deltaText).not.toContain('%');
      expect(pair.intervalText ?? '').not.toContain('%');
    });
  });

  it('부호와 절대값만 내보낸다', () => {
    expect(formatDelta(0.84)).toBe('+0.840');
    expect(formatDelta(-0.42)).toBe('−0.420');
    expect(formatDelta(null)).toBe('값 없음');
    expect(formatInterval({ low: -0.1, high: 0.3 })).toBe('−0.100 ‥ +0.300');
    expect(formatInterval(null)).toBeNull();
  });
});

describe('빠진 값은 조용히 넘어가지 않는다', () => {
  it('caveats 가 없으면 큰 소리 나는 자리표시자를 넣는다', () => {
    const result = normalizeTwinSurvey({
      pairs: [{ pairId: 'P1', taskType: 'DOMINANCE', winner: 'X', measurable: true }],
    });
    const pair = result.pairs[0];
    expect(pair.caveatsMissing).toBe(true);
    expect(pair.caveats[0]).toContain('인용하지 마라');
    expect(result.warnings).toEqual(['P1: 경계 문구 없음']);
  });

  it('모르는 유형·판정은 «표기 없음» 으로 드러난다', () => {
    expect(taskTypeView('ETHICAL_VALUE').label).toBe('유형 표기 없음');
    expect(taskTypeView(undefined).tone).toBe('danger');
    expect(winnerView(undefined).label).toBe('판정 없음');
  });

  it('입력이 결과가 아니면 null 을 준다', () => {
    expect(normalizeTwinSurvey(null)).toBeNull();
    expect(normalizeTwinSurvey('결과')).toBeNull();
  });
});
