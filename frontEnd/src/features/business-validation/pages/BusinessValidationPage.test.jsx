import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import BusinessValidationPage, { BusinessValidationContent } from './BusinessValidationPage.jsx';

const { pageClient } = vi.hoisted(() => ({
  pageClient: { get: vi.fn(), post: vi.fn(), put: vi.fn() },
}));

vi.mock('react-router-dom', () => ({
  useParams: () => ({ projectId: '41' }),
  useOutletContext: () => ({ liveRevision: 0 }),
}));
vi.mock('../../../shared/api/ApiClientProvider.jsx', () => ({
  useApiClient: () => pageClient,
}));

vi.mock('../../market/MarketResearchPage.jsx', () => ({
  MarketResultBody: () => <div>시장 결과 본문</div>,
}));
vi.mock('../../market/BmCanvasPage.jsx', () => ({
  BusinessModelResultBody: () => <div>비즈니스 모델 결과 본문</div>,
}));
vi.mock('../../market/useCellFocus.js', () => ({
  default: () => ({ active: null, jump: vi.fn() }),
}));

const stage = (state, result = null) => ({ state, result });
const view = (state, market = stage('WAITING'), businessModel = stage('WAITING'), session = 'session-B') => ({
  businessValidationSessionId: session, state, stale: state === 'STALE', market, businessModel, actions: [],
});
const api = {
  currentCompetitorSeeds: vi.fn().mockResolvedValue({ seeds: [] }),
  saveCompetitorSeeds: vi.fn(),
};
const policy = { priceChangePercent: 30, listChangeAllowance: 1, maxProposals: 6, maxRounds: 3 };
const refinement = (state, extra = {}) => ({ state, stale: false, round: state === 'NOT_STARTED' ? 0 : 1, policy,
  proposals: [], retry: { available: false }, nextRound: { available: false, currentRound: 1, maxRounds: 3 },
  sourceBusinessValidationSessionId: 'session-B', ...extra });
const finalView = (outcome = 'REFINED', stale = false, session = 'session-B') => ({
  sourceBusinessValidationSessionId: session, state: 'FINALIZED', outcome, stale,
  value: {
    outcome,
    selectedConcept: { identity: { conceptName: '지역 연결', targetUsers: '지역 상점', coreValue: '폐기 절감' },
      solution: { featureSet: ['매칭'] }, operation: { operatingModel: '직접 운영' } },
    finalHypotheses: {
      targetRegion: { value: '대한민국' }, revenueModel: { value: '구독' }, price: { value: '12,500원' },
      channels: { value: ['앱'] }, differentiators: { value: ['지역성'] },
      preMarketSomShare: { value: { targetSharePercent: 2.5 } },
      preMarketSom: { value: { amount: 100000000, currency: 'KRW' } },
    },
    businessModelPlan: { key_activities: ['상점 확보'], key_resources: ['운영팀'],
      key_partners: ['지역 협회'], customer_relationship: ['전담 지원'] },
    selectedChanges: [{ fieldKey: 'price', currentValue: '10,000원', proposedValue: '12,500원',
      rationale: '수익성 보완', source: 'MARKET' }],
  },
});

