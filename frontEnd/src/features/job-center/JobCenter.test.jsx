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
    render(<MemoryRouter><JobCenter projectId="41" compact sheet={{ mounted: true, phase: 'open', focusJobId: 'job-1' }} onOpenList={onOpenList} onCloseSheet={onCloseSheet} onShowList={vi.fn()} onOpenJob={vi.fn()} /></MemoryRouter>);
    expect(screen.getByText('현재 진행')).toBeInTheDocument();
    expect(screen.getByText('사업안 검토')).toBeInTheDocument();
    expect(screen.queryByText('CONCEPT_PORTFOLIO_V2_RUN')).not.toBeInTheDocument();
    expect(screen.getAllByText('작업 상세')).toHaveLength(2);
    expect(screen.getByText('사업 방향을 탐색하고 있습니다.')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toHaveAttribute('data-phase', 'open');
    fireEvent.click(screen.getByRole('button', { name: '작업 센터 닫기' }));
    expect(onCloseSheet).toHaveBeenCalled();
    fireEvent.click(screen.getByText('전체 작업 보기'));
    expect(onOpenList).toHaveBeenCalled();
  });
});
