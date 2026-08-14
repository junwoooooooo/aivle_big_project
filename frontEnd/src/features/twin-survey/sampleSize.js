/**
 * 표본 크기가 곧 **측정 가능 최소 효과(MDE)** 다.
 *
 * 이 화면이 표본을 고르게 하는 이유는 예산 때문이 아니라, n 이 「무엇을 못 잴지」를
 * 정하기 때문이다. 가격형은 λ=0.823 이라 n 이 작으면 웬만한 차이를 못 잡는다 —
 * 고르는 자리에서 그 사실을 같이 보여주지 않으면, 사용자는 «차이 없음» 이라는
 * 있지도 않은 결론을 얻는다.
 *
 * 실측 상수는 `combine_csv/_build/g3d/G3D_성능분석/perf_quant.json` 의 n=300 값이다.
 * 스케일링은 sd ∝ 1/√n 에서 온다:  MDE(n) = MDE₃₀₀ × √(300/n)
 * ⚠ 이건 연어 조사에서 잰 값의 **외삽**이다. 다른 범주에서 같은 λ 가 나온다는 보장은 없다.
 */

export const REFERENCE_N = 300;

/** n=300 실측 MDE. */
export const MDE_AT_300 = Object.freeze({
  DOMINANCE: 0.01364,
  PRICE: 0.13587,
});

/** 화면이 고르는 세 값. 서버도 이 셋만 받는다. */
export const SAMPLE_SIZES = Object.freeze([50, 100, 300]);

/** 적응식 k 실측 평균 반복 수 — 2회 선행 + 불일치 셀만 3회차. */
export const REPS_PER_CELL = 2.2;
/** 실측 처리량(동시 32). 예상 시간은 어림이지 약속이 아니다. */
const CELLS_PER_SECOND = 7;

export function mdeFor(taskType, n) {
  const base = MDE_AT_300[taskType];
  if (!base || !n) return null;
  return base * Math.sqrt(REFERENCE_N / n);
}

/** 셀 = n × 쌍 × 2방향 × 반복. 양방향은 옵션이 아니라 설계다. */
export function cellsFor(n, pairCount) {
  if (!n || !pairCount) return 0;
  return Math.round(n * pairCount * 2 * REPS_PER_CELL);
}

export function estimatedSeconds(n, pairCount) {
  return Math.round(cellsFor(n, pairCount) / CELLS_PER_SECOND);
}

export function formatDuration(seconds) {
  if (!seconds) return '—';
  if (seconds < 60) return `${seconds}초`;
  return `약 ${Math.round(seconds / 60)}분`;
}

/**
 * 고른 n 이 그 유형에서 쓸모 있는지. 「못 잼」으로 끝날 가능성이 큰 조합을 미리 경고한다.
 * 임계 0.20 은 −1‥+1 척도에서 «웬만한 실무적 차이»의 어림이다. 관문이 아니라 안내다.
 */
const USEFUL_MDE = 0.20;

export function planFor(n, pairs) {
  const types = [...new Set((pairs ?? []).map((pair) => pair.taskType).filter(Boolean))];
  const rows = types.map((taskType) => {
    const mde = mdeFor(taskType, n);
    return {
      taskType,
      mde,
      weak: mde !== null && mde > USEFUL_MDE,
    };
  });
  return {
    sampleSize: n,
    cells: cellsFor(n, (pairs ?? []).length),
    seconds: estimatedSeconds(n, (pairs ?? []).length),
    rows,
    warnings: rows.filter((row) => row.weak).map((row) => (
      `${row.taskType === 'PRICE' ? '가격형' : '이 유형'}은 n=${n} 에서 측정 한계가 `
      + `${row.mde.toFixed(2)} 다 — 이보다 작은 차이는 «못 잼» 으로 끝난다. `
      + '표본을 키우면 잴 수도 있다.'
    )),
  };
}

/** 화면이 세 선택지를 나란히 보여줄 때 쓰는 표. */
export function comparisonTable(pairs) {
  return SAMPLE_SIZES.map((n) => planFor(n, pairs));
}
