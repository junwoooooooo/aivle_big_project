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
    expect(screen.getByText(/IDEA BRIEF DERIVATION · 진행 중/)).toBeInTheDocument();
  });

  it('names the terminal task in the notice', () => {
    useProjectJobs.mockReturnValue({
      loading: false, error: null,
      notice: { jobId: 'failed-1', status: 'FAILED', taskType: 'CONCEPT_FACTORY_RUN' },
      active: [],
      recent: [{ jobId: 'failed-1', taskType: 'CONCEPT_FACTORY_RUN', status: 'FAILED', targetRoute: '/concepts' }],
      selectedJobId: 'failed-1', selectJob: vi.fn(), refresh: vi.fn(),
      events: { transport: 'SSE', error: null, events: [], reconnect: vi.fn() },
    });

    render(<MemoryRouter><JobCenter projectId="41" /></MemoryRouter>);

    expect(screen.getByText('CONCEPT FACTORY RUN 작업이 실패 상태로 종료되었습니다.')).toBeInTheDocument();
  });

  it('separates current and resolved needs-input presentation', () => {
    useProjectJobs.mockReturnValue({
      loading: false, error: null, notice: null,
      active: [{ jobId: 'job-current', taskType: 'IDEA_BRIEF_DERIVATION', status: 'NEEDS_INPUT',
        rawStatus: 'NEEDS_INPUT', actionable: true, presentationStatus: 'NEEDS_INPUT', targetRoute: '/idea' }],
      recent: [{ jobId: 'job-old', taskType: 'IDEA_BRIEF_DERIVATION', status: 'NEEDS_INPUT',
        rawStatus: 'NEEDS_INPUT', actionable: false, presentationStatus: 'RESOLVED_INPUT', targetRoute: '/idea' }],
      selectedJobId: 'job-current', selectJob: vi.fn(), refresh: vi.fn(),
      events: { transport: 'SSE', error: null, events: [], reconnect: vi.fn() },
    });

    render(<MemoryRouter><JobCenter projectId="41" /></MemoryRouter>);

    expect(screen.getByText('입력 필요')).toBeInTheDocument();
    expect(screen.getByText('입력 반영 완료')).toBeInTheDocument();
  });

  it('shows failed initial and running retry as separate task runs', () => {
    useProjectJobs.mockReturnValue({
      loading: false, error: null, notice: null,
      active: [{ jobId: 'retry-1', taskType: 'CONCEPT_FACTORY_RUN', status: 'RUNNING',
        rawStatus: 'RUNNING', subjectType: 'CONCEPT_FACTORY_RUN', subjectId: 'run-1',
        latestForSubject: true, startedAt: '2026-08-09T06:21:00Z', targetRoute: '/concepts' }],
      recent: [{ jobId: 'initial', taskType: 'CONCEPT_FACTORY_RUN', status: 'FAILED',
        rawStatus: 'FAILED', subjectType: 'CONCEPT_FACTORY_RUN', subjectId: 'run-1',
        latestForSubject: false, startedAt: '2026-08-09T06:10:00Z', targetRoute: '/concepts' }],
      selectedJobId: 'retry-1', selectJob: vi.fn(), refresh: vi.fn(),
      events: { transport: 'SSE', error: null, events: [{ jobId: 'retry-1', sequence: 1,
        status: 'RUNNING', eventType: 'job.concept.run.started',
        messageKey: 'job.concept.run.started' }], reconnect: vi.fn() },
    });

    render(<MemoryRouter><JobCenter projectId="41" /></MemoryRouter>);

    expect(screen.getByText(/현재 실행/)).toBeInTheDocument();
    expect(screen.getByText(/이전 실행/)).toBeInTheDocument();
    expect(screen.getByText('컨셉 생성과 법률 근거 확인을 시작했습니다.')).toBeInTheDocument();
  });
});
