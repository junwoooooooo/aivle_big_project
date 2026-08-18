import { readFileSync } from 'node:fs';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import BusinessProposalWorkspace, { CandidateInput, HypothesisField, LegalReport, PortfolioStatus } from './BusinessProposalWorkspace.jsx';
import { useConceptPortfolio } from '../hooks/useConceptPortfolio.js';

const { mockApiClient, mockBmPlanApi } = vi.hoisted(() => ({
  mockApiClient: {},
  mockBmPlanApi: {
    currentBmPlan: vi.fn().mockResolvedValue({ plan: {}, constraints: {}, revision: 0 }),
    saveBmPlan: vi.fn(),
  },
}));

vi.mock('../hooks/useConceptPortfolio.js', () => ({ useConceptPortfolio: vi.fn() }));
vi.mock('../../../shared/async-events/index.js', () => ({
  useJobEvents: () => ({ events: [], transport: 'idle' }),
}));
vi.mock('../../market/marketApi.js', () => ({
  createMarketApi: () => mockBmPlanApi,
}));
vi.mock('../../../shared/api/ApiClientProvider.jsx', () => ({ useApiClient: () => mockApiClient }));

const base = (overrides = {}) => ({ loading: false, error: null, busy: false,
  run: { runId: 'run', productStatus: 'RESULTS_AVAILABLE', producedConceptCount: 1, openInputCount: 0 },
  concepts: [{ conceptId: 'c1', candidateId: 'candidate', conceptName: '지역 서비스', summary: '요약', selectable: true }],
  inputRequests: [], selection: null, hypotheses: [], report: null, marketSeed: null,
  select: vi.fn(), refresh: vi.fn(), start: vi.fn(), respond: vi.fn(), retryContinuation: vi.fn(),
  confirm: vi.fn(), alternative: vi.fn(), retryDelta: vi.fn(), finalizeReport: vi.fn(), finalizeMarketSeed: vi.fn(),
  ...overrides,
});
const renderWorkspace = (entry = '/app/projects/41/concepts') => render(<MemoryRouter initialEntries={[entry]}><Routes><Route path="/app/projects/:projectId/concepts" element={<BusinessProposalWorkspace />} /></Routes></MemoryRouter>);

describe('CandidateInput', () => {
  it('uses the one allowed string field without guessing', () => {
    const onDraft = vi.fn();
    render(<CandidateInput request={{ status: 'OPEN', question: '판매 주체는?', affectedFields: ['sellerRole'] }} draft={{ values: { sellerRole: '' } }} onDraft={onDraft} onSubmit={vi.fn()} onRetry={vi.fn()} onExplore={vi.fn()} busy={false} />);
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /정보 입력해서 검토 계속/ }));
    expect(screen.getByLabelText('실제로 상품·서비스를 판매하는 주체')).toBeInTheDocument();
    expect(screen.queryByLabelText('답변할 사업정보')).not.toBeInTheDocument();
  });
  it('renders and submits multiple affected fields together', () => {
    const onDraft = vi.fn();
    const onSubmit = vi.fn();
    const request = { status: 'OPEN', question: '무엇인가요?', affectedFields: ['personalDataUsage', 'paymentFlow'] };
    const view = render(<CandidateInput request={request} draft={{ values: { personalDataUsage: '', paymentFlow: '' } }} onDraft={onDraft} onSubmit={onSubmit} onRetry={vi.fn()} onExplore={vi.fn()} busy={false} />);
    expect(screen.queryByLabelText('답변할 사업정보')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /정보 입력해서 검토 계속/ }));
    expect(screen.getByLabelText('수집·이용할 개인정보')).toBeInTheDocument();
    expect(screen.getByLabelText('결제와 정산이 이루어지는 흐름')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '정보 제출' })).toBeDisabled();
    view.rerender(<CandidateInput request={request} draft={{ values: { personalDataUsage: '이름', paymentFlow: '카드' } }} onDraft={onDraft} onSubmit={onSubmit} onRetry={vi.fn()} busy={false} />);
    fireEvent.click(screen.getByRole('button', { name: '정보 제출' }));
    expect(onSubmit).toHaveBeenCalled();
  });
  it('does not offer a guessed eight-field selector when the target is unresolved', () => {
    const view = render(<CandidateInput request={{ status: 'OPEN', question: 'What specific types of providers are required?', reason: 'Concept의 사업 구조를 보완해야 합니다.', affectedFields: [], nextAction: 'INPUT_TARGET_UNRESOLVED', candidateDisplayName: '방문 돌봄 연결', candidateOneLineSummary: '돌봄 제공자를 연결합니다.' }} draft={{ values: {} }} onDraft={vi.fn()} onSubmit={vi.fn()} onRetry={vi.fn()} busy={false} />);
    expect(view.container).toBeEmptyDOMElement();
    expect(screen.queryByLabelText('답변할 사업정보')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '정보 제출' })).not.toBeInTheDocument();
    expect(screen.queryByText(/What specific types/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Concept의 사업 구조/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '다른 방향 다시 탐색' })).not.toBeInTheDocument();
  });
  it('retries an answered continuation without asking for the same fact', () => {
    const onRetry = vi.fn();
    render(<CandidateInput request={{ status: 'ANSWERED', nextAction: 'RETRY_CONTINUATION' }} draft={{ values: {} }} onDraft={vi.fn()} onSubmit={vi.fn()} onRetry={onRetry} onExplore={vi.fn()} busy={false} />);
    fireEvent.click(screen.getByText('추가 사업정보 반영 다시 시도'));
    expect(onRetry).toHaveBeenCalled();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });
});

