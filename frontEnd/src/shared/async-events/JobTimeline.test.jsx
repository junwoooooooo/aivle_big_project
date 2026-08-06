import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { JobTimeline } from './JobTimeline.jsx';

describe('JobTimeline', () => {
  it('shows safe user messages in sequence order without fake percent or technical codes', () => {
    render(<JobTimeline events={[
      {
        jobId: 'job-1', sequence: 2, status: 'COMPLETED',
        messageKey: 'job.idea.questions.completed', occurredAt: '2026-08-05T00:00:01Z',
        technicalCode: 'INTERNAL_DIAGNOSTIC',
      },
      {
        jobId: 'job-1', sequence: 1, status: 'RUNNING',
        messageKey: 'job.idea.attachment.parsing.started', occurredAt: '2026-08-05T00:00:00Z',
      },
    ]} />);

    const items = screen.getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('첨부파일');
    expect(items[1]).toHaveTextContent('추가 질문');
    expect(screen.queryByText(/%/)).not.toBeInTheDocument();
    expect(screen.queryByText(/INTERNAL_DIAGNOSTIC/)).not.toBeInTheDocument();
    expect(screen.getByRole('list')).toHaveAttribute('aria-live', 'polite');
  });

  it('maps boundary blocked events without exposing technical codes', () => {
    render(<JobTimeline events={[{ sequence: 1, status: 'BLOCKED', messageKey: 'job.boundary.blocked',
      technicalCode: 'BOUNDARY_INTERNAL' }]} />);
    expect(screen.getByText('고정 조건과 규제 경계가 충돌합니다.')).toBeInTheDocument();
    expect(screen.getByText('수정 필요')).toBeInTheDocument();
    expect(screen.queryByText('BOUNDARY_INTERNAL')).not.toBeInTheDocument();
  });

  it('hides internal claimed and started events from the user timeline', () => {
    render(<JobTimeline events={[
      { jobId: 'job-2', sequence: 1, status: 'RUNNING', messageKey: 'job.claimed', occurredAt: '2026-08-05T00:00:00Z' },
      { jobId: 'job-2', sequence: 2, status: 'RUNNING', messageKey: 'job.started', occurredAt: '2026-08-05T00:00:01Z' },
      { jobId: 'job-2', sequence: 3, status: 'RUNNING', messageKey: 'job.idea.information.extraction.started', occurredAt: '2026-08-05T00:00:02Z' },
    ]} />);

    expect(screen.getAllByRole('listitem')).toHaveLength(1);
    expect(screen.getByText('입력 내용을 이해하고 있습니다.')).toBeInTheDocument();
    expect(screen.queryByText('작업 상태가 업데이트되었습니다.')).not.toBeInTheDocument();
  });

  it('maps conversation schema repair without exposing issue details', () => {
    render(<JobTimeline events={[{
      jobId: 'job-3', sequence: 4, status: 'RUNNING',
      messageKey: 'job.idea.result.repairing',
      messageParams: { attemptPhase: 'REPAIR', issueCount: 5 },
      technicalCode: 'RESULT_SCHEMA_INVALID',
      occurredAt: '2026-08-05T00:00:03Z',
    }]} />);

    expect(screen.getByText('응답 형식을 정리하고 있습니다.')).toBeInTheDocument();
    expect(screen.queryByText(/RESULT_SCHEMA_INVALID|issueCount|REPAIR|5/)).not.toBeInTheDocument();
  });
});
