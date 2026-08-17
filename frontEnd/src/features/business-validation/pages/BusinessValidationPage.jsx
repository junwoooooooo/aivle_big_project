import { useCallback, useEffect, useMemo, useState } from 'react';
import { useOutletContext, useParams } from 'react-router-dom';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { Alert, Button, LoadingState, ProjectStageHeader, ProjectWorkspace } from '../../../shared/ui';
import { MarketResultBody } from '../../market/MarketResearchPage.jsx';
import { BusinessModelResultBody } from '../../market/BmCanvasPage.jsx';
import CompetitorSeedForm from '../../market/CompetitorSeedForm.jsx';
import { BmPlanReview } from '../../market/BmPlanForm.jsx';
import { draftFrom } from '../../market/bmPlan.js';
import { normalizeMarketResult } from '../../market/marketResult.js';
import useCellFocus from '../../market/useCellFocus.js';
import { createBusinessValidationApi } from '../api/businessValidationApi.js';
import ConceptRefinementPanel from '../components/ConceptRefinementPanel.jsx';
import { resolveRefinementCycle } from '../model/refinementView.js';
import '../styles/business-validation.css';

const RUNNING = new Set(['MARKET_RUNNING', 'MARKET_COMPLETED', 'BM_RUNNING']);
const today = () => new Date().toISOString().slice(0, 10);

export default function BusinessValidationPage() {
  const { projectId } = useParams();
  const { liveRevision = 0 } = useOutletContext() ?? {};
  const client = useApiClient();
  const api = useMemo(() => createBusinessValidationApi(client, projectId), [client, projectId]);
  const [current, setCurrent] = useState(null);
  const [plan, setPlan] = useState(null);
  const [refinement, setRefinement] = useState(null);
  const [refinementFinal, setRefinementFinal] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [refinementBusy, setRefinementBusy] = useState(false);
  const [error, setError] = useState(null);
  const [refinementError, setRefinementError] = useState(null);

  const refreshRefinement = useCallback(async () => {
    const [nextRefinement, nextFinal] = await Promise.allSettled([
      api.currentRefinement(), api.currentRefinementFinal(),
    ]);
    if (nextRefinement.status === 'fulfilled') setRefinement(nextRefinement.value);
    else setRefinement((previous) => previous ?? { state: 'UNAVAILABLE', stale: false });
    if (nextFinal.status === 'fulfilled') setRefinementFinal(nextFinal.value);
    const failure = [nextRefinement, nextFinal].find((result) => result.status === 'rejected');
    if (failure) setRefinementError(getUserErrorMessage(failure.reason));
    else setRefinementError(null);
  }, [api]);

  const refresh = useCallback(async () => {
    const [next, nextPlan, nextRefinement, nextFinal] = await Promise.allSettled([
      api.current(), api.currentBmPlan(), api.currentRefinement(), api.currentRefinementFinal(),
    ]);
    if (next.status === 'fulfilled') setCurrent(next.value);
    if (nextPlan.status === 'fulfilled') setPlan(nextPlan.value);
    const validationFailure = [next, nextPlan].find((result) => result.status === 'rejected');
    setError(validationFailure ? getUserErrorMessage(validationFailure.reason) : null);
    if (nextRefinement.status === 'fulfilled') setRefinement(nextRefinement.value);
    else setRefinement((previous) => previous ?? { state: 'UNAVAILABLE', stale: false });
    if (nextFinal.status === 'fulfilled') setRefinementFinal(nextFinal.value);
    const refinementFailure = [nextRefinement, nextFinal].find((result) => result.status === 'rejected');
    setRefinementError(refinementFailure ? getUserErrorMessage(refinementFailure.reason) : null);
    setLoading(false);
  }, [api]);

  useEffect(() => {
    const timer = setTimeout(refresh, 0);
    return () => clearTimeout(timer);
  }, [refresh, liveRevision]);

  const act = useCallback(async (action) => {
    setBusy(true);
    setError(null);
    try { setCurrent(await action()); }
    catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setBusy(false); }
  }, []);

  const refine = useCallback(async (action) => {
    setRefinementBusy(true);
    setRefinementError(null);
    try { await action(); }
    catch (failure) {
      await refreshRefinement();
      setRefinementError(getUserErrorMessage(failure));
    } finally { setRefinementBusy(false); }
  }, [refreshRefinement]);

  const applyBody = useCallback((source = refinement) => ({
    expectedRound: source?.round,
    expectedDecisionHash: source?.decision?.decisionHash ?? null,
  }), [refinement]);

  const startRefinement = () => refine(async () => setRefinement(await api.startRefinement()));
  const retryRefinement = () => refine(async () => setRefinement(await api.retryRefinement()));
  const nextRefinement = () => refine(async () => {
    const awaiting = refinement?.state === 'AWAITING_DECISION';
    setRefinement(await api.nextRefinement({
      expectedRound: refinement?.round,
      expectedProposalSetHash: awaiting ? refinement?.proposalSetHash : null,
      expectedDecisionHash: awaiting ? null : refinement?.decision?.decisionHash ?? null,
    }));
  });
  const decideAndApply = (selectedProposalKeys) => refine(async () => {
    const decided = await api.decideRefinement({ expectedRound: refinement.round,
      proposalSetHash: refinement.proposalSetHash, selectedProposalKeys, keepCurrent: false });
    setRefinement(decided);
    setRefinement(await api.applyRefinement(applyBody(decided)));
  });
  const keepCurrent = () => refine(async () => {
    const decided = await api.decideRefinement({ expectedRound: refinement.round,
      proposalSetHash: refinement.proposalSetHash, selectedProposalKeys: [], keepCurrent: true });
    setRefinement(decided);
    setRefinementFinal(await api.finalizeRefinement(applyBody(decided)));
  });
  const applyRefinement = () => refine(async () =>
    setRefinement(await api.applyRefinement(applyBody())));
  const retryLegal = () => refine(async () =>
    setRefinement(await api.retryRefinementLegal(applyBody())));
  const recoverLegalBlocked = () => refine(async () =>
    setRefinement(await api.recoverLegalBlocked(applyBody())));
  const finalizeRefinement = () => refine(async () =>
    setRefinementFinal(await api.finalizeRefinement(applyBody())));

  if (loading) return <LoadingState label="사업 검증 상태를 불러오는 중" />;
  return <BusinessValidationContent current={current} plan={plan} refinement={refinement}
    refinementFinal={refinementFinal} api={api} busy={busy} refinementBusy={refinementBusy}
    error={error} refinementError={refinementError}
    onStart={() => act(() => api.start(today()))}
    onRetryBm={() => act(api.retryBusinessModel)}
    onStartRefinement={startRefinement} onRetryRefinement={retryRefinement}
    onNextRefinement={nextRefinement}
    onDecideAndApply={decideAndApply} onKeepCurrent={keepCurrent}
    onApplyRefinement={applyRefinement} onRetryLegal={retryLegal}
    onRecoverLegalBlocked={recoverLegalBlocked}
    onFinalizeRefinement={finalizeRefinement} />;
}

