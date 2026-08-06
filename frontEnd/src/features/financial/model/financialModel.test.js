import { describe, expect, it } from 'vitest';
import { buildFinancialPreview, INITIAL_ASSUMPTIONS, SCENARIOS, parseFinancialNumber, requestBody } from './financialModel.js';

describe('financial model', () => {
  it('keeps an empty amount distinct from an explicit zero', () => {
    expect(parseFinancialNumber('')).toBeNull();
    expect(parseFinancialNumber('0')).toBe(0);
    expect(parseFinancialNumber('25,000')).toBe(25000);
    expect(parseFinancialNumber('not-a-number')).toBeNull();
  });

  it('serializes formatted money inputs without commas', () => {
    const payload = requestBody({ title: '분석', analysisPeriodMonths: 12, assumptions: { ...INITIAL_ASSUMPTIONS, unitPrice: '25,000', monthlySalesVolume: '10' }, scenarios: SCENARIOS });
    expect(payload.assumptions.unitPrice).toBe(25000);
    expect(payload.assumptions.monthlySalesVolume).toBe(10);
    expect(payload.scenarios).toHaveLength(3);
  });

  it('builds a current-input preview only when required revenue inputs exist', () => {
    expect(buildFinancialPreview(INITIAL_ASSUMPTIONS)).toBeNull();
    const preview = buildFinancialPreview({
      ...INITIAL_ASSUMPTIONS,
      unitPrice: '25,000',
      monthlySalesVolume: '10',
      unitVariableCost: '5,000',
      monthlyLaborCost: '100,000',
    });
    expect(preview.scenarios[0].totalRevenue).toBe(250000);
    expect(preview.scenarios[0].totalOperatingProfit).toBe(100000);
  });
});
