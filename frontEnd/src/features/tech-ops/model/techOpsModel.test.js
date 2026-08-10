import { describe, expect, it } from 'vitest';
import { createFactDraft, factsFromDraft, proposalValue } from './techOpsModel.js';

describe('tech ops input model', () => {
  it('maps user facts into the structured preparation contract', () => {
    const values = factsFromDraft({ productSummary: '서비스', productFeatures: '기능 A\n기능 B', targetLaunchDate: '2027-03-01', personnel: '개발|2|내부\n운영|0',
      assets: '클라우드 계정\n현재 보유 설비 없음', fixedOperatingCost: '1200000', initialInvestment: '30000000',
      targetMetric: 'customerCount', targetUnit: '명', targets: ['100', '500', '1500'] });
    expect(values.ownedPersonnel).toEqual([{ role: '개발', count: 2, notes: '내부' }, { role: '운영', count: 0 }]);
    expect(values.fixedOperatingCost).toEqual({ amount: 1200000, currency: 'KRW', period: 'MONTHLY' });
    expect(values.threeYearTargets).toEqual({ metric: 'customerCount', unit: '명', years: [
      { year: 1, value: 100 }, { year: 2, value: 500 }, { year: 3, value: 1500 },
    ] });
  });

  it('preserves structured proposal decisions and preparation values', () => {
    expect(proposalValue('expectedMonthlyThroughputOrSales', '2500|건')).toEqual({ amount: 2500, unit: '건' });
    expect(proposalValue('technicalSupplyOperationalConstraints', '공급사 이중화\n월별 점검')).toEqual(['공급사 이중화', '월별 점검']);
    const draft = createFactDraft({ targetLaunchDate: { value: '2027-03-01' }, ownedPersonnel: { value: [{ role: '개발', count: 2 }] } });
    expect(draft.targetLaunchDate).toBe('2027-03-01');
    expect(draft.personnel).toBe('개발|2');
  });
});
