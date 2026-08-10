export const FACT_LABELS = Object.freeze({
  productServiceSpecification: '제품·서비스 사양', targetLaunchDate: '목표 출시일', ownedPersonnel: '보유 인력',
  ownedAssetsAndFacilities: '보유 자산·설비', fixedOperatingCost: '고정운영비', initialInvestment: '초기투자금',
  threeYearTargets: '3개년 목표',
});

export const DECISION_FIELDS = Object.freeze([
  ['deliveryOrProductionMethod', '생산·개발·서비스 제공 방식'],
  ['expectedMonthlyThroughputOrSales', '예상 월 처리량 또는 판매량'],
  ['technicalSupplyOperationalConstraints', '기술·공급·운영 제약'],
]);

export function createFactDraft(facts = {}) {
  const personnel = facts.ownedPersonnel?.value;
  const targets = facts.threeYearTargets?.value;
  return {
    productSummary: facts.productServiceSpecification?.value?.summary ?? '',
    productFeatures: Array.isArray(facts.productServiceSpecification?.value?.features)
      ? facts.productServiceSpecification.value.features.join('\n') : '',
    targetLaunchDate: facts.targetLaunchDate?.value ?? '',
    personnel: Array.isArray(personnel) ? personnel.map((item) => `${item.role}|${item.count}${item.notes ? `|${item.notes}` : ''}`).join('\n') : '',
    assets: Array.isArray(facts.ownedAssetsAndFacilities?.value) ? facts.ownedAssetsAndFacilities.value.join('\n') : '',
    fixedOperatingCost: facts.fixedOperatingCost?.value?.amount ?? '',
    initialInvestment: facts.initialInvestment?.value?.amount ?? '',
    targetMetric: targets?.metric ?? 'customerCount',
    targetUnit: targets?.unit ?? '명',
    targets: [1, 2, 3].map((year) => targets?.years?.find?.((item) => item.year === year)?.value ?? ''),
  };
}

export function factsFromDraft(draft) {
  return {
    productServiceSpecification: { summary: draft.productSummary.trim(), features: lines(draft.productFeatures) },
    targetLaunchDate: draft.targetLaunchDate,
    ownedPersonnel: String(draft.personnel).split('\n').map((line) => line.trim()).filter(Boolean).map((line) => {
      const [role, count, notes] = line.split('|').map((item) => item?.trim());
      return { role, count: numberOrNull(count), ...(notes ? { notes } : {}) };
    }),
    ownedAssetsAndFacilities: lines(draft.assets),
    fixedOperatingCost: { amount: numberOrNull(draft.fixedOperatingCost), currency: 'KRW', period: 'MONTHLY' },
    initialInvestment: { amount: numberOrNull(draft.initialInvestment), currency: 'KRW' },
    threeYearTargets: {
      metric: draft.targetMetric, unit: draft.targetUnit.trim(),
      years: [1, 2, 3].map((year) => ({ year, value: numberOrNull(draft.targets[year - 1]) })),
    },
  };
}

export function proposalDraft(fieldKey, value) {
  if (fieldKey === 'deliveryOrProductionMethod') return value?.method ?? '';
  if (fieldKey === 'expectedMonthlyThroughputOrSales') return `${value?.amount ?? ''}|${value?.unit ?? ''}`;
  return Array.isArray(value) ? value.join('\n') : '';
}

export function proposalValue(fieldKey, draft) {
  if (fieldKey === 'deliveryOrProductionMethod') return { method: draft.trim(), operatingModel: '', partnerModel: '' };
  if (fieldKey === 'expectedMonthlyThroughputOrSales') {
    const [amount, unit] = draft.split('|').map((item) => item.trim());
    return { amount: numberOrNull(amount), unit };
  }
  return lines(draft);
}

export function lines(value) { return String(value ?? '').split('\n').map((item) => item.trim()).filter(Boolean); }
function numberOrNull(value) { return String(value ?? '').trim() === '' ? null : Number(value); }
export function decisionComplete(value) { return ['ACCEPTED', 'USER_EDITED_ACCEPTED'].includes(value?.decision); }
export function displayValue(value) {
  if (value == null) return '제안 없음';
  if (Array.isArray(value)) return value.join(' · ');
  if (typeof value === 'object') return Object.values(value).filter((item) => item !== '' && item != null).join(' · ');
  return String(value);
}
