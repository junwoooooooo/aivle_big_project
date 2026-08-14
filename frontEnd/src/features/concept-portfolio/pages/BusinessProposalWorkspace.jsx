import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { Link, useOutletContext, useParams } from 'react-router-dom';

import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { useJobEvents } from '../../../shared/async-events/index.js';
import { AppIcon, ProjectExecutionExperience, ProjectStageHeader, ProjectWorkspace } from '../../../shared/ui/index.js';
import {
  BUSINESS_BASIS_TYPES, CANDIDATE_FACT_FIELDS, HYPOTHESIS_LABELS, MARKET_TARGET_TYPES,
  buildHypothesisChanges, buildProposalPreview, businessDecisionStage, canChangeSelection,
  canOpenComparison, candidateFieldOptions, candidateRequests, comparisonRows, createCandidateDraft,
  detailedComparisonGroups, groupLegalEvidence, hypothesisDecisionLabel, hypothesisDisplay,
  hypothesisValueText, portfolioRunPresentation, selectedConceptId, serializeCandidateFacts,
  toggleComparedConcept,
} from '../businessProposalModel.js';
import { businessProposalExecutionPresentation } from '../businessProposalExecution.js';
import { useConceptPortfolio } from '../hooks/useConceptPortfolio.js';
import '../styles/business-proposal.css';

