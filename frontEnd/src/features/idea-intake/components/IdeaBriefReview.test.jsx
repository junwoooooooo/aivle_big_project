import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { createIdeaIntakeDraft } from '../model/ideaIntakeModel.js';
import IdeaBriefReview from './IdeaBriefReview.jsx';

describe('IdeaBriefReview', () => {
  it('shows assessment metadata and lets the user choose field authority', () => {
    const draft = createIdeaIntakeDraft();
    draft.intake.overview = '원문 개요';
    draft.assessment = {
      userFacingSummary: 'AI가 정리한 요약',
      contradictions: [{ fieldKeys: ['problem', 'targetCustomers'], summary: '대상이 충돌합니다.' }],
      readiness: {
        score: 55,
        completedRequiredFieldCount: 5,
        totalRequiredFieldCount: 10,
        missingFieldKeys: ['payment'],
        readyForConfirm: false,
      },
      clarificationRound: 2,
      maxClarificationRounds: 2,
    };
    const onDecisionStateChange = vi.fn();

    render(<IdeaBriefReview draft={draft} onFieldChange={vi.fn()}
      onDecisionStateChange={onDecisionStateChange} onConfirm={vi.fn()} />);

    expect(screen.getByText('AI가 정리한 요약')).toBeInTheDocument();
    expect(screen.getByText(/미정 필드: payment/)).toBeInTheDocument();
    expect(screen.getByText(/대상이 충돌합니다/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('해결 문제 결정 상태'), { target: { value: 'LOCKED' } });
    expect(onDecisionStateChange).toHaveBeenCalledWith('problem', 'LOCKED');
    expect(screen.getByRole('button', { name: '저장하고 준비 상태 확인' })).toBeInTheDocument();
  });
});
