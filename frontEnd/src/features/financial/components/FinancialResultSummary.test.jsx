import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import FinancialResultSummary from './FinancialResultSummary.jsx';

const result = { scenarios: [{ code: 'BASE', totalRevenue: 1000000, totalOperatingProfit: -100000, breakEvenMonth: null, paybackMonth: null, requiredWorkingCapital: 500000 }] };

describe('FinancialResultSummary', () => {
  it('labels stored results and loss states without fake break-even values', () => {
    render(<FinancialResultSummary result={result} />);
    expect(screen.getByText('저장된 재무 분석 결과')).toBeInTheDocument();
    expect(screen.getByText('적자')).toBeInTheDocument();
    expect(screen.getByText('손익분기 미도달')).toBeInTheDocument();
    expect(screen.getByText('회수 미도달')).toBeInTheDocument();
  });

  it('labels previews separately', () => {
    render(<FinancialResultSummary result={result} preview />);
    expect(screen.getByText('현재 입력 기준 미리보기')).toBeInTheDocument();
  });
});
