/**
 * 패널 트윈 조사 결과 정규화기.
 *
 * ⚠ **이 프로젝트에는 TypeScript 도 스키마 검증도 없다. 그래서 이 파일이 타입 시스템이다.**
 * 컴포넌트는 서버 JSON 을 직접 읽지 않고 여기를 거친다.
 *
 * 이 화면이 특히 위험한 이유: 산출물이 「어느 쪽이 이겼다」로 읽히기 쉬운데, 이 파이프라인은
 * **방향과 신뢰구간까지만** 낸다. 크기·점유율·선택확률은 구조적으로 못 준다.
 * 그래서 여기서 두 가지를 강제한다 —
 *   ① `caveats` 가 비면 조용히 지나가지 않고 **큰 소리 나는 자리표시자**를 만든다
 *   ② Δ 를 퍼센트로 바꾸지 않는다. 퍼센트로 쓰는 순간 크기 주장이 된다
 */

/** 과제 유형 — 무엇을 근거로 파는지가 라벨에 같이 붙는다. */
export const TASK_TYPE_VIEW = {
  DOMINANCE: {
    label: '명백한 우열형',
    tone: 'success',
    standing: '관문 3에서 4/4 통과한 유형',
  },
  PRICE: {
    label: '가격형',
    tone: 'warning',
    standing: '모듈 성적(B 3/4) — 관문 통과가 아니다',
  },
};
const TASK_TYPE_MISSING = {
  label: '유형 표기 없음',
  tone: 'danger',
  standing: '근거 지위를 알 수 없다',
};

/** 판정. `TIE` 는 «차이 없음»이 아니라 «못 잼»이다 — 이 구분을 흐리면 없는 결론이 생긴다. */
export const WINNER_VIEW = {
  X: { label: 'A안 우세', tone: 'success' },
  Y: { label: 'B안 우세', tone: 'success' },
  TIE: { label: '측정 한계 이하 — 못 잼', tone: 'neutral' },
};
const WINNER_MISSING = { label: '판정 없음', tone: 'danger' };

/** 응답자 갈래. 위치응답이 많으면 그 쌍의 신호가 얇다는 뜻이라 숨기지 않는다. */
export const CLASS_LABEL = {
  content_X: 'A안을 내용으로 선택',
  content_Y: 'B안을 내용으로 선택',
  position_driven: '먼저 보인 쪽 선택(위치응답)',
  anti_position: '나중 보인 쪽 선택(위치응답)',
  undecided: '미결정',
};

const CAVEATS_MISSING = [
  '⚠ 경계 문구가 결과에 실려오지 않았다. 이 수치를 인용하지 마라 — '
  + '값만 떼어 나가는 것을 막는 장치가 빠진 상태다.',
];

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function asNumber(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

/**
 * 방향 지표를 사람이 읽는 문자열로. **퍼센트로 바꾸지 않는다.**
 * −1‥+1 척도의 방향 지표이지 점유율이 아니다.
 */
export function formatDelta(value) {
  const number = asNumber(value);
  if (number === null) return '값 없음';
  return `${number >= 0 ? '+' : '−'}${Math.abs(number).toFixed(3)}`;
}

export function formatInterval(interval) {
  if (!interval) return null;
  const low = asNumber(interval.low);
  const high = asNumber(interval.high);
  if (low === null || high === null) return null;
  return `${formatDelta(low)} ‥ ${formatDelta(high)}`;
}

export function taskTypeView(taskType) {
  return TASK_TYPE_VIEW[taskType] ?? TASK_TYPE_MISSING;
}

export function winnerView(winner) {
  return WINNER_VIEW[winner] ?? WINNER_MISSING;
}

function normalizePair(raw) {
  const caveats = asArray(raw?.caveats).filter((note) => typeof note === 'string' && note.trim());
  const classes = Object.entries(raw?.respondentClasses ?? {}).map(([key, count]) => ({
    key,
    label: CLASS_LABEL[key] ?? key,
    count,
  }));

  return {
    pairId: raw?.pairId ?? null,
    taskType: raw?.taskType ?? null,
    taskTypeView: taskTypeView(raw?.taskType),
    taskTypeReason: raw?.taskTypeReason ?? null,
    labels: { X: raw?.labels?.X ?? 'A안', Y: raw?.labels?.Y ?? 'B안' },
    profiles: { X: raw?.profiles?.X ?? '', Y: raw?.profiles?.Y ?? '' },
    winner: raw?.winner ?? null,
    winnerView: winnerView(raw?.winner),
    winnerLabel: raw?.winnerLabel ?? null,
    measurable: raw?.measurable === true,
    decisionReason: raw?.decisionReason ?? null,
    delta: asNumber(raw?.deltaAvg),
    deltaText: formatDelta(raw?.deltaAvg),
    intervalText: formatInterval(raw?.confidenceInterval),
    mde: asNumber(raw?.mde),
    positionComponent: asNumber(raw?.positionComponent),
    contentShare: asNumber(raw?.contentShare),
    nPaired: asNumber(raw?.nPaired) ?? 0,
    nRespondents: asNumber(raw?.nRespondents) ?? 0,
    classes,
    excerpts: asArray(raw?.rationaleExcerpts),
    // 비어 있으면 자리표시자를 넣는다. 빈 배열로 두면 화면에 아무것도 안 나오고,
    // 그러면 경계 없는 수치가 그대로 읽힌다 — 이 파이프라인이 없애려던 실패 그 자체다.
    caveats: caveats.length > 0 ? caveats : CAVEATS_MISSING,
    caveatsMissing: caveats.length === 0,
  };
}

export function normalizeTwinSurvey(raw) {
  if (!raw || typeof raw !== 'object') return null;

  const pairs = asArray(raw.pairs).map(normalizePair);
  const strata = Object.entries(raw.sampling?.strata ?? {})
    .map(([cell, count]) => ({ cell, count }));
  const shortCells = Object.entries(raw.sampling?.shortCells ?? {})
    .map(([cell, detail]) => ({ cell, ...detail }));

  return {
    situation: raw.situation ?? '',
    sampleSize: asNumber(raw.sampleSize) ?? 0,
    sampling: {
      requested: asNumber(raw.sampling?.requested) ?? 0,
      drawn: asNumber(raw.sampling?.drawn) ?? 0,
      strata,
      shortCells,
      // 층이 얕아 목표를 못 채운 셀이 있으면 실효표본을 드러낸다. 조용히 채우지 않는다.
      hasShortCells: shortCells.length > 0,
    },
    pairs,
    telemetry: raw.telemetry ?? {},
    notes: asArray(raw.notes),
    // 화면 상단에 띄울 구조적 경고. 하나라도 있으면 결과를 그대로 인용하면 안 된다.
    warnings: pairs.filter((pair) => pair.caveatsMissing)
      .map((pair) => `${pair.pairId}: 경계 문구 없음`),
  };
}
