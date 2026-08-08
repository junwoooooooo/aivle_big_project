import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import HypothesisDecisionPanel from './HypothesisDecisionPanel.jsx';

const selection = {
  decisionComplete: false,
  hypotheses: [
    { decisionId: 'locked', hypothesisType: 'PRICE', proposedValue: '월 9,900원', finalValue: '월 9,900원',
      proposalVersion: 1, locked: true, decisionStatus: 'ACCEPTED', legalImpact: 'LEGAL_SENSITIVE', legalReviewStatus: 'NOT_REQUIRED' },
    { decisionId: 'revenue', hypothesisType: 'REVENUE_MODEL', proposedValue: '월 구독', finalValue: null,
      proposalVersion: 1, locked: false, decisionStatus: 'PROPOSED', legalImpact: 'LEGAL_SENSITIVE', legalReviewStatus: 'NOT_REQUIRED' },
    { decisionId: 'som', hypothesisType: 'PRE_MARKET_SOM', proposedValue: { amount: 100000000, currency: 'KRW' }, finalValue: null,
      proposalVersion: 1, locked: false, decisionStatus: 'PROPOSED', legalImpact: 'NON_LEGAL', legalReviewStatus: 'NOT_REQUIRED' },
  ],
};

describe('HypothesisDecisionPanel', () => {
  it('keeps locked values read-only and requests an alternative without a dead end', async () => {
    const onAction = vi.fn().mockResolvedValue({});
    render(<HypothesisDecisionPanel selection={selection} onAction={onAction} />);

    expect(screen.getByText('사용자가 입력 · 확정됨')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '채택' })).toHaveLength(2);
    fireEvent.click(screen.getAllByRole('button', { name: '다른 제안' })[0]);

    await waitFor(() => expect(onAction).toHaveBeenCalledWith('REVENUE_MODEL', 'REQUEST_ALTERNATIVE', 1, undefined));
  });

  it('sends a structured SOM edit through the non-legal decision action', async () => {
    const onAction = vi.fn().mockResolvedValue({});
    render(<HypothesisDecisionPanel selection={{ ...selection, hypotheses: [selection.hypotheses[2]] }} onAction={onAction} />);
    fireEvent.click(screen.getByRole('button', { name: '수정 후 채택' }));
    fireEvent.change(screen.getByLabelText('수정값'), { target: { value: '{"amount":200000000,"currency":"KRW"}' } });
    fireEvent.click(screen.getByRole('button', { name: '수정값 채택' }));

    await waitFor(() => expect(onAction).toHaveBeenCalledWith('PRE_MARKET_SOM', 'EDIT_AND_ACCEPT', 1,
      { amount: 200000000, currency: 'KRW' }));
  });
});
