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
  ['customer_relationship', '고객과 계속 이어지는 방식은 무엇인가요?', '고객 관계',
    '예: 예약 확인·리마인드 자동 발송으로 접점을 유지한다'],
  ['key_activities', '이 사업이 반복해서 해야 하는 일은 무엇인가요?', '핵심 활동',
    '한 줄에 하나씩. 예: 예약 채널을 하나로 통합 운영'],
  ['key_resources', '그 일을 하려면 반드시 있어야 하는 것은 무엇인가요?', '핵심 자원',
    '한 줄에 하나씩. 예: 예치금 결제 연동'],
  ['key_partners', '혼자 못 하는 부분은 누가 맡나요?', '핵심 파트너',
    '한 줄에 하나씩. ⚠ 계약된 상대가 아니라 **필요한 유형**을 적으세요 — 예: 결제 처리 대행'],
]);

/** 여러 줄로 받는 칸. 나머지는 문장 하나다. */
export const LIST_FIELDS = Object.freeze(['key_activities', 'key_resources', 'key_partners']);

/** 비용 구조 칸. `[key, 라벨, 단위, 아이디어 단계의 대응 칸]` */
export const CONSTRAINT_FIELDS = Object.freeze([
  ['budget_krw', '쓸 수 있는 예산', '원', '예산 제약'],
  ['months', '기간', '개월', '일정 제약'],
  ['team', '인원', '명', '팀 제약'],
]);

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
