/**
 * BM 실행 계획 — 화면이 받는 칸의 정본.
 *
 * <p><b>왜 이 넷뿐인가.</b> 컨셉 계약(`시장조사/문서/계약/2_입구계약서…` §1)이 주는 것은
 * 필수 5 · 다듬기 5 · 가설 4(수익·가격, 채널, 차별점, SOM) · 선택(지역·가격가설·제약·
 * 업종분류·경쟁씨앗)이다. 거기에 <b>활동·자원·파트너·고객 관계가 없다.</b>
 * 수익모델·채널·차별점은 가설 4가 이미 사용자 승인을 거치므로 여기서 또 묻지 않는다 —
 * 물으면 사용자가 아이디어 단계에서 친 것을 다시 치게 된다.
 *
 * <p>⚠ 키 이름은 AI 쪽 `bm_adapter.PLAN_FIELDS` 와 같아야 한다. 다르면 조용히 안 실린다.
 */

/** 계획 칸. `[key, 묻는 말, 어느 칸을 채우나, 도움말]` — 렌더는 이 표를 돈다. */
export const PLAN_FIELDS = Object.freeze([
  ['customer_relationship', '고객 관계 유지 방식', '고객 관계',
    '서비스 이용 뒤에도 관계를 이어가는 방법을 적어 주세요. 예: 예약 알림, 정기 안내, 고객 지원, 재구매 혜택'],
  ['key_activities', '사업 운영에서 반복적으로 해야 하는 일', '핵심 활동',
    '서비스를 제공하기 위해 계속 수행해야 하는 핵심 업무를 한 줄에 하나씩 적어 주세요.'],
  ['key_resources', '사업 운영에 꼭 필요한 자원', '핵심 자원',
    '시스템, 장비, 데이터, 인력 등 없으면 운영하기 어려운 자원을 한 줄에 하나씩 적어 주세요.'],
  ['key_partners', '필요한 파트너', '핵심 파트너',
    '회사명이 정해지지 않았다면 필요한 역할이나 유형만 적어도 됩니다. 예: 결제 대행사, 물류 파트너, 전문 자격 보유 업체'],
]);

/** 여러 줄로 받는 칸. 나머지는 문장 하나다. */
export const LIST_FIELDS = Object.freeze(['key_activities', 'key_resources', 'key_partners']);

/** 비용 구조 칸. `[key, 라벨, 단위, 아이디어 단계의 대응 칸]` */
export const CONSTRAINT_FIELDS = Object.freeze([
  ['budget_krw', '사용 가능한 예산', '원', '예산'],
  ['months', '준비 가능한 기간', '개월', '기간'],
  ['team', '투입 가능한 인원', '명', '인원'],
]);

const conceptCandidate = (concept) => concept?.candidate?.candidate ?? concept?.candidate ?? {};
const suggestionLines = (...values) => [...new Set(values.flatMap((value) => Array.isArray(value) ? value : value == null || value === '' ? [] : [value])
  .map((value) => String(value).trim()).filter(Boolean))].join('\n');

export function buildConceptPlanSuggestions(concept) {
  const candidate = conceptCandidate(concept);
  return Object.fromEntries([
    ['key_activities', suggestionLines(candidate.operatingModel, candidate.transactionFlow)],
    ['key_resources', suggestionLines(candidate.platformRole, candidate.featureSet)],
    ['key_partners', suggestionLines(candidate.partnerModel, candidate.partnerRequirements)],
  ].filter(([, value]) => value));
}

/** 계획 키 → 캔버스 칸. 미리보기와 확인 문구가 같은 표를 본다. */
export const PLAN_CELL = Object.freeze({
  customer_relationship: 'CUSTOMER_RELATIONSHIPS',
  key_activities: 'KEY_ACTIVITIES',
  key_resources: 'KEY_RESOURCES',
  key_partners: 'KEY_PARTNERS',
  constraint: 'COST_STRUCTURE',
});

/** 화면이 들고 있는 초안의 빈 모양. 목록은 **문자열**로 들고 있다가 보낼 때 가른다. */
export function emptyDraft() {
  return {
    customer_relationship: '',
    key_activities: '',
    key_resources: '',
    key_partners: '',
    budget_krw: '',
    months: '',
    team: '',
  };
}

/** 서버가 준 저장분 → 화면 초안. 목록은 줄바꿈으로 합친다. */
export function draftFrom(view) {
  const draft = emptyDraft();
  const plan = view?.plan ?? {};
  const constraints = view?.constraints ?? {};
  for (const [key] of PLAN_FIELDS) {
    const value = plan[key];
    draft[key] = Array.isArray(value) ? value.join('\n') : (value ?? '');
  }
  for (const [key] of CONSTRAINT_FIELDS) {
    draft[key] = constraints[key] === undefined || constraints[key] === null
      ? '' : String(constraints[key]);
  }
  return draft;
}

const lines = (text) => String(text ?? '').split('\n').map((s) => s.trim()).filter(Boolean);

/**
 * 초안 → 보낼 값. <b>빈 칸은 키 자체를 만들지 않는다.</b>
 *
 * ⚠ 빈 배열·빈 문자열을 보내면 「안 썼다」와 「비웠다」가 같아지고, 뒷단이 채울 기회를
 * 조용히 뺏는다. 서버도 같은 규칙을 한 번 더 지킨다 — 두 곳이 같은 규칙을 지키는 것이
 * 「층마다 다른 정의」보다 낫다.
 */
export function toPayload(draft) {
  const plan = {};
  for (const [key] of PLAN_FIELDS) {
    if (LIST_FIELDS.includes(key)) {
      const items = lines(draft[key]);
      if (items.length > 0) plan[key] = items;
    } else if (String(draft[key] ?? '').trim()) {
      plan[key] = String(draft[key]).trim();
    }
  }
  const constraints = {};
  for (const [key] of CONSTRAINT_FIELDS) {
    const raw = String(draft[key] ?? '').trim();
    if (!raw) continue;
    // ⚠ **정수만.** 소수는 canonical hash 가 거부한다. 0 으로 지어내지도 않는다 —
    //   숫자가 아니면 안 보낸다.
    const number = Number(raw);
    if (Number.isInteger(number) && number >= 0) constraints[key] = number;
  }
  return { plan, constraints };
}

/** 채워지지 않은 칸의 **사람이 읽는 이름**. 제출 전 확인 문구가 이 목록을 읽는다. */
export function emptyCellNames(draft) {
  const names = PLAN_FIELDS
    .filter(([key]) => (LIST_FIELDS.includes(key)
      ? lines(draft[key]).length === 0
      : !String(draft[key] ?? '').trim()))
    .map(([, , cell]) => cell);
  const costFilled = CONSTRAINT_FIELDS.some(([key]) => String(draft[key] ?? '').trim());
  if (!costFilled) names.push('비용 구조');
  return names;
}
