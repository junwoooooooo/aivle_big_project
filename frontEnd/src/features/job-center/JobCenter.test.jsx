import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import JobCenter from './JobCenter.jsx';
import { useProjectJobs } from './useProjectJobs.js';

vi.mock('./useProjectJobs.js', () => ({ useProjectJobs: vi.fn() }));

describe('JobCenter', () => {
  it('shows server-restored categories and module navigation without route locking', () => {
    useProjectJobs.mockReturnValue({
      loading: false, error: null, notice: null,
      active: [{ jobId: 'job-1', taskType: 'IDEA_BRIEF_DERIVATION', status: 'RUNNING', messageKey: 'job.status.running', targetRoute: '/idea' }],
      recent: [{ jobId: 'job-2', taskType: 'MARKETING_CONTENT_GENERATION', status: 'FAILED', messageKey: 'job.status.failed', targetRoute: '/marketing' }],
      selectedJobId: 'job-1', selectJob: vi.fn(), refresh: vi.fn(),
      events: { transport: 'SSE', error: null, events: [], reconnect: vi.fn() },
    });

    render(<MemoryRouter><JobCenter projectId="41" /></MemoryRouter>);

    expect(screen.getByText('진행 중인 작업')).toBeInTheDocument();
    expect(screen.getByText('최근 실패')).toBeInTheDocument();
    expect(screen.getAllByText('모듈로 이동')[0]).toHaveAttribute('href', '/app/projects/41/idea');
    expect(screen.getByText('SSE')).toBeInTheDocument();
  });
});
