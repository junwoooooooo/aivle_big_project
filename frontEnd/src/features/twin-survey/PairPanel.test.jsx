import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';

import { PairPanel, TwinFootnote } from './TwinSurveyPage.jsx';
import { normalizeTwinSurvey } from './twinSurveyResult.js';

/** AI 가 검증하는 것과 **같은 골든 픽스처**(`ai/tests/test_twin_golden.py`). */
const GOLDEN = normalizeTwinSurvey(JSON.parse(readFileSync(resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../../../../ai/tests/fixtures/twin_survey/survey.json'), 'utf-8')));

const decided = GOLDEN.pairs[0];      // winner=X, measurable
const unmeasured = GOLDEN.pairs[1];   // TIE, 못 잼

function panel(pair) {
  return render(<PairPanel pair={pair} result={GOLDEN} />);
}

describe('PairPanel — 무엇을 비교했는지가 결과에 있어야 한다', () => {
  /**
   * 회귀 방지. `twinSurveyResult.js` 가 `profiles` 를 정규화해 두고도 화면이 한 번도
   * 안 그려서, 결과가 「신선 냉장 우세」라고만 말하고 그 «신선 냉장»이 무엇이었는지는
   * 어디에도 없었다.
   */
  it('응답자에게 보인 자극 문장을 양쪽 다 보여준다', () => {
    panel(decided);
    expect(screen.getByText('형태 신선(냉장), 가격 4,500원')).toBeInTheDocument();
    expect(screen.getByText('형태 냉동, 가격 4,500원')).toBeInTheDocument();
  });

  /** 라벨은 머리글·인터뷰 배지에도 나오므로 비교 판(`.twin-compare`) 안에서만 센다. */
  const compareCards = (container) => [...container.querySelectorAll('.twin-compare > div')]
    .map((card) => [card.querySelector('dt').textContent, card.getAttribute('data-lead')]);

  it('이긴 쪽을 자극 카드에도 표시한다 — 판정과 자극이 서로를 가리킨다', () => {
    const { container } = panel(decided);
    expect(compareCards(container)).toEqual([['신선 냉장', 'true'], ['냉동', 'false']]);
  });

  it('«못 잼»인 쌍은 어느 쪽도 이긴 것으로 칠하지 않는다', () => {
    const { container } = panel(unmeasured);
    expect(screen.getByText('판정 불가 — 못 잼')).toBeInTheDocument();
    expect(compareCards(container).map(([, lead]) => lead)).toEqual(['false', 'false']);
  });

  /**
   * 경계 문구는 **각주로 모았다**(사용자 결정 2026-08-11) — 쌍마다 같은 9줄이 반복돼
   * 인터뷰와 측정치를 밀어냈다. 카드 안에서는 조용하지만, **빠졌을 때는 카드가 운다.**
   */
  it('경계가 살아 있으면 카드는 조용하다 — 문장은 각주가 든다', () => {
    const { container } = panel(decided);
    expect(container.querySelector('.twin-panel__caveats')).toBeNull();
  });

  it('경계가 빠지면 그 카드가 소리를 낸다', () => {
    const { container } = panel({ ...decided, caveats: ['⚠ 경계 문구가 없다'], caveatsMissing: true });
    const alarm = container.querySelector('.twin-panel__caveats');
    expect(alarm.classList.contains('is-missing')).toBe(true);
    expect(alarm.textContent).toContain('경계 문구가 없다');
  });
});

describe('TwinFootnote — 지운 게 아니라 중복을 걷어 모은 것이다', () => {
  it('일반 면책은 결과가 없어도 늘 있다', () => {
    render(<TwinFootnote result={null} />);
    expect(screen.getByText(/실존 인물의 응답이 아니라/)).toBeInTheDocument();
  });

  it('쌍별 경계 문구를 한 곳에 모으되 중복은 한 번만 싣는다', () => {
    render(<TwinFootnote result={GOLDEN} />);
    const unique = new Set(GOLDEN.pairs.flatMap((pair) => pair.caveats));

    expect(screen.getByText(`이 결과를 읽는 법 ${unique.size}가지`)).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(unique.size);
    // 두 쌍이 공유하는 문장이 실제로 있어야 «중복을 걷었다»가 의미를 갖는다.
    expect(unique.size).toBeLessThan(GOLDEN.pairs.flatMap((pair) => pair.caveats).length);
  });
});
