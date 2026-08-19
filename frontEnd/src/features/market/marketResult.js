/**
 * 시장조사·BM 결과 정규화기.
 *
 * ⚠ **이 프로젝트에는 TypeScript 도 스키마 검증도 없다. 그래서 이 파일이 타입 시스템이다.**
 * 컴포넌트는 서버 JSON 을 직접 읽지 않고 여기를 거친다.
 *
 * 왜 필요한가: 지금 journey 화면들은 서버 응답을 그대로 `useState` 에 넣고 JSX 에서 꺼내 쓴다.
 * 필드 하나가 빠지면 `undefined` 가 조용히 렌더된다 — **`grade` 가 없으면 「등급 없음」이 아니라
 * 그냥 아무것도 안 나오고, 읽는 사람은 그 숫자를 확정치로 본다.** 그게 이 파이프라인이
 * 없애려고 만든 실패 그 자체라서, 누락은 **명시적 문구**로 바꾼다.
 */

/** 근거의 확실성 등급. 서버가 정하고 화면은 옮기기만 한다. */
export const GRADE_VIEW = {
  '확정': { label: '확정', tone: 'success' },
  '실무 신뢰': { label: '실무 신뢰', tone: 'info' },
  '추정': { label: '추정', tone: 'warning' },
  '근거 없음': { label: '근거 없음', tone: 'neutral' },
};
const GRADE_MISSING = { label: '등급 표기 없음', tone: 'danger' };

/**
 * 캔버스 칸 상태.
 *
 * ⚠ <b>「확인됨」은 이 표에 없다.</b> 그 말은 성적표(`SCORE_STATE_VIEW`)가 가져갔다
 * (와이어프레임 정본, 2026-08-13). 칸은 「근거 있음/근거 필요」로 말한다 — 두 표가 같은
 * 낱말을 쓰면 한 화면에서 「확인됨」이 두 뜻으로 뜬다. 한쪽만 고치지 말 것.
 */
export const CELL_STATUS_VIEW = {
  VERIFIED: { label: '근거 있음', tone: 'success' },
  PARTIAL: { label: '일부 확인', tone: 'warning' },
  UNVERIFIED: { label: '근거 필요', tone: 'danger' },
  PLAN: { label: '작성됨', tone: 'neutral' },
  BLOCKED: { label: '진행 불가', tone: 'danger' },
};

export const DECISION_VIEW = {
  PASS: { label: '통과', tone: 'success' },
  CONDITIONAL: { label: '조건부', tone: 'warning' },
  REVISION_REQUIRED: { label: '수정 필요', tone: 'danger' },
  BLOCKED: { label: '진행 불가', tone: 'danger' },
};

/**
 * 판정 게이트의 규칙 코드 → 사람이 읽는 제목.
 *
 * <p>모델이 쓴 status 를 안 믿고 **근거 개수**로 반증한 것들이다. 정본은 AI 쪽
 * `app/validation/gate.py` — 코드를 늘리면 여기도 늘려야 한다. 모르는 코드는 코드 자체를
 * 그대로 보여준다(숨기면 화면이 이유를 잃는다).
 */
export const GATE_TITLE = {
  G1: '근거 없음',
  G4: '관측 미달',
  G5: '수요 미확인',
};

/**
 * 사유의 **갈래** — 「컨셉을 고쳐서 될 일인가」.
 *
 * <p>이것이 없으면 세 가지가 한 덩어리로 읽힌다. 특히 `UNCOLLECTED` 는 <b>컨셉을 고쳐도
 * 안 고쳐진다</b> — 그걸 모르고 컨셉을 다듬어 통과시키면 그게 우리가 만든 방식의 「다 패스」다.
 * 정본은 AI 쪽 `app/validation/gate.py` 의 `_cause`.
 */
export const GATE_CAUSE_VIEW = {
  UNCOLLECTED: { label: '못 찾음', tone: 'danger', note: '컨셉을 고쳐도 안 고쳐져요 — 다시 조사해야 해요' },
  // ⚠ 2026-08-15 정정. 이 갈래는 「찾아 놓고 이 칸에 붙이지 못했어요」라고 적고 있었다.
  //    이제 **붙어는 있는데 그 칸을 확인해 주진 못하는** 경우가 같은 갈래로 온다
  //    (채널 칸에 조사 결과 4건이 붙었지만 귀촌 통계·택배 배송기간이었다 — 유료 실측
  //    `p46-bm-01`). 옛 문구를 그대로 두면 근거표에 4건이 보이는 옆에서 「못 붙였다」고
  //    말해 **한 화면이 스스로를 부정한다.** 두 경우를 다 담는 말로 바꾼다.
  UNCITED: { label: '확인 못 함', tone: 'warning', note: '찾긴 했는데 이 칸을 확인해 주진 못해요' },
  UNMAPPED: { label: '판별 불가', tone: 'neutral', note: '조사 항목에 없어서 갈래를 알 수 없어요' },
};

/**
 * 성적표 과목 = <b>화면의 목차</b>다. ⚠ **키 순서가 곧 절 번호**다(`subjectNumber`).
 *
 * <p>판 ㊸ 에서 셋이 늘었다 — 채널·원가·수익성·규제. 절 체인이 채우는 과목이고,
 * 「찾지 못한 것」 <b>앞에</b> 끼웠다: 그래야 번호가 밀리는 줄이 그것 하나뿐이다(7→10).
 * 서버 `serialize._SUBJECT` + `_SECTION_SUBJECT` 와 <b>같은 순서</b>여야 한다.
 */
export const SUBJECT_LABEL = {
  MARKET_SIZE: '시장 크기',
  GROWTH: '성장률',
  COMPETITOR: '경쟁사',
  PRICE: '가격',
  DEMAND: '수요 근거',
  CALCULATION: '시장 규모 계산',
  CHANNEL: '채널',
  UNIT_ECONOMICS: '원가·수익성',
  REGULATION: '규제',
  NOT_FOUND: '찾지 못한 것',
};

