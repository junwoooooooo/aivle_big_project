import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import FeasibilityPage from './FeasibilityPage.jsx';
import { useFeasibility } from './hooks/useFeasibility.js';

vi.mock('./hooks/useFeasibility.js', () => ({ useFeasibility: vi.fn() }));
vi.mock('../projects/ProjectContext.jsx', () => ({
  useProjectContext: () => ({
    project: { stage: 'FEASIBILITY', stageLabel: '사업성 분석' },
  }),
}));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/projects/10/feasibility']}>
      <Routes>
        <Route path="/projects/:projectId/feasibility" element={<FeasibilityPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('FeasibilityPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows immutable plan and legal sources before start', () => {
    useFeasibility.mockReturnValue({
      status: 'ready',
      plan: { planId: 3, sourceDocumentVersionId: 4 },
      legalReview: { legalReviewId: 5 },
      start: vi.fn(), retry: vi.fn(),
    });
    renderPage();
    expect(screen.getByRole('heading', {
      name: '확정된 입력으로 사업 타당성 사전분석을 시작합니다',
    })).toBeInTheDocument();
    expect(screen.getByText('#5')).toBeInTheDocument();
    expect(screen.getByText(/시장 규모와 재무 수치는 자동 생성하지 않습니다/)).toBeInTheDocument();
  });

  it('renders server progress and refresh recovery guidance', () => {
    useFeasibility.mockReturnValue({
      status: 'processing',
      job: { jobId: 9, status: 'RUNNING', progress: 70 },
      retry: vi.fn(),
    });
    renderPage();
    expect(screen.getByText('사업 타당성 사전분석 진행 중')).toBeInTheDocument();
    expect(screen.getByText(/새로고침하거나 다시 로그인해도/)).toBeInTheDocument();
    expect(screen.getAllByText('70%')).toHaveLength(2);
  });

  it('shows evidence types, validation tasks, finance boundary and disclaimer', () => {
    useFeasibility.mockReturnValue({
      status: 'result',
      assessment: {
        status: 'NEEDS_VALIDATION', verdict: 'CONDITIONAL', overallScore: 66,
        confidence: 'LOW', summary: '검증이 필요합니다.', structuredPlanId: 3,
        legalReviewId: 5, provider: 'mock', keyStrengthsJson: '["강점"]',
        keyRisksJson: '["위험"]', disclaimer: '성공을 보장하지 않습니다.',
        dimensions: [{
          code: 'MARKET_ATTRACTIVENESS', score: 62, confidence: 'LOW',
          finding: '시장 주장이 있습니다.', rationale: '외부 검증 전입니다.',
          evidenceJson: '[{"type":"EXTERNAL_VERIFICATION_REQUIRED","description":"출처 확인","reference":"MARKET_SIZE"}]',
          risksJson: '["통계 미검증"]', recommendedActionsJson: '["출처 확인"]',
        }],
        validationTasks: [{
          code: 'VERIFY_MARKET', priority: 'HIGH', title: '시장 검증',
          description: '외부 자료를 확인합니다.', reason: '사실성 확인',
          validationMethod: '공공 통계 대조', expectedEvidence: '출처와 발행일',
        }],
      },
      retry: vi.fn(),
    });
    renderPage();
    expect(screen.getByText('외부 검증 필요')).toBeInTheDocument();
    expect(screen.getByText('시장 검증')).toBeInTheDocument();
    expect(screen.getByText(/부족한 정보는 0으로 처리하지 않습니다/)).toBeInTheDocument();
    expect(screen.getByText('성공을 보장하지 않습니다.')).toBeInTheDocument();
  });
});
