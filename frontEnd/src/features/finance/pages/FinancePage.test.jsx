import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import FinancePage from './FinancePage.jsx';

const money = (value = null, readOnly = false) => ({ value, readOnly, source: readOnly ? 'TECH_OPS_INPUT' : 'USER_INPUT', decision: value ? 'LOCKED' : 'OPEN' });
const { save, refresh, generateEstimates } = vi.hoisted(() => ({
  save: vi.fn(), refresh: vi.fn(), generateEstimates: vi.fn(),
}));
vi.mock('../hooks/useFinance.js', () => ({ default: () => ({ loading: false, busy: null, error: null, snapshot: null, run: null,
  analysis: { status: 'SUCCEEDED', result: { report: { headline: '분석 완료', findings: [], cautions: [], recommendedActions: [] },
    annualProjections: [], calculation: { scenarios: [] }, monteCarlo: {}, cashFlowChart: [], stressScenarios: [] } },
  preparation: { preparationId: 'finance-prep-1', revision: 1,
    sourceMarketResearchVersionId: 101, sourceBusinessModelVersionId: 201, inputSnapshotId: null,
    missingRequiredInputs: ['annualFixedRentAndManagementCost'], readyToFinalize: false,
    financialFields: { annualFixedLaborCost: money({ amount: 120000000, currency: 'KRW' }, true),
      annualFixedRentAndManagementCost: money(), annualFixedInfrastructureCost: money(),
      initialDevelopmentAndRnDCost: money(), initialEquipmentAndInfrastructureCost: money(), initialPatentAndLicensingCost: money(),
      totalMarketingCost: money(), totalSalesCost: money(), newCustomerCount: { value: null, readOnly: false },
      revenueModel: { value: 'ONE_TIME', readOnly: false }, unitPrice: money(), monthlySubscriptionPrice: money(),
      monthlyChurnRate: { value: null, readOnly: false },
      threeYearTargets: { value: null, readOnly: false }, unitVariableCost: money(), paymentFee: money(), partnerPayout: money(), shippingCost: money(), customerIncrementalInfraCost: money() },
    upstreamReferences: { marketAnalysis: { tam: { value: 1000000, unit: '명' }, sam: { value: 100000, unit: '명' },
      growth: { value: 12, unit: '%' }, price: { base: 9900, currency: 'KRW' } },
      businessModel: { financialHandoff: { revenueModel: 'ONE_TIME' } }, conceptHypotheses: {} },
    assistance: { cac: { explanation: '비용을 입력하면 CAC를 계산합니다.', example: '계산 예시', proposalValue: null },
      annualFixedRentAndManagementCost: { explanation: 'AI 추천', proposalValue: { amount: 12000000, currency: 'KRW' }, assumptions: ['월 100만원'], confidence: 'MEDIUM', source: 'AI_ESTIMATE', decision: 'PROPOSED', estimateStatus: 'SUCCEEDED' },
      threeYearTargets: { explanation: '목표 안내', proposalValue: null, estimateStatus: 'NONE' } }, calculatedCac: null },
  generateEstimate: vi.fn(), generateEstimates, decideEstimate: vi.fn(), save, refresh,
  finalize: vi.fn(), reopen: vi.fn(), analyze: vi.fn(), handoff: vi.fn() }) }));

describe('FinancePage', () => {
  it('승계 표시, 세부 재무 입력, 시스템 CAC 영역을 독립 화면에 표시한다', () => {
    const { container } = render(<MemoryRouter initialEntries={['/app/projects/1/finance']}><Routes>
      <Route path="/app/projects/:projectId/finance" element={<FinancePage />} />
    </Routes></MemoryRouter>);
    expect(screen.getByRole('heading', { name: '재무 가정의 원본과 근거' })).toBeInTheDocument();
    expect(screen.getByText(/Market Version 101/)).toBeInTheDocument();
    expect(screen.queryByText(/TechOps Snapshot/)).not.toBeInTheDocument();
    expect(screen.queryByText(/기술.?운영 분석에서 전달된 값/)).not.toBeInTheDocument();
    const source = screen.getByRole('heading', { name: '재무 가정의 원본과 근거' }).closest('section');
    expect(within(source).queryByText('고정운영비')).not.toBeInTheDocument();
    expect(within(source).queryByText('초기투자')).not.toBeInTheDocument();
    expect(within(source).queryByText('기존 3개년 목표')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '연간 고정비 세부항목' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '가격 및 반복 매출 가정' })).toBeInTheDocument();
    expect(screen.getByText('시스템 계산 CAC')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'AI 추천 채택' }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('button', { name: '다른 추천 요청' }).length).toBeGreaterThan(0);
    expect(screen.getByText('AI 추천', { selector: 'small' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '입력 내용 저장' })).toBeDisabled();
    expect(container.querySelectorAll('.finance-form-grid.project-form-layout').length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: '다음 - 가상 시장 인터뷰' }))
      .toHaveAttribute('href', '/app/projects/1/market-interview');
  });

  it('추천값은 input에 미리 보이지만 일반 저장 payload에는 들어가지 않는다', () => {
    render(<MemoryRouter initialEntries={['/app/projects/1/finance']}><Routes>
      <Route path="/app/projects/:projectId/finance" element={<FinancePage />} />
    </Routes></MemoryRouter>);
    const input = screen.getByLabelText(/^연간 임차·관리비/);
    expect(input).toHaveValue(12000000);
    expect(input).toHaveAttribute('data-proposal-preview', 'true');
    fireEvent.click(screen.getByRole('button', { name: '재무 입력 저장' }));
    expect(save).toHaveBeenCalledWith(expect.objectContaining({ annualFixedRentAndManagementCost: null }));
  });

  it('preserveView 새로고침과 그룹 추천을 실제 동작에 연결한다', () => {
    render(<MemoryRouter initialEntries={['/app/projects/1/finance']}><Routes>
      <Route path="/app/projects/:projectId/finance" element={<FinancePage />} />
    </Routes></MemoryRouter>);
    fireEvent.click(screen.getAllByRole('button', { name: '새로고침' })[0]);
    expect(refresh).toHaveBeenCalledWith({ preserveView: true });
    fireEvent.click(screen.getByRole('button', { name: '미확정 항목 그룹 추천' }));
    expect(generateEstimates).toHaveBeenCalledWith(expect.arrayContaining(['threeYearTargets']));
    expect(generateEstimates.mock.calls[0][0]).not.toContain('annualFixedRentAndManagementCost');
  });
});