export default function BusinessProposalWorkspace() {
  const { projectId } = useParams();
  const outlet = useOutletContext() ?? {};
  const portfolio = useConceptPortfolio(projectId, outlet.liveRevision);
  const progressJobId = portfolio.run?.activeTaskRunId ?? portfolio.run?.initialTaskRunId ?? null;
  const progressEvents = useJobEvents(progressJobId);
  const [clock, setClock] = useState(0);
  const [view, setView] = useState('BROWSE');
  const [compared, setCompared] = useState([]);
  const [showGallery, setShowGallery] = useState(false);
  const [drafts, setDrafts] = useState({});
  const [edits, setEdits] = useState({});
  const [recoveredNotice, setRecoveredNotice] = useState(false);
  const selectionBaseline = useRef({ selectionId: null, conceptIds: new Set() });
  const previousSelectionId = useRef(null);
  const basisRef = useRef(null);
  const selectedId = selectedConceptId(portfolio.selection);
  const selectedConcept = portfolio.concepts.find((concept) => concept.conceptId === selectedId);
  const comparedConcepts = portfolio.concepts.filter((concept) => compared.includes(concept.conceptId));
  const actionableInputs = candidateRequests(portfolio.inputRequests);
  const unmatchedInputs = actionableInputs.filter((request) => !portfolio.concepts.some((concept) => concept.candidateId === request.candidateId));
  const hypothesisMap = useMemo(() => Object.fromEntries(portfolio.hypotheses.map((item) => [item.hypothesisType, item])), [portfolio.hypotheses]);
  const readyToReview = portfolio.concepts.length > 0;
  const preGeneration = !portfolio.run && !readyToReview;
  const decisionStage = businessDecisionStage(portfolio.selection);
  const galleryVisible = !portfolio.selection || showGallery;

  useEffect(() => {
    if (!progressJobId) return undefined;
    const timer = setInterval(() => setClock(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [progressJobId]);

  useEffect(() => {
    const selectionId = portfolio.selection?.selectionId ?? null;
    const currentIds = new Set(portfolio.concepts.map((concept) => concept.conceptId));
    if (!selectionId) {
      selectionBaseline.current = { selectionId: null, conceptIds: currentIds };
      // 서버 선택 기준이 사라진 순간에만 이전 복구 알림을 초기화한다.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setRecoveredNotice(false);
      return;
    }
    if (selectionBaseline.current.selectionId !== selectionId) {
      selectionBaseline.current = { selectionId, conceptIds: currentIds };
      // 새 선택은 별개의 기준선이므로 이전 선택의 복구 알림을 이어받지 않는다.
      setRecoveredNotice(false);
      return;
    }
    if ([...currentIds].some((id) => !selectionBaseline.current.conceptIds.has(id))) {
      // 서버에서 뒤늦게 도착한 후보만 사용자에게 한 번 알린다.
      setRecoveredNotice(true);
    }
    selectionBaseline.current = { selectionId, conceptIds: currentIds };
  }, [portfolio.concepts, portfolio.selection?.selectionId]);

  useEffect(() => {
    const selectionId = portfolio.selection?.selectionId ?? null;
    if (selectionId && selectionId !== previousSelectionId.current && basisRef.current) {
      const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
      basisRef.current.scrollIntoView?.({ behavior: reduced ? 'auto' : 'smooth', block: 'start' });
      basisRef.current.focus?.({ preventScroll: true });
      setShowGallery(false);
      setView('BROWSE');
    }
    previousSelectionId.current = selectionId;
  }, [portfolio.selection?.selectionId]);

  const draft = (request) => drafts[request.inputRequestId] ?? createCandidateDraft(request);
  const updateDraft = (request, next) => setDrafts((current) => ({ ...current, [request.inputRequestId]: { ...draft(request), ...next } }));
  const submitInput = (request) => {
    const payload = serializeCandidateFacts(request, draft(request));
    if (payload) portfolio.respond(request.inputRequestId, payload, request.question ?? '');
  };

  if (portfolio.loading) return <main className="business-proposal" aria-busy="true"><p>검토된 사업안을 불러오고 있습니다.</p></main>;
  const header = readyToReview && view === 'COMPARE'
    ? { eyebrow: '사업안 선택', title: '두 사업안 비교', description: '핵심 차이를 먼저 확인하고, 필요한 세부 내용만 펼쳐보세요.' }
    : readyToReview
      ? { eyebrow: '사업안 선택', title: '생성된 사업안을 살펴보세요', description: '각 사업안이 어떤 고객과 방식에 초점을 두는지 살펴보고 실행할 방향을 선택하세요. 비슷한 두 사업안을 직접 비교할 수도 있습니다.' }
      : { eyebrow: '사업안 생성', title: '사업안 생성 및 검토', description: '확정한 아이디어를 바탕으로 서로 다른 방향의 사업안을 만들고, 법률·규제 검토를 거친 뒤 비교할 수 있는 결과를 준비합니다.' };

  return <ProjectWorkspace as="main" mode="decide" className="business-proposal">
    <ProjectStageHeader step={2} {...header} />
    {portfolio.error && <section className="business-proposal__error" role="alert"><span>{getUserErrorMessage(portfolio.error)}</span><button type="button" onClick={portfolio.refresh}>다시 시도</button></section>}
    {preGeneration && <PreGeneration onStart={portfolio.start} busy={portfolio.busy} />}
    {portfolio.run && !readyToReview && <PortfolioStatus run={portfolio.run} busy={portfolio.busy} onRestart={portfolio.start} events={progressEvents.events} now={clock} onDetail={() => outlet.openWorkCenterJob?.(progressJobId)} />}
    {recoveredNotice && <p className="business-proposal__notice" role="status">추가 사업안이 준비되었습니다. 현재 선택은 유지됩니다.</p>}

    {readyToReview && view === 'COMPARE' && <ComparisonFocus concepts={comparedConcepts} onBack={() => setView('BROWSE')} onSelect={portfolio.select} busy={portfolio.busy} />}
    {readyToReview && view === 'BROWSE' && <>
      {portfolio.selection && <DecisionProgress stage={decisionStage} />}
      {portfolio.selection && !showGallery && <SelectedSummary concept={selectedConcept}
        canChange={canChangeSelection(portfolio.selection)} onShow={() => setShowGallery(true)} />}
      {galleryVisible && <ProposalGallery concepts={portfolio.concepts} selectedId={selectedId} compared={compared}
        requests={portfolio.inputRequests} drafts={drafts} busy={portfolio.busy}
        onDraft={updateDraft} onRespond={submitInput} onRetry={portfolio.retryContinuation} onExplore={portfolio.start}
        onCompare={(conceptId) => setCompared((value) => toggleComparedConcept(value, conceptId))}
        onOpenComparison={() => setView('COMPARE')} onSelect={portfolio.select} />}
      {galleryVisible && unmatchedInputs.length > 0 && <InputGroup title="추가 검토 중인 사업안" description="아래 정보는 검토 완료된 다른 사업안의 선택을 막지 않습니다." requests={unmatchedInputs} drafts={drafts} onDraft={updateDraft} onSubmit={submitInput} onRetry={portfolio.retryContinuation} onExplore={portfolio.start} busy={portfolio.busy} />}

      {portfolio.selection && !showGallery && <section ref={basisRef} tabIndex="-1" className="business-decision__current">
        {decisionStage === 'BUSINESS_BASIS' && <BusinessBasis portfolio={portfolio} hypothesisMap={hypothesisMap} edits={edits} setEdits={setEdits} />}
        {decisionStage !== 'BUSINESS_BASIS' && <BasisSummary hypotheses={portfolio.hypotheses} />}
        {decisionStage === 'LEGAL_REVIEW' && <LegalWorkspace portfolio={portfolio} />}
        {decisionStage === 'MARKET_READY' && <LegalSummaryCompleted />}
        {decisionStage === 'MARKET_READY' && <MarketReady projectId={projectId} />}
      </section>}
    </>}
    {portfolio.concepts.length === 0 && actionableInputs.length > 0 && <InputGroup title="사업안을 완성하려면 실제 사업정보가 필요합니다." requests={actionableInputs} drafts={drafts} onDraft={updateDraft} onSubmit={submitInput} onRetry={portfolio.retryContinuation} onExplore={portfolio.start} busy={portfolio.busy} />}
  </ProjectWorkspace>;
}

export function PreGeneration({ onStart, busy }) {
  const phases = [['사업안 생성', '서로 다른 사업 방향으로 후보를 만듭니다.'], ['법률·규제 검토', '각 사업안에서 확인해야 할 법률·규제 요소를 검토합니다.'], ['비교 및 선택', '검토를 거친 사업안을 같은 기준으로 비교하고 선택합니다.']];
  return <section className="business-proposal__pre-generation"><div className="business-proposal__process" aria-label="사업안 생성 및 검토 과정">{phases.map(([title, description], index) => <article key={title}><span>{index + 1}</span><div><h2>{title}</h2><p>{description}</p></div></article>)}</div><button type="button" disabled={busy} onClick={onStart}>사업안 생성 및 법률 검토 시작</button></section>;
}

export function PortfolioStatus({ run, busy, onRestart, onDetail, events = [], now = 0 }) {
  const presentation = portfolioRunPresentation(run);
  const running = run.productStatus === 'RUNNING';
  const started = Date.parse(events[0]?.occurredAt ?? run.updatedAt ?? '');
  const elapsed = Number.isFinite(started) ? Math.max(0, Math.floor((now - started) / 1000)) : 0;
  const latest = Date.parse(events.at(-1)?.occurredAt ?? '');
  const summary = [...events].reverse().find((event) => event.stage === 'SUMMARY')?.messageParams ?? {};
  const reviewed = summary.reviewed ?? run.runSummary?.candidateGenerated;
  const failed = run.productStatus === 'FAILED';
  const needsInput = run.productStatus === 'NEEDS_INPUT';
  const outcome = needsInput && reviewed != null ? `${reviewed}개의 사업안 후보를 검토했습니다. 현재 바로 선택 가능한 사업안은 없으며, ${run.openInputCount ?? summary.needsInput ?? 0}개의 사업안은 실제 운영정보 확인 후 검토를 계속할 수 있습니다.` : failed && reviewed != null ? `${reviewed}개의 사업안 후보를 검토했지만 최종 결과를 확정하지 못했습니다.` : `${run.producedConceptCount ?? 0}개 사업안 · 추가 검토 ${run.openInputCount ?? 0}건`;
  return <ProjectExecutionExperience title={running ? '사업안을 생성하고 검토하고 있습니다' : presentation.title} {...businessProposalExecutionPresentation(run, events)} elapsedSeconds={running ? elapsed : undefined} latestUpdate={running && Number.isFinite(latest) ? `마지막 업데이트 ${Math.max(0, Math.floor((now - latest) / 1000))}초 전` : undefined} metric={running && reviewed != null ? `${reviewed}개의 사업안 후보를 검토했습니다.` : outcome} failureMessage="사업안 생성을 완료하지 못했습니다." needsInputMessage="사업안을 계속 검토하려면 추가 정보가 필요합니다." onDetail={(failed || running || needsInput) ? onDetail : undefined}>{presentation.restart && <button type="button" disabled={busy} onClick={onRestart}>{presentation.action}</button>}</ProjectExecutionExperience>;
}

function ProposalGallery({ concepts, selectedId, compared, requests, drafts, busy, onDraft, onRespond, onRetry, onExplore, onCompare, onOpenComparison, onSelect }) {
  const comparedConcepts = concepts.filter((concept) => compared.includes(concept.conceptId));
  return <section className="proposal-gallery" aria-labelledby="proposal-gallery-title">
    <header className="comparison-picker"><div><span>비교할 사업안</span><strong>{compared.length} / 2</strong><p>{compared.length === 0 ? '비슷한 사업안 두 개를 선택해 직접 비교할 수 있습니다.' : compared.length === 1 ? `${comparedConcepts[0]?.conceptName} · 한 개 더 선택하세요.` : `${comparedConcepts[0]?.conceptName} ↔ ${comparedConcepts[1]?.conceptName}`}</p></div>{canOpenComparison(compared) && <button type="button" onClick={onOpenComparison}>두 사업안 비교</button>}</header>
    <h2 id="proposal-gallery-title" className="sr-only">생성된 사업안</h2>
    <div className="proposal-grid">{concepts.map((concept) => <ProposalCard key={concept.conceptId} concept={concept} allConcepts={concepts} selected={concept.conceptId === selectedId} compared={compared.includes(concept.conceptId)} compareDisabled={!compared.includes(concept.conceptId) && compared.length >= 2} requests={candidateRequests(requests, concept.candidateId)} drafts={drafts} onDraft={onDraft} onRespond={onRespond} onRetry={onRetry} onExplore={onExplore} onCompare={() => onCompare(concept.conceptId)} onSelect={() => onSelect(concept.conceptId)} busy={busy} />)}</div>
  </section>;
}

function ProposalCard({ concept, allConcepts, selected, compared, compareDisabled, requests, drafts, onDraft, onRespond, onRetry, onExplore, onCompare, onSelect, busy }) {
  const preview = buildProposalPreview(concept, allConcepts);
  return <article className="proposal-card" data-selected={selected}><header><div><h3>{concept.conceptName}</h3><span>{selected ? '현재 선택' : '선택 가능'}</span></div><label><input type="checkbox" checked={compared} disabled={compareDisabled} onChange={onCompare} /> 비교에 추가</label></header><p className="proposal-card__definition">{preview.definition}</p><dl>{preview.highlights.map((item) => <div key={item.key}><dt>{item.label}</dt><dd>{item.value}</dd></div>)}</dl><p className="proposal-card__legal"><AppIcon name="check" size={15} /> 법률·규제 사전 검토 완료</p>{requests.map((request) => <CandidateInput key={request.inputRequestId} request={request} draft={drafts[request.inputRequestId] ?? createCandidateDraft(request)} onDraft={(next) => onDraft(request, next)} onSubmit={() => onRespond(request)} onRetry={() => onRetry(request.inputRequestId)} onExplore={onExplore} busy={busy} />)}<button type="button" className="proposal-card__select" disabled={busy || selected} onClick={onSelect}>{selected ? '선택됨' : '이 사업안 선택'}<AppIcon name="arrowRight" size={16} /></button></article>;
}

function ComparisonFocus({ concepts, onBack, onSelect, busy }) {
  if (!canOpenComparison(concepts.map((item) => item.conceptId))) return <section className="proposal-comparison"><button type="button" className="proposal-comparison__back" aria-label="사업안으로 돌아가기" onClick={onBack}><AppIcon name="chevronLeft" /> 사업안으로 돌아가기</button><p>비교할 사업안 두 개를 먼저 선택해 주세요.</p></section>;
  const rows = comparisonRows(concepts);
  const groups = detailedComparisonGroups(concepts);
  return <section className="proposal-comparison"><button type="button" className="proposal-comparison__back" aria-label="사업안으로 돌아가기" onClick={onBack}><AppIcon name="chevronLeft" /> 사업안으로 돌아가기</button><ComparisonMatrix concepts={concepts} rows={rows} onSelect={onSelect} busy={busy} /> <div className="proposal-comparison__details"><h3>세부 내용 보기</h3>{groups.map((group) => <Disclosure key={group.title} title={group.title}><ComparisonMatrix concepts={concepts} rows={group.rows} compact /></Disclosure>)}</div></section>;
}

function ComparisonMatrix({ concepts, rows, onSelect, busy, compact = false }) {
  return <div className={`proposal-comparison__matrix${compact ? ' is-compact' : ''}`}><div className="proposal-comparison__corner">비교 항목</div>{concepts.map((concept) => <header key={concept.conceptId}><strong>{concept.conceptName}</strong>{!compact && <button type="button" disabled={busy} onClick={() => onSelect(concept.conceptId)}>이 사업안 선택</button>}</header>)}{rows.flatMap((row) => [<strong className="proposal-comparison__label" key={`${row.label}-label`}>{row.label}</strong>, ...row.values.map((value, index) => <p key={`${row.label}-${concepts[index].conceptId}`}><span>{concepts[index].conceptName}</span>{value}</p>)])}</div>;
}

function DecisionProgress({ stage }) {
  const stages = [['PROPOSAL_SELECTION', '사업안 선택'], ['BUSINESS_BASIS', '사업 기준 확인'], ['LEGAL_REVIEW', '법률·규제 확인'], ['MARKET_READY', '시장 분석 준비']];
  const current = stages.findIndex(([key]) => key === stage);
  return <ol className="decision-progress" aria-label="사업안 결정 과정">{stages.map(([key, label], index) => <li key={key} data-state={index < current ? 'completed' : index === current ? 'current' : 'upcoming'} aria-current={index === current ? 'step' : undefined}><span>{index < current ? <AppIcon name="check" size={14} /> : index + 1}</span><strong>{label}</strong></li>)}</ol>;
}

function SelectedSummary({ concept, canChange, onShow }) {
  const preview = buildProposalPreview(concept, [concept]);
  return <section className="selected-proposal-summary"><div><span><AppIcon name="check" size={16} /> 사업안 선택 완료</span><h2>{concept?.conceptName ?? '선택한 사업안'}</h2><p>{preview.definition}</p></div><div><Disclosure title="선택한 사업안 보기"><dl>{preview.highlights.map((item) => <div key={item.key}><dt>{item.label}</dt><dd>{item.value}</dd></div>)}</dl></Disclosure>{canChange && <button type="button" onClick={onShow}>선택 변경</button>}</div></section>;
}

function BusinessBasis({ portfolio, hypothesisMap, edits, setEdits }) {
  const disabled = portfolio.busy || Boolean(portfolio.selection.activeTaskRunId);
  const field = (type) => <HypothesisField key={type} type={type} value={hypothesisMap[type]} edit={edits[type]} onEdit={(next) => setEdits((current) => ({ ...current, [type]: next }))} onAlternative={() => portfolio.alternative(type)} disabled={disabled} />;
  return <section className="business-basis"><header><div><p>사업 기준 확인</p><h2>시장 분석에 사용할 기준값</h2><span>선택한 사업안을 바탕으로 다음 시장 분석에 사용할 기준을 정리했습니다. 실제 계획과 다른 값은 수정해 주세요.</span></div><strong>{portfolio.selection.activeTaskRunId ? '처리 중' : `${portfolio.selection.hypothesisConfirmedCount}/7 확인 완료`}</strong></header><section className="business-basis__core"><h3>사업 기본 조건</h3>{BUSINESS_BASIS_TYPES.map(field)}</section><section className="business-basis__targets"><h3>시장 목표</h3>{MARKET_TARGET_TYPES.map(field)}</section>{portfolio.selection.status === 'DELTA_LEGAL_PENDING' && <p role="status">변경한 기준값의 법률·규제 영향을 확인하고 있습니다.</p>}<div className="business-basis__actions"><button type="button" disabled={disabled} onClick={() => portfolio.confirm(buildHypothesisChanges(portfolio.hypotheses, edits))}>기준값 확인 완료</button>{portfolio.selection.nextAction === 'REVISE_OR_RETRY' && <button type="button" disabled={portfolio.busy} onClick={portfolio.retryDelta}>법률·규제 재검토 다시 시도</button>}</div></section>;
}

export function HypothesisField({ type, value, edit, onEdit, onAlternative, disabled }) {
  const locked = value?.locked;
  const source = value?.finalValue ?? value?.proposedValue;
  const current = edit ?? source;
  const updateObject = (field, next) => onEdit({ ...(typeof current === 'object' && current ? current : {}), [field]: next });
  let editor;
  if (type === 'PRE_MARKET_SOM_SHARE') editor = <div className="hypothesis-structured hypothesis-structured--share"><label><span>목표 점유율</span><span className="input-with-suffix"><input aria-label="목표 점유율" type="number" min="0.01" step="0.01" disabled={locked || disabled} value={current?.targetSharePercent ?? ''} onChange={(event) => updateObject('targetSharePercent', Number(event.target.value))} /><i>%</i></span></label><label><span>목표 기간</span><span className="input-with-suffix"><input aria-label="목표 기간" type="number" min="1" step="1" disabled={locked || disabled} value={current?.horizonYears ?? ''} onChange={(event) => updateObject('horizonYears', Number(event.target.value))} /><i>년</i></span></label><label className="is-wide"><span>가정 근거</span><textarea disabled={locked || disabled} value={(current?.assumptions ?? []).join('\n')} onChange={(event) => updateObject('assumptions', event.target.value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean))} /></label></div>;
  else if (type === 'PRE_MARKET_SOM') editor = <div className="hypothesis-structured hypothesis-structured--som"><label><span>목표 규모</span><input aria-label="초기 목표 시장 규모" type="number" min="0" disabled={locked || disabled} value={current?.amount ?? ''} onChange={(event) => updateObject('amount', Number(event.target.value))} /></label><label><span>통화</span><input aria-label="통화" disabled={locked || disabled} value={current?.currency ?? ''} onChange={(event) => updateObject('currency', event.target.value)} /></label><label><span>기간</span><input aria-label="시장 규모 기간" disabled={locked || disabled} value={current?.period ?? ''} onChange={(event) => updateObject('period', event.target.value)} /></label><label className="is-wide"><span>계산 기준</span><textarea disabled={locked || disabled} value={current?.calculationBasis ?? ''} onChange={(event) => updateObject('calculationBasis', event.target.value)} /></label><label className="is-wide"><span>근거</span><textarea disabled={locked || disabled} value={(current?.assumptions ?? []).join('\n')} onChange={(event) => updateObject('assumptions', event.target.value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean))} /></label></div>;
  else editor = <textarea aria-label={HYPOTHESIS_LABELS[type]} disabled={locked || disabled} value={edit ?? hypothesisValueText(source)} onChange={(event) => onEdit(event.target.value)} />;
  return <article className={`hypothesis-field hypothesis-field--${type.toLowerCase()}`}><header><strong>{HYPOTHESIS_LABELS[type]}</strong><span>{hypothesisDecisionLabel(value)}</span></header>{MARKET_TARGET_TYPES.includes(type) && <p className="hypothesis-value-preview">{hypothesisDisplay(type, current)}</p>}{editor}{!locked && <button type="button" disabled={disabled} onClick={onAlternative}>다른 값 추천받기</button>}</article>;
}

function BasisSummary({ hypotheses }) {
  const values = Object.fromEntries(hypotheses.map((item) => [item.hypothesisType, item.finalValue ?? item.proposedValue]));
  return <section className="basis-summary"><div><span><AppIcon name="check" size={16} /> 사업 기준값 확인 완료</span><h2>시장 분석 기준값</h2></div><dl><div><dt>사업 대상 지역</dt><dd>{hypothesisDisplay('TARGET_REGION', values.TARGET_REGION) || '확인 완료'}</dd></div><div><dt>수익 방식</dt><dd>{hypothesisDisplay('REVENUE_MODEL', values.REVENUE_MODEL) || '확인 완료'}</dd></div><div><dt>시장 목표</dt><dd>{hypothesisDisplay('PRE_MARKET_SOM_SHARE', values.PRE_MARKET_SOM_SHARE) || '확인 완료'}</dd></div></dl></section>;
}

function LegalWorkspace({ portfolio }) {
  return <section className="legal-workspace"><header><p>법률·규제 확인</p><h2>법률·규제 검토 결과를 확인하세요</h2><span>선택한 사업안과 확정한 기준값을 바탕으로 사업을 진행할 때 확인해야 할 법률·규제 사항을 정리했습니다.</span></header>{portfolio.report ? <LegalReport report={portfolio.report} /> : <p role="status">법률·규제 검토 결과를 준비하고 있습니다.</p>}<div className="legal-workspace__actions">{portfolio.selection.nextAction === 'REVISE_OR_RETRY' && <button type="button" disabled={portfolio.busy} onClick={portfolio.retryDelta}>법률·규제 재검토 다시 시도</button>}{portfolio.selection.nextAction === 'REVIEW_LEGAL_REPORT' && <button type="button" disabled={portfolio.busy} onClick={portfolio.finalizeReport}>법률·규제 결과 확인 완료</button>}{portfolio.selection.nextAction === 'FINALIZE_MARKET_SEED' && <button type="button" disabled={portfolio.busy} onClick={portfolio.finalizeMarketSeed}>시장 분석 준비 완료하기</button>}</div></section>;
}

function LegalSummaryCompleted() { return <section className="legal-summary-completed"><AppIcon name="check" size={16} /><div><strong>법률·규제 결과 확인 완료</strong><span>관련 법률·규제와 주의사항을 확인했습니다.</span></div></section>; }
function MarketReady({ projectId }) { return <section className="business-proposal__ready"><div><strong>시장 분석을 시작할 준비가 되었습니다.</strong><span>선택한 사업안, 확인한 기준값, 법률·규제 결과를 시장 분석 입력으로 저장했습니다.</span></div><Link to={projectRoutes.market(projectId)}>시장 분석 시작하기 <AppIcon name="arrowRight" size={16} /></Link></section>; }

export function CandidateInput({ request, draft, onDraft, onSubmit, onRetry, onExplore, busy }) {
  if (request.status === 'ANSWERED' && request.nextAction === 'RETRY_CONTINUATION') return <section className="candidate-input"><strong>제출한 정보의 반영을 완료하지 못했습니다.</strong><p>같은 정보를 다시 입력하지 않고 반영 작업만 다시 시도합니다.</p><button type="button" disabled={busy} onClick={onRetry}>추가 사업정보 반영 다시 시도</button></section>;
  const options = candidateFieldOptions(request);
  const payload = serializeCandidateFacts(request, draft);
  const unresolved = request.nextAction === 'INPUT_TARGET_UNRESOLVED' || options.length === 0;
  return <section className="candidate-input"><header><div><small>추가 검토 중인 사업안</small><strong>{request.candidateDisplayName ?? '사업안 세부 검토'}</strong></div>{request.candidateOneLineSummary && <p>{request.candidateOneLineSummary}</p>}</header><div><strong>확인이 필요한 내용</strong><p>{request.question ?? request.safeSummary ?? '실제 사업정보를 확인해 주세요.'}</p></div>{request.reason && <div><strong>왜 필요한가</strong><p>{request.reason}</p></div>}{unresolved ? <div role="alert"><strong>필요한 사업정보 항목을 자동으로 특정하지 못했습니다.</strong><p>{(request.unknownFacts ?? []).join(' ') || '질문 내용을 다시 확인해야 합니다.'}</p><button type="button" disabled={busy} onClick={onExplore}>다른 방향 다시 탐색</button></div> : <><div className="candidate-input__fields">{options.map((field) => { const contract = CANDIDATE_FACT_FIELDS[field]; return <label key={field}><strong>{contract.label}</strong><span>{contract.type === 'list' ? '한 줄에 한 항목씩 입력해 주세요. 해당 사항이 없으면 ‘해당 없음’을 입력할 수 있습니다.' : '실제 사업 사실을 입력해 주세요.'}</span><textarea aria-label={contract.label} value={draft.values?.[field] ?? ''} onChange={(event) => onDraft({ values: { ...(draft.values ?? {}), [field]: event.target.value } })} /></label>; })}</div><div className="candidate-input__actions"><button type="button" disabled={busy || !payload} onClick={onSubmit}>정보 제출</button><button type="button" disabled={busy} onClick={onExplore}>다른 방향 다시 탐색</button></div></>}</section>;
}

function InputGroup({ title, description, requests, drafts, onDraft, onSubmit, onRetry, onExplore, busy }) { return <section className="business-proposal__input-first"><h2>{title}</h2>{description && <p>{description}</p>}{requests.map((request) => <CandidateInput key={request.inputRequestId} request={request} draft={drafts[request.inputRequestId] ?? createCandidateDraft(request)} onDraft={(next) => onDraft(request, next)} onSubmit={() => onSubmit(request)} onRetry={() => onRetry(request.inputRequestId)} onExplore={onExplore} busy={busy} />)}</section>; }

const asList = (value) => Array.isArray(value) ? value : value == null || value === '' ? [] : [value];
function BulletSection({ title, values, empty = '해당 사항이 없습니다.' }) { const items = asList(values); return <article><h3>{title}</h3>{items.length === 0 ? <p>{empty}</p> : <ul>{items.map((item, index) => <li key={`${title}-${index}`}>{typeof item === 'string' ? item : item.safeSummary ?? item.title ?? String(item)}</li>)}</ul>}</article>; }
function FlowSection({ title, values }) { const items = asList(values); return <article><h3>{title}</h3>{items.length === 0 ? <p>확인된 흐름이 없습니다.</p> : <ol className="legal-flow">{items.map((item, index) => <li key={`${title}-${index}`}><span>{index + 1}</span>{typeof item === 'string' ? item : item.safeSummary ?? item.title ?? String(item)}</li>)}</ol>}</article>; }

function Disclosure({ title, children, className = '' }) {
  const [open, setOpen] = useState(false);
  const reactId = useId();
  const id = `disclosure-${String(title).replace(/[^a-zA-Z0-9가-힣]+/g, '-')}-${reactId.replace(/[^a-zA-Z0-9]/g, '')}`;
  return <section className={`disclosure ${className}`}><button type="button" aria-expanded={open} aria-controls={id} onClick={() => setOpen((value) => !value)}><span>{title}</span><AppIcon name={open ? 'chevronUp' : 'chevronDown'} size={17} /></button><div id={id} hidden={!open}>{children}</div></section>;
}

function EvidenceSection({ values }) {
  const groups = groupLegalEvidence(values);
  return <article className="legal-evidence"><h3>관련 법률·규제</h3>{groups.length === 0 ? <p>표시할 공식 근거가 없습니다.</p> : <div>{groups.map((group) => <section className="legal-evidence__law" key={group.lawName}><header><strong>{group.lawName}</strong><div>{group.articles.map((article, index) => article.articleReference && <span key={`${article.articleReference}-${index}`}>{article.articleReference}</span>)}</div></header>{group.articles.map((article, index) => <Disclosure key={article.contentHash ?? `${article.articleReference}-${article.officialSourceUri}-${index}`} title={`${article.articleReference ?? '관련 근거'}${article.title ? ` ${article.title}` : ''}`}><div className="legal-evidence__article">{article.boundedProvisionSummary && <p>{article.boundedProvisionSummary}</p>}{article.effectiveDate && <small>시행일 {article.effectiveDate}</small>}{article.contentHash && <small>근거 해시 {article.contentHash}</small>}{article.officialSourceUri && <a href={article.officialSourceUri} target="_blank" rel="noreferrer">법령 원문 보기</a>}</div></Disclosure>)}</section>)}</div>}</article>;
}

export function LegalReport({ report }) {
  const body = report.report ?? {};
  const rawConclusion = body.finalLegalConclusion;
  const conclusion = rawConclusion && typeof rawConclusion === 'object' ? rawConclusion : { safeSummary: rawConclusion };
  const sourcePartial = conclusion.legalSourceStatus === 'SOURCE_PARTIAL' || conclusion.sourceStatus === 'SOURCE_PARTIAL' || conclusion.evidenceDiagnostics?.coverageStatus === 'SOURCE_PARTIAL';
  const roles = body.businessRoles ?? {};
  const roleRows = [['플랫폼 역할', roles.platformRole], ['판매 주체', roles.sellerRole], ['서비스 제공 주체', roles.providerRole], ['중개 주체', roles.intermediaryRole]].filter(([, value]) => value);
  const advertising = body.advertisingExpressionCautions ?? {};
  const delta = asList(body.deltaLegalHistory);
  return <section className="final-legal-report"><article className="legal-conclusion"><h3>한눈에 보는 검토 결과</h3><strong>{conclusion.productionStatus ?? conclusion.route ?? '검토 완료'}</strong><p>{conclusion.safeSummary ?? '법률·규제 검토 결과가 준비되었습니다.'}</p>{sourcePartial && <div role="alert">공식 근거를 확인했지만 일부 법률 소스의 조회 범위에는 제한이 있습니다.</div>}<small>검토 기준일 {report.basisDate}</small></article><section className="legal-report__attention"><h3>특히 확인할 사항</h3><BulletSection title="반드시 해야 할 조치" values={body.requiredControls} /><BulletSection title="필수 고지" values={body.requiredDisclosures} /><BulletSection title="파트너·자격·인허가" values={[...asList(body.partnerRequirements), ...asList(body.qualificationRequirements), ...asList(body.requiredPartnersAndQualifications)]} /><BulletSection title="아직 확인되지 않은 사항" values={body.unknownFacts} /></section><article className="legal-report__roles"><h3>사업 구조에서의 역할</h3>{roleRows.length === 0 ? <p>표시할 역할 정보가 없습니다.</p> : <dl>{roleRows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>}</article><EvidenceSection values={body.officialEvidenceReferences} /><section className="legal-report__advertising"><h3>광고·표현 주의사항</h3><BulletSection title="허용 가능한 표현" values={advertising.allowedClaims} /><BulletSection title="반드시 함께 표시할 내용" values={advertising.requiredDisclosures} /><BulletSection title="피해야 할 표현" values={body.prohibitedVariants} /></section><Disclosure title="상세 근거"><div className="legal-report__details"><BulletSection title="개인정보 이용" values={body.personalDataUsage} /><BulletSection title="물리 활동" values={body.physicalActivities} /><FlowSection title="거래 흐름" values={body.transactionFlow} /><FlowSection title="결제·수취 흐름" values={body.paymentFlow} /><article><h3>변경사항 재검토 이력</h3>{delta.length === 0 ? <p>이번 확정 과정에서 재검토가 필요한 변경은 없었습니다.</p> : <ol>{delta.map((item, index) => <li key={item.reviewToken ?? index}>{item.legalReview?.safeSummary ?? item.safeSummary ?? item.status ?? `재검토 ${index + 1}`}</li>)}</ol>}</article></div></Disclosure>{body.sourceHashes && <Disclosure title="기술 정보" className="legal-technical"><dl>{Object.entries(body.sourceHashes).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl></Disclosure>}</section>;
}
