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
    expect(screen.getAllByText('사업안 만들기').length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByText('CONCEPT_PORTFOLIO_V2_RUN')).not.toBeInTheDocument();
    expect(screen.getByText('작업 상세')).toBeInTheDocument();
    expect(screen.getAllByText('사업 방향을 탐색하고 있습니다.').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByRole('dialog')).toHaveAttribute('data-phase', 'open');
    expect(screen.queryByLabelText('작업 요약')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '작업 센터 닫기' }));
    expect(onCloseSheet).toHaveBeenCalled();
    expect(screen.queryByText('전체 작업 보기')).not.toBeInTheDocument();
    expect(onOpenList).not.toHaveBeenCalled();
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
    expect(screen.getByRole('link', { name: '업무 화면 열기' }))
      .toHaveAttribute('href', '/app/projects/41/concepts');
    expect(screen.getAllByRole('link', { name: '업무 화면 열기' })).toHaveLength(1);
  });

  it('quick은 1/1/3 규칙과 나머지 건수를 표시한다', () => {
    const job = (id, status) => ({ jobId: id, taskType: 'MARKET_RESEARCH', status, targetRoute: '/market' });
    useProjectJobs.mockReturnValue({ loading: false, error: null,
      active: [job('r1', 'RUNNING'), job('r2', 'RUNNING'), job('r3', 'RUNNING'), job('i1', 'NEEDS_INPUT'), job('i2', 'NEEDS_INPUT')],
      recent: Array.from({ length: 20 }, (_, index) => job(`done-${index}`, 'COMPLETED')),
      selectedJobId: null, selectJob: vi.fn(), refresh: vi.fn(),
      events: { transport: 'SSE', error: null, reconnect: vi.fn(), events: [] } });
    const { container } = render(<MemoryRouter><JobCenter projectId="41" compact onOpenList={vi.fn()} onOpenJob={vi.fn()} /></MemoryRouter>);
    expect(container.querySelector('.job-center--compact')).not.toHaveClass('pipeline-task-center');
    expect(screen.getByText('+ 외 2건')).toBeInTheDocument();
    expect(screen.getByText('+ 외 1건')).toBeInTheDocument();
    expect(screen.getByText('+ 외 17건')).toBeInTheDocument();
    expect(screen.getByLabelText('작업 요약')).toHaveTextContent('최근 작업 20');
  });

  it('Full은 Quick popover 밖의 body portal에 하나만 렌더링한다', () => {
    useProjectJobs.mockReturnValue({ loading: false, error: null, active: [], recent: [], selectedJobId: null,
      selectJob: vi.fn(), refresh: vi.fn(), history: { items: [], page: 0, hasMore: false, totalElements: 0, loading: false, error: null },
      loadHistory: vi.fn(), events: { error: null, reconnect: vi.fn(), events: [] } });
    const { container } = render(<MemoryRouter><JobCenter projectId="41" compact quickOpen
      quickContainerId="project-work-center-popover" sheet={{ mounted: true, phase: 'open', view: 'list', direction: 'forward' }}
      onOpenList={vi.fn()} onCloseSheet={vi.fn()} onShowList={vi.fn()} onOpenJob={vi.fn()} /></MemoryRouter>);
    expect(container.querySelectorAll('.project-work-popover')).toHaveLength(0);
    expect(document.body.querySelectorAll('.work-center-sheet')).toHaveLength(1);
    const dialog = screen.getByRole('dialog');
    const buttons = dialog.querySelectorAll('button:not([disabled])');
    const first = buttons[0];
    const last = buttons[buttons.length - 1];
    expect(first).toHaveFocus();
    last.focus();
    fireEvent.keyDown(last, { key: 'Tab' });
    expect(first).toHaveFocus();
    fireEvent.keyDown(first, { key: 'Tab', shiftKey: true });
    expect(last).toHaveFocus();
    expect(screen.getByText('아직 실행한 작업이 없습니다.')).toBeInTheDocument();
  });

  it('종료 작업의 저장된 처리 이벤트를 동일한 상세 타임라인에 표시한다', () => {
    const events = ['QUEUED', 'READY', 'RUNNING', 'RUNNING', 'COMPLETED'].map((status, index) => ({
      eventId: `event-${index + 1}`,
      sequence: index + 1,
      occurredAt: `2026-08-11T00:0${index}:00Z`,
      status,
      messageKey: `job.test.event-${index + 1}`,
    }));
    useProjectJobs.mockReturnValue({ loading: false, error: null, notice: null,
      active: [], recent: [{ jobId: 'done-job', taskType: 'MARKET_RESEARCH', status: 'COMPLETED', targetRoute: '/market' }],
      selectedJobId: 'done-job', selectJob: vi.fn(), refresh: vi.fn(),
      events: { transport: 'REST', loading: false, error: null, terminal: true, reconnect: vi.fn(), events } });

    render(<MemoryRouter><JobCenter projectId="41" compact
      sheet={{ mounted: true, phase: 'open', view: 'detail', focusJobId: 'done-job', direction: 'forward' }}
      onOpenList={vi.fn()} onCloseSheet={vi.fn()} onShowList={vi.fn()} onOpenJob={vi.fn()} /></MemoryRouter>);

    expect(document.body.querySelectorAll('.job-center__timeline ol > li')).toHaveLength(5);
    expect(screen.queryByText('이 작업에는 저장된 처리 기록이 없습니다.')).not.toBeInTheDocument();
  });

  it('필터 결과가 0건이면 상태별 빈 문구만 바꾼다', () => {
    useProjectJobs.mockReturnValue({ loading: false, error: null, active: [], recent: [], selectedJobId: null,
      selectJob: vi.fn(), refresh: vi.fn(), history: { items: [{ jobId: 'done-job', status: 'COMPLETED', taskType: 'MARKET_RESEARCH' }], page: 0, hasMore: false, totalElements: 1, loading: false, error: null },
      loadHistory: vi.fn(), events: { error: null, reconnect: vi.fn(), events: [] } });
    render(<MemoryRouter><JobCenter projectId="41" compact
      sheet={{ mounted: true, phase: 'open', view: 'list', direction: 'forward' }}
      onOpenList={vi.fn()} onCloseSheet={vi.fn()} onShowList={vi.fn()} onOpenJob={vi.fn()} /></MemoryRouter>);

    fireEvent.click(screen.getByRole('button', { name: '진행 중' }));
    expect(screen.getByText('현재 진행 중인 작업이 없습니다.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '입력 필요' }));
    expect(screen.getByText('지금 입력이 필요한 작업이 없습니다.')).toBeInTheDocument();
  });
});