export function BusinessValidationContent({ current, plan, refinement, refinementFinal, api,
  busy = false, refinementBusy = false, error, refinementError,
  onStart = () => {}, onRetryBm = () => {}, onStartRefinement = () => {},
  onRetryRefinement = () => {}, onDecideAndApply = () => {}, onKeepCurrent = () => {},
  onNextRefinement = () => {},
  onApplyRefinement = () => {}, onRetryLegal = () => {}, onRecoverLegalBlocked = () => {},
  onFinalizeRefinement = () => {} }) {
  const state = current?.state ?? 'NOT_STARTED';
  const marketResult = normalizeMarketResult(current?.market?.result);
  const bmResult = normalizeMarketResult(current?.businessModel?.result);
  const marketFocus = useCellFocus('sec-');
  const running = RUNNING.has(state);
  const started = state !== 'NOT_STARTED' && state !== 'STALE';
  const { effectiveRefinement, effectiveFinal } = resolveRefinementCycle({
    validation: current, refinement, finalView: refinementFinal,
  });
  const refinementStarted = effectiveRefinement?.state
    && !['NOT_STARTED', 'UNAVAILABLE'].includes(effectiveRefinement.state);
  const finalStarted = effectiveFinal?.state && effectiveFinal.state !== 'NOT_STARTED';
  const healthyRefinement = (refinementStarted && !effectiveRefinement?.stale)
    || (finalStarted && !effectiveFinal?.stale);
  const actualRefinementStale = effectiveRefinement?.stale || effectiveFinal?.stale;
  const showRefinement = (state === 'COMPLETED' && !current?.stale) || refinementStarted || finalStarted;
  const deferValidationRerun = state === 'STALE' && healthyRefinement && !actualRefinementStale;
  const hasResults = Boolean(marketResult || bmResult);

  return <ProjectWorkspace as="section" mode="analyze" className="business-validation">
    <ProjectStageHeader step={2} eyebrow="사업 검증" title="시장성과 사업 모델을 함께 검증하세요"
      description="한 번 시작하면 시장 분석을 완료한 뒤 같은 결과를 근거로 비즈니스 모델 분석을 이어갑니다." />

    {error ? <Alert tone="danger">{error}</Alert> : null}
    {state === 'STALE' && actualRefinementStale
      ? <Alert tone="warning">사업안이 추가로 변경되어 이 다듬기 결과를 현재 결과로 사용할 수 없습니다.</Alert>
      : state === 'STALE' && healthyRefinement
        ? <Alert tone="info">다듬기에서 사업안이 변경되어 이전 검증 결과는 기준 결과로 보존되고 있습니다.</Alert>
        : state === 'STALE' ? <Alert tone="warning">사업안이 변경되어 다시 검증이 필요합니다. 기존 결과는 보존되어 있습니다.</Alert> : null}

    {!started && !deferValidationRerun ? <BusinessValidationPreparation api={api} plan={plan} disabled={busy}
      actionLabel={state === 'STALE' ? '사업 검증 다시 실행' : '사업 검증 시작'} onStart={onStart} /> : null}

    {running ? <BusinessValidationProgress state={state} /> : null}

    {hasResults ? <>
      <ValidationSummary market={marketResult?.market} bm={bmResult?.bm} />
      <nav className="business-validation__local-nav" aria-label="사업 검증 결과 바로가기">
        <a href="#validation-summary">요약</a>
        {marketResult ? <a href="#validation-market">시장 분석</a> : null}
        {bmResult ? <a href="#validation-bm">사업 모델</a> : null}
        {showRefinement ? <a href="#validation-refinement">사업안 다듬기</a> : null}
        {finalStarted ? <a href="#validation-final">최종 결과</a> : null}
      </nav>
    </> : null}

    {state === 'MARKET_FAILED' ? <FailurePanel title="시장 분석을 완료하지 못했습니다"
      message="비즈니스 모델 분석은 시작하지 않았습니다. 입력을 확인한 뒤 시장 분석부터 다시 실행할 수 있습니다."
      action="사업 검증 다시 실행" busy={busy} onAction={onStart} /> : null}

    {marketResult ? <section id="validation-market" className="business-validation__result" aria-labelledby="market-result-title">
      <header><span>시장 분석 완료</span><h2 id="market-result-title">시장 분석 결과</h2></header>
      <MarketResultBody result={marketResult} activeId={marketFocus.active} onJump={marketFocus.jump} />
    </section> : null}

    {state === 'BM_FAILED' ? <FailurePanel title="비즈니스 모델 분석을 완료하지 못했습니다"
      message="완료된 시장 분석 결과는 그대로 보존되어 있습니다. 같은 시장 결과로 비즈니스 모델만 다시 실행합니다."
      action="BM 다시 시도" busy={busy} onAction={onRetryBm} /> : null}

    {bmResult ? <section id="validation-bm" className="business-validation__result" aria-labelledby="bm-result-title">
      <header><span>비즈니스 모델 분석 완료</span><h2 id="bm-result-title">비즈니스 모델 결과</h2></header>
      <BusinessModelResultBody result={bmResult} />
    </section> : null}

    {showRefinement ? <div id="validation-refinement">{finalStarted ? <span id="validation-final" className="business-validation__anchor" /> : null}<ConceptRefinementPanel refinement={effectiveRefinement} finalView={effectiveFinal}
      busy={refinementBusy} error={refinementError} onStart={onStartRefinement}
      onRetry={onRetryRefinement} onDecideAndApply={onDecideAndApply}
      onNext={onNextRefinement}
      onKeepCurrent={onKeepCurrent} onApply={onApplyRefinement}
      onRetryLegal={onRetryLegal} onRecoverLegalBlocked={onRecoverLegalBlocked}
      onFinalize={onFinalizeRefinement} /></div> : null}

    {deferValidationRerun ? <div className="business-validation__secondary-rerun">
      <p>새 기준으로 전체 시장·사업 모델 검증이 필요한 경우 다시 실행할 수 있습니다.</p>
      <Button variant="ghost" onClick={onStart} disabled={busy}>사업 검증 다시 실행</Button>
    </div> : null}
  </ProjectWorkspace>;
}

