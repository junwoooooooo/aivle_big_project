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
    render(<MemoryRouter><JobCenter projectId="41" compact /></MemoryRouter>);
    expect(screen.getByText('현재 진행')).toBeInTheDocument();
    expect(screen.getByText('사업안 검토')).toBeInTheDocument();
    expect(screen.queryByText('CONCEPT_PORTFOLIO_V2_RUN')).not.toBeInTheDocument();
    fireEvent.click(screen.getByText('선택 작업 상세 보기'));
    expect(screen.getByText('작업 상세')).toBeInTheDocument();
    expect(screen.getByText('사업 방향을 탐색하고 있습니다.')).toBeInTheDocument();
  });
});