/**
 * <b>목차에 서는 절 제목</b> — 목표 보고서(`TARGET_REPORT.md`)의 절 제목 그대로다.
 *
 * <p>⚠ <b>`SUBJECT_LABEL` 과 일부러 갈랐다</b>(판 ㊺). 그 표는 <b>두 주인을 섬긴다</b> —
 * 성적표 라벨(`:405`)과 「근거 보기」 링크 문구(`RefinementSummary`)가 같이 쓴다.
 * 거기에 「시장 크기 — 얼마나 큰가」 같은 <b>물음형 제목을 넣으면 성적표 배지 옆이 길어져
 * 한눈에 안 든다.</b> 목차는 읽히는 제목이 필요하고 성적표는 짧은 이름이 필요하다.
 *
 * <p>여기 없는 과목은 `SUBJECT_LABEL` 로 물러선다.
 */
export const SECTION_TITLE = {
  MARKET_SIZE: '시장 크기 — 얼마나 큰가',
  PRICE: '내 가격은 어디에 서는가',
  COMPETITOR: '경쟁 지형 — 그 자리에 누가 있나',
  CHANNEL: '채널 — 어디서 팔리나',
  DEMAND: '수요 — 우리 고객이 실재하는가',
  UNIT_ECONOMICS: '원가와 수익성 — 이 사업이 남기는가',
  REGULATION: '규제 — 팔기 전에 확인할 것',
  GAPS: '못 구한 것 — 어디서 구하나',
  SYNTHESIS: '이 조사가 말하는 것',
};

/**
 * <b>화면 목차 순서</b> — 목표 보고서(`docs/market-research-redesign/TARGET_REPORT.md`)와 같다.
 *
 * <p>⚠ <b>절 번호의 정본은 이제 이 배열이다</b>(`SUBJECT_LABEL` 의 키 순서가 아니다).
 * 판 ㊺ 전에는 키 순서였는데, 성장률·계산을 1절 «안으로» 접으면서 「목차에 있는 것」과
 * 「성적표가 보내는 것」이 갈렸다. 두 목록을 한 표에 두면 접힌 과목이 절 번호를 먹는다.
 *
 * <p>`GAPS`·`SYNTHESIS` 는 성적표 과목이 <b>아니다</b> — 처방·9절이라 서버 판정이 없다.
 * 그래서 화면이 라벨과 설명을 직접 준다.
 */
export const SECTION_ORDER = [
  'MARKET_SIZE', 'PRICE', 'COMPETITOR', 'CHANNEL',
  'DEMAND', 'UNIT_ECONOMICS', 'REGULATION', 'GAPS', 'SYNTHESIS',
];

// FULL compatibility exports. The canonical /market renderer uses SECTION_* above, while the
// retained non-route MarketReportView still imports the former names.
export const REPORT_SECTION_ORDER = SECTION_ORDER;
export const REPORT_SECTION_TITLE = {
  MARKET_SIZE: '시장 크기', PRICE: '가격', COMPETITOR: '경쟁', CHANNEL: '채널',
  DEMAND: '수요', UNIT_ECONOMICS: '원가·수익성', REGULATION: '규제',
  GAPS: '못 구한 것', SYNTHESIS: '사업가에게 의미하는 것',
};

/** 성적표 과목 상태. ⚠ 「확인됨」이 여기 있다 — `CELL_STATUS_VIEW` 와 낱말이 겹치면 안 된다. */
export const SCORE_STATE_VIEW = {
  FILLED: { label: '확인됨', tone: 'success' },
  PARTIAL: { label: '일부만 확인', tone: 'warning' },
  MISSING: { label: '비어 있음', tone: 'danger' },
  REPORTED: { label: '보고됨', tone: 'neutral' },
};

/** 계약이 쓰는 단위 코드 → 사람이 읽는 말. 모르는 코드는 **원문 그대로** 통과시킨다. */
export const UNIT_VIEW = { KRW: '원', PERCENT_PER_YEAR: '%/년' };

/** 출처의 기관 유형. 「어디서 온 숫자인가」는 등급만큼 중요하다. */
export const SOURCE_KIND_VIEW = {
  gov_stat: '정부 통계',
  public_filing: '공시',
  official_page: '공식 페이지',
  news: '언론',
  web: '웹',
};

/**
 * 「못 찾은 것」의 갈래 — **사용자의 다음 행동이 다르면 다른 갈래다.**
 * 서버 `serialize._NOT_FOUND` 와 같은 분류다. 갈리면 `marketResult.test.js` 가 잡는다.
 */
export const NOT_FOUND_GROUP = {
  NOT_YET: { label: '아직 못 채운 것', note: '더 찾으면 나올 수 있어요', tone: 'warning' },
  ASSUMED: { label: '가정으로 메운 변수', note: '자료가 아니라 판단이 필요해요', tone: 'warning' },
  CONFIRMED_ABSENT: { label: '찾아도 없는 것', note: '여기가 종착이에요', tone: 'neutral' },
  SCREENED_OUT: { label: '찾았지만 쓰지 않은 것', note: '규격 미달로 걸러냈어요', tone: 'info' },
  DIVERGED: { label: '값이 갈린 것', note: '그 자체가 조사 결과예요', tone: 'info' },
};

