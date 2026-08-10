import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import JobCenter from './JobCenter.jsx';
import { useProjectJobs } from './useProjectJobs.js';

vi.mock('./useProjectJobs.js', () => ({ useProjectJobs: vi.fn() }));

describe('compact Work Center', () => {
  it('shows current, actionable and recent work then opens real event detail drawer', () => {
    useProjectJobs.mockReturnValue({ loading: false, error: null, notice: null,
      active: [{ jobId: 'job-1', taskType: 'CONCEPT_PORTFOLIO_V2_RUN', status: 'RUNNING', targetRoute: '/concepts' }],
      recent: [], selectedJobId: 'job-1', selectJob: vi.fn(), refresh: vi.fn(),
      events: { transport: 'SSE', error: null, terminal: false, reconnect: vi.fn(), events: [{ eventId: '81', occurredAt: '2026-08-11T00:00:00Z', status: 'RUNNING', messageKey: 'job.status.running' }] } });
    render(<MemoryRouter><JobCenter projectId="41" compact /></MemoryRouter>);
    expect(screen.getByText('현재 진행')).toBeInTheDocument();
    fireEvent.click(screen.getByText('선택 작업 상세 보기'));
    expect(screen.getByText('작업 상세')).toBeInTheDocument();
    expect(screen.getByText('SSE')).toBeInTheDocument();
  });
});