describe('BusinessValidationContent', () => {
  beforeEach(() => vi.clearAllMocks());
  it('준비 정보와 하나의 사업 검증 시작 명령을 보여준다', async () => {
    render(<BusinessValidationContent current={view('NOT_STARTED')} plan={{ revision: 1 }} api={api} />);
    expect(screen.getByRole('button', { name: '사업 검증 시작' })).toBeInTheDocument();
    expect(screen.getByText('현재 검증 기준을 확인하세요')).toBeInTheDocument();
  });

  it('시장 분석 실행 중에는 BM을 대기로 표시한다', () => {
    render(<BusinessValidationContent current={view('MARKET_RUNNING', stage('RUNNING'))} api={api} />);
    expect(screen.getByText('사업 검증 진행 중')).toBeInTheDocument();
    expect(screen.getAllByText('대기').length).toBeGreaterThan(0);
  });

  it('시장 완료 후 BM 실행 중에도 시장 결과를 보존한다', () => {
    render(<BusinessValidationContent current={view('BM_RUNNING',
      stage('SUCCEEDED', { market: {} }), stage('RUNNING'))} api={api} />);
    expect(screen.getByText('시장 결과 본문')).toBeInTheDocument();
    expect(screen.getByText('비즈니스 모델 분석')).toBeInTheDocument();
    expect(screen.getAllByText('진행 중').length).toBeGreaterThan(0);
  });

  it('BM 실패 시 시장 결과와 BM 전용 재시도를 함께 보여준다', () => {
    render(<BusinessValidationContent current={view('BM_FAILED',
      stage('SUCCEEDED', { market: {} }), stage('FAILED'))} api={api} />);
    expect(screen.getByText('시장 결과 본문')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'BM 다시 시도' })).toBeInTheDocument();
    expect(screen.getByText(/같은 시장 결과로 비즈니스 모델만/)).toBeInTheDocument();
  });

  it('완료되면 시장과 BM 결과를 한 화면에 표시한다', () => {
    render(<BusinessValidationContent current={view('COMPLETED',
      stage('SUCCEEDED', { market: {} }), stage('SUCCEEDED', { bm: {} }))} api={api} />);
    expect(screen.getByText('시장 결과 본문')).toBeInTheDocument();
    expect(screen.getByText('비즈니스 모델 결과 본문')).toBeInTheDocument();
  });

  it('완료된 사업 검증에서만 수동 다듬기 시작 CTA를 보여준다', () => {
    render(<BusinessValidationContent current={view('COMPLETED')} refinement={refinement('NOT_STARTED')}
      refinementFinal={{ state: 'NOT_STARTED', stale: false }} api={api} />);
    expect(screen.getByRole('button', { name: '다듬기 제안 받기' })).toBeInTheDocument();
    expect(screen.getByText(/제안을 받는 것만으로 사업안이 변경되지는 않습니다/)).toBeInTheDocument();
    expect(screen.getByText(/최대 ±30%/)).toBeInTheDocument();
  });

  it('제안 생성 중에는 진행 문구만 표시하고 선택 UI를 열지 않는다', () => {
    render(<BusinessValidationContent current={view('COMPLETED')} refinement={refinement('PROPOSING')} api={api} />);
    expect(screen.getByText('검증 결과를 읽고 개선 제안을 만들고 있습니다.')).toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '선택한 변경 반영' })).not.toBeInTheDocument();
  });

  it('제안은 사람용 label과 before/after로 표시하고 proposalKey만 선택 callback에 전달한다', () => {
    const onDecideAndApply = vi.fn();
    const proposal = { proposalKey: 'sha256:proposal', fieldKey: 'price', currentValue: '10,000원',
      proposedValue: '12,500원', rationale: '수익성을 보완합니다.', source: 'MARKET', evidenceIds: ['e1'] };
    render(<BusinessValidationContent current={view('COMPLETED')}
      refinement={refinement('AWAITING_DECISION', { proposals: [proposal], proposalSetHash: 'sha256:set',
        nextRound: { available: true, currentRound: 1, maxRounds: 3 } })}
      api={api} onDecideAndApply={onDecideAndApply} />);
    expect(screen.getByText('가격')).toBeInTheDocument();
    expect(screen.getByText('10,000원')).toBeInTheDocument();
    expect(screen.getByText('12,500원')).toBeInTheDocument();
    expect(screen.getByText(/아직 사업안에는 반영되지 않았습니다/)).toBeInTheDocument();
    expect(screen.queryByText('price')).not.toBeInTheDocument();
    const action = screen.getByRole('button', { name: '선택한 변경 반영' });
    const next = screen.getByRole('button', { name: '다른 제안 받기' });
    expect(action).toBeDisabled();
    fireEvent.click(screen.getByRole('checkbox', { name: '가격 변경안 반영' }));
    expect(action).toBeEnabled(); expect(next).toBeDisabled();
    expect(screen.getByText(/선택한 변경을 먼저 해제/)).toBeInTheDocument(); fireEvent.click(action);
    expect(onDecideAndApply).toHaveBeenCalledWith(['sha256:proposal']);
  });

  it('적용 완료 후 확정과 현재 변경을 유지하는 다음 제안 action을 함께 제공한다', () => {
    const onNext = vi.fn();
    render(<BusinessValidationContent current={view('STALE')}
      refinement={refinement('APPLIED_PENDING_FINALIZATION', {
        nextRound: { available: true, currentRound: 1, maxRounds: 3 },
        decision: { decisionHash: 'sha256:decision' },
      })} api={api} onNextRefinement={onNext} />);
    expect(screen.getByRole('button', { name: '이 컨셉으로 확정하기' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '다른 제안 더 받기' }));
    expect(onNext).toHaveBeenCalledTimes(1);
    expect(screen.getByText(/지금까지 반영한 변경은 그대로 유지/)).toBeInTheDocument();
  });

  it('Round 2 proposal을 그대로 표시하고 Round 3에서는 마지막 안내와 함께 next를 숨긴다', () => {
    const proposal = { proposalKey: 'sha256:r2', fieldKey: 'channels', currentValue: ['앱'],
      proposedValue: ['앱', '파트너'], source: 'MARKET', evidenceIds: ['e1'] };
    const { rerender } = render(<BusinessValidationContent current={view('STALE')}
      refinement={refinement('AWAITING_DECISION', { round: 2, proposals: [proposal],
        proposalSetHash: 'sha256:set-2', nextRound: { available: true, currentRound: 2, maxRounds: 3 } })}
      api={api} />);
    expect(screen.getByText('제안 2 / 3')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: '고객 접점·채널 변경안 반영' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다른 제안 받기' })).toBeInTheDocument();
    rerender(<BusinessValidationContent current={view('STALE')}
      refinement={refinement('AWAITING_DECISION', { round: 3, proposals: [proposal],
        proposalSetHash: 'sha256:set-3', nextRound: { available: false, currentRound: 3, maxRounds: 3 } })}
      api={api} />);
    expect(screen.getByText('제안 3 / 3')).toBeInTheDocument();
    expect(screen.getByText('마지막 제안입니다.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '다른 제안 받기' })).not.toBeInTheDocument();
    rerender(<BusinessValidationContent current={view('STALE')}
      refinement={refinement('APPLIED_PENDING_FINALIZATION', { round: 3,
        decision: { decisionHash: 'sha256:decision-3' },
        nextRound: { available: false, currentRound: 3, maxRounds: 3 } })} api={api} />);
    expect(screen.getByRole('button', { name: '이 컨셉으로 확정하기' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '다른 제안 더 받기' })).not.toBeInTheDocument();
  });

  it.each([
    ['DECISION_RECORDED', '선택한 변경안이 저장되었습니다.', '선택한 변경 반영'],
    ['APPLY_FAILED', '선택한 변경을 반영하지 못했습니다.', '변경 반영 다시 시도'],
    ['LEGAL_REVIEW_FAILED', '법률 영향 확인을 완료하지 못했습니다.', '법률 검토 다시 시도'],
    ['APPLIED_PENDING_FINALIZATION', '선택한 변경을 반영했습니다.', '이 컨셉으로 확정하기'],
    ['FINALIZATION_FAILED', '최종 컨셉을 정리하지 못했습니다.', '최종 확정 다시 시도'],
  ])('%s 상태에 맞는 복구 또는 다음 action을 제공한다', (state, message, action) => {
    render(<BusinessValidationContent current={view('STALE')} refinement={refinement(state)} api={api} />);
    expect(screen.getByText(new RegExp(message))).toBeInTheDocument();
    expect(screen.getByRole('button', { name: action })).toBeInTheDocument();
  });

  it('법률 검토 중과 차단 상태를 성공으로 표현하지 않는다', () => {
    const { rerender } = render(<BusinessValidationContent current={view('STALE')}
      refinement={refinement('LEGAL_REVIEW_PENDING')} api={api} />);
    expect(screen.getByText('선택한 변경의 법률 영향을 확인하고 있습니다.')).toBeInTheDocument();
    rerender(<BusinessValidationContent current={view('STALE')} refinement={refinement('LEGAL_BLOCKED')} api={api} />);
    expect(screen.getByText(/법률 검토를 통과하지 못해/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /확정/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /다른 제안/ })).not.toBeInTheDocument();
    expect(screen.queryByText('다듬기 완료')).not.toBeInTheDocument();
  });

  it('안전한 snapshot이 있는 LEGAL_BLOCKED에서만 명시적 복구 action을 제공한다', () => {
    const onRecover = vi.fn();
    const { rerender } = render(<BusinessValidationContent current={view('STALE')}
      refinement={refinement('LEGAL_BLOCKED', { recovery: { available: true },
        decision: { decisionHash: 'sha256:decision' } })} api={api}
      onRecoverLegalBlocked={onRecover} />);
    fireEvent.click(screen.getByRole('button', { name: '차단된 변경 취소' }));
    expect(onRecover).toHaveBeenCalledTimes(1);
    expect(screen.getByText(/변경 전 상태로 돌아갈 수 있습니다/)).toBeInTheDocument();
    rerender(<BusinessValidationContent current={view('STALE')}
      refinement={refinement('LEGAL_BLOCKED', { recovery: { available: false } })} api={api} />);
    expect(screen.queryByRole('button', { name: '차단된 변경 취소' })).not.toBeInTheDocument();
    expect(screen.getByText(/사업 검증을 다시 진행해야 합니다/)).toBeInTheDocument();
  });

  it('RECOVERED는 현재 상태 확정과 허용된 다음 제안을 제공하고 마지막 Round에서는 next를 숨긴다', () => {
    const onNext = vi.fn(); const onFinalize = vi.fn();
    const { rerender } = render(<BusinessValidationContent current={view('STALE')}
      refinement={refinement('RECOVERED', { decision: { decisionHash: 'sha256:decision' },
        nextRound: { available: true, currentRound: 1, maxRounds: 3 } })} api={api}
      onNextRefinement={onNext} onFinalizeRefinement={onFinalize} />);
    expect(screen.getByText(/이전 검증값으로 복구했습니다/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '현재 상태로 확정' }));
    fireEvent.click(screen.getByRole('button', { name: '다른 제안 받기' }));
    expect(onFinalize).toHaveBeenCalledTimes(1); expect(onNext).toHaveBeenCalledTimes(1);
    rerender(<BusinessValidationContent current={view('STALE')}
      refinement={refinement('RECOVERED', { round: 3, decision: { decisionHash: 'sha256:decision' },
        nextRound: { available: false, currentRound: 3, maxRounds: 3 } })} api={api} />);
    expect(screen.getByRole('button', { name: '현재 상태로 확정' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '다른 제안 받기' })).not.toBeInTheDocument();
  });

  it('최종 structured authority로 컨셉·가설·BM·실제 변경을 표시한다', () => {
    render(<BusinessValidationContent current={view('STALE')} refinement={refinement('FINALIZED')}
      refinementFinal={finalView()} api={api} />);
    expect(screen.getByText('다듬어진 컨셉')).toBeInTheDocument();
    expect(screen.getAllByText('지역 연결').length).toBeGreaterThan(0);
    expect(screen.getByText('대한민국')).toBeInTheDocument();
    expect(screen.getByText('상점 확보')).toBeInTheDocument();
    expect(screen.getByText('10,000원 → 12,500원')).toBeInTheDocument();
    expect(screen.getByText('다듬기 완료')).toBeInTheDocument();
  });

  it.each([
    ['KEEP_CURRENT', '현재 사업안 유지'], ['NO_CHANGES', '바꿀 점 없음'],
  ])('최종 outcome %s를 사용자 언어로 구분한다', (outcome, label) => {
    render(<BusinessValidationContent current={view('STALE')} refinement={refinement('FINALIZED')}
      refinementFinal={finalView(outcome)} api={api} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });

  it('self-induced BV stale에서도 건강한 refinement를 보존하고 추가 변경 stale은 경고한다', () => {
    const { rerender } = render(<BusinessValidationContent current={view('STALE')}
      refinement={refinement('APPLIED_PENDING_FINALIZATION')} api={api} />);
    expect(screen.getByText('검증 결과로 사업안 다듬기')).toBeInTheDocument();
    expect(screen.getByText(/이전 검증 결과는 기준 결과로 보존/)).toBeInTheDocument();
    rerender(<BusinessValidationContent current={view('STALE')} refinement={refinement('FINALIZED')}
      refinementFinal={finalView('REFINED', true)} api={api} />);
    expect(screen.getByText(/이후 사업안이 변경되어 이 다듬기 결과는 현재 기준이 아닙니다/)).toBeInTheDocument();
  });

  it('새 완료 cycle에서는 과거 refinement/final 대신 새 다듬기 시작 CTA를 보여준다', () => {
    render(<BusinessValidationContent current={view('COMPLETED')}
      refinement={refinement('FINALIZED', { sourceBusinessValidationSessionId: 'session-A' })}
      refinementFinal={finalView('REFINED', false, 'session-A')} api={api} />);
    expect(screen.getByRole('button', { name: '다듬기 제안 받기' })).toBeInTheDocument();
    expect(screen.queryByText('다듬기 완료')).not.toBeInTheDocument();
  });

  it('새 validation 진행 중에는 과거 Final이 현재 진행 화면을 덮지 않는다', () => {
    render(<BusinessValidationContent current={view('MARKET_RUNNING', stage('RUNNING'))}
      refinement={refinement('FINALIZED', { sourceBusinessValidationSessionId: 'session-A' })}
      refinementFinal={finalView('REFINED', false, 'session-A')} api={api} />);
    expect(screen.getByText('사업 검증 진행 중')).toBeInTheDocument();
    expect(screen.queryByText('다듬어진 컨셉')).not.toBeInTheDocument();
  });

  it('현재 cycle PROPOSING이 과거 FINALIZED보다 우선한다', () => {
    render(<BusinessValidationContent current={view('COMPLETED')} refinement={refinement('PROPOSING')}
      refinementFinal={finalView('REFINED', false, 'session-A')} api={api} />);
    expect(screen.getByText('검증 결과를 읽고 개선 제안을 만들고 있습니다.')).toBeInTheDocument();
    expect(screen.queryByText('다듬어진 컨셉')).not.toBeInTheDocument();
  });

  it('현재 cycle AWAITING_DECISION이 과거 FINALIZED 대신 proposal 선택을 제공한다', () => {
    const proposal = { proposalKey: 'sha256:current', fieldKey: 'targetUsers', currentValue: '기존 고객',
      proposedValue: '핵심 고객', source: 'MARKET', evidenceIds: [] };
    render(<BusinessValidationContent current={view('COMPLETED')}
      refinement={refinement('AWAITING_DECISION', { proposals: [proposal], proposalSetHash: 'sha256:set-B' })}
      refinementFinal={finalView('REFINED', false, 'session-A')} api={api} />);
    expect(screen.getByRole('checkbox', { name: '타깃 고객 변경안 반영' })).toBeInTheDocument();
    expect(screen.queryByText('다듬어진 컨셉')).not.toBeInTheDocument();
  });

  it('현재 cycle의 건강한 refinement는 과거 stale Final에 오염되지 않는다', () => {
    render(<BusinessValidationContent current={view('STALE')}
      refinement={refinement('APPLIED_PENDING_FINALIZATION')}
      refinementFinal={finalView('REFINED', true, 'session-A')} api={api} />);
    expect(screen.getByText(/이전 검증 결과는 기준 결과로 보존/)).toBeInTheDocument();
    expect(screen.queryByText(/사업안이 추가로 변경되어 이 다듬기 결과/)).not.toBeInTheDocument();
    expect(screen.queryByText(/이후 사업안이 변경되어 이 다듬기 결과/)).not.toBeInTheDocument();
  });
});