function BusinessValidationPreparation({ api, plan, disabled, actionLabel, onStart }) {
  const draft = draftFrom(plan);
  const preparedOperations = Object.values(draft).filter((value) => String(value ?? '').trim()).length;
  const checks = ['시장 규모·기초 관측', '성장 관련 관측', '경쟁·대체재', '가격·비용',
    '수요 근거', '사업 모델 적합성', '수익 구조 일관성'];
  return <div className="business-validation__preparation">
    <section className="business-validation__mission">
      <header><span>사업 검증 준비</span><h2>현재 검증 기준을 확인하세요</h2>
        <p>저장한 경쟁 정보와 운영 계획을 사용하며, 결과에 없는 수치나 판단은 새로 만들지 않습니다.</p></header>
      <ul className="business-validation__checks">{checks.map((item) => <li key={item}>{item}</li>)}</ul>
      <div className="business-validation__input-cards">
        <details><summary><span><b>경쟁·대체재 정보</b><small>시장 비교 대상을 찾는 출발점입니다.</small></span><em>확인·수정</em></summary><CompetitorSeedForm api={api} disabled={disabled} /></details>
        <details><summary><span><b>사업 운영 정보</b><small>{preparedOperations ? `저장된 운영·자원 항목 ${preparedOperations}개` : '아직 저장된 운영 정보가 없습니다.'}</small></span><em>확인·수정</em></summary><BmPlanReview draft={draft} /></details>
      </div>
    </section>
    <div className="business-validation__primary-action"><ol aria-label="사업 검증 진행 방식"><li>시장 근거 수집</li><li>시장 결과 정리</li><li>비즈니스 모델 검토</li><li>사업안 다듬기</li></ol>
      <Button onClick={onStart} disabled={disabled}>{disabled ? '시작하는 중…' : actionLabel}</Button>
    </div>
  </div>;
}