describe('structured hypothesis fields', () => {
  it('renders SOM as typed controls rather than raw JSON', () => {
    const view = render(<HypothesisField type="PRE_MARKET_SOM" value={{ proposedValue: { amount: 240000000, currency: 'KRW', period: '3년', calculationBasis: '시장 × 점유율', assumptions: ['초기 지역'] }, decisionStatus: 'PROPOSED' }} onEdit={vi.fn()} onAlternative={vi.fn()} disabled={false} />);
    expect(view.container.textContent).toContain('240,000,000 KRW');
    expect(view.container.textContent).toContain('2억 4천만 원');
    expect(view.container.textContent).toContain('시장 × 점유율');
    expect(view.container.textContent).toContain('초기 지역');
    expect(screen.queryByDisplayValue('240000000')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /수정/ }));
    expect(screen.getByDisplayValue('240000000')).toBeInTheDocument();
    expect(screen.getByDisplayValue('KRW')).toBeInTheDocument();
    expect(view.container.textContent).not.toContain('{"amount"');
  });
  it('확정 identity가 바뀌면 draft를 지우지 않고 editor만 닫는다', () => {
    const onEdit = vi.fn();
    const view = render(<HypothesisField type="TARGET_REGION" value={{ proposedValue: '서울', decisionStatus: 'PROPOSED' }} resetKey="PROPOSED:null" onEdit={onEdit} onAlternative={vi.fn()} disabled={false} />);
    fireEvent.click(screen.getByRole('button', { name: /수정/ }));
    expect(screen.getByRole('textbox', { name: '사업 대상 지역' })).toBeInTheDocument();
    view.rerender(<HypothesisField type="TARGET_REGION" value={{ proposedValue: '서울', finalValue: '부산', decisionStatus: 'ACCEPTED', locked: true }} edit="부산" resetKey={'ACCEPTED:"부산"'} onEdit={onEdit} onAlternative={vi.fn()} disabled={false} />);
    expect(screen.queryByRole('textbox', { name: '사업 대상 지역' })).not.toBeInTheDocument();
    expect(screen.getByText('부산')).toBeInTheDocument();
  });
});