/** 진단 키 → 갈래 + 사람이 읽는 이름. 모르는 키는 원문 노출 + `danger` 로 **드러낸다.** */
export const NOT_FOUND_VIEW = {
  empty_slots: ['NOT_YET', '근거를 하나도 못 찾은 조사 칸'],
  thin_slots: ['NOT_YET', '근거가 기준에 못 미치는 조사 칸'],
  retry_hints: ['NOT_YET', '재조사 힌트 — 자동으로 돌지 않아요'],
  url_filtered: ['NOT_YET', '열지 않고 거른 후보'],
  unknown_error_codes: ['NOT_YET', '분류하지 못한 외부 응답'],
  unfilled_vars: ['ASSUMED', '관측 없이 가정으로 채운 변수'],
  suspect_var: ['ASSUMED', '재조사 1순위 변수'],
  independent_topdown_blocked: ['CONFIRMED_ABSENT', '위에서 아래로 재는 길이 막혔어요'],
  '자료_부재_확정': ['CONFIRMED_ABSENT', '그 형태로 발행되지 않는 자료'],
  adapters: ['CONFIRMED_ABSENT', '설정되지 않은 수집 경로'],
  off_slot: ['SCREENED_OUT', '조사 칸과 안 맞아 격리한 근거'],
  contradictions: ['DIVERGED', '같은 대상·단위인데 값이 갈렸어요'],
  unit_mismatch: ['DIVERGED', '단위가 어긋나 멈춘 계산'],
  range_capped: ['DIVERGED', '범위 상한에 부딪힌 추정'],
  skipped_checks: ['DIVERGED', '선행 규칙 위반으로 건너뛴 검사'],
};

export const CANVAS_CELL_LABEL = {
  KEY_PARTNERS: '핵심 파트너',
  KEY_ACTIVITIES: '핵심 활동',
  KEY_RESOURCES: '핵심 자원',
  VALUE_PROPOSITIONS: '가치 제안',
  CUSTOMER_RELATIONSHIPS: '고객 관계',
  CHANNELS: '채널',
  CUSTOMER_SEGMENTS: '고객 세그먼트',
  COST_STRUCTURE: '비용 구조',
  REVENUE_STREAMS: '수익원',
};

// Kept for compatibility with retained FULL consumers; MAIN BmCanvas uses CANVAS_LAYOUT.
export const CANVAS_BANDS = [
  ['고객과 가치', ['CUSTOMER_SEGMENTS', 'VALUE_PROPOSITIONS', 'CHANNELS', 'CUSTOMER_RELATIONSHIPS']],
  ['실행 구조', ['KEY_ACTIVITIES', 'KEY_RESOURCES', 'KEY_PARTNERS']],
  ['수익과 비용', ['REVENUE_STREAMS', 'COST_STRUCTURE']],
];

/**
 * 캔버스 배치 — <b>3×3 균등 9칸.</b> 캔버스도 칸별 세부 목록도 이 순서를 따른다.
 *
 * <p>예전에는 4·3·2 세 밴드(「고객과 가치」·「실행 구조」·「수익과 비용」)였다. 와이어프레임
 * 정본(`public/wireframe.html`)이 3×3 이라 그쪽으로 맞췄다 — 밴드 제목도 같이 뗐다.
 * 순서는 목업의 9칸 순서를 그대로 옮긴 것이다.
 */
export const CANVAS_LAYOUT = [
  'KEY_PARTNERS', 'KEY_ACTIVITIES', 'VALUE_PROPOSITIONS',
  'CUSTOMER_RELATIONSHIPS', 'CUSTOMER_SEGMENTS', 'CHANNELS',
  'KEY_RESOURCES', 'COST_STRUCTURE', 'REVENUE_STREAMS',
].map((cell) => ({ cell, label: CANVAS_CELL_LABEL[cell] }));

/**
 * 칸의 «성격». 정본은 AI 쪽 `research2/harness/vocab.json` 의 canvas 라우팅 표다.
 *
 * ⚠ **9칸을 전부 근거로 채우는 것은 설계가 아니다.** 「계획」 칸은 조사 슬롯이 «불필요»하고
 * 컨셉 서술·입력 제약에서 온다. 화면이 이 구분을 안 하면 정상 결과가 미완성으로 읽힌다.
 */
export const CELL_KIND = {
  CUSTOMER_SEGMENTS: ['관측', '시장조사가 근거로 채워요'],
  VALUE_PROPOSITIONS: ['관측', '시장조사가 근거로 채워요'],
  CHANNELS: ['관측', '시장조사가 근거로 채워요'],
  REVENUE_STREAMS: ['관측', '시장조사가 근거로 채워요'],
  CUSTOMER_RELATIONSHIPS: ['계획', '컨셉 서술에서 와요'],
  KEY_RESOURCES: ['계획', '컨셉 서술에서 와요'],
  KEY_ACTIVITIES: ['계획', '컨셉 서술에서 와요'],
  KEY_PARTNERS: ['계획', '컨셉 서술에서 와요'],
  COST_STRUCTURE: ['계획', '입력 제약(예산·팀·기간)에서 와요'],
};

/** 캔버스 상태 → 점 색. 배지 라벨은 `CELL_STATUS_VIEW` 가 따로 갖는다. */
export const CELL_DOT = {
  VERIFIED: 'ok', PARTIAL: 'mid', UNVERIFIED: 'none', PLAN: 'off', BLOCKED: 'none',
};

/**
 * <b>조사가 덜 된 사유</b> → 사람이 읽는 말. 봉투 `degradations[].code` 중 <b>사용자의
 * 읽기를 바꾸는 것만</b> 고른다.
 *
 * <p>⚠ 이 표가 없던 동안 `degradations` 는 봉투까지 오고 <b>그리는 곳이 0곳</b>이었다.
 * 「읽다 만 것을 다 읽은 것처럼 두지 않는다」가 <b>개발자 원장에서만 참</b>이었다 —
 * 화면에는 「채널 · 실린 사실 9건 · 일부만 확인」만 떴다.
 *
 * <p>여기 없는 코드(`NOT_WIRED` 등)는 안 그린다. 전부 그리면 정상 실행에도 경고가 깔려
 * <b>진짜 경고가 안 읽힌다.</b>
 */
