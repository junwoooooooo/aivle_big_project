export const SCENARIOS = [
  { code: 'CONSERVATIVE', label: '보수', salesVolumeAdjustment: -20, priceAdjustment: 0, variableCostAdjustment: 10, fixedCostAdjustment: 10 },
  { code: 'BASE', label: '기준', salesVolumeAdjustment: 0, priceAdjustment: 0, variableCostAdjustment: 0, fixedCostAdjustment: 0 },
  { code: 'OPTIMISTIC', label: '낙관', salesVolumeAdjustment: 20, priceAdjustment: 0, variableCostAdjustment: -5, fixedCostAdjustment: -5 },
];
export const INITIAL_ASSUMPTIONS = {
  revenueModel: 'ONE_TIME', unitPrice: '', monthlySalesVolume: '', monthlyGrowthRate: 0,
  unitVariableCost: 0, paymentFeeRate: 0, otherVariableCostPerUnit: 0,
  monthlyLaborCost: 0, monthlyMarketingCost: 0, monthlyInfrastructureCost: 0,
  monthlyRentCost: 0, monthlyOtherFixedCost: 0, initialDevelopmentCost: 0,
  initialEquipmentCost: 0, initialMarketingCost: 0, initialOtherCost: 0,
  monthlySubscriptionPrice: '', initialSubscribers: 0, monthlyNewSubscribers: 0, monthlyChurnRate: 0,
};
export function parseFinancialNumber(value) {
  const normalized = String(value ?? '').replaceAll(',', '').trim();
  if (normalized === '') return null;
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}
export function requestBody(form) { return { ...form, assumptions: Object.fromEntries(Object.entries(form.assumptions).map(([key, value]) => [key, key === 'revenueModel' ? value : parseFinancialNumber(value)])), scenarios: form.scenarios.map((item) => ({ ...item, salesVolumeAdjustment: parseFinancialNumber(item.salesVolumeAdjustment), priceAdjustment: parseFinancialNumber(item.priceAdjustment), variableCostAdjustment: parseFinancialNumber(item.variableCostAdjustment), fixedCostAdjustment: parseFinancialNumber(item.fixedCostAdjustment) })) }; }
export function money(value) { return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW', maximumFractionDigits: 0 }).format(Number(value ?? 0)); }

export function buildFinancialPreview(assumptions) {
  const values = Object.fromEntries(Object.entries(assumptions).map(([key, value]) => [
    key, key === 'revenueModel' ? value : parseFinancialNumber(value),
  ]));
  const hasOneTime = values.revenueModel !== 'SUBSCRIPTION';
  const hasSubscription = values.revenueModel !== 'ONE_TIME';
  if (hasOneTime && (values.unitPrice == null || values.monthlySalesVolume == null)) return null;
  if (hasSubscription && values.monthlySubscriptionPrice == null) return null;
  const oneTimeVolume = hasOneTime ? values.monthlySalesVolume : 0;
  const subscribers = hasSubscription ? (values.initialSubscribers ?? 0) : 0;
  const revenue = (values.unitPrice ?? 0) * oneTimeVolume
    + (values.monthlySubscriptionPrice ?? 0) * subscribers;
  const variableCost = (values.unitVariableCost ?? 0) * (oneTimeVolume + subscribers)
    + (values.otherVariableCostPerUnit ?? 0) * (oneTimeVolume + subscribers)
    + revenue * ((values.paymentFeeRate ?? 0) / 100);
  const fixedCost = ['monthlyLaborCost', 'monthlyMarketingCost', 'monthlyInfrastructureCost', 'monthlyRentCost', 'monthlyOtherFixedCost']
    .reduce((total, key) => total + (values[key] ?? 0), 0);
  const initial = ['initialDevelopmentCost', 'initialEquipmentCost', 'initialMarketingCost', 'initialOtherCost']
    .reduce((total, key) => total + (values[key] ?? 0), 0);
  return {
    scenarios: [{
      code: 'BASE',
      totalRevenue: revenue,
      totalOperatingProfit: revenue - variableCost - fixedCost,
      breakEvenMonth: null,
      paybackMonth: null,
      requiredWorkingCapital: initial,
    }],
  };
}