export function BusinessValidationProgress({ state }) {
  const activeIndex = state === 'BM_RUNNING' ? 4 : state === 'MARKET_COMPLETED' ? 3 : 1;
  const stages = ['검증 기준 확인', '시장 조사 설계', '시장 근거 수집', '시장 결과 정리',
    '비즈니스 모델 분석', '사업안 다듬기'];
  return <section className="business-validation__progress" aria-live="polite">
    <header><span>사업 검증 진행 중</span><h2>{state === 'BM_RUNNING' ? '시장 결과를 바탕으로 비즈니스 모델을 분석하고 있습니다' : '시장 근거를 수집하고 결과를 정리하고 있습니다'}</h2></header>
    <ol>{stages.map((stage, index) => <li key={stage} data-state={index < activeIndex ? 'complete' : index === activeIndex ? 'active' : 'waiting'}><span>{index < activeIndex ? '✓' : index + 1}</span><div><strong>{stage}</strong><small>{index < activeIndex ? '완료' : index === activeIndex ? '진행 중' : '대기'}</small></div></li>)}</ol>
    <p>상세 실행 기록은 작업센터에서 확인할 수 있습니다.</p>
  </section>;
}

function ValidationSummary({ market, bm }) {
  const growth = market?.growth;
  const price = market?.price;
  const cards = [
    ['시장 규모', market?.tam?.value != null || market?.sam?.value != null ? '계산 결과 있음' : '근거 부족 · 미측정'],
    ['관측 지표 변화', growth?.value != null ? `${growth.value}${growth.unit === 'PERCENT_PER_YEAR' ? '%/년' : ` ${growth.unit ?? ''}`}` : '미측정'],
    ['관련 가격·비용', price?.min != null && price?.max != null ? `${Number(price.min).toLocaleString('ko-KR')} ~ ${Number(price.max).toLocaleString('ko-KR')} ${price.currency ?? ''}` : '미측정'],
    ['BM 판정', bm?.decision ?? '분석 대기'],
  ];
  return <section id="validation-summary" className="business-validation__summary" aria-labelledby="validation-summary-title"><header><span>검증 결과 한눈에 보기</span><h2 id="validation-summary-title">확보한 근거와 판정을 먼저 확인하세요</h2></header><div>{cards.map(([label, value]) => <article key={label}><span>{label}</span><strong>{value}</strong></article>)}</div></section>;
}

function FailurePanel({ title, message, action, busy, onAction }) {
  return <section className="business-validation__failure"><h2>{title}</h2><p>{message}</p>
    <Button onClick={onAction} disabled={busy}>{busy ? '요청 중…' : action}</Button></section>;
}