export const SHORTFALL_VIEW = {
  SECTIONS_TRUNCATED: '예산 상한에 걸려 **문서를 다 읽지 못했어요.** 빈 절은 「없다」가 아니라 「못 봤다」일 수 있어요',
  BUDGET_EXHAUSTED: '예산이 모자라 **이 걸음을 아예 돌리지 못했어요.**',
  SECTIONS_READ_FAILED: '문서 읽기가 **실패했어요.** 절별 사실이 이번 실행에는 없어요',
  SYNTHESIS_SKIPPED: '**「미는 것과 흔드는 것」을 만들지 못했어요** — 예산이 모자라거나 재채점이었어요',
  SYNTHESIS_FAILED: '**「미는 것과 흔드는 것」 생성이 실패했어요.**',
  // ⚠ 유료 스모크(2026-08-15)의 **유일한 실패**가 이 코드였는데 여기 없어서 화면 0곳에
  //    닿았다. 요약 카드는 `summary` 가 null 이면 통째로 안 그려지므로, 이 줄이 없으면
  //    사용자는 **요약이 있어야 한다는 사실 자체를 모른다.** 봉투에서는 시끄럽게 죽고
  //    화면에서는 조용히 죽던 자리다.
  CHECK_FAILED: '**요약 문장이 검사를 통과하지 못해 버렸어요.** 값과 근거는 그대로예요 — 문장만 없어요',
};

/**
 * <b>실패가 아니라 「이만큼만 보여 준다」는 알림.</b> `SHORTFALL_VIEW` 와 <b>일부러 갈라
 * 놓았다.</b>
 *
 * <p>⚠ 이 코드를 `SHORTFALL_VIEW` 에 넣으면 안 된다. 그 상자의 제목은 「이 조사가 다 돌지
 * 못했어요」인데 서랍 표본은 <b>정상 실행마다 항상</b> 뜬다 — 성공한 조사에 실패 딱지가
 * 붙고, 위 주석이 경고한 <b>「전부 그리면 진짜 경고가 안 읽힌다」</b>가 그대로 일어난다.
 *
 * <p>그렇다고 안 그릴 수도 없다. 안 그리면 「근거 42건」이 <b>전량인 척</b>한다 —
 * 실제로는 203건 중 20건이다.
 */
export const NOTICE_VIEW = {
  DRAWER_SAMPLED: '**참고 근거는 절마다 20건까지만 보여 줘요.** 접힌 것도 조사 원장에는 그대로 있어요',
};

/** 경쟁사 지표로 취급하는 계량. ⚠ 임시 — 봉투에 과목 필드가 생기면 이 표는 없어진다. */
export const COMP_METRICS = [
  '가입 매장 수', '누적 가입자 수', '매출액', '이용 요금', '월 활성 사용자',
];

const list = (value) => (Array.isArray(value) ? value : []);
const text = (value) => (typeof value === 'string' && value.trim() ? value : null);
const count = (value) => (typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : 0);

/** 출처 도메인. 건수와 독립성은 다르다 — 한 도메인에서 3건은 3중 확인이 아니다. */
export function hostOf(url) {
  try {
    return new URL(url).host.replace(/^www\./, '');
  } catch {
    return null;
  }
}

/**
 * 등급을 표시용으로 바꾼다.
 *
 * ⚠ 모르는 값이나 누락을 **조용히 넘기지 않는다.** 「등급 표기 없음」이라고 쓰는 것이
 * 아무것도 안 쓰는 것보다 정직하다 — 빈 자리는 「확정」처럼 읽힌다.
 */
export function gradeView(grade) {
  return GRADE_VIEW[grade] ?? { ...GRADE_MISSING, raw: grade ?? null };
}

/** 숫자 + 단위. 값이 없으면 「미확보」. 0 은 값이므로 살린다. */
export function formatValue(value, unit) {
  if (value === null || value === undefined || Number.isNaN(value)) return '미확보';
  const number = typeof value === 'number' ? value.toLocaleString('ko-KR') : String(value);
  const label = unit ? (UNIT_VIEW[unit] ?? unit) : null;
  return label ? `${number} ${label}` : number;
}

/**
 * 큰 원화를 「10.25억원」처럼 줄인다. **원값을 대체하지 않고 곁들이는 용도**다 —
 * 자릿수를 줄여 보여 주면 가정 4개가 곱해진 수가 정밀해 보인다.
 */
export function abbreviateKrw(value) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return null;
  const units = [[1e12, '조'], [1e8, '억'], [1e4, '만']];
  for (const [size, name] of units) {
    if (Math.abs(value) >= size) {
      return `${(value / size).toLocaleString('ko-KR', { maximumFractionDigits: 2 })}${name}원`;
    }
  }
  return null;
}

/**
 * 서버가 준 「못 찾은 것」 한 덩이를 갈래·항목으로 편다.
 * `detail` 은 줄바꿈으로 이은 항목 목록이다(`serialize._not_found_blocks`).
 */
function normalizeNotFound(raw) {
  const key = text(raw?.item) ?? '(항목 없음)';
  const known = NOT_FOUND_VIEW[key];
  const entries = (text(raw?.detail) ?? '').split('\n').map((s) => s.trim()).filter(Boolean);
  return {
    key,
    // 모르는 키를 조용히 한 갈래에 밀어 넣지 않는다 — 다음 판에 잘못된 서랍에서 잠든다.
    group: known ? known[0] : null,
    label: known ? known[1] : key,
    tone: known ? NOT_FOUND_GROUP[known[0]].tone : 'danger',
    entries,
    count: entries.length,
  };
}

