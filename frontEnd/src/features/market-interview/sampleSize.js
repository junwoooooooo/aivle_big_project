/**
 * 표본 크기 — 정성 조사에서 n 이 정하는 것은 «측정 한계»가 아니라 **분모**다.
 *
 * 우열 조사의 `sampleSize.js` 와 다른 점이 셋이다.
 *  ① MDE 표가 없다. 이 조사는 통계적 판정을 내지 않으므로 잴 한계도 없다
 *  ② 1인 1셀이다. 우열 조사는 `n × 쌍 × 2방향 × 반복` 이었다
 *  ③ 뒤에 **주제 코딩**과 **타겟 조건식**이 붙는다. 셀 수가 적어도 그쪽이 길다
 *
 * 세 값의 근거는 정성 조사의 표준 표본(8~20명)이다. 20명이면 같은 이야기가 서너 번
 * 반복되기 시작하고, 그 지점부터는 사람을 늘려도 새 주제가 잘 안 나온다. 80 을 위 끝으로
 * 둔 것은 층(성×연령 10칸)을 한 번씩은 채우기 위해서다.
 */

/** 화면이 고르는 세 값. 서버(`MarketInterviewService.SAMPLE_SIZES`)·DB CHECK 와 같다. */
export const SAMPLE_SIZES = Object.freeze([20, 40, 80]);

/**
 * 실측 처리량(동시 32). 예상 시간은 **어림이지 약속이 아니다.**
 *
 * 2026-08-15 유료 1판 실측: **n=40 · 43호출 · 67초**(gpt-4o-mini). 옛 상수는 같은 조건을
 * 57초로 잡아 **10초 짧았다** — 7문항 시절 값이었기 때문이다. 그 차이만큼 기준을 올렸다.
 *
 * ⚠ 아직 한 점짜리 어림이다. n=20·n=80 은 안 재 봤고, 이 실측 뒤 배정 배치가 40 → 8 로
 * 바뀌어(코딩 호출이 1회 → ⌈n/8⌉회, 동시 실행) 코딩 구간이 다시 움직였다.
 * 다음 유료 판에서 **끝에서 끝까지** 다시 잰다.
 */
const CELLS_PER_SECOND = 3;
/** 코드북 1회(+빈 축이면 1회 더) + 배정 ⌈n/8⌉회. 배정은 동시에 돈다. */
const CODING_SECONDS_PER_PERSON = 0.6;
const CODING_BASE_SECONDS = 30;

/** 응답 수 = 사람 수. 1인 1셀이다. */
export function cellsFor(n) {
  return Number.isFinite(n) ? n : 0;
}

export function estimatedSeconds(n) {
  if (!n) return 0;
  return Math.round(n / CELLS_PER_SECOND + CODING_BASE_SECONDS + n * CODING_SECONDS_PER_PERSON);
}

export function formatDuration(seconds) {
  if (!seconds) return '—';
  if (seconds < 60) return `${seconds}초`;
  return `약 ${Math.round(seconds / 60)}분`;
}

/**
 * 고른 n 이 정성 조사로 쓸모 있는지. 관문이 아니라 안내다.
 *
 * 20 미만은 서버가 받지 않으므로 「너무 적다」 경고는 없다. 대신 **위쪽**을 경고한다 —
 * 표본을 키우면 답이 «더 정확해진다»고 읽기 쉬운데, 정성 조사에서 늘어나는 것은 정확도가
 * 아니라 주제의 폭이고 비용은 그대로 늘어난다.
 */
export function planFor(n) {
  return {
    sampleSize: n,
    cells: cellsFor(n),
    seconds: estimatedSeconds(n),
    notes: n >= 80
      ? ['표본을 키워도 답이 «더 정확해지지는» 않는다 — 늘어나는 것은 나오는 주제의 폭이다.']
      : [],
  };
}
