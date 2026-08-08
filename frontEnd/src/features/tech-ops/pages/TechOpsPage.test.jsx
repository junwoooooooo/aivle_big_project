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
});
