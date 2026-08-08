export const FIXED_COST_FIELDS = Object.freeze([
  ['annualFixedLaborCost', '연간 고정 인건비'],
  ['annualFixedRentAndManagementCost', '연간 임차·관리비'],
  ['annualFixedInfrastructureCost', '연간 인프라비'],
]);
export const INITIAL_INVESTMENT_FIELDS = Object.freeze([
  ['initialDevelopmentAndRnDCost', '초기 개발·R&D 비용'],
  ['initialEquipmentAndInfrastructureCost', '초기 설비·인프라 비용'],
  ['initialPatentAndLicensingCost', '초기 특허·라이선스 비용'],
]);
export const CAC_FIELDS = Object.freeze([
  ['totalMarketingCost', '총 마케팅비'],
  ['totalSalesCost', '총 영업비'],
]);
export const CONDITIONAL_FIELDS = Object.freeze([
  ['unitVariableCost', '단위 변동비'],
  ['paymentFee', '결제 수수료'],
  ['partnerPayout', '파트너 지급액'],
  ['shippingCost', '배송비'],
  ['customerIncrementalInfraCost', '고객 증가분 인프라비'],
]);
export const TARGET_METRICS = Object.freeze([
  ['salesVolume', '판매량'], ['customerCount', '고객 수'],
  ['subscriberCount', '구독자 수'], ['transactionCount', '거래 건수'],
]);

const MONEY_FIELDS = [...FIXED_COST_FIELDS, ...INITIAL_INVESTMENT_FIELDS, ...CAC_FIELDS, ...CONDITIONAL_FIELDS].map(([key]) => key);

export function createFinancialDraft(fields = {}) {
  const targets = fields.threeYearTargets?.value;
  const draft = Object.fromEntries(MONEY_FIELDS.map((key) => [key, fields[key]?.value?.amount ?? '']));
  return {
    ...draft,
    targetMetric: targets?.metric ?? 'customerCount',
    targetUnit: targets?.unit ?? '명',
    targetYears: [1, 2, 3].map((year) => targets?.years?.find?.((item) => item.year === year)?.value ?? ''),
    newCustomerCount: fields.newCustomerCount?.value ?? '',
  };
}

export function financialValuesFromDraft(draft, fields = {}) {
  const values = {};
  for (const key of MONEY_FIELDS) {
    if (!fields[key]?.readOnly) values[key] = moneyOrNull(draft[key]);
  }
  if (!fields.threeYearTargets?.readOnly) values.threeYearTargets = targetsOrNull(draft);
  if (!fields.newCustomerCount?.readOnly) values.newCustomerCount = numberOrNull(draft.newCustomerCount);
  return values;
}

function targetsOrNull(draft) {
  const unit = String(draft.targetUnit ?? '').trim();
  const years = [1, 2, 3].map((year) => ({ year, value: numberOrNull(draft.targetYears[year - 1]) }));
  if (!unit || years.some((item) => item.value == null)) return null;
  return { metric: draft.targetMetric, unit, years };
}

function moneyOrNull(value) {
  const amount = numberOrNull(value);
  return amount == null ? null : { amount, currency: 'KRW' };
}
function numberOrNull(value) { return String(value ?? '').trim() === '' ? null : Number(value); }
export function formatMoney(value) {
  if (!value || typeof value.amount !== 'number') return '값 없음';
  return `${new Intl.NumberFormat('ko-KR').format(value.amount)} ${value.currency ?? 'KRW'}`;
}
