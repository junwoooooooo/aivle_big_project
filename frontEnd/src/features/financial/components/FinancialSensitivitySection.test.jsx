import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import FinancialSensitivitySection from './FinancialSensitivitySection.jsx';

describe('FinancialSensitivitySection', () => {
  it('shows variable, profit, break-even, and working-capital outcomes', () => {
    render(<FinancialSensitivitySection result={{ sensitivity: [{ variable: 'VOLUME', adjustment: -20, totalOperatingProfit: 100, breakEvenMonth: null, requiredWorkingCapital: 200 }] }} />);
    expect(screen.getByText('판매량 -20%')).toBeInTheDocument();
    expect(screen.getByText(/영업손익/)).toBeInTheDocument();
    expect(screen.getByText('손익분기 미도달')).toBeInTheDocument();
    expect(screen.getByText(/운영자금/)).toBeInTheDocument();
  });
});
