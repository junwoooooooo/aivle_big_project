import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import FinancialScenarioEditor from './FinancialScenarioEditor.jsx';
import { SCENARIOS } from '../model/financialModel.js';

describe('FinancialScenarioEditor', () => {
  it('renders and edits conservative, base, and optimistic scenarios', () => {
    const onChange = vi.fn();
    render(<FinancialScenarioEditor scenarios={SCENARIOS} onChange={onChange} />);
    expect(screen.getByRole('group', { name: /보수/ })).toBeInTheDocument();
    expect(screen.getByRole('group', { name: /기준/ })).toBeInTheDocument();
    expect(screen.getByRole('group', { name: /낙관/ })).toBeInTheDocument();
    fireEvent.change(screen.getAllByLabelText(/판매량 조정/)[0], { target: { value: '-15' } });
    expect(onChange).toHaveBeenCalledWith(0, 'salesVolumeAdjustment', '-15');
  });
});