function normalizeEvidence(raw) {
  return {
    id: text(raw?.id) ?? '(id 없음)',
    kind: text(raw?.kind) ?? '알 수 없음',
    metric: text(raw?.metric),
    subject: text(raw?.subject),
    period: text(raw?.period),
    value: typeof raw?.value === 'number' ? raw.value : null,
    unit: text(raw?.unit),
    grade: text(raw?.grade),
    gradeReason: text(raw?.gradeReason),
    sourceUrl: text(raw?.sourceUrl),
    sourceKind: text(raw?.sourceKind),
    retrievedAt: text(raw?.retrievedAt),
    quote: text(raw?.quote),
    // **경계는 값과 한 몸이다.** 빈 배열로 두어 화면이 «없음»과 «안 실림»을 헷갈리지 않게.
    caveats: list(raw?.caveats).filter(Boolean),
    formula: text(raw?.formula),
    inputs: raw?.inputs && typeof raw.inputs === 'object' ? raw.inputs : null,
    materialIds: list(raw?.materialIds),
    assumptions: list(raw?.assumptions),
    // ── 판 ㊸ — 절 배치가 **서버 것**이 됐다 ────────────────────
    // 이 다섯이 오기 전에는 `bucketEvidence` 가 여기서 같은 물음을 다시 풀었고,
    // 두 화면이 같은 근거를 다른 과목이라고 말할 위험을 코드가 스스로 적어 뒀다.
    section: text(raw?.section),
    placement: text(raw?.placement),
    issuer: text(raw?.issuer),
    // 어느 행이 한 표인가. 없으면 「합 100.0%」도 「⚠ 100%가 아니다」도 못 만든다.
    tableKey: text(raw?.tableKey),
    // 원문 수 표기(`36,745억원`). 환산값만으로는 원문을 되짚을 수 없다.
    raw: text(raw?.raw),
  };
}

/**
 * 계산식 한 항. ⚠ **여기서 판정하지 않는다** — `basis`(관측·가정·가설)는 서버가 정한다.
 * 화면이 값을 보고 다시 가르면 두 구현이 갈라지고, 갈라지는 순간 표의 판정이 거짓이 된다.
 */
function normalizeFactor(raw) {
  return {
    name: text(raw?.name) ?? '(이름 없음)',
    value: typeof raw?.value === 'number' ? raw.value : null,
    unit: text(raw?.unit),
    basis: text(raw?.basis) ?? '가정',
    note: text(raw?.note),
    bound: text(raw?.bound),
    falsifiedIf: text(raw?.falsifiedIf),
    sourceCount: typeof raw?.sourceCount === 'number' ? raw.sourceCount : 0,
    sourceDomains: list(raw?.sourceDomains),
    caveats: list(raw?.caveats),
  };
}

function normalizeFigure(raw) {
  if (!raw || typeof raw !== 'object') return null;
  return {
    value: typeof raw.value === 'number' ? raw.value : null,
    unit: text(raw.unit),
    grade: text(raw.grade),
    formula: text(raw.formula),
    // 계산식의 항. 이것이 있으면 화면은 문장이 아니라 **표**로 그린다.
    factors: list(raw.factors).map(normalizeFactor),
    // 표가 말할 수 없는 해석 경계만 남는다(예: 「연평균이 아니다」). 요인이 없는
    // 옛 결과에서는 여기에 가정 문장이 통째로 온다 — 그때는 표 대신 이것을 그린다.
    assumptions: list(raw.assumptions),
    caveats: list(raw.caveats),
    evidenceIds: list(raw.evidenceIds),
  };
}

/**
 * 서버 result → 화면이 읽는 모양.
 *
 * 두 모드가 같은 봉투를 쓰고 해당 없는 칸은 `null` 이다. 그래서 한 함수로 받는다.
 */
