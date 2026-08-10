import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import HypothesisDecisionPanel from './HypothesisDecisionPanel.jsx';

const selection = {
  decisionComplete: false,
  hypotheses: [
    { decisionId: 'region', hypothesisType: 'TARGET_REGION', proposedValue: '대한민국', finalValue: '대한민국',
      proposalVersion: 1, locked: true, decisionStatus: 'ACCEPTED', legalImpact: 'LEGAL_SENSITIVE', legalReviewStatus: 'NOT_REQUIRED' },
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

    expect(screen.getAllByText('사용자가 입력 · 확정됨')).toHaveLength(2);
    expect(screen.getByRole('heading', { name: '대상 지역' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '채택' })).toHaveLength(2);
    fireEvent.click(screen.getAllByRole('button', { name: '다른 제안' })[0]);

    await waitFor(() => expect(onAction).toHaveBeenCalledWith('REVENUE_MODEL', 'REQUEST_ALTERNATIVE', 1, undefined));
  });

  it('shows the safe KR-only jurisdiction message returned by the action endpoint', async () => {
    const message = '현재 공식 법률 검토는 대한민국을 대상으로 지원합니다. 대상 지역을 대한민국으로 변경하면 계속 진행할 수 있습니다.';
    const openRegion = { ...selection.hypotheses[0], locked: false, finalValue: null, decisionStatus: 'PROPOSED' };
    const onAction = vi.fn().mockRejectedValue(new Error(message));
    render(<HypothesisDecisionPanel selection={{ ...selection, hypotheses: [openRegion] }} onAction={onAction} />);

    fireEvent.click(screen.getByRole('button', { name: '수정 후 채택' }));
    fireEvent.change(screen.getByLabelText('수정값'), { target: { value: '미국 캘리포니아' } });
    fireEvent.click(screen.getByRole('button', { name: '수정값 채택' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
  });

  it('sends a structured SOM edit through the non-legal decision action', async () => {
    const onAction = vi.fn().mockResolvedValue({});
    const som = selection.hypotheses.find((item) => item.hypothesisType === 'PRE_MARKET_SOM');
    render(<HypothesisDecisionPanel selection={{ ...selection, hypotheses: [som] }} onAction={onAction} />);
    fireEvent.click(screen.getByRole('button', { name: '수정 후 채택' }));
    fireEvent.change(screen.getByLabelText('수정값'), { target: { value: '{"amount":200000000,"currency":"KRW"}' } });
    fireEvent.click(screen.getByRole('button', { name: '수정값 채택' }));

    await waitFor(() => expect(onAction).toHaveBeenCalledWith('PRE_MARKET_SOM', 'EDIT_AND_ACCEPT', 1,
      { amount: 200000000, currency: 'KRW' }));
  });

  it.each([
    [{ actionStatus: 'QUEUED', pendingActionType: 'REQUEST_ALTERNATIVE', pendingHypothesisType: 'REVENUE_MODEL' }, '다른 제안을 만들고 있습니다.'],
    [{ actionStatus: 'RUNNING', pendingActionType: 'EDIT_AND_ACCEPT', pendingHypothesisType: 'REVENUE_MODEL' }, '변경된 조건의 법률 영향을 확인하고 있습니다.'],
    [{ actionStatus: 'LEGAL_INELIGIBLE' }, '법률 조건을 통과하지 못했습니다.'],
    [{ actionStatus: 'FAILED', safeActionError: 'AI_SERVICE_UNAVAILABLE' }, 'AI 요청을 완료하지 못했습니다. 다시 시도할 수 있습니다.'],
  ])('restores the async action state from the selection query', (asyncState, message) => {
    render(<HypothesisDecisionPanel selection={{ ...selection, ...asyncState }} onAction={vi.fn()} />);
    expect(screen.getByText(message)).toBeInTheDocument();
  });

  it('shows that a successfully committed alternative is ready', () => {
    const alternative = { ...selection.hypotheses[2], proposalVersion: 2,
      decisionStatus: 'ALTERNATIVE_PROPOSED' };
    render(<HypothesisDecisionPanel selection={{ ...selection, actionStatus: 'SUCCEEDED',
      hypotheses: [alternative] }} onAction={vi.fn()} />);
    expect(screen.getByText('새 제안이 준비되었습니다.')).toBeInTheDocument();
  });
});
