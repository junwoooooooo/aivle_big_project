import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import ProjectDashboard from './ProjectDashboard.jsx';
import { useIntegratedReport } from './hooks/useIntegratedReport.js';
import { toIntegratedReportViewModel } from './model/reportViewModel.js';
import { emptyResources, fullResources, jobResource, projectFixture } from './tests/reportTestFixtures.js';

vi.mock('./hooks/useIntegratedReport.js', () => ({ useIntegratedReport: vi.fn() }));

function renderDashboard() {
  return render(<MemoryRouter><ProjectDashboard project={projectFixture} /></MemoryRouter>);
}

describe('ProjectDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useIntegratedReport.mockReturnValue({
      status: 'success',
      report: toIntegratedReportViewModel(projectFixture, fullResources()),
      retry: vi.fn(),
    });
  });

  it('shows project status, stage, and latest server update', () => {
    renderDashboard();
    expect(screen.getByText('진행 중')).toBeInTheDocument();
    expect(screen.getByText('보고서')).toBeInTheDocument();
    expect(screen.getByText(/2026/)).toBeInTheDocument();
  });

  it('shows five analysis status cards including financial analysis', () => {
    renderDashboard();
    expect(screen.getAllByText('상세 결과 보기')).toHaveLength(5);
  });

  it('links to the canonical report route', () => {
    renderDashboard();
    expect(screen.getByRole('link', { name: '통합 보고서 보기' })).toHaveAttribute('href', '/report');
  });

  it('shows the report next action when all results exist', () => {
    renderDashboard();
    expect(screen.getByRole('heading', { name: '통합 보고서 확인' })).toBeInTheDocument();
  });

  it('shows document upload as the first action', () => {
    useIntegratedReport.mockReturnValue({
      status: 'success',
      report: toIntegratedReportViewModel(projectFixture, emptyResources()),
      retry: vi.fn(),
    });
    renderDashboard();
    expect(screen.getByRole('heading', { name: '사업계획서 등록' })).toBeInTheDocument();
  });

  it('shows running legal status from the durable job', () => {
    const resources = emptyResources();
    resources.plan = fullResources().plan;
    resources.legalJob = jobResource('RUNNING');
    useIntegratedReport.mockReturnValue({
      status: 'success',
      report: toIntegratedReportViewModel(projectFixture, resources),
      retry: vi.fn(),
    });
    renderDashboard();
    expect(screen.getAllByText('진행 중').length).toBeGreaterThan(1);
  });

  it('shows failed analysis warning', () => {
    const resources = fullResources();
    resources.legalReview = { state: 'error', data: null, error: new Error('failed') };
    useIntegratedReport.mockReturnValue({
      status: 'success',
      report: toIntegratedReportViewModel(projectFixture, resources),
      retry: vi.fn(),
    });
    renderDashboard();
    expect(screen.getByText('확인할 분석 오류가 있습니다')).toBeInTheDocument();
  });

  it('discloses mock summaries', () => {
    renderDashboard();
    expect(screen.getByText('Mock 분석 결과 포함')).toBeInTheDocument();
  });

  it('announces loading', () => {
    useIntegratedReport.mockReturnValue({ status: 'loading' });
    renderDashboard();
    expect(screen.getAllByRole('status')[0]).toHaveTextContent('프로젝트 진행 상태');
  });

  it('renders an error state', () => {
    useIntegratedReport.mockReturnValue({ status: 'error', error: new Error('failed'), retry: vi.fn() });
    renderDashboard();
    expect(screen.getByRole('alert')).toHaveTextContent('프로젝트 진행 상태를 불러오지 못했습니다');
  });
});
