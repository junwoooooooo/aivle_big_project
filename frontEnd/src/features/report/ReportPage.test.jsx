import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { toIntegratedReportViewModel } from './model/reportViewModel.js';
import ReportPage from './ReportPage.jsx';
import { useIntegratedReport } from './hooks/useIntegratedReport.js';
import { useProjectContext } from '../projects/ProjectContext.jsx';
import { downloadReportMarkdown } from './export/reportMarkdownExporter.js';
import { openReportPrintWindow } from './export/reportPrintWindow.js';
import { emptyResources, fullResources, projectFixture } from './tests/reportTestFixtures.js';

vi.mock('./hooks/useIntegratedReport.js', () => ({ useIntegratedReport: vi.fn() }));
vi.mock('../projects/ProjectContext.jsx', () => ({ useProjectContext: vi.fn() }));
vi.mock('./export/reportMarkdownExporter.js', async (original) => {
  const actual = await original();
  return { ...actual, downloadReportMarkdown: vi.fn() };
});
vi.mock('./export/reportPrintWindow.js', () => ({ openReportPrintWindow: vi.fn() }));

function renderPage() {
  return render(<MemoryRouter><ReportPage /></MemoryRouter>);
}

describe('ReportPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useProjectContext.mockReturnValue({ project: projectFixture });
    useIntegratedReport.mockReturnValue({
      status: 'success',
      report: toIntegratedReportViewModel(projectFixture, fullResources()),
      retry: vi.fn(),
    });
  });

  it('renders a single report heading and all sections', () => {
    renderPage();
    expect(screen.getByRole('document')).toHaveClass('integrated-report');
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('통합 분석 보고서');
    expect(screen.getByText(/인쇄 창의 대상 또는 프린터에서/)).toBeInTheDocument();
    for (const name of ['사업계획 구조화', '법률·규제 사전검토', '사업 타당성', '페르소나·고객 검증 계획', '검증 과제', '출처와 생성 정보']) {
      expect(screen.getAllByText(name).length).toBeGreaterThan(0);
    }
  });

  it('renders provider provenance and mock disclosure', () => {
    renderPage();
    expect(screen.getAllByText(/Mock/).length).toBeGreaterThan(0);
    expect(screen.getByText(/Mock provider · mock-legal/)).toBeInTheDocument();
  });

  it('does not render a null feasibility score as zero', () => {
    renderPage();
    expect(screen.queryByText('서버 종합 점수')).not.toBeInTheDocument();
  });

  it('keeps legal, feasibility, and persona disclaimers', () => {
    renderPage();
    expect(screen.getByText('법률 자문이 아닙니다.')).toBeInTheDocument();
    expect(screen.getByText('투자 추천이 아닙니다.')).toBeInTheDocument();
    expect(screen.getByText('실제 고객 조사 결과가 아닙니다.')).toBeInTheDocument();
  });

  it('shows validation tasks without completion controls', () => {
    renderPage();
    expect(screen.getByText('고객 인터뷰')).toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });

  it('opens the independent report print document', () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: '인쇄 / PDF 저장' }));
    expect(openReportPrintWindow).toHaveBeenCalledWith(expect.objectContaining({ project: projectFixture }));
  });

  it('downloads markdown from the same report view model', () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Markdown 다운로드' }));
    expect(downloadReportMarkdown).toHaveBeenCalledWith(
      expect.objectContaining({ project: projectFixture }),
    );
  });

  it('renders a partial report with actionable empty sections', () => {
    const resources = emptyResources();
    resources.plan = fullResources().plan;
    useIntegratedReport.mockReturnValue({
      status: 'success',
      report: toIntegratedReportViewModel(projectFixture, resources),
      retry: vi.fn(),
    });
    renderPage();
    expect(screen.getByText('현재까지의 분석 결과 · 일부 검증 미완료')).toBeInTheDocument();
    expect(screen.getAllByText('해당 단계로 이동')).toHaveLength(4);
  });

  it('renders a failed section while keeping other results', () => {
    const resources = fullResources();
    resources.legalReview = { state: 'error', data: null, error: new Error('failed') };
    useIntegratedReport.mockReturnValue({
      status: 'success',
      report: toIntegratedReportViewModel(projectFixture, resources),
      retry: vi.fn(),
    });
    renderPage();
    expect(screen.getByText('영역 조회 실패')).toBeInTheDocument();
    expect(screen.getByText('조건부')).toBeInTheDocument();
  });

  it('announces loading', () => {
    useIntegratedReport.mockReturnValue({ status: 'loading', report: null });
    renderPage();
    expect(screen.getAllByRole('status')[0]).toHaveTextContent('현재 분석 결과를 통합하고 있습니다');
  });

  it('renders a retryable top-level error', () => {
    const retry = vi.fn();
    useIntegratedReport.mockReturnValue({
      status: 'error',
      report: null,
      error: new Error('project failed'),
      retry,
    });
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(retry).toHaveBeenCalledOnce();
  });

  it('provides a report navigation landmark', () => {
    renderPage();
    expect(screen.getByRole('navigation', { name: '보고서 목차' })).toBeInTheDocument();
  });

  it.each(['사업계획', '법률', '타당성', '페르소나', '검증 과제', '출처'])(
    'links to the %s report section',
    (name) => {
      renderPage();
      expect(screen.getByRole('link', { name })).toHaveAttribute('href', expect.stringMatching(/^#/));
    },
  );
});
