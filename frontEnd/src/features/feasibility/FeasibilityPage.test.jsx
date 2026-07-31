import { render, screen, within } from '@testing-library/react';
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
    useFeasibility.mockReturnValue(resultState());
    renderPage();
    expect(screen.getByText('외부 검증 필요')).toBeInTheDocument();
    expect(screen.getByText('시장 검증')).toBeInTheDocument();
    expect(screen.getByText(/부족한 정보는 0으로 처리하지 않습니다/)).toBeInTheDocument();
    expect(screen.getByText('성공을 보장하지 않습니다.')).toBeInTheDocument();
  });

  function resultState() {
    return {
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
        }, {
          code: 'BUSINESS_MODEL', score: 70, confidence: 'MEDIUM',
          finding: '수익 구조가 서술되어 있습니다.', rationale: '가정 검증 전입니다.',
          evidenceJson: '[]', risksJson: '[]', recommendedActionsJson: '[]',
        }],
        groups: [{
          analysisType: 'MARKET', displayOrder: 1, score: 62, verdict: 'CONDITIONAL',
          headline: '고객은 특정됐지만 시장 규모는 외부 확인이 필요합니다.',
          summary: '문제·고객·시장·경쟁을 함께 봤습니다.',
          strengthsJson: '["목표 고객이 특정되어 있습니다."]',
          risksJson: '["시장 규모 근거가 계획서 안에만 있습니다."]',
          nextFocus: '시장 규모 주장의 출처를 확보하세요.',
        }, {
          analysisType: 'BUSINESS_MODEL', displayOrder: 2, score: 70, verdict: 'CONDITIONAL',
          headline: '수익 구조는 있으나 재무 가정이 검증 가능하지 않습니다.',
          summary: '제품·수익·진입·재무 가정을 함께 봤습니다.',
          strengthsJson: '[]', risksJson: '[]',
          nextFocus: '가격과 원가 가정을 근거와 함께 정리하세요.',
        }, {
          analysisType: 'TECHNOLOGY_OPERATION', displayOrder: 3, score: null,
          verdict: 'INSUFFICIENT_INFORMATION',
          headline: '실행 역량을 판단할 근거가 부족합니다.',
          summary: '기술·운영과 규제 제약을 함께 봤습니다.',
          strengthsJson: '[]', risksJson: '[]', nextFocus: '생산 방식을 구체화하세요.',
        }],
        validationTasks: [{
          code: 'VERIFY_MARKET', priority: 'HIGH', title: '시장 검증',
          description: '외부 자료를 확인합니다.', reason: '사실성 확인',
          validationMethod: '공공 통계 대조', expectedEvidence: '출처와 발행일',
        }],
      },
      retry: vi.fn(),
    };
  }

  it('folds dimensions into three analysis groups with their own conclusions', () => {
    useFeasibility.mockReturnValue(resultState());
    renderPage();

    // 세 묶음이 각자 결론을 갖고 나온다
    ['시장 분석', '비즈니스 모델 분석', '기술·운영 분석'].forEach((label) => {
      expect(screen.getByRole('region', { name: label })).toBeInTheDocument();
    });
    const market = screen.getByRole('region', { name: '시장 분석' });
    expect(within(market).getByText(/고객은 특정됐지만 시장 규모는 외부 확인이 필요합니다/))
      .toBeInTheDocument();
    expect(within(market).getByText('시장 규모 주장의 출처를 확보하세요.')).toBeInTheDocument();

    // 묶음 점수는 헤더의 전용 슬롯에서 본다 (같은 숫자가 차원 행에도 있으므로)
    expect(within(market).getByLabelText('묶음 점수')).toHaveTextContent('62');

    // 점수 미상 묶음은 0으로 채우지 않는다 — 숫자 자리는 비고 판정이 '정보 부족'을 말한다
    const tech = screen.getByRole('region', { name: '기술·운영 분석' });
    expect(within(tech).getByLabelText('묶음 점수')).toHaveTextContent('—');
    expect(within(tech).getByText('정보 부족')).toBeInTheDocument();
    expect(within(tech).getByText('실행 역량을 판단할 근거가 부족합니다.')).toBeInTheDocument();

    // 차원은 사라지지 않고 해당 묶음 안으로 접혀 들어간다
    expect(within(market).getByText('시장 매력도')).toBeInTheDocument();
    const bm = screen.getByRole('region', { name: '비즈니스 모델 분석' });
    expect(within(bm).getByText('비즈니스 모델')).toBeInTheDocument();
    expect(within(market).queryByText('비즈니스 모델')).not.toBeInTheDocument();
  });

  it('offers the financial follow-up as a disabled next step', () => {
    useFeasibility.mockReturnValue(resultState());
    renderPage();
    const next = screen.getByRole('button', { name: /재무 분석 실행/ });
    expect(next).toBeDisabled();
    expect(screen.getByText(/같은 단계 안에서 이어지는 후속 분석/)).toBeInTheDocument();
  });
});