export function normalizeMarketResult(raw) {
  if (!raw || typeof raw !== 'object') return null;
  const evidence = list(raw.evidence).map(normalizeEvidence);
  const byId = new Map(evidence.map((item) => [item.id, item]));

  const canvas = raw.canvas && Array.isArray(raw.canvas.cells)
    ? CANVAS_LAYOUT.map((slot) => {
      const found = raw.canvas.cells.find((cell) => cell?.canvasCell === slot.cell);
      const [kind, origin] = CELL_KIND[slot.cell] ?? ['관측', ''];
      const evidenceIds = list(found?.marketEvidenceIds);
      return {
        ...slot,
        // 「관측」/「계획」 — 이 구분이 없으면 계획 칸의 «근거 없음» 이 조사 실패로 읽힌다.
        kind,
        origin,
        status: text(found?.status) ?? 'UNVERIFIED',
        content: list(found?.content),
        reason: text(found?.reason) ?? '사유가 오지 않았다',
        sourceLabels: list(found?.sourceLabels),
        evidenceIds,
        // 근거를 «그것을 쓴 칸» 옆에 붙여 둔다. 화면이 id 로 다시 찾아 헤매지 않게.
        // ⚠ 봉투에 없는 id 는 조용히 사라진다 — 그건 칸의 `missingEvidence` 가 말할 일이다.
        evidence: evidenceIds.map((id) => byId.get(id)).filter(Boolean),
        missingEvidence: list(found?.missingEvidence),
        caveats: list(found?.caveats),
        // 칸이 아예 안 온 경우를 «미확인»과 구분한다 — 다른 사건이다.
        absent: !found,
      };
    })
    : null;

  return {
    runId: text(raw.runId),
    conceptId: text(raw.conceptId),
    asOf: text(raw.asOf),
    mode: text(raw.mode),
    stages: list(raw.stages),
    degradations: list(raw.degradations),
    scorecard: Array.isArray(raw.scorecard)
      ? raw.scorecard.map((item) => ({
        subject: text(item?.subject) ?? 'UNKNOWN',
        label: SUBJECT_LABEL[item?.subject] ?? item?.subject ?? '알 수 없는 과목',
        state: text(item?.state) ?? 'MISSING',
        detail: text(item?.detail) ?? '',
      }))
      : null,
    market: raw.market
      ? {
        tam: normalizeFigure(raw.market.tam),
        sam: normalizeFigure(raw.market.sam),
        som: normalizeFigure(raw.market.som),
        growth: normalizeFigure(raw.market.growth),
        price: raw.market.price
          ? {
            min: raw.market.price.min ?? null,
            base: raw.market.price.base ?? null,
            max: raw.market.price.max ?? null,
            currency: text(raw.market.price.currency),
            baseKind: text(raw.market.price.baseKind),
            // ⚠ 이 문장을 화면에서 빼지 마라. 「잠정 대표값」이 사라지면 확정 단가로 읽힌다.
            baseNote: text(raw.market.price.baseNote),
            grade: text(raw.market.price.grade),
            caveats: list(raw.market.price.caveats),
            evidenceIds: list(raw.market.price.evidenceIds),
          }
          : null,
        notFound: list(raw.market.notFound).map(normalizeNotFound),
        coverageCaveat: text(raw.market.coverageCaveat),
      }
      : null,
    canvas,
    bm: raw.bm && typeof raw.bm === 'object'
      ? {
        decision: text(raw.bm.decision),
        gateReasons: Array.isArray(raw.bm.gateReasons)
          ? raw.bm.gateReasons.map((reason) => ({
            code: text(reason?.code),
            cell: text(reason?.cell),
            message: text(reason?.message) ?? '사유 없음',
            evidenceIds: list(reason?.evidenceIds),
            // 옛 결과에는 없다 — 그때는 「판별 불가」다. undefined 면 화면이 터진다.
            cause: text(reason?.cause) ?? 'UNMAPPED',
          }))
          : [],
        confidence: text(raw.bm.confidence),
        summary: text(raw.bm.summary),
        marketFitStatus: text(raw.bm.marketFitStatus),
        marketFitSummary: text(raw.bm.marketFitSummary),
        consistencyStatus: text(raw.bm.consistencyStatus),
        consistencySummary: text(raw.bm.consistencySummary),
        strengths: list(raw.bm.strengths),
        weaknesses: list(raw.bm.weaknesses),
        risks: list(raw.bm.risks),
        legal: raw.bm.legal ?? null,
        financialHandoff: raw.bm.financialHandoff ?? null,
      }
      : null,
    // 칸별 종합 요약. **예전엔 여기서 통째로 떨어뜨렸다** — 봉투엔 있는데 화면에 없었다.
    summary: Array.isArray(raw.summary)
      ? raw.summary.map((line) => ({
        cell: text(line?.cell),
        sentence: text(line?.sentence) ?? '요약 없음',
        cardIds: list(line?.cardIds),
      }))
      : null,
    evidence,
    evidenceById: byId,
    usedIn: usedInIndex(raw, evidence),
    notes: list(raw.notes),
    // ── 판 ㊸ — 사람 보고서의 2·8·9절 ────────────────────────────
    // ⚠ **`null` 과 빈 배열은 다른 사건이다.** null 은 「이 실행은 안 돌렸다」,
    //   빈 배열은 「돌렸는데 할 말이 없었다」. 화면이 그 둘을 다르게 말해야 한다.
    judgment: raw.judgment
      ? {
        price: typeof raw.judgment.price === 'number' ? raw.judgment.price : null,
        lines: list(raw.judgment.lines).map((line) => ({
          what: text(line?.what) ?? '무엇 없음',
          sentence: text(line?.sentence),
          formula: text(line?.formula),
          // 못 쓴 이유도 값이다. 침묵을 「해당 없음」으로 읽히게 두지 않는다.
          silentBecause: text(line?.silentBecause),
          // ⚠ **연도를 빼지 마라.** 「배달 한 끼 8,244원」의 자장면값이 2018년인데
          //    결론 문장에는 그 사실이 없어 오늘 값처럼 읽혔다.
          sources: list(line?.sources).map((s) => ({
            raw: text(s?.raw) ?? '', subject: text(s?.subject) ?? '',
            period: text(s?.period), url: text(s?.url) ?? '',
          })),
        })),
        // ⚠ 이 문장을 빼지 마라. 계산식만 남으면 「1.37배」로 끝나고
        //   「그래서 어느 쪽으로 팔라」가 사라진다 — 사업가가 사는 것은 뒤쪽이다.
        conclusion: text(raw.judgment.conclusion),
      }
      : null,
    prescriptions: Array.isArray(raw.prescriptions)
      ? raw.prescriptions.map((row) => ({
        section: text(row?.section) ?? 'UNKNOWN',
        kind: text(row?.kind) ?? 'UNKNOWN',
        kindLabel: text(row?.kindLabel) ?? '갈래 없음',
        what: text(row?.what) ?? '',
        why: text(row?.why) ?? '',
        where: text(row?.where) ?? '',
      }))
      : null,
    synthesis: Array.isArray(raw.synthesis)
      ? raw.synthesis.map((row) => ({
        key: text(row?.key) ?? '',
        stance: text(row?.stance) ?? '미상',
        sentence: text(row?.sentence) ?? '',
        what: text(row?.what) ?? '',
        sources: list(row?.sources).map((s) => ({
          raw: text(s?.raw) ?? '', subject: text(s?.subject) ?? '', period: text(s?.period),
        })),
      }))
      : null,
    // ── 봉투가 실어 주는 «보고서 글» ────────────────────────────
    // ⚠ **`null` 일 수 있다.** 그때 화면은 글 없이 지금 모양 그대로 선다.
    // ⚠ `unverifiedNumbers`·`conceptLeaks` 는 **경고의 근거**다. 안 왔으면 0 으로 두되,
    //    「AI 가 쓴 글」이라는 사실 자체는 수와 상관없이 늘 말한다.
    report: raw.report && typeof raw.report === 'object'
      ? {
        writtenBy: text(raw.report.writtenBy),
        unverifiedNumbers: count(raw.report.unverifiedNumbers),
        conceptLeaks: count(raw.report.conceptLeaks),
        // ⚠ **머리말은 경계 표시다** — 재료 건수·쓴 모델·인용 대조 여부·유령 수가 여기 있다.
        //    ⚠ 꼬리말은 **지금 늘 `null`** 이다(도구가 아직 안 쓴다). 없으면 안 그린다.
        lead: text(raw.report.lead),
        tail: text(raw.report.tail),
        sections: list(raw.report.sections).map((section) => ({
          subject: text(section?.subject) ?? 'UNKNOWN',
          markdown: text(section?.markdown) ?? '',
        })),
      }
      : null,
  };
}

