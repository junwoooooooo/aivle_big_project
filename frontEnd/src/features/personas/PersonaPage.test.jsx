import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import PersonaPage from './PersonaPage.jsx';
import { usePersonas } from './hooks/usePersonas.js';
import useAvailablePersonas from './hooks/useAvailablePersonas.js';
import { useServicePolicy } from '../service-policy/useServicePolicy.js';

vi.mock('./hooks/usePersonas.js', () => ({ usePersonas: vi.fn() }));
vi.mock('./hooks/useAvailablePersonas.js', () => ({ default: vi.fn() }));
vi.mock('../service-policy/useServicePolicy.js', () => ({ useServicePolicy: vi.fn() }));
vi.mock('../projects/ProjectContext.jsx', () => ({
  useProjectContext: () => ({ project: { stageLabel: '사업 타당성' } }),
}));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/projects/10/personas']}>
      <Routes><Route path="/projects/:projectId/personas" element={<PersonaPage />} /></Routes>
    </MemoryRouter>,
  );
}

describe('PersonaPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useServicePolicy.mockReturnValue({
      loading: false,
      error: null,
      policy: {
        documentProcessingEnabled: true,
        maintenanceMode: false,
        clusterPersonaEnabled: false,
      },
      refresh: vi.fn(),
    });
    useAvailablePersonas.mockReturnValue({
      data: null,
      loading: false,
      error: null,
      savingId: null,
      refresh: vi.fn(),
      select: vi.fn(),
    });
  });

  it('explains the hypothesis boundary before start', () => {
    usePersonas.mockReturnValue({
      status: 'ready', catalog: [], start: vi.fn(), retry: vi.fn(),
      feasibility: { assessmentId: 4, structuredPlanId: 3, validationTasks: [{ id: 1 }] },
    });
    renderPage();
    expect(screen.getByText(/추천은 실제 고객 조사 결과가 아닙니다/)).toBeInTheDocument();
    expect(screen.getByText('#4')).toBeInTheDocument();
  });

  it('renders comparisons, questions, and the persisted disclaimer', () => {
    usePersonas.mockReturnValue({
      status: 'result', catalog: [], retry: vi.fn(),
      recommendation: {
        summary: '우선 검증 고객군', status: 'NEEDS_VALIDATION', confidence: 'LOW',
        catalogVersion: 'persona-catalog-v1', provider: 'mock',
        disclaimer: '실제 고객 조사 결과가 아닙니다.',
        items: [{
          id: 1, rank: 1, recommendationLevel: 'PRIMARY', fitScore: 52,
          confidence: 'LOW', interpretation: '가설 해석',
          matchReasonsJson: '["맞는 근거"]', mismatchRisksJson: '["불일치 위험"]',
          assumptionsJson: '["가정"]', verificationQuestionsJson: '["확인 질문"]',
          baselinePersona: { displayName: '디지털 탐색형' },
        }],
        hypotheses: [{
          id: 1, priority: 'HIGH', statement: '문제 가설', rationale: '검증 필요',
          hypothesisType: 'PROBLEM', sourceType: 'AI_INFERENCE',
        }],
        validationPlans: [{
          id: 1, method: 'INTERVIEW', priority: 'HIGH', objective: '행동 검증',
          targetParticipantDescription: '실제 참여자', recruitmentChannel: '팀 결정',
          suggestedSampleSize: null, interviewQuestionsJson: '["인터뷰 질문"]',
          surveyQuestionsJson: '["설문 질문"]',
        }],
      },
    });
    renderPage();
    expect(screen.getByText('디지털 탐색형')).toBeInTheDocument();
    expect(screen.getAllByText('맞는 근거')).toHaveLength(2);
    expect(screen.getAllByText('인터뷰 질문')).toHaveLength(2);
    expect(screen.getByText('실제 고객 조사 결과가 아닙니다.')).toBeInTheDocument();
  });
});
