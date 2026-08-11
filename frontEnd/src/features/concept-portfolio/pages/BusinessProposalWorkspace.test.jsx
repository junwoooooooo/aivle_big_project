import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import BusinessProposalWorkspace, { CandidateInput, HypothesisField, LegalReport, PortfolioStatus } from './BusinessProposalWorkspace.jsx';
import { useConceptPortfolio } from '../hooks/useConceptPortfolio.js';

vi.mock('../hooks/useConceptPortfolio.js', () => ({ useConceptPortfolio: vi.fn() }));
vi.mock('../../../shared/async-events/index.js', () => ({
  jobEventMessage: (event) => event.message ?? event.messageKey,
  useJobEvents: () => ({ events: [], transport: 'idle' }),
}));

const base = (overrides = {}) => ({ loading: false, error: null, busy: false,
  run: { runId: 'run', productStatus: 'RESULTS_AVAILABLE', producedConceptCount: 1, openInputCount: 0 },
  concepts: [{ conceptId: 'c1', candidateId: 'candidate', conceptName: '지역 서비스', summary: '요약', selectable: true }],
  inputRequests: [], selection: null, hypotheses: [], report: null, marketSeed: null,
  select: vi.fn(), refresh: vi.fn(), start: vi.fn(), respond: vi.fn(), retryContinuation: vi.fn(),
  confirm: vi.fn(), alternative: vi.fn(), retryDelta: vi.fn(), finalizeReport: vi.fn(), finalizeMarketSeed: vi.fn(),
  ...overrides,
});
const renderWorkspace = () => render(<MemoryRouter initialEntries={['/app/projects/41/concepts']}><Routes><Route path="/app/projects/:projectId/concepts" element={<BusinessProposalWorkspace />} /></Routes></MemoryRouter>);

describe('CandidateInput', () => {
  it('uses the one allowed string field without guessing', () => {
    const onDraft = vi.fn();
    render(<CandidateInput request={{ status: 'OPEN', question: '판매 주체는?', affectedFields: ['sellerRole'] }} draft={{ values: { sellerRole: '' } }} onDraft={onDraft} onSubmit={vi.fn()} onRetry={vi.fn()} onExplore={vi.fn()} busy={false} />);
    expect(screen.getByLabelText('실제 판매 주체')).toBeInTheDocument();
    expect(screen.queryByLabelText('답변할 사업정보')).not.toBeInTheDocument();
  });
  it('renders and submits multiple affected fields together', () => {
    const onDraft = vi.fn();
    const onSubmit = vi.fn();
    const request = { status: 'OPEN', question: '무엇인가요?', affectedFields: ['personalDataUsage', 'paymentFlow'] };
    const view = render(<CandidateInput request={request} draft={{ values: { personalDataUsage: '', paymentFlow: '' } }} onDraft={onDraft} onSubmit={onSubmit} onRetry={vi.fn()} onExplore={vi.fn()} busy={false} />);
    expect(screen.queryByLabelText('답변할 사업정보')).not.toBeInTheDocument();
    expect(screen.getByLabelText('개인정보 이용')).toBeInTheDocument();
    expect(screen.getByLabelText('결제·수취 흐름')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '정보 제출' })).toBeDisabled();
    view.rerender(<CandidateInput request={request} draft={{ values: { personalDataUsage: '이름', paymentFlow: '카드' } }} onDraft={onDraft} onSubmit={onSubmit} onRetry={vi.fn()} onExplore={vi.fn()} busy={false} />);
    fireEvent.click(screen.getByRole('button', { name: '정보 제출' }));
    expect(onSubmit).toHaveBeenCalled();
  });
  it('does not offer a guessed eight-field selector when the target is unresolved', () => {
    const onExplore = vi.fn();
    render(<CandidateInput request={{ status: 'OPEN', question: '실제 운영 정보를 확인해 주세요.', reason: '법률 판단에 필요합니다.', affectedFields: [], nextAction: 'INPUT_TARGET_UNRESOLVED', candidateDisplayName: '방문 돌봄 연결', candidateOneLineSummary: '돌봄 제공자를 연결합니다.' }} draft={{ values: {} }} onDraft={vi.fn()} onSubmit={vi.fn()} onRetry={vi.fn()} onExplore={onExplore} busy={false} />);
    expect(screen.getByText('방문 돌봄 연결')).toBeInTheDocument();
    expect(screen.getByText('돌봄 제공자를 연결합니다.')).toBeInTheDocument();
    expect(screen.getByText('법률 판단에 필요합니다.')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('필요한 사업정보 항목을 자동으로 특정하지 못했습니다.');
    expect(screen.queryByLabelText('답변할 사업정보')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '정보 제출' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '다른 방향 다시 탐색' }));
    expect(onExplore).toHaveBeenCalled();
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
    expect(screen.getByDisplayValue('240000000')).toBeInTheDocument();
    expect(screen.getByDisplayValue('KRW')).toBeInTheDocument();
    expect(view.container.textContent).toContain('240,000,000 KRW · 3년');
    expect(view.container.textContent).not.toContain('{"amount"');
  });
});

describe('BusinessProposalWorkspace', () => {
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
    useConceptPortfolio.mockReturnValue(base({ selection: { selectionId: 17, conceptId: 'c1', nextAction: 'REVISE_OR_RETRY', hypothesisConfirmedCount: 7 }, retryDelta }));
    renderWorkspace();
    fireEvent.click(screen.getByText('변경사항 법률·규제 재검토 다시 시도'));
    expect(retryDelta).toHaveBeenCalled();
  });
});

describe('Portfolio status summary', () => {
  it('uses actual review counts and keeps technical failure distinct', () => {
    render(<PortfolioStatus run={{ productStatus: 'FAILED', producedConceptCount: 0, openInputCount: 0 }}
      events={[{ stage: 'SUMMARY', messageParams: { reviewed: 5, prepared: 0, needsInput: 0 } }]}
      onRestart={vi.fn()} onDetail={vi.fn()} />);
    expect(screen.getByText('5개의 사업안 후보를 검토했지만 최종 결과를 확정하지 못했습니다.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '작업 상세 보기' })).toBeInTheDocument();
  });
  it('explains actionable zero-accepted NEEDS_INPUT', () => {
    render(<PortfolioStatus run={{ productStatus: 'NEEDS_INPUT', producedConceptCount: 0, openInputCount: 1 }}
      events={[{ stage: 'SUMMARY', messageParams: { reviewed: 5, needsInput: 1 } }]} />);
    expect(screen.getByText(/5개의 사업안 후보를 검토했습니다/)).toHaveTextContent('1개의 사업안은 실제 운영정보 확인 후');
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
    expect(screen.getByRole('link', { name: '법령 원문 보기' })).toHaveAttribute('href', 'https://law.go.kr/example');
    expect(view.container.querySelector('pre')).toBeNull();
    expect(screen.getByText('정본 검증 정보').closest('details')).not.toHaveAttribute('open');
  });
});
