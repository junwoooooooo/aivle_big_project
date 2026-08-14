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

/** 캔버스 칸 상태. */
export const CELL_STATUS_VIEW = {
  VERIFIED: { label: '확인됨', tone: 'success' },
  PARTIAL: { label: '일부 확인', tone: 'warning' },
  UNVERIFIED: { label: '미확인', tone: 'danger' },
  PLAN: { label: '계획(근거 없음)', tone: 'neutral' },
  BLOCKED: { label: '진행 불가', tone: 'danger' },
};

export const DECISION_VIEW = {
  PASS: { label: '통과', tone: 'success' },
  CONDITIONAL: { label: '조건부', tone: 'warning' },
  REVISION_REQUIRED: { label: '수정 필요', tone: 'danger' },
  BLOCKED: { label: '진행 불가', tone: 'danger' },
};

export const SUBJECT_LABEL = {
  MARKET_SIZE: '시장 크기',
  GROWTH: '성장률',
  COMPETITOR: '경쟁사',
  PRICE: '가격',
  DEMAND: '수요 근거',
  CALCULATION: '시장 규모 계산',
  NOT_FOUND: '못 찾은 것',
};

export const SCORE_STATE_VIEW = {
  FILLED: { label: '채워짐', tone: 'success' },
  PARTIAL: { label: '부분', tone: 'warning' },
  MISSING: { label: '미확보', tone: 'danger' },
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
  NOT_YET: { label: '아직 못 채운 것', note: '더 찾으면 나올 수 있다', tone: 'warning' },
  ASSUMED: { label: '가정으로 메운 변수', note: '자료가 아니라 판단이 필요하다', tone: 'warning' },
  CONFIRMED_ABSENT: { label: '찾아도 없는 것', note: '여기가 종착이다', tone: 'neutral' },
  SCREENED_OUT: { label: '찾았지만 쓰지 않은 것', note: '규격 미달로 걸러냈다', tone: 'info' },
  DIVERGED: { label: '값이 갈린 것', note: '그 자체가 조사 결과다', tone: 'info' },
};

/** 진단 키 → 갈래 + 사람이 읽는 이름. 모르는 키는 원문 노출 + `danger` 로 **드러낸다.** */
export const NOT_FOUND_VIEW = {
  empty_slots: ['NOT_YET', '근거를 하나도 못 찾은 조사 칸'],
  thin_slots: ['NOT_YET', '근거가 기준에 못 미치는 조사 칸'],
  retry_hints: ['NOT_YET', '재조사 힌트 — 자동으로 돌지 않는다'],
  url_filtered: ['NOT_YET', '열지 않고 거른 후보'],
  unknown_error_codes: ['NOT_YET', '분류하지 못한 외부 응답'],
  unfilled_vars: ['ASSUMED', '관측 없이 가정으로 채운 변수'],
  suspect_var: ['ASSUMED', '재조사 1순위 변수'],
  independent_topdown_blocked: ['CONFIRMED_ABSENT', '위에서 아래로 재는 길이 막혔다'],
  '자료_부재_확정': ['CONFIRMED_ABSENT', '그 형태로 발행되지 않는 자료'],
  adapters: ['CONFIRMED_ABSENT', '설정되지 않은 수집 경로'],
  off_slot: ['SCREENED_OUT', '조사 칸과 안 맞아 격리한 근거'],
  contradictions: ['DIVERGED', '같은 대상·단위인데 값이 갈렸다'],
  unit_mismatch: ['DIVERGED', '단위가 어긋나 멈춘 계산'],
  range_capped: ['DIVERGED', '범위 상한에 부딪힌 추정'],
  skipped_checks: ['DIVERGED', '선행 규칙 위반으로 건너뛴 검사'],
};

/**
 * 9칸을 «의미»로 세 묶음 — 4·3·2.
 *
 * ⚠ 표준 BMC 5열 배치는 **포스터 판형**이다. 본문 폭 1150px 에서 칸당 195px 밖에 안 나와
 * 한글이 10~12자마다 끊기고, 2행을 차지하는 칸 옆에는 250px 짜리 빈 구멍이 생긴다.
 * 4·3·2 는 칸당 280px 이상을 주고 행 병합을 없앤다.
 *
 * 순서는 장식이 아니다 — 누구에게(고객·가치) → 어떻게 만들고(실행) → 돈은(수익·비용).
 */
export const CANVAS_BANDS = [
  ['고객과 가치', ['CUSTOMER_SEGMENTS', 'VALUE_PROPOSITIONS', 'CHANNELS', 'CUSTOMER_RELATIONSHIPS']],
  ['실행 구조', ['KEY_ACTIVITIES', 'KEY_RESOURCES', 'KEY_PARTNERS']],
  ['수익과 비용', ['REVENUE_STREAMS', 'COST_STRUCTURE']],
];

const CANVAS_CELL_LABEL = {
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

/** 배치 순서 = 밴드를 편 순서. 캔버스도 칸별 세부 목록도 이 순서를 따른다. */
export const CANVAS_LAYOUT = CANVAS_BANDS
  .flatMap(([, cells]) => cells)
  .map((cell) => ({ cell, label: CANVAS_CELL_LABEL[cell] }));

/**
 * 칸의 «성격». 정본은 AI 쪽 `research2/harness/vocab.json` 의 canvas 라우팅 표다.
 *
 * ⚠ **9칸을 전부 근거로 채우는 것은 설계가 아니다.** 「계획」 칸은 조사 슬롯이 «불필요»하고
 * 컨셉 서술·입력 제약에서 온다. 화면이 이 구분을 안 하면 정상 결과가 미완성으로 읽힌다.
 */
export const CELL_KIND = {
  CUSTOMER_SEGMENTS: ['관측', '시장조사가 근거로 채운다'],
  VALUE_PROPOSITIONS: ['관측', '시장조사가 근거로 채운다'],
  CHANNELS: ['관측', '시장조사가 근거로 채운다'],
  REVENUE_STREAMS: ['관측', '시장조사가 근거로 채운다'],
  CUSTOMER_RELATIONSHIPS: ['계획', '컨셉 서술에서 온다'],
  KEY_RESOURCES: ['계획', '컨셉 서술에서 온다'],
  KEY_ACTIVITIES: ['계획', '컨셉 서술에서 온다'],
  KEY_PARTNERS: ['계획', '컨셉 서술에서 온다'],
  COST_STRUCTURE: ['계획', '입력 제약(예산·팀·기간)에서 온다'],
};

/** 캔버스 상태 → 점 색. 배지 라벨은 `CELL_STATUS_VIEW` 가 따로 갖는다. */
export const CELL_DOT = {
  VERIFIED: 'ok', PARTIAL: 'mid', UNVERIFIED: 'none', PLAN: 'off', BLOCKED: 'none',
};

/** 경쟁사 지표로 취급하는 계량. ⚠ 임시 — 봉투에 과목 필드가 생기면 이 표는 없어진다. */
export const COMP_METRICS = [
  '가입 매장 수', '누적 가입자 수', '매출액', '이용 요금', '월 활성 사용자',
];

const list = (value) => (Array.isArray(value) ? value : []);
const text = (value) => (typeof value === 'string' && value.trim() ? value : null);

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
  };
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
