import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import PlanningChangeCard from './PlanningChangeCard.jsx';

const proposal = {
  proposalId: 'proposal-1', meaningfulTitle: '초기 고객을 관리사무소 계약 단지로 좁히기',
  affectedFields: ['targetCustomer'], before: '서울 시민', after: '계약 단지 관리사무소',
  reason: '초기 영업 효율을 높일 수 있습니다.', impactAreas: ['고객', '채널'], decisionStatus: 'PENDING',
  evidenceReferences: [{ title: '공식 자료', url: 'https://example.com/source' }], modifiedAfter: null,
};

describe('PlanningChangeCard', () => {
  it('uses a meaningful title and supports a user-edited partial adoption value', () => {
    const onDecision = vi.fn();
    render(<PlanningChangeCard proposal={proposal} onDecision={onDecision} />);
    expect(screen.getByRole('heading', { name: proposal.meaningfulTitle })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '부분 채택' }));
    fireEvent.change(screen.getByLabelText('수정해 채택할 값'), { target: { value: '강남구 3개 계약 단지' } });
    fireEvent.click(screen.getByRole('button', { name: '수정값으로 부분 채택' }));
    expect(onDecision).toHaveBeenCalledWith('PARTIALLY_ADOPT', '강남구 3개 계약 단지');
  });
});