/** 절 하나의 보고서 글. 없으면 `null` — 화면이 그 절만 글 없이 선다. */
export function reportMarkdown(report, subject) {
  if (!report) return null;
  const found = report.sections.find((section) => section.subject === subject);
  return found && found.markdown ? found.markdown : null;
}

/** 공백·기호를 접고 비교한다. 발췌가 같은 말을 두 칸에 넣는 일이 잦다. */
function 같은값(a, b) {
  const n = (x) => String(x ?? '').replace(/[\s,·]/g, '').toLowerCase();
  return Boolean(n(a)) && n(a) === n(b);
}

/**
 * 표·카드의 <b>「구분」 칸에 쓰는 이름.</b>
 *
 * <p>⚠ <b>`subject` 와 `metric` 은 둘 다 없을 수 있다.</b> 그대로 이어 붙이면 화면에
 * <b>「null · null」</b>이 찍힌다 — 2026-08-15 실측(7절 규제 표 첫 줄, 발췌가 값만 건지고
 * 이름을 못 건진 카드). 같은 이유로 절 머리 카드의 라벨이 <b>빈칸</b>으로 서서
 * 「미확보」라는 값만 덩그러니 떴다.
 */
export function factName(item) {
  if (같은값(item.subject, item.metric)) return item.metric;
  return [item.subject, item.metric].filter(Boolean).join(' · ') || '(이름이 오지 않았어요)';
}

/**
 * 근거 id → **그 근거가 실제로 쓰인 자리** 목록.
 *
 * 왜 필요한가: 화면에 12조짜리 「네이버 전사 매출」이 있는데 TAM 에는 안 들어가 있다.
 * 이 열이 없으면 읽는 사람은 그 12조가 시장 규모의 근거라고 읽는다. **「쓰인 곳 없음」이
 * 값 그 자체보다 중요한 정보**인 자리다.
 */
function usedInIndex(raw, evidence) {
  const index = new Map();
  const add = (id, where) => {
    if (!id) return;
    const seen = index.get(id) ?? [];
    if (!seen.includes(where)) seen.push(where);
    index.set(id, seen);
  };
  const market = raw?.market ?? {};
  for (const [key, where] of [['tam', 'TAM'], ['sam', 'SAM'], ['som', 'SOM'], ['growth', '성장률']]) {
    for (const id of list(market[key]?.evidenceIds)) add(id, where);
  }
  for (const id of list(market.price?.evidenceIds)) add(id, '가격');
  // 계산 카드의 재료 — 「이 관측이 저 계산을 떠받쳤다」.
  for (const card of evidence) {
    for (const id of card.materialIds) add(id, card.id);
  }
  return index;
}

/**
 * 근거를 성적표 «과목» 별로 가른다 — 과목이 곧 화면의 목차이기 때문이다.
 *
 * ⚠ **임시 분류다.** 봉투의 근거에 과목 필드가 없어서 화면이 되짚는다.
 * 어느 과목에도 안 걸린 관측은 「수요 근거」로 떨어진다 — **버리지 않는다는 것이 요점**이다.
 * 서버가 과목을 실어 주면 이 함수는 통째로 없어진다.
 */
/**
 * 근거를 <b>과목별로</b> 나눈다. 판 ㊸ 부터 서버가 `evidence[].section` 을 준다.
 *
 * <p>⚠ 옛 결과는 그 칸이 없다(DB 에 저장돼 있다). 그래서 <b>한 건도 절이 없을 때만</b>
 * 옛 셈으로 물러선다 — 섞어 쓰면 새 결과의 채널 사실이 「그 밖」으로 떨어진다.
 */
export function sectionEvidence(result) {
  const items = list(result?.evidence);
  if (!items.some((item) => item.section)) return bucketByGuess(result);
  const out = {};
  for (const key of Object.keys(SUBJECT_LABEL)) out[key] = [];
  for (const item of items) {
    if (out[item.section]) out[item.section].push(item);
  }
  return out;
}



/**
 * <b>절 머리만 고른다.</b> 봉투의 `placement` 가 「밖」이면 서랍(참고)이다.
 *
 * <p>⚠ <b>이 구분이 없으면 「원가와 수익성」 표에 넥슨 영업이익률이 앉는다</b>
 * (2026-08-15 실측 — 머리 17건 중 4건이 게임회사였다). 서랍은 「버리지 않는다」의 결과라
 * 화면에 남지만, <b>절의 답으로 읽히면 안 된다.</b>
 *
 * <p>정렬은 ① 등급 ② 같은 표끼리(`tableKey`) ③ 최신 연도. 목표 보고서가 절마다
 * <b>한 표</b>를 세우는 모양을 그대로 만든다.
 */
export function headFacts(rows) {
  // ★ **줄 세우기를 여기서 «다시» 하지 않는다** (판 ㊺).
  //
  // 서버(`promote_cards.build`)가 이미 「갈래 → 절 표지 적중 → 등급」으로 세워서 보낸다.
  // 화면이 또 세우면 **같은 물음을 두 곳이 각자 푸는** 그 함정이고, 실제로 한 번 밟았다 —
  // 화면이 등급을 먼저 보는 바람에 「지역자율형바우처 20억원」(확정)이
  // 「가정간편식 판매액 6조 8천억」(추정)을 이겼다. 그 판정은 **어휘를 아는 쪽**이 해야 한다.
  //
  // 여기서 하는 일은 **거르기뿐**이다.
  return list(rows).filter((item) => item?.placement && item.placement !== '밖');
}

