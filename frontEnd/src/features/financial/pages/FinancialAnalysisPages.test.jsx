import { MemoryRouter } from 'react-router-dom';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import FinancialAnalysisListPage from './FinancialAnalysisListPage.jsx';
import { projectRoutes } from '../../projects/routing/projectRoutes.js';

const navigate = vi.fn();
let maintenanceMode = false;
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useParams: () => ({ projectId: '10' }), useNavigate: () => navigate };
});
vi.mock('../hooks/useFinancialAnalyses.js', () => ({
  default: () => ({
    items: [
      { id: 1, title: '초안 분석', status: 'DRAFT', analysisPeriodMonths: 12, versionNumber: 1 },
      { id: 2, title: '완료 분석', status: 'COMPLETED', analysisPeriodMonths: 24, versionNumber: 2, completedAt: '2026-07-30T00:00:00Z' },
    ],
    source: { ready: true },
    loading: false,
    error: null,
    refresh: vi.fn(),
  }),
}));
vi.mock('../../service-policy/useServicePolicy.js', () => ({
  useServicePolicy: () => ({ policy: { maintenanceMode } }),
}));

describe('FinancialAnalysis pages and routes', () => {
  beforeEach(() => { maintenanceMode = false; navigate.mockReset(); });

  it('renders draft and completed analyses with canonical detail links', () => {
    render(<MemoryRouter><FinancialAnalysisListPage /></MemoryRouter>);
    expect(screen.getByText('초안 분석')).toBeInTheDocument();
    expect(screen.getByText('완료 분석')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: '열기' })[1])
      .toHaveAttribute('href', '/app/projects/10/journey/concept-analysis');
    expect(projectRoutes.financialNew(10)).toBe('/app/projects/10/journey/concept-analysis');
  });

  it('blocks creation during maintenance while preserving result links', () => {
    maintenanceMode = true;
    render(<MemoryRouter><FinancialAnalysisListPage /></MemoryRouter>);
    expect(screen.getByRole('button', { name: '새 재무 분석' })).toBeDisabled();
    expect(screen.getByText('점검 중에는 새 분석을 만들 수 없습니다.')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: '열기' })).toHaveLength(2);
  });
});
