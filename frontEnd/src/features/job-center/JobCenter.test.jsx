import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import JobCenter from './JobCenter.jsx';
import { useProjectJobs } from './useProjectJobs.js';

vi.mock('./useProjectJobs.js', () => ({ useProjectJobs: vi.fn() }));

describe('compact Work Center', () => {
  it('uses product labels and opens actual event detail', () => {
    useProjectJobs.mockReturnValue({ loading: false, error: null, notice: null,
      active: [{ jobId: 'job-1', taskType: 'CONCEPT_PORTFOLIO_V2_RUN', status: 'RUNNING', targetRoute: '/concepts' }],
      recent: [], selectedJobId: 'job-1', selectJob: vi.fn(), refresh: vi.fn(),
      events: { transport: 'SSE', error: null, terminal: false, reconnect: vi.fn(), events: [{ eventId: '81', occurredAt: '2026-08-11T00:00:00Z', status: 'RUNNING', messageKey: 'job.concept-portfolio.running' }] } });
    const onOpenList = vi.fn();
    const onCloseSheet = vi.fn();
    render(<MemoryRouter><JobCenter projectId="41" compact sheet={{ mounted: true, phase: 'open', view: 'detail', focusJobId: 'job-1', direction: 'forward' }} onOpenList={onOpenList} onCloseSheet={onCloseSheet} onShowList={vi.fn()} onOpenJob={vi.fn()} /></MemoryRouter>);
    expect(screen.getAllByText('현재 진행')).toHaveLength(2);
    expect(screen.getAllByText('사업안 검토').length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByText('CONCEPT_PORTFOLIO_V2_RUN')).not.toBeInTheDocument();
    expect(screen.getByText('작업 상세')).toBeInTheDocument();
    expect(screen.getAllByText('사업 방향을 탐색하고 있습니다.').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByRole('dialog')).toHaveAttribute('data-phase', 'open');
    expect(screen.getByLabelText('작업 요약')).toHaveTextContent('현재 진행 1');
    fireEvent.click(screen.getByRole('button', { name: '작업 센터 닫기' }));
    expect(onCloseSheet).toHaveBeenCalled();
    fireEvent.click(screen.getByText('전체 작업 보기'));
    expect(onOpenList).toHaveBeenCalled();
  });

  it('starts a fresh Portfolio run once from the failed detail retry', async () => {
    useProjectJobs.mockReturnValue({ loading: false, error: null, notice: null,
      active: [], recent: [{ jobId: 'failed-job', taskType: 'CONCEPT_PORTFOLIO_V2_RUN', status: 'FAILED', retryable: true, targetRoute: '/concepts', updatedAt: '2026-08-11T00:00:00Z' }],
      selectedJobId: 'failed-job', selectJob: vi.fn(), refresh: vi.fn(),
      events: { transport: 'SSE', error: null, terminal: true, reconnect: vi.fn(), events: [] } });
    let release;
    const onRetryJob = vi.fn(() => new Promise((resolve) => { release = resolve; }));
    render(<MemoryRouter><JobCenter projectId="41" compact onRetryJob={onRetryJob}
      sheet={{ mounted: true, phase: 'open', view: 'detail', focusJobId: 'failed-job', direction: 'forward' }}
      onOpenList={vi.fn()} onCloseSheet={vi.fn()} onShowList={vi.fn()} onOpenJob={vi.fn()} /></MemoryRouter>);
    const retry = screen.getByRole('button', { name: '다시 시도' });
    fireEvent.click(retry);
    fireEvent.click(retry);
    expect(onRetryJob).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: '다시 시도 중' })).toBeDisabled();
    release();
  });

  it('offers an input route for NEEDS_INPUT and keeps diagnostics inside technical details', () => {
    useProjectJobs.mockReturnValue({ loading: false, error: null, notice: null,
      active: [{ jobId: 'input-job', taskType: 'CONCEPT_PORTFOLIO_V2_RUN', status: 'NEEDS_INPUT', targetRoute: '/concepts' }],
      recent: [], selectedJobId: 'input-job', selectJob: vi.fn(), refresh: vi.fn(),
      events: { transport: 'SSE', error: null, terminal: true, reconnect: vi.fn(), events: [{
        eventId: '91', occurredAt: '2026-08-11T00:01:00Z', status: 'NEEDS_INPUT',
        messageKey: 'job.concept-portfolio.needs-input', messageParams: {},
      }] } });
    render(<MemoryRouter><JobCenter projectId="41" compact
      sheet={{ mounted: true, phase: 'open', view: 'detail', focusJobId: 'input-job', direction: 'forward' }}
      onOpenList={vi.fn()} onCloseSheet={vi.fn()} onShowList={vi.fn()} onOpenJob={vi.fn()} /></MemoryRouter>);
    expect(screen.getByRole('link', { name: '정보 입력하러 가기' }))
      .toHaveAttribute('href', '/app/projects/41/concepts');
  });
});
