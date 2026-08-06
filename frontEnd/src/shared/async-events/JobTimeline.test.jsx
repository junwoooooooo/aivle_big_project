import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { JobTimeline } from './JobTimeline.jsx';

describe('JobTimeline', () => {
  it('shows safe user messages in sequence order without fake percent or technical codes', () => {
    render(<JobTimeline events={[
      {
        jobId: 'job-1', sequence: 2, status: 'COMPLETED',
        messageKey: 'job.idea.questions.preparing', occurredAt: '2026-08-05T00:00:01Z',
        technicalCode: 'INTERNAL_DIAGNOSTIC',
      },
      {
        jobId: 'job-1', sequence: 1, status: 'RUNNING',
        messageKey: 'job.idea.extracting', occurredAt: '2026-08-05T00:00:00Z',
      },
    ]} />);

    const items = screen.getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('핵심 정보');
    expect(items[1]).toHaveTextContent('후속 질문');
    expect(screen.queryByText(/%/)).not.toBeInTheDocument();
    expect(screen.queryByText(/INTERNAL_DIAGNOSTIC/)).not.toBeInTheDocument();
    expect(screen.getByRole('list')).toHaveAttribute('aria-live', 'polite');
  });

  it('maps current concept events without exposing technical codes', () => {
    render(<JobTimeline events={[{ sequence: 1, status: 'RUNNING', messageKey: 'job.concept.slot.validating_legal',
      technicalCode: 'LEGAL_INTERNAL' }]} />);
    expect(screen.getByText('규제 경계와 구현 방식을 확인하고 있습니다.')).toBeInTheDocument();
    expect(screen.queryByText('LEGAL_INTERNAL')).not.toBeInTheDocument();
  });

  it('hides internal claimed and started events from the user timeline', () => {
    render(<JobTimeline events={[
      { jobId: 'job-2', sequence: 1, status: 'RUNNING', messageKey: 'job.claimed', occurredAt: '2026-08-05T00:00:00Z' },
      { jobId: 'job-2', sequence: 2, status: 'RUNNING', messageKey: 'job.started', occurredAt: '2026-08-05T00:00:01Z' },
      { jobId: 'job-2', sequence: 3, status: 'RUNNING', messageKey: 'job.idea.extracting', occurredAt: '2026-08-05T00:00:02Z' },
    ]} />);

    expect(screen.getAllByRole('listitem')).toHaveLength(1);
    expect(screen.getByText('입력에서 핵심 정보를 추출하고 있습니다.')).toBeInTheDocument();
    expect(screen.queryByText('작업 상태가 업데이트되었습니다.')).not.toBeInTheDocument();
  });

  it('maps current marketing progress without exposing issue details', () => {
    render(<JobTimeline events={[{
      jobId: 'job-3', sequence: 4, status: 'RUNNING',
      messageKey: 'job.marketing.legal_checking',
      messageParams: { attemptPhase: 'REPAIR', issueCount: 5 },
      technicalCode: 'RESULT_SCHEMA_INVALID',
      occurredAt: '2026-08-05T00:00:03Z',
    }]} />);

    expect(screen.getByText('금지 표현과 필수 고지를 확인하고 있습니다.')).toBeInTheDocument();
    expect(screen.queryByText(/RESULT_SCHEMA_INVALID|issueCount|REPAIR|5/)).not.toBeInTheDocument();
  });
});
