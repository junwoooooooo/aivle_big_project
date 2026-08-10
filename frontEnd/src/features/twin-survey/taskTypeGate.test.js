import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  DOMINANCE, ETHICAL_VALUE, PRICE, SERVICEABLE, UNMEASURABLE,
  classifyPair, gateSurvey,
} from './taskTypeGate.js';

/**
 * **파이썬 게이트와 같은 사례표**를 읽는다 (`ai/tests/test_twin_gate_parity.py` 가 같은 파일을
 * 검증한다). 거울이 갈리면 화면은 실행 버튼을 열어주고 서버가 422 로 막는다 —
 * 그 상황을 배포 전에 여기서 잡는 것이 이 테스트의 목적이다.
 */
function gateCases() {
  const here = dirname(fileURLToPath(import.meta.url));
  const raw = JSON.parse(readFileSync(
    resolve(here, '../../../../ai/tests/fixtures/twin_survey/gate_cases.json'), 'utf-8',
  ));
  return raw.cases;
}

describe('판매 경계 게이트 — 서버와의 일치', () => {
  it.each(gateCases())('$name → $expected', ({ expected, X, Y }) => {
    expect(classifyPair({ X, Y }).taskType).toBe(expected);
  });

  it('사례표가 다섯 유형을 모두 덮는다', () => {
    const covered = new Set(gateCases().map((item) => item.expected));
    expect(covered).toEqual(new Set([
      'DOMINANCE', 'PRICE', 'ETHICAL_VALUE', 'UNMEASURABLE', 'IDENTICAL',
    ]));
  });
});

describe('무엇을 팔 수 있는가', () => {
  it('서비스 가능한 유형은 통과한 둘뿐이다', () => {
    expect(SERVICEABLE).toEqual([DOMINANCE, PRICE]);
  });

  it('막힌 유형은 이유를 함께 준다 — 사용자가 고칠 수 있어야 한다', () => {
    const ethical = classifyPair({
      X: { attrs: { 인증: '있음' }, priceKrw: 4500 },
      Y: { attrs: { 인증: '없음' }, priceKrw: 4500 },
    });
    expect(ethical.serviceable).toBe(false);
    expect(ethical.reason).toContain('예측을 제공하지 않는다');

    const tangled = classifyPair({
      X: { attrs: { 형태: '신선', 원산지: '칠레산' }, priceKrw: 4500 },
      Y: { attrs: { 형태: '냉동', 원산지: '노르웨이산' }, priceKrw: 4500 },
    });
    expect(tangled.taskType).toBe(UNMEASURABLE);
    expect(tangled.reason).toContain('한 번에 한 속성만');
    expect(tangled.differing).toEqual(['원산지', '형태']);
  });
});

describe('gateSurvey', () => {
  const ok = {
    X: { attrs: { 형태: '신선' }, priceKrw: 4500 },
    Y: { attrs: { 형태: '냉동' }, priceKrw: 4500 },
  };
  const blocked = {
    X: { attrs: { 인증: '있음' }, priceKrw: 4500 },
    Y: { attrs: { 인증: '없음' }, priceKrw: 4500 },
  };

  it('전부 통과하면 실행할 수 있다', () => {
    const gate = gateSurvey([ok]);
    expect(gate.canRun).toBe(true);
    expect(gate.blocked).toHaveLength(0);
  });

  it('하나라도 막히면 실행할 수 없다', () => {
    const gate = gateSurvey([ok, blocked]);
    expect(gate.canRun).toBe(false);
    expect(gate.blocked).toHaveLength(1);
    expect(gate.blocked[0].taskType).toBe(ETHICAL_VALUE);
  });

  it('쌍이 없으면 실행할 수 없다', () => {
    expect(gateSurvey([]).canRun).toBe(false);
    expect(gateSurvey(undefined).canRun).toBe(false);
  });

  it('가격형은 통과하되 유형이 그대로 보인다 — 지위를 화면이 병기해야 한다', () => {
    const gate = gateSurvey([{
      X: { attrs: { 형태: '신선' }, priceKrw: 5000 },
      Y: { attrs: { 형태: '냉동' }, priceKrw: 4500 },
    }]);
    expect(gate.canRun).toBe(true);
    expect(gate.verdicts[0].taskType).toBe(PRICE);
  });
});