/** 서랍(참고). <b>버리지 않되 절의 답으로 세우지 않는다.</b> */
export function drawerFacts(rows) {
  return list(rows).filter((item) => item?.placement === '밖');
}

/** ⚠ **판 ㊸ 이전 결과 전용 폴백.** 새 결과에는 쓰이지 않는다. */
export function bucketEvidence(result) {
  const market = result?.market ?? {};
  const idsOf = (...figures) => new Set(figures.flatMap((f) => list(f?.evidenceIds)));
  const sizeIds = idsOf(market.tam, market.sam, market.som);
  const growIds = idsOf(market.growth);
  const priceIds = idsOf(market.price);

  const buckets = { size: [], grow: [], comp: [], price: [], demand: [], calc: [] };
  for (const item of list(result?.evidence)) {
    if (item.kind === '계산') buckets.calc.push(item);
    else if (COMP_METRICS.includes(item.metric)) buckets.comp.push(item);
    else if (priceIds.has(item.id)) buckets.price.push(item);
    else if (sizeIds.has(item.id)) buckets.size.push(item);
    else if (growIds.has(item.id)) buckets.grow.push(item);
    else buckets.demand.push(item);
  }
  return buckets;
}

/** `bucketEvidence` 의 갈래 → 성적표 과목. 두 이름을 잇는 표는 여기 하나다. */
const BUCKET_SUBJECT = {
  size: 'MARKET_SIZE', grow: 'GROWTH', comp: 'COMPETITOR',
  price: 'PRICE', demand: 'DEMAND', calc: 'CALCULATION',
};

/** 옛 결과용 폴백. 이름을 갈라 「지금 쓰는 것」과 「물러설 자리」를 구분한다. */
function bucketByGuess(result) {
  const bag = bucketEvidence(result);
  const out = {};
  for (const key of Object.keys(SUBJECT_LABEL)) out[key] = [];
  for (const [bucket, subject] of Object.entries(BUCKET_SUBJECT)) out[subject] = bag[bucket];
  return out;
}

/**
 * 근거 id → <b>그 근거가 사는 과목</b>. `bucketEvidence` 를 <b>거꾸로</b> 읽는다.
 *
 * <p>화면 2 의 「근거 보기」가 화면 1 의 어느 줄로 착지할지 정하는 표다. 분류를 여기서
 * 다시 짜면 두 화면이 같은 근거를 다른 과목이라고 말한다 — 그래서 새로 만들지 않고
 * 같은 함수를 쓴다.
 */
export function evidenceSubjectIndex(result) {
  const index = new Map();
  // ⚠ `sectionEvidence` 를 쓴다 — 화면 1 과 **같은 함수**여야 한다.
  //   여기서 따로 세면 「근거 보기」가 그 근거가 없는 줄로 착지한다.
  const buckets = sectionEvidence(result);
  for (const [subject, items] of Object.entries(buckets)) {
    for (const item of items) index.set(item.id, subject);
  }
  return index;
}

/**
 * <b>접힌 과목 → 그것을 품은 절.</b>
 *
 * <p>⚠ 판 ㊺ 에서 성장률·계산이 1절 «안»으로, 「찾지 못한 것」이 8절로 접혔다.
 * 이 표가 없으면 「근거 보기 — 시장 분석 <b>{null}</b>. 성장률」이 찍히고
 * (React 가 `null` 을 안 그려 <b>주인 없는 마침표</b>만 남는다),
 * 눌러도 `getElementById('sec-GROWTH')` 가 없어 <b>화면만 바뀌고 아무 데도 안 간다.</b>
 * 감사에서 잡힌 자리다 — 시장 테스트 99개가 전부 통과하는 채로 깨져 있었다.
 */
export const SECTION_PARENT = {
  GROWTH: 'MARKET_SIZE',
  CALCULATION: 'MARKET_SIZE',
  NOT_FOUND: 'GAPS',
};

/** 그 과목이 화면에서 <b>실제로 서는 자리</b>. 접힌 것은 부모 절로 접는다. */
export function sectionAnchor(subject) {
  return SECTION_PARENT[subject] ?? subject;
}

/**
 * 절 번호(1부터). <b>접힌 과목은 그것을 품은 절의 번호</b>를 돌려준다.
 * 목차에도 없고 부모도 없는 것만 `null` 이다.
 */
export function subjectNumber(subject) {
  const at = SECTION_ORDER.indexOf(sectionAnchor(subject));
  return at < 0 ? null : at + 1;
}

/** 「S13 — 네이버 예약 · 매출액 (2025, 원) · …」 한 줄에서 (회사, 지표) 를 뽑는다. */
const SLOT_LINE = /^\S+\s+—\s+([^·]+)·\s*([^(]+)\(/;

/**
 * **못 찾은 경쟁사 슬롯**을 (회사, 지표) 쌍으로 편다.
 *
 * 왜: 경쟁사 카드에 관측된 지표만 그리면 「이 회사는 이것만 알아냈다」가 아니라
 * 「이 회사는 이게 전부다」로 읽힌다. 못 찾은 칸을 **같은 카드 안에** 세워야
 * 조사 범위와 조사 결과가 구분된다.
 */
export function competitorGaps(notFound) {
  const gaps = [];
  for (const block of list(notFound)) {
    if (block.key !== 'empty_slots' && block.key !== 'thin_slots') continue;
    for (const line of list(block.entries)) {
      const match = SLOT_LINE.exec(line);
      if (!match) continue;
      const [, subject, metric] = match;
      if (COMP_METRICS.includes(metric.trim())) gaps.push([subject.trim(), metric.trim()]);
    }
  }
  return gaps;
}
