import { describe, expect, it } from 'vitest';
import { createFinancialDraft, financialValuesFromDraft, formatMoney } from './financeModel.js';

describe('financeModel', () => {
  it('상위 근거의 readOnly 값은 재전송하지 않고 사용자 입력값만 요청 본문으로 만든다', () => {
    const fields = {
      annualFixedLaborCost: { value: { amount: 120, currency: 'KRW' }, readOnly: true },
      annualFixedInfrastructureCost: { value: null, readOnly: false },
      threeYearTargets: { value: null, readOnly: false },
      newCustomerCount: { value: null, readOnly: false },
      revenueModel: { value: 'SUBSCRIPTION', readOnly: false },
      monthlySubscriptionPrice: { value: null, readOnly: false },
      monthlyChurnRate: { value: null, readOnly: false },
    };
    const draft = createFinancialDraft(fields);
    draft.annualFixedInfrastructureCost = '30';
    draft.targetYears = ['100', '200', '300'];
    draft.newCustomerCount = '25';
    draft.monthlySubscriptionPrice = '9900';
    draft.monthlyChurnRate = '4.5';
    const values = financialValuesFromDraft(draft, fields);
    expect(values.annualFixedLaborCost).toBeUndefined();
    expect(values.annualFixedInfrastructureCost).toEqual({ amount: 30, currency: 'KRW' });
    expect(values.threeYearTargets.years).toEqual([{ year: 1, value: 100 }, { year: 2, value: 200 }, { year: 3, value: 300 }]);
    expect(values.newCustomerCount).toBe(25);
    expect(values.revenueModel).toBe('SUBSCRIPTION');
    expect(values.monthlySubscriptionPrice).toEqual({ amount: 9900, currency: 'KRW' });
    expect(values.monthlyChurnRate).toBe(4.5);
  });

  it('금액을 한국어 표기로 표시한다', () => {
    expect(formatMoney({ amount: 1234567, currency: 'KRW' })).toBe('1,234,567 KRW');
  });
});
