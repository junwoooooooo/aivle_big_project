import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import FinancialAssumptionEditor from './FinancialAssumptionEditor.jsx';
import { INITIAL_ASSUMPTIONS } from '../model/financialModel.js';

describe('FinancialAssumptionEditor', () => {
  it('distinguishes revenue models, periods, sources, and field errors', () => {
    const onChange = vi.fn();
    const onPeriodChange = vi.fn();
    render(<FinancialAssumptionEditor assumptions={INITIAL_ASSUMPTIONS} analysisPeriodMonths={12} onChange={onChange} onPeriodChange={onPeriodChange} errors={{ unitPrice: '판매 단가가 필요합니다.' }} />);
    expect(screen.getByText('출처: 사용자 가정')).toBeInTheDocument();
    expect(screen.getByText('판매 단가가 필요합니다.')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText(/분석 기간/), { target: { value: '24' } });
    expect(onPeriodChange).toHaveBeenCalledWith(24);
    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: 'SUBSCRIPTION' } });
    expect(onChange).toHaveBeenCalledWith('revenueModel', 'SUBSCRIPTION');
  });
});
