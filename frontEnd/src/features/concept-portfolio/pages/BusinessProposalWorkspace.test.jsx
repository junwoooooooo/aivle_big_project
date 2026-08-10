import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import BusinessProposalWorkspace, { CandidateInput, LegalReport } from './BusinessProposalWorkspace.jsx';
import { useConceptPortfolio } from '../hooks/useConceptPortfolio.js';

vi.mock('../hooks/useConceptPortfolio.js', () => ({ useConceptPortfolio: vi.fn() }));

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
    render(<CandidateInput request={{ status: 'OPEN', question: '판매 주체는?', affectedFields: ['sellerRole'] }} draft={{ field: 'sellerRole', value: '' }} onDraft={onDraft} onSubmit={vi.fn()} onRetry={vi.fn()} busy={false} />);
    expect(screen.getByText('답변 항목: 실제 판매 주체')).toBeInTheDocument();
    expect(screen.queryByLabelText('답변할 사업정보')).not.toBeInTheDocument();
  });
  it('requires target selection for multiple affected fields', () => {
    const onDraft = vi.fn();
    render(<CandidateInput request={{ status: 'OPEN', question: '무엇인가요?', affectedFields: ['sellerRole', 'paymentFlow'] }} draft={{ field: '', value: '값' }} onDraft={onDraft} onSubmit={vi.fn()} onRetry={vi.fn()} busy={false} />);
    expect(screen.getByRole('button', { name: '정보 제출' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('답변할 사업정보'), { target: { value: 'paymentFlow' } });
    expect(onDraft).toHaveBeenCalledWith({ field: 'paymentFlow', value: '' });
  });
  it('retries an answered continuation without asking for the same fact', () => {
    const onRetry = vi.fn();
    render(<CandidateInput request={{ status: 'ANSWERED', nextAction: 'RETRY_CONTINUATION' }} draft={{ field: '', value: '' }} onDraft={vi.fn()} onSubmit={vi.fn()} onRetry={onRetry} busy={false} />);
    fireEvent.click(screen.getByText('추가 사업정보 반영 다시 시도'));
    expect(onRetry).toHaveBeenCalled();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
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

describe('Final Legal Report actual contract', () => {
  it('renders actual Backend keys and values', () => {
    const report = { basisDate: '2026-08-11', report: {
      finalLegalConclusion: '조건부 가능', personalDataUsage: ['예약 정보 이용'],
      requiredPartnersAndQualifications: ['자격 보유 파트너'], prohibitedVariants: ['무자격 직접 제공'],
      advertisingExpressionCautions: ['보장 표현 금지'], unknownFacts: ['판매 주체 미확정'],
      officialEvidenceReferences: [{ title: '공식 근거' }], deltaLegalHistory: [{ revision: 3 }],
      transactionFlow: ['고객→플랫폼'], paymentFlow: ['고객→판매자'],
    } };
    const view = render(<LegalReport report={report} />);
    for (const text of ['조건부 가능', '예약 정보 이용', '자격 보유 파트너', '무자격 직접 제공', '보장 표현 금지', '판매 주체 미확정', '공식 근거', 'revision', '고객→플랫폼', '고객→판매자']) {
      expect(view.container.textContent).toContain(text);
    }
  });
});