describe('BusinessProposalWorkspace', () => {
  it('run 전에는 생성·법률검토·비교 과정을 보여주고 비교 tab을 숨긴다', () => {
    useConceptPortfolio.mockReturnValue(base({ run: null, concepts: [] }));
    renderWorkspace();
    expect(screen.getByRole('heading', { name: '사업안 생성 및 검토' })).toBeInTheDocument();
    expect(screen.getByText('법률·규제 검토')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '사업안 생성 및 법률 검토 시작' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '비교' })).not.toBeInTheDocument();
  });

  it('탭 없이 정확히 두 개를 고른 뒤 비교 Focus View로 이동하고 돌아온다', () => {
    useConceptPortfolio.mockReturnValue(base({ concepts: [
      { conceptId: 'c1', candidateId: 'a', conceptName: 'A', candidate: { targetUsers: ['매장'], revenueModel: '구독' } },
      { conceptId: 'c2', candidateId: 'b', conceptName: 'B', candidate: { targetUsers: ['고객'], revenueModel: '수수료' } },
      { conceptId: 'c3', candidateId: 'c', conceptName: 'C', candidate: { targetUsers: ['파트너'], revenueModel: '광고' } },
    ] }));
    renderWorkspace();
    expect(screen.getByRole('heading', { name: '생성된 사업안을 살펴보세요' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '사업안 목록' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '두 사업안 비교' })).not.toBeInTheDocument();
    const checkboxes = screen.getAllByRole('checkbox', { name: '비교에 추가' });
    fireEvent.click(checkboxes[0]);
    expect(screen.getByText(/한 개 더 선택하세요/)).toBeInTheDocument();
    fireEvent.click(checkboxes[1]);
    expect(checkboxes[2]).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '두 사업안 비교' }));
    expect(screen.getByRole('heading', { name: '두 사업안 비교' })).toBeInTheDocument();
    expect(screen.getByText('주요 고객')).toBeInTheDocument();
    expect(screen.queryByText('선택 전 법률·규제 요약')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '사업안으로 돌아가기' }));
    expect(screen.getByRole('heading', { name: '생성된 사업안을 살펴보세요' })).toBeInTheDocument();
  });

  it('사업안이 하나뿐이면 비교 picker와 checkbox를 표시하지 않는다', () => {
    useConceptPortfolio.mockReturnValue(base());
    renderWorkspace();
    expect(screen.queryByText(/^비교$/)).not.toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: '비교에 추가' })).not.toBeInTheDocument();
  });

  it('선택 전에는 사업안 선택 단계만 탐색할 수 있다', () => {
    useConceptPortfolio.mockReturnValue(base());
    renderWorkspace();
    expect(screen.getByRole('button', { name: '사업안 선택' })).toBeEnabled();
    for (const name of ['분석 기준 확정', '법률·규제 확인', '사업 검증 준비']) {
      expect(screen.getByRole('button', { name })).toBeDisabled();
      expect(screen.getByRole('button', { name })).toHaveAttribute('aria-disabled', 'true');
    }
  });

  it('does not show a recovered notice for proposals already present at selection baseline', () => {
    let state = base({ selection: { selectionId: 17, conceptId: 'c1', hypothesisConfirmedCount: 0 },
      concepts: [{ conceptId: 'c1', candidateId: 'a', conceptName: 'A' }, { conceptId: 'c2', candidateId: 'b', conceptName: 'B' }] });
    useConceptPortfolio.mockImplementation(() => state);
    const view = renderWorkspace();
    expect(screen.queryByText('추가 사업안이 준비되었습니다. 현재 선택은 유지됩니다.')).not.toBeInTheDocument();
    state = { ...state, concepts: [...state.concepts, { conceptId: 'c3', candidateId: 'c', conceptName: 'C' }] };
    view.rerender(<MemoryRouter initialEntries={['/app/projects/41/concepts']}><Routes><Route path="/app/projects/:projectId/concepts" element={<BusinessProposalWorkspace />} /></Routes></MemoryRouter>);
    expect(screen.getByText('추가 사업안이 준비되었습니다. 현재 선택은 유지됩니다.')).toBeInTheDocument();
  });
  it('offers Delta retry without resubmitting confirmed hypotheses', () => {
    const retryDelta = vi.fn();
    useConceptPortfolio.mockReturnValue(base({ selection: { selectionId: 17, conceptId: 'c1', status: 'DELTA_LEGAL_FAILED', nextAction: 'REVISE_OR_RETRY', hypothesisConfirmedCount: 7 }, retryDelta }));
    renderWorkspace();
    fireEvent.click(screen.getByText('법률·규제 재검토 다시 시도'));
    expect(retryDelta).toHaveBeenCalled();
  });
  it('선택 후 gallery를 접고 기준값만 펼친다', () => {
    useConceptPortfolio.mockReturnValue(base({
      concepts: [{ conceptId: 'c1', candidateId: 'a', conceptName: 'A', summary: '선택 요약' }, { conceptId: 'c2', candidateId: 'b', conceptName: 'B' }],
      selection: { selectionId: 17, conceptId: 'c1', status: 'PENDING_HYPOTHESIS_CONFIRMATION', hypothesisConfirmedCount: 0 },
      hypotheses: [{ hypothesisType: 'TARGET_REGION', proposedValue: '서울', decisionStatus: 'PROPOSED' }],
    }));
    renderWorkspace();
    expect(screen.getByText('선택한 사업안')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '시장 분석에 사용할 기준값' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: '생성된 사업안' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '선택 변경' })).toBeInTheDocument();
    expect(screen.queryByText('7개 검증 가정')).not.toBeInTheDocument();
  });
  it('현재 선택 카드로 돌아갈 때 selection API를 다시 호출하지 않는다', () => {
    const select = vi.fn();
    useConceptPortfolio.mockReturnValue(base({ select,
      concepts: [{ conceptId: 'c1', candidateId: 'a', conceptName: 'A' }, { conceptId: 'c2', candidateId: 'b', conceptName: 'B' }],
      selection: { selectionId: 17, conceptId: 'c1', status: 'PENDING_HYPOTHESIS_CONFIRMATION', hypothesisConfirmedCount: 0 },
    }));
    renderWorkspace();
    fireEvent.click(screen.getByRole('button', { name: '선택 변경' }));
    fireEvent.click(screen.getByRole('button', { name: /현재 선택으로 계속/ }));
    expect(select).not.toHaveBeenCalled();
    expect(screen.queryByRole('region', { name: '생성된 사업안' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '시장 분석에 사용할 기준값' })).toBeInTheDocument();
  });
  it('기준값 확정과 법률 보고서 준비를 서로 다른 action으로 제공한다', () => {
    const confirm = vi.fn(() => Promise.resolve());
    const finalizeReport = vi.fn();
    let state = base({ confirm, finalizeReport,
      selection: { selectionId: 17, conceptId: 'c1', status: 'PENDING_HYPOTHESIS_CONFIRMATION', hypothesisConfirmedCount: 0 },
      hypotheses: [
        ['TARGET_REGION', '서울'], ['REVENUE_MODEL', '구독'], ['PRICE', '월 5만원'],
        ['CHANNELS', ['웹']], ['DIFFERENTIATORS', ['자동화']],
        ['PRE_MARKET_SOM_SHARE', { targetSharePercent: 1, horizonYears: 2 }],
        ['PRE_MARKET_SOM', { amount: 500000, currency: 'KRW', period: '연간' }],
      ].map(([hypothesisType, proposedValue]) => ({ hypothesisType, proposedValue, decisionStatus: 'PROPOSED' })),
    });
    useConceptPortfolio.mockImplementation(() => state);
    const view = renderWorkspace();
    fireEvent.click(screen.getByRole('button', { name: '기준값 확정' }));
    expect(confirm).toHaveBeenCalled();
    state = { ...state, selection: { ...state.selection, status: 'READY_FOR_LEGAL_REPORT', hypothesisConfirmedCount: 7, nextAction: 'REVIEW_LEGAL_REPORT' }, hypotheses: state.hypotheses.map((item) => ({ ...item, finalValue: '서울', decisionStatus: 'ACCEPTED', locked: true })) };
    view.rerender(<MemoryRouter initialEntries={['/app/projects/41/concepts']}><Routes><Route path="/app/projects/:projectId/concepts" element={<BusinessProposalWorkspace />} /></Routes></MemoryRouter>);
    expect(screen.getByText('7/7 입력 완료')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /현재 값으로 진행/ }));
    expect(finalizeReport).toHaveBeenCalled();
  });
  it('Delta Legal 진행 중에는 기준값 화면을 유지하고 다음 action을 숨긴다', () => {
    useConceptPortfolio.mockReturnValue(base({
      selection: { selectionId: 17, conceptId: 'c1', status: 'DELTA_LEGAL_PENDING', hypothesisConfirmedCount: 7, activeTaskRunId: 'task-delta', nextAction: 'WAIT' },
      hypotheses: [{ hypothesisType: 'TARGET_REGION', finalValue: '부산', decisionStatus: 'USER_EDITED_ACCEPTED', locked: true }],
    }));
    renderWorkspace();
    expect(screen.getByRole('heading', { name: '시장 분석에 사용할 기준값' })).toBeInTheDocument();
    expect(screen.getByText(/변경한 기준이 법률·규제에 미치는 영향/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /현재 값으로 진행/ })).not.toBeInTheDocument();
  });
  it('LEGAL_REPORT_READY에서는 보고서를 즉시 표시하고 준비 action을 header에 둔다', () => {
    useConceptPortfolio.mockReturnValue(base({
      selection: { selectionId: 17, conceptId: 'c1', status: 'LEGAL_REPORT_READY', hypothesisConfirmedCount: 7, nextAction: 'FINALIZE_MARKET_SEED' },
      hypotheses: [{ hypothesisType: 'TARGET_REGION', finalValue: '서울', decisionStatus: 'ACCEPTED', locked: true }],
      report: { basisDate: '2026-08-14', report: { finalLegalConclusion: { status: 'IMPLEMENTABLE', safeSummary: '공식 근거 범위에서 검토했습니다.' } } },
    }));
    renderWorkspace();
    expect(screen.getByText('현재 조건으로 진행 가능')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /법률·규제 보고서 PDF/ })).toHaveAttribute('href', '/app/projects/41/concepts/legal-report');
    expect(screen.getByRole('button', { name: /시장 분석 준비하기/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '법률·규제 결과 확인 완료' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /시장 분석 준비하기/ }));
    expect(screen.getByRole('heading', { name: '사업 검증에 사용할 운영 정보를 준비하세요' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '사업 검증 준비' })).toHaveAttribute('aria-current', 'step');
  });
  it('법률 화면에서는 선택 변경을 숨기고 분석 기준과 인접하게 왕복하며 API를 호출하지 않는다', () => {
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
    const state = base({
      selection: { selectionId: 17, conceptId: 'c1', status: 'LEGAL_REPORT_READY', hypothesisConfirmedCount: 7, nextAction: 'FINALIZE_MARKET_SEED' },
      hypotheses: [{ hypothesisType: 'TARGET_REGION', finalValue: '서울', decisionStatus: 'ACCEPTED', locked: true }],
      report: { basisDate: '2026-08-14', report: { finalLegalConclusion: { status: 'IMPLEMENTABLE' } } },
    });
    useConceptPortfolio.mockReturnValue(state);
    renderWorkspace();
    expect(screen.queryByRole('button', { name: '선택 변경' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /분석 기준 확정으로 돌아가기/ }));
    expect(screen.getByRole('heading', { name: '시장 분석에 사용할 기준값' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '선택 변경' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /법률·규제 결과로 돌아가기/ }));
    expect(screen.getByRole('heading', { name: '법률·규제 검토 결과를 확인하세요' })).toBeInTheDocument();
    for (const action of [state.select, state.confirm, state.finalizeReport, state.finalizeMarketSeed]) expect(action).not.toHaveBeenCalled();
    expect(scrollTo).toHaveBeenCalledWith(expect.objectContaining({ top: 0 }));
    scrollTo.mockRestore();
  });
  it('지원 후보만 primary에 표시하고 미지원 후보는 compact disclosure로 분리한다', () => {
    useConceptPortfolio.mockReturnValue(base({ inputRequests: [
      { inputRequestId: 'supported', scope: 'CANDIDATE', status: 'OPEN', candidateId: 's', candidateDisplayName: '지원 후보', affectedFields: ['sellerRole'] },
      { inputRequestId: 'unsupported', scope: 'CANDIDATE', status: 'OPEN', candidateId: 'u', candidateDisplayName: '미지원 후보', affectedFields: [], question: 'What provider type?' },
    ] }));
    renderWorkspace();
    expect(screen.getByText('지원 후보')).toBeInTheDocument();
    expect(screen.getByText('미지원 후보')).not.toBeVisible();
    expect(screen.getByRole('button', { name: '이번에 이어서 검토하지 못한 사업안 1개' })).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(screen.getByRole('button', { name: '이번에 이어서 검토하지 못한 사업안 1개' }));
    expect(screen.getByText('미지원 후보')).toBeVisible();
    expect(document.body.textContent).not.toContain('What provider type?');
  });
  it('기준값의 사용자 언어와 responsive overflow 계약을 유지한다', () => {
    const hypotheses = [
      ['TARGET_REGION', '서울'], ['REVENUE_MODEL', '월 구독'], ['PRICE', '월 39,000원'],
      ['CHANNELS', ['웹']], ['DIFFERENTIATORS', ['자동화']],
      ['PRE_MARKET_SOM_SHARE', { targetSharePercent: 2.5, horizonYears: 3 }],
      ['PRE_MARKET_SOM', { amount: 8000000000, currency: 'KRW', period: '연간' }],
    ].map(([hypothesisType, proposedValue]) => ({ hypothesisType, proposedValue, decisionStatus: 'PROPOSED' }));
    useConceptPortfolio.mockReturnValue(base({ selection: { selectionId: 17, conceptId: 'c1', status: 'PENDING_HYPOTHESIS_CONFIRMATION', hypothesisConfirmedCount: 0 }, hypotheses }));
    renderWorkspace();
    for (const label of ['사업 대상 지역', '수익을 만드는 방식', '가격·과금 방식', '고객에게 제공하는 경로', '핵심 차별점', '목표 시장 점유율', '초기 목표 시장 규모']) expect(screen.getByText(label)).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/7개 검증 가정|시장 점유 가정|초기 확보 시장 규모|제안값/);
    const css = readFileSync('src/features/concept-portfolio/styles/business-proposal.css', 'utf8');
    expect(css).toContain('grid-template-columns: minmax(9rem, 10rem) repeat(2, minmax(0, 1fr))');
    expect(css).toContain('grid-template-columns: repeat(3, minmax(0, 27.5rem))');
    expect(css).toContain('width: min(100%, 80rem)');
    expect(css).toContain('.hypothesis-field__editor');
    expect(css).toContain('.business-decision-stack');
    expect(css).toContain('gap: 1.75rem');
    expect(css).toContain('.candidate-input__fields textarea');
    expect(css).toContain('background: #fff');
    expect(css).toContain('.hypothesis-field__read--structured');
    expect(css).toContain('box-shadow: 0 0 0 3px');
    expect(css).toContain('.bp-button--primary');
    expect(css).toContain('min-height: 2.75rem');
    expect(css).toContain('-webkit-line-clamp: 6');
    expect(css).toContain('@media (max-width: 25rem)');
    expect(css).toContain('max-width: 100%');
    expect(css).not.toContain('min-width: max-content');
    expect(css).not.toContain('repeat(auto-fit');
  });
  it('7개 AI 제안값을 7/7 입력 완료로 세고 한 번에 확정한다', async () => {
    const confirm = vi.fn().mockResolvedValue(undefined);
    const values = [
      ['TARGET_REGION', '대한민국'], ['REVENUE_MODEL', 'B2B 구독'],
      ['PRICE', '서비스 계약에 따라 변동'], ['CHANNELS', ['직접 영업']],
      ['DIFFERENTIATORS', ['AI 카메라 데이터 분석']],
      ['PRE_MARKET_SOM_SHARE', { targetSharePercent: 2, horizonYears: 3 }],
      ['PRE_MARKET_SOM', { amount: 500000, currency: 'KRW', period: '연간' }],
    ];
    const hypotheses = values.map(([hypothesisType, proposedValue]) => ({
      hypothesisType, proposedValue, decisionStatus: 'PROPOSED', semanticStatus: 'VALID', legalReviewStatus: 'NOT_REQUIRED', source: 'AI',
    }));
    useConceptPortfolio.mockReturnValue(base({ confirm,
      selection: { selectionId: 17, conceptId: 'c1', status: 'PENDING_HYPOTHESIS_CONFIRMATION', hypothesisConfirmedCount: 6 }, hypotheses }));
    renderWorkspace();
    expect(screen.getByText('7/7 입력 완료')).toBeInTheDocument();
    expect(screen.getAllByText('AI 제안')).toHaveLength(7);
    expect(screen.queryByText(/확인이 필요한 항목/)).not.toBeInTheDocument();
    expect(screen.getByText(/500,000 KRW/).parentElement).toHaveTextContent('500,000 KRW · 50만 원');
    fireEvent.click(screen.getByRole('button', { name: '기준값 확정' }));
    await waitFor(() => expect(confirm).toHaveBeenCalledTimes(1));
    expect(confirm).toHaveBeenCalledWith({});
  });
  it('실제 빈 값만 6/7로 표시하고 해당 행으로 이동한다', async () => {
    const confirm = vi.fn();
    const hypotheses = [
      ['TARGET_REGION', '대한민국'], ['REVENUE_MODEL', 'B2B 구독'], ['PRICE', ''],
      ['CHANNELS', ['직접 영업']], ['DIFFERENTIATORS', ['자동화']],
      ['PRE_MARKET_SOM_SHARE', { targetSharePercent: 2, horizonYears: 3 }],
      ['PRE_MARKET_SOM', { amount: 500000, currency: 'KRW', period: '연간' }],
    ].map(([hypothesisType, proposedValue]) => ({ hypothesisType, proposedValue, decisionStatus: 'PROPOSED' }));
    useConceptPortfolio.mockReturnValue(base({ confirm,
      selection: { selectionId: 17, conceptId: 'c1', status: 'PENDING_HYPOTHESIS_CONFIRMATION' }, hypotheses }));
    renderWorkspace();
    expect(screen.getByText('6/7 입력 완료')).toBeInTheDocument();
    expect(screen.getByText(/값이 필요한 항목/).parentElement).toHaveTextContent('가격·과금 방식');
    fireEvent.click(screen.getByRole('button', { name: '기준값 확정' }));
    expect(confirm).not.toHaveBeenCalled();
    expect(await screen.findByRole('alert')).toHaveTextContent('가격·과금 방식 값이 비어 있습니다');
    await waitFor(() => expect(document.activeElement).toHaveAttribute('id', 'business-basis-PRICE'));
  });
  it('값 존재 7/7과 확정 가능 상태를 분리하고 차단 사유로 이동한다', async () => {
    const confirm = vi.fn();
    const hypotheses = [
      ['TARGET_REGION', '대한민국'], ['REVENUE_MODEL', 'B2B 구독'], ['PRICE', '협의'],
      ['CHANNELS', ['직접 영업']], ['DIFFERENTIATORS', ['자동화']],
      ['PRE_MARKET_SOM_SHARE', { targetSharePercent: 2, horizonYears: 3 }],
      ['PRE_MARKET_SOM', { amount: 500000, currency: 'KRW', period: '연간' }],
    ].map(([hypothesisType, proposedValue]) => ({ hypothesisType, proposedValue,
      decisionStatus: 'PROPOSED', semanticStatus: hypothesisType === 'PRICE' ? 'INVALID' : 'VALID',
      semanticReason: hypothesisType === 'PRICE' ? '가격 산정 기준을 입력해 주세요.' : null }));
    useConceptPortfolio.mockReturnValue(base({ confirm,
      selection: { selectionId: 17, conceptId: 'c1', status: 'PENDING_HYPOTHESIS_CONFIRMATION' }, hypotheses }));
    renderWorkspace();
    expect(screen.getByText('7/7 입력 완료')).toBeInTheDocument();
    expect(screen.getByText(/확정할 수 없는 항목/).parentElement).toHaveTextContent('가격·과금 방식');
    fireEvent.click(screen.getByRole('button', { name: '기준값 확정' }));
    expect(confirm).not.toHaveBeenCalled();
    expect(await screen.findByRole('alert')).toHaveTextContent('가격·과금 방식: 가격 산정 기준을 입력해 주세요.');
    await waitFor(() => expect(document.activeElement).toHaveAttribute('id', 'business-basis-PRICE'));
  });
  it('시장 준비 상태에서는 저장 정보를 조회하고 실제 시장 분석 CTA를 표시한다', async () => {
    useConceptPortfolio.mockReturnValue(base({ selection: { selectionId: 17, conceptId: 'c1', status: 'READY_FOR_MARKET', hypothesisConfirmedCount: 7 } }));
    renderWorkspace();
    expect(screen.getByRole('heading', { name: '사업 검증 준비를 마쳤습니다.' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: '저장한 운영 정보' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /시장 분석 시작하기/ })).toBeInTheDocument();
  });

  it('READY_FOR_MARKET에서 4→3→2→1→3을 조회하고 mutation API를 호출하지 않는다', async () => {
    const state = base({
      concepts: [{ conceptId: 'c1', candidateId: 'a', conceptName: 'A', summary: 'A 요약' }, { conceptId: 'c2', candidateId: 'b', conceptName: 'B', summary: 'B 요약' }],
      selection: { selectionId: 17, conceptId: 'c1', status: 'READY_FOR_MARKET', hypothesisConfirmedCount: 7 },
      hypotheses: [{ hypothesisType: 'TARGET_REGION', finalValue: '서울', decisionStatus: 'ACCEPTED', locked: true }],
      report: { basisDate: '2026-08-14', report: {
        finalLegalConclusion: { status: 'IMPLEMENTABLE', safeSummary: '진행 가능합니다.' },
        businessRoles: { platformRole: '예약 연결' }, transactionFlow: ['고객 신청'], personalDataUsage: ['예약 정보'],
      } },
    });
    useConceptPortfolio.mockReturnValue(state);
    renderWorkspace();
    for (const name of ['사업안 선택', '분석 기준 확정', '법률·규제 확인', '사업 검증 준비']) expect(screen.getByRole('button', { name })).toBeEnabled();

    fireEvent.click(screen.getByRole('button', { name: '법률·규제 확인' }));
    expect(screen.getByRole('heading', { name: '사업 구조 검토' })).toBeInTheDocument();
    expect(screen.getByText('고객 신청')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '분석 기준 확정' }));
    expect(screen.getByRole('heading', { name: '시장 분석에 사용할 기준값' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '사업안 선택' }));
    expect(screen.getByRole('region', { name: '생성된 사업안' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /이 사업안 선택/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '선택 변경' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '법률·규제 확인' }));
    expect(screen.getByRole('heading', { name: '법률·규제 검토 결과를 확인하세요' })).toBeInTheDocument();
    for (const action of [state.select, state.confirm, state.finalizeReport, state.finalizeMarketSeed]) expect(action).not.toHaveBeenCalled();
  });

  it('같은 selectionId에서 conceptId가 바뀌어도 gallery를 접고 새 기준 단계로 전환한다', async () => {
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
    const select = vi.fn(() => Promise.resolve());
    let state = base({
      select,
      concepts: [{ conceptId: 'c1', candidateId: 'a', conceptName: 'A', summary: 'A 요약' }, { conceptId: 'c2', candidateId: 'b', conceptName: 'B', summary: 'B 요약' }],
      selection: { selectionId: 17, conceptId: 'c1', status: 'PENDING_HYPOTHESIS_CONFIRMATION', hypothesisConfirmedCount: 0 },
      hypotheses: [{ hypothesisType: 'TARGET_REGION', proposedValue: '서울', decisionStatus: 'PROPOSED' }],
    });
    useConceptPortfolio.mockImplementation(() => state);
    const view = renderWorkspace();
    fireEvent.click(screen.getByRole('button', { name: '선택 변경' }));
    fireEvent.click(screen.getByRole('button', { name: /이 사업안 선택/ }));
    expect(select).toHaveBeenCalledWith('c2');
    state = { ...state, selection: { ...state.selection, conceptId: 'c2' }, hypotheses: [{ hypothesisType: 'TARGET_REGION', proposedValue: '부산', decisionStatus: 'PROPOSED' }] };
    view.rerender(<MemoryRouter initialEntries={['/app/projects/41/concepts']}><Routes><Route path="/app/projects/:projectId/concepts" element={<BusinessProposalWorkspace />} /></Routes></MemoryRouter>);
    await waitFor(() => expect(screen.queryByRole('region', { name: '생성된 사업안' })).not.toBeInTheDocument());
    expect(screen.getByRole('heading', { name: 'B' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '시장 분석에 사용할 기준값' })).toBeInTheDocument();
    expect(document.activeElement).toHaveClass('business-decision__current');
    expect(scrollTo).toHaveBeenCalledWith(expect.objectContaining({ top: 0 }));
    scrollTo.mockRestore();
  });

  it('새 selectionId 재선택도 동일하게 gallery를 접는다', async () => {
    const select = vi.fn(() => Promise.resolve());
    let state = base({
      select,
      concepts: [{ conceptId: 'c1', candidateId: 'a', conceptName: 'A' }, { conceptId: 'c2', candidateId: 'b', conceptName: 'B' }],
      selection: { selectionId: 17, conceptId: 'c1', status: 'PENDING_HYPOTHESIS_CONFIRMATION', hypothesisConfirmedCount: 0 },
    });
    useConceptPortfolio.mockImplementation(() => state);
    const view = renderWorkspace();
    fireEvent.click(screen.getByRole('button', { name: '선택 변경' }));
    fireEvent.click(screen.getByRole('button', { name: /이 사업안 선택/ }));
    state = { ...state, selection: { ...state.selection, selectionId: 18, conceptId: 'c2' } };
    view.rerender(<MemoryRouter initialEntries={['/app/projects/41/concepts']}><Routes><Route path="/app/projects/:projectId/concepts" element={<BusinessProposalWorkspace />} /></Routes></MemoryRouter>);
    await waitFor(() => expect(screen.queryByRole('region', { name: '생성된 사업안' })).not.toBeInTheDocument());
    expect(screen.getByRole('heading', { name: 'B' })).toBeInTheDocument();
  });

  it('재선택 API 실패 시 gallery와 기존 선택 authority를 유지한다', async () => {
    const select = vi.fn(() => Promise.resolve());
    let state = base({
      select,
      concepts: [{ conceptId: 'c1', candidateId: 'a', conceptName: 'A' }, { conceptId: 'c2', candidateId: 'b', conceptName: 'B' }],
      selection: { selectionId: 17, conceptId: 'c1', status: 'PENDING_HYPOTHESIS_CONFIRMATION', hypothesisConfirmedCount: 0 },
    });
    useConceptPortfolio.mockImplementation(() => state);
    const view = renderWorkspace();
    fireEvent.click(screen.getByRole('button', { name: '선택 변경' }));
    fireEvent.click(screen.getByRole('button', { name: /이 사업안 선택/ }));
    state = { ...state, error: new Error('selection rejected') };
    view.rerender(<MemoryRouter initialEntries={['/app/projects/41/concepts']}><Routes><Route path="/app/projects/:projectId/concepts" element={<BusinessProposalWorkspace />} /></Routes></MemoryRouter>);
    await waitFor(() => expect(screen.getByRole('region', { name: '생성된 사업안' })).toBeInTheDocument());
    expect(screen.getByText('A').closest('.proposal-card')).toHaveAttribute('data-selected', 'true');
    expect(screen.getByText('B').closest('.proposal-card')).toHaveAttribute('data-selected', 'false');
  });

  it('재선택 요청 중에는 대상 카드에만 즉시 진행 상태를 표시한다', async () => {
    let resolveSelect;
    const select = vi.fn(() => new Promise((resolve) => { resolveSelect = resolve; }));
    useConceptPortfolio.mockReturnValue(base({
      select,
      concepts: [{ conceptId: 'c1', candidateId: 'a', conceptName: 'A' }, { conceptId: 'c2', candidateId: 'b', conceptName: 'B' }],
      selection: { selectionId: 17, conceptId: 'c1', status: 'PENDING_HYPOTHESIS_CONFIRMATION', hypothesisConfirmedCount: 0 },
    }));
    renderWorkspace();
    fireEvent.click(screen.getByRole('button', { name: '선택 변경' }));
    fireEvent.click(screen.getByRole('button', { name: /이 사업안 선택/ }));
    expect(screen.getByRole('button', { name: '선택 중...' })).toBeInTheDocument();
    resolveSelect();
    await waitFor(() => expect(screen.getByRole('button', { name: /이 사업안 선택/ })).toBeInTheDocument());
  });
});

