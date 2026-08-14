import { describe, expect, it } from 'vitest';
import {
  MDE_AT_300, REFERENCE_N, SAMPLE_SIZES,
  cellsFor, comparisonTable, formatDuration, mdeFor, planFor,
} from './sampleSize.js';

const DOMINANCE_PAIR = { taskType: 'DOMINANCE' };
const PRICE_PAIR = { taskType: 'PRICE' };

describe('MDE 는 표본 크기의 함수다', () => {
  it('n=300 은 실측 상수 그대로다', () => {
    expect(mdeFor('DOMINANCE', 300)).toBeCloseTo(MDE_AT_300.DOMINANCE, 6);
    expect(mdeFor('PRICE', 300)).toBeCloseTo(MDE_AT_300.PRICE, 6);
  });

  it('sd ∝ 1/√n 으로 스케일한다', () => {
    expect(mdeFor('PRICE', 100)).toBeCloseTo(MDE_AT_300.PRICE * Math.sqrt(3), 6);
    expect(mdeFor('PRICE', 75)).toBeCloseTo(mdeFor('PRICE', 300) * 2, 6);
    expect(mdeFor('DOMINANCE', REFERENCE_N / 4)).toBeCloseTo(MDE_AT_300.DOMINANCE * 2, 6);
  });

  it('표본이 작을수록 못 재는 차이가 커진다', () => {
    expect(mdeFor('PRICE', 50)).toBeGreaterThan(mdeFor('PRICE', 100));
    expect(mdeFor('PRICE', 100)).toBeGreaterThan(mdeFor('PRICE', 300));
  });

  it('가격형은 우열형보다 한 자릿수 나쁘다 — λ 가 낮기 때문이다', () => {
    expect(mdeFor('PRICE', 100)).toBeGreaterThan(mdeFor('DOMINANCE', 100) * 5);
  });

  it('모르는 유형은 값을 지어내지 않는다', () => {
    expect(mdeFor('ETHICAL_VALUE', 100)).toBeNull();
    expect(mdeFor('DOMINANCE', 0)).toBeNull();
  });
});

describe('셀 수와 시간', () => {
  it('양방향 × 적응식 반복이 곱해진다 — 양방향은 옵션이 아니라 설계다', () => {
    expect(cellsFor(100, 2)).toBe(880);
    expect(cellsFor(50, 2)).toBe(440);
    expect(cellsFor(300, 2)).toBe(2640);
  });

  it('쌍이 없으면 0 이다', () => {
    expect(cellsFor(100, 0)).toBe(0);
    expect(cellsFor(0, 2)).toBe(0);
  });

  it('시간은 분 단위로 읽힌다', () => {
    expect(formatDuration(0)).toBe('—');
    expect(formatDuration(45)).toBe('45초');
    expect(formatDuration(126)).toBe('약 2분');
  });
});

describe('planFor — 고르는 자리에서 한계를 같이 보여준다', () => {
  it('가격형 n=50 은 경고가 붙는다', () => {
    const plan = planFor(50, [PRICE_PAIR]);
    expect(plan.warnings).toHaveLength(1);
    expect(plan.warnings[0]).toContain('«못 잼»');
    expect(plan.warnings[0]).toContain('표본을 키우면');
  });

  it('가격형 n=300 이면 경고가 사라진다', () => {
    expect(planFor(300, [PRICE_PAIR]).warnings).toHaveLength(0);
  });

  it('우열형은 어떤 표본에서도 넉넉하다', () => {
    SAMPLE_SIZES.forEach((n) => {
      expect(planFor(n, [DOMINANCE_PAIR]).warnings).toHaveLength(0);
    });
  });

  it('유형이 섞이면 약한 쪽만 경고한다', () => {
    const plan = planFor(50, [DOMINANCE_PAIR, PRICE_PAIR]);
    expect(plan.rows).toHaveLength(2);
    expect(plan.warnings).toHaveLength(1);
    expect(plan.cells).toBe(440);
  });
});

describe('comparisonTable', () => {
  const table = comparisonTable([DOMINANCE_PAIR, PRICE_PAIR]);

  it('세 선택지를 준다', () => {
    expect(table.map((row) => row.sampleSize)).toEqual([50, 100, 300]);
  });

  it('표본이 커질수록 셀도 시간도 늘어난다', () => {
    expect(table[0].cells).toBeLessThan(table[1].cells);
    expect(table[1].cells).toBeLessThan(table[2].cells);
    expect(table[0].seconds).toBeLessThan(table[2].seconds);
  });
});
