import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import FinancePage from './FinancePage.jsx';

const money = (value = null, readOnly = false) => ({ value, readOnly, source: readOnly ? 'TECH_OPS_INPUT' : 'USER_INPUT', decision: value ? 'LOCKED' : 'OPEN' });
vi.mock('../hooks/useFinance.js', () => ({ default: () => ({ loading: false, busy: null, error: null, snapshot: null, run: null,
  preparation: { preparationId: 'finance-prep-1', revision: 1, sourceTechOpsSnapshotId: 'tech-1',
    sourceMarketResearchVersionId: 101, sourceBusinessModelVersionId: 201, inputSnapshotId: null,
    missingRequiredInputs: ['annualFixedRentAndManagementCost'], readyToFinalize: false,
    financialFields: { annualFixedLaborCost: money({ amount: 120000000, currency: 'KRW' }, true),
      annualFixedRentAndManagementCost: money(), annualFixedInfrastructureCost: money(),
      initialDevelopmentAndRnDCost: money(), initialEquipmentAndInfrastructureCost: money(), initialPatentAndLicensingCost: money(),
      totalMarketingCost: money(), totalSalesCost: money(), newCustomerCount: { value: null, readOnly: false },
      revenueModel: { value: 'ONE_TIME', readOnly: false }, unitPrice: money(), monthlySubscriptionPrice: money(),
      monthlyChurnRate: { value: null, readOnly: false },
      threeYearTargets: { value: null, readOnly: false }, unitVariableCost: money(), paymentFee: money(), partnerPayout: money(), shippingCost: money(), customerIncrementalInfraCost: money() },
    upstreamReferences: { fixedOperatingCost: { annualEquivalent: { amount: 120000000, currency: 'KRW' } }, initialInvestment: { value: null }, threeYearTargets: { value: null } },
    assistance: { cac: { explanation: '비용을 입력하면 CAC를 계산합니다.', example: '계산 예시', proposalValue: null },
      annualFixedRentAndManagementCost: { explanation: 'AI 추천', proposalValue: { amount: 12000000, currency: 'KRW' }, assumptions: ['월 100만원'], confidence: 'MEDIUM', source: 'AI_ESTIMATE', decision: 'PROPOSED', estimateStatus: 'SUCCEEDED' },
      threeYearTargets: { explanation: '목표 안내', proposalValue: null, estimateStatus: 'NONE' } }, calculatedCac: null },
  generateEstimate: vi.fn(), decideEstimate: vi.fn(), save: vi.fn(), finalize: vi.fn(), reopen: vi.fn(), analyze: vi.fn(), handoff: vi.fn() }) }));

describe('FinancePage', () => {
  it('승계 표시, 세부 재무 입력, 시스템 CAC 영역을 독립 화면에 표시한다', () => {
    render(<MemoryRouter initialEntries={['/app/projects/1/finance']}><Routes>
      <Route path="/app/projects/:projectId/finance" element={<FinancePage />} />
    </Routes></MemoryRouter>);
    expect(screen.getByRole('heading', { name: '재무 가정의 원본과 근거' })).toBeInTheDocument();
    expect(screen.getByText(/Market Version 101/)).toBeInTheDocument();
    expect(screen.queryByText(/TechOps Snapshot/)).not.toBeInTheDocument();
    expect(screen.queryByText(/기술.?운영 분석에서 전달된 값/)).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '연간 고정비 세부항목' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '가격 및 반복 매출 가정' })).toBeInTheDocument();
    expect(screen.getByText('시스템 계산 CAC')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'AI 추천 채택' }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('button', { name: '다른 추천 요청' }).length).toBeGreaterThan(0);
    expect(screen.getByText('AI 추천', { selector: 'small' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '입력 Snapshot 확정' })).toBeDisabled();
  });
});