describe('Portfolio status summary', () => {
  it('uses actual review counts and keeps technical failure distinct', () => {
    render(<PortfolioStatus run={{ productStatus: 'FAILED', producedConceptCount: 0, openInputCount: 0 }}
      events={[{ stage: 'SUMMARY', messageKey: 'job.concept-portfolio.summary', messageParams: { reviewed: 5, prepared: 0, needsInput: 0 } }]}
      onRestart={vi.fn()} onDetail={vi.fn()} />);
    expect(screen.getByText('5개 검토 · 0개 준비 · 0개 추가 확인')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /작업센터에서 상세 기록 보기/ })).toBeInTheDocument();
  });
  it('explains actionable zero-accepted NEEDS_INPUT', () => {
    render(<PortfolioStatus run={{ productStatus: 'NEEDS_INPUT', producedConceptCount: 0, openInputCount: 1 }}
      events={[{ stage: 'SUMMARY', messageKey: 'job.concept-portfolio.summary', messageParams: { reviewed: 5, prepared: 0, needsInput: 1 } }]} />);
    expect(screen.getByText('5개 검토 · 0개 준비 · 1개 추가 확인')).toBeInTheDocument();
  });
  it('running 초기에 0개·0건 metric을 표시하지 않는다', () => {
    const view = render(<PortfolioStatus run={{ productStatus: 'RUNNING', producedConceptCount: 0, openInputCount: 0 }} events={[]} />);
    expect(view.container.textContent).not.toMatch(/0개 사업안|추가 검토 0건/);
  });
});

