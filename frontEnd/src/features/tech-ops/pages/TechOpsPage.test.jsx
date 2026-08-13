import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import TechOpsPage from './TechOpsPage.jsx';
import useTechOps from '../hooks/useTechOps.js';

vi.mock('../hooks/useTechOps.js', () => ({ default: vi.fn() }));

const proposal = (value = null, pending = null) => ({ proposalValue: value, finalValue: null,
  source: 'AI_HYPOTHESIS', decision: 'PROPOSED', proposalVersion: 1,
  alternativeRequested: Boolean(pending), pendingAlternativeTaskRunId: pending });

const preparation = {
  preparationId: 'prep-1', revision: 1, sourceMarketSeedSnapshotId: 'seed-1', inputSnapshotId: null,
  requiredFacts: { productServiceSpecification: { value: { summary: '서비스', features: [] } } },
  proposalDecisions: {
    deliveryOrProductionMethod: proposal(), expectedMonthlyThroughputOrSales: proposal(),
    technicalSupplyOperationalConstraints: proposal(),
  },
  evidenceReferences: [], missingRequiredInputs: ['deliveryOrProductionMethod'], readyToFinalize: false,
};

const renderPage = () => render(<MemoryRouter initialEntries={['/app/projects/41/tech-ops']}>
  <Routes><Route path="/app/projects/:projectId/tech-ops" element={<TechOpsPage />} /></Routes>
</MemoryRouter>);

describe('TechOpsPage proposal generation state', () => {
  beforeEach(() => vi.clearAllMocks());

  it('restores initial generation state from the preparation query', () => {
    useTechOps.mockReturnValue({ loading: false, preparation: { ...preparation,
      proposalGenerationStatus: 'RUNNING', activeProposalTaskRunId: 'task-1' },
      snapshot: null, busy: null, error: null, saveFacts: vi.fn(), decide: vi.fn(),
      retryProposals: vi.fn(), addEvidence: vi.fn(), removeEvidence: vi.fn(), finalize: vi.fn() });
    renderPage();
    expect(screen.getByText('AI 운영 가설 생성 중')).toBeInTheDocument();
    expect(screen.getByLabelText('근거 파일')).toHaveAttribute('type', 'file');
    expect(screen.queryByLabelText('파일 또는 자료 참조')).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText('업로드된 파일 ID 또는 안전한 자료 참조')).not.toBeInTheDocument();
  });

  it('shows field alternative progress and a safe recoverable failure', () => {
    const pending = { ...preparation.proposalDecisions,
      deliveryOrProductionMethod: proposal({ method: '직접 제공' }, 'task-2') };
    useTechOps.mockReturnValue({ loading: false, preparation: { ...preparation,
      proposalDecisions: pending, proposalGenerationStatus: 'RUNNING', activeProposalTaskRunId: 'task-2' },
      snapshot: null, busy: null, error: null, saveFacts: vi.fn(), decide: vi.fn(),
      retryProposals: vi.fn(), addEvidence: vi.fn(), removeEvidence: vi.fn(), finalize: vi.fn() });
    const { rerender } = renderPage();
    expect(screen.getByText('새 제안 생성 중')).toBeInTheDocument();

    useTechOps.mockReturnValue({ loading: false, preparation: { ...preparation,
      proposalGenerationStatus: 'FAILED', safeError: 'AI_SERVICE_UNAVAILABLE' },
      snapshot: null, busy: null, error: null, saveFacts: vi.fn(), decide: vi.fn(),
      retryProposals: vi.fn(), addEvidence: vi.fn(), removeEvidence: vi.fn(), finalize: vi.fn() });
    rerender(<MemoryRouter initialEntries={['/app/projects/41/tech-ops']}>
      <Routes><Route path="/app/projects/:projectId/tech-ops" element={<TechOpsPage />} /></Routes>
    </MemoryRouter>);
    expect(screen.getByRole('alert')).toHaveTextContent('AI 제안 생성 실패 — 직접 입력하거나 다시 시도할 수 있습니다.');
  });

  it('renders canonical commercialization advisory sections and the Finance next step', () => {
    const areas = ['MARKET_BM', 'PRODUCT_TECH', 'OPERATIONS', 'RISK_GATE', 'PARTNER_SUPPLY', 'PILOT', 'SCALE'];
    const result = { productName: '예약 서비스', decision: 'CONDITIONAL_GO', summary: '상용화 요약', disclaimer: '가정 기반 자문입니다.',
      advice: areas.map((area) => ({ area, priority: 'HIGH', advice: `${area} 조언`, validationMethod: '로그', basisIds: ['FACT-001'] })),
      pilotPlan: { objective: '파일럿', scope: ['파트너'], metrics: ['완료율'], stopConditions: ['장애'], scaleConditions: ['목표'] },
      operatingCosts: Array.from({ length: 5 }, (_, index) => ({ category: `비용${index}`, behavior: 'VARIABLE', driver: '처리', trigger: '요청', measurementUnit: '건', pilotMeasurement: '기록', basisIds: ['FACT-001'] })),
      readiness: ['DATA_AI', 'CUSTOMER_TRUST', 'OBSERVABILITY_SLA', 'SCALABILITY'].map((topic) => ({ topic, priority: 'HIGH', assessment: '확인', watchouts: ['주의'], controls: ['통제'], validationMethod: '기록', basisIds: ['FACT-001'] })),
      gates: Array.from({ length: 6 }, (_, index) => ({ title: `게이트${index}`, status: 'OPEN', exitCriteria: '통과 조건', owner: '담당', basisIds: ['FACT-001'] })),
      layer1Facts: [{ factId: 'FACT-001', source: 'MARKET', path: 'MARKET.price', value: '9900' }], layer2Evidence: [] };
    useTechOps.mockReturnValue({ loading: false, preparation: { ...preparation, inputSnapshotId: 'snap-1' },
      snapshot: { snapshotId: 'snap-1', snapshotHash: `sha256:${'a'.repeat(64)}` }, advisory: { status: 'SUCCEEDED', stale: false, result },
      advisoryEvents: { events: [] }, busy: null, error: null, saveFacts: vi.fn(), decide: vi.fn(), retryProposals: vi.fn(),
      removeEvidence: vi.fn(), finalize: vi.fn(), handoff: vi.fn(), startAdvisory: vi.fn() });
    const { container } = renderPage();
    expect(screen.getByRole('heading', { name: '기술·운영 자문' })).toBeInTheDocument();
    expect(container.querySelector('.tech-ops-form-grid.project-form-layout')).toBeInTheDocument();
    expect(screen.getByText('7개 상용화 조언')).toBeInTheDocument();
    expect(screen.getByText('운영 비용 계측')).toBeInTheDocument();
    expect(screen.getByText('상용화 준비도')).toBeInTheDocument();
    expect(screen.getByText('출시 게이트')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '다음 - 6. 재무 분석' })).toBeInTheDocument();
  });
});
