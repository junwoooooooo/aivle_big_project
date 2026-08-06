import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import FinancialMonthlyTable from './FinancialMonthlyTable.jsx';

describe('FinancialMonthlyTable', () => {
  it('provides a caption and keyboard-scrollable mobile region', () => {
    const result = { scenarios: [{ code: 'BASE', months: [{ month: 1, salesVolume: 10, revenue: 100, variableCost: 20, contributionMargin: 80, fixedCost: 30, operatingProfit: 50, cumulativeCashFlow: -50 }] }] };
    render(<FinancialMonthlyTable result={result} />);
    expect(screen.getByText('기준 시나리오 월별 매출·비용·손익과 누적 현금흐름')).toBeInTheDocument();
    expect(screen.getByRole('region')).toHaveAttribute('tabindex', '0');
    expect(screen.getByText('공헌이익')).toBeInTheDocument();
  });
});