describe('BusinessValidationPage multi-round command', () => {
  const currentResponse = view('COMPLETED');
  const finalResponse = { sourceBusinessValidationSessionId: 'session-B', state: 'NOT_STARTED',
    outcome: null, stale: false, value: null };
  const proposal = { proposalKey: 'sha256:proposal', fieldKey: 'price', currentValue: '10,000원',
    proposedValue: '12,500원', rationale: '수익성 보완', source: 'MARKET', evidenceIds: ['e1'] };

  const arrangeGets = (refinementResponse) => pageClient.get.mockImplementation((url) => {
    if (url.endsWith('/business-validation/current')) return Promise.resolve({ data: currentResponse });
    if (url.endsWith('/business-model/plan')) return Promise.resolve({ data: { revision: 3 } });
    if (url.endsWith('/refinement/current')) return Promise.resolve({ data: refinementResponse });
    if (url.endsWith('/refinement/final')) return Promise.resolve({ data: finalResponse });
    return Promise.reject(new Error('unexpected GET'));
  });

  beforeEach(() => vi.clearAllMocks());

  it('AWAITING next는 proposalSetHash만 보내고 network ambiguity 시 current/final만 각 1회 복구 조회한다', async () => {
    const awaiting = refinement('AWAITING_DECISION', { proposals: [proposal], proposalSetHash: 'sha256:set',
      nextRound: { available: true, currentRound: 1, maxRounds: 3 } });
    arrangeGets(awaiting); pageClient.post.mockRejectedValueOnce(new Error('network ambiguous'));
    render(<BusinessValidationPage />);
    fireEvent.click(await screen.findByRole('button', { name: '다른 제안 받기' }));
    await waitFor(() => expect(pageClient.post).toHaveBeenCalledTimes(1));
    expect(pageClient.post.mock.calls[0][0]).toMatch(/\/refinement\/next$/);
    expect(pageClient.post.mock.calls[0][1]).toEqual({ expectedRound: 1,
      expectedProposalSetHash: 'sha256:set', expectedDecisionHash: null });
    await waitFor(() => {
      expect(pageClient.get.mock.calls.filter(([url]) => url.endsWith('/refinement/current'))).toHaveLength(2);
      expect(pageClient.get.mock.calls.filter(([url]) => url.endsWith('/refinement/final'))).toHaveLength(2);
    });
    expect(pageClient.post).toHaveBeenCalledTimes(1);
  });

  it('APPLIED next는 decisionHash만 보내고 성공한 Round 2 PROPOSING을 즉시 표시한다', async () => {
    const applied = refinement('APPLIED_PENDING_FINALIZATION', {
      decision: { decisionHash: 'sha256:decision' },
      nextRound: { available: true, currentRound: 1, maxRounds: 3 },
    });
    arrangeGets(applied);
    pageClient.post.mockResolvedValueOnce({ data: refinement('PROPOSING', { round: 2,
      nextRound: { available: false, currentRound: 2, maxRounds: 3 } }) });
    render(<BusinessValidationPage />);
    fireEvent.click(await screen.findByRole('button', { name: '다른 제안 더 받기' }));
    await screen.findByText('앞선 선택을 바탕으로 다른 개선안을 만들고 있습니다.');
    expect(screen.queryByText('제안 2 / 3')).not.toBeInTheDocument();
    expect(pageClient.post.mock.calls[0][1]).toEqual({ expectedRound: 1,
      expectedProposalSetHash: null, expectedDecisionHash: 'sha256:decision' });
    expect(screen.getByText('앞선 선택을 바탕으로 다른 개선안을 만들고 있습니다.')).toBeInTheDocument();
  });

  it('LEGAL_BLOCKED 복구는 round와 decisionHash만 보내고 ambiguity에서 command를 재전송하지 않는다', async () => {
    const blocked = refinement('LEGAL_BLOCKED', { recovery: { available: true },
      decision: { decisionHash: 'sha256:decision' } });
    arrangeGets(blocked); pageClient.post.mockRejectedValueOnce(new Error('network ambiguous'));
    render(<BusinessValidationPage />);
    fireEvent.click(await screen.findByRole('button', { name: '차단된 변경 취소' }));
    await waitFor(() => expect(pageClient.post).toHaveBeenCalledTimes(1));
    expect(pageClient.post.mock.calls[0][0]).toMatch(/\/refinement\/recover-legal-blocked$/);
    expect(pageClient.post.mock.calls[0][1]).toEqual({ expectedRound: 1,
      expectedDecisionHash: 'sha256:decision' });
    await waitFor(() => {
      expect(pageClient.get.mock.calls.filter(([url]) => url.endsWith('/refinement/current'))).toHaveLength(2);
      expect(pageClient.get.mock.calls.filter(([url]) => url.endsWith('/refinement/final'))).toHaveLength(2);
    });
    expect(pageClient.post).toHaveBeenCalledTimes(1);
  });

  it('RECOVERED next는 decisionHash를 사용한다', async () => {
    const recovered = refinement('RECOVERED', { decision: { decisionHash: 'sha256:decision' },
      nextRound: { available: true, currentRound: 1, maxRounds: 3 } });
    arrangeGets(recovered); pageClient.post.mockResolvedValueOnce({ data: refinement('PROPOSING', { round: 2 }) });
    render(<BusinessValidationPage />);
    fireEvent.click(await screen.findByRole('button', { name: '다른 제안 받기' }));
    await waitFor(() => expect(pageClient.post).toHaveBeenCalledTimes(1));
    expect(pageClient.post.mock.calls[0][1]).toEqual({ expectedRound: 1,
      expectedProposalSetHash: null, expectedDecisionHash: 'sha256:decision' });
  });
});