describe('Final Legal Report actual contract', () => {
  it('renders actual Backend keys and values', () => {
    const report = { basisDate: '2026-08-11', report: {
      finalLegalConclusion: { productionStatus: 'CONDITIONAL', safeSummary: '조건부 가능', legalSourceStatus: 'SOURCE_PARTIAL' }, personalDataUsage: ['예약 정보 이용'],
      requiredPartnersAndQualifications: ['자격 보유 파트너'], prohibitedVariants: ['무자격 직접 제공'],
      advertisingExpressionCautions: { allowedClaims: ['검토된 범위 표현'], requiredDisclosures: ['보장 표현 금지'] }, unknownFacts: ['판매 주체 미확정'],
      officialEvidenceReferences: [{ lawName: '전자상거래법', articleReference: '제13조', boundedProvisionSummary: '사업자 정보를 고지합니다.', officialSourceUri: 'https://law.go.kr/example' }],
      deltaLegalHistory: [{ reviewToken: 'delta-3', safeSummary: '가격 변경 영향 검토 완료' }], sourceHashes: { selectedConcept: 'sha256:abc' },
      transactionFlow: ['고객→플랫폼'], paymentFlow: ['고객→판매자'],
    } };
    const view = render(<LegalReport report={report} />);
    for (const text of ['조건부 가능', '예약 정보 이용', '자격 보유 파트너', '무자격 직접 제공', '보장 표현 금지', '판매 주체 미확정', '전자상거래법', '가격 변경 영향 검토 완료', '고객→플랫폼', '고객→판매자']) {
      expect(view.container.textContent).toContain(text);
    }
    expect(screen.getByRole('alert')).toHaveTextContent('조회 범위에는 제한');
    const articleButton = screen.getByRole('button', { name: '제13조' });
    expect(articleButton).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(articleButton);
    expect(articleButton).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('link', { name: '법령 원문 보기' })).toHaveAttribute('href', 'https://law.go.kr/example');
    expect(view.container.querySelector('pre')).toBeNull();
    expect(screen.queryByRole('button', { name: '기술 정보' })).not.toBeInTheDocument();
    expect(view.container.textContent).not.toContain('sha256:abc');
    expect(view.container.textContent).not.toContain('CONDITIONAL');
    expect(screen.queryByRole('heading', { name: '사업 진행 전 확인할 내용' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '특히 확인할 사항' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '사업 구조 검토' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '상세 검토 내용' })).toBeInTheDocument();
  });

  it('여러 source의 동일 법률 문장을 한 번만 표시하고 광고 고지는 교차 중복을 제거한다', () => {
    const view = render(<LegalReport report={{ basisDate: '2026-08-14', report: {
      finalLegalConclusion: { status: 'IMPLEMENTABLE' },
      requiredControls: ['개인정보 동의', '  개인정보   동의  '],
      requiredDisclosures: ['판매 주체 표시'],
      partnerRequirements: ['전문 파트너'], qualificationRequirements: ['전문 파트너가 필요함.'], requiredPartnersAndQualifications: ['전문 파트너'],
      advertisingExpressionCautions: { requiredDisclosures: ['판매 주체 표시', '광고 문구 조건 표시'] },
    } }} />);
    expect(screen.getAllByText('개인정보 동의')).toHaveLength(1);
    expect(screen.getAllByText('전문 파트너')).toHaveLength(1);
    expect(screen.getAllByText('판매 주체 표시')).toHaveLength(1);
    expect(screen.getByText('광고 문구 조건 표시')).toBeInTheDocument();
    expect(view.container.textContent).not.toContain('사업 진행 전 확인할 내용');
  });
});
