import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import ConceptSlotCard from './ConceptSlotCard.jsx';

describe('ConceptSlotCard', () => {
  it('shows safe progress without exposing draft concept detail', () => {
    render(<ConceptSlotCard slot={{ slotNumber: 1, variationFocus: 'CUSTOMER_EXPERIENCE', status: 'VALIDATING_LEGAL', candidateCount: 1, legalReviewAttemptCount: 2, updatedAt: '2026-08-06T12:00:00Z' }} />);
    expect(screen.getByRole('article', { name: /컨셉 1/ })).toHaveTextContent('법률 근거를 확인하고 있습니다.');
    expect(screen.getByText('후보 생성 횟수')).toBeInTheDocument();
    expect(screen.getByText('법률 검토 상태')).toBeInTheDocument();
    expect(screen.queryByText(/value proposition/i)).not.toBeInTheDocument();
  });

  it('shows only readiness copy for an eligible slot before global reveal', () => {
    render(<ConceptSlotCard slot={{ slotNumber: 5, variationFocus: 'LOW_RISK_FAST_EXECUTION', status: 'ELIGIBLE', candidateCount: 2, legalReviewAttemptCount: 3 }} />);
    expect(screen.getByText('컨셉 준비됨 · 법률검토 통과')).toBeInTheDocument();
  });
});
