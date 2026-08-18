import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { Link, useOutletContext, useParams, useSearchParams } from 'react-router-dom';

import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { useJobEvents } from '../../../shared/async-events/index.js';
import { AppIcon, ProjectExecutionExperience, ProjectStageHeader, ProjectWorkspace, scrollPageToTop } from '../../../shared/ui/index.js';
import {
  BUSINESS_BASIS_TYPES, CANDIDATE_FACT_FIELDS, HYPOTHESIS_LABELS, HYPOTHESIS_TYPES, MARKET_TARGET_TYPES,
  buildHypothesisChanges, buildProposalPreview, businessDecisionReachability, businessDecisionStage, canChangeSelection,
  canOpenComparison, candidateFieldOptions, candidateRequests, comparisonRows, createCandidateDraft,
  detailedComparisonGroups, groupLegalEvidence, hypothesisDecisionLabel, hypothesisDisplay,
  hypothesisHasValue, hypothesisInputCount,
  hypothesisPresentation,
  formatKoreanCurrencyAmount, hypothesisValueText, legalStatusLabel, portfolioRunPresentation, selectedConceptId, serializeCandidateFacts,
  toggleComparedConcept,
} from '../businessProposalModel.js';
import { businessProposalExecutionPresentation, businessProposalSummaryMetric } from '../businessProposalExecution.js';
import { advertisingOnlyDisclosures, excludeLegalItems, legalAttentionGroups, uniqueLegalItems } from '../legalReportPresentation.js';
import BusinessValidationPreparation from '../components/BusinessValidationPreparation.jsx';
import { useConceptPortfolio } from '../hooks/useConceptPortfolio.js';
import '../styles/business-proposal.css';

export default function BusinessProposalWorkspace() {
  const { projectId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const outlet = useOutletContext() ?? {};
  const portfolio = useConceptPortfolio(projectId, outlet.liveRevision);
  const progressJobId = portfolio.run?.activeTaskRunId ?? portfolio.run?.initialTaskRunId ?? null;
  const progressEvents = useJobEvents(progressJobId);
  const [clock, setClock] = useState(0);
  const [view, setView] = useState('BROWSE');
  const [decisionView, setDecisionView] = useState(null);
  const [compared, setCompared] = useState([]);
  const [showGallery, setShowGallery] = useState(false);
  const [selectionChangeMode, setSelectionChangeMode] = useState(false);
  const [selectingConceptId, setSelectingConceptId] = useState(null);
  const [drafts, setDrafts] = useState({});
  const [edits, setEdits] = useState({});
  const [recoveredNotice, setRecoveredNotice] = useState(false);
  const selectionBaseline = useRef({ selectionId: null, conceptIds: new Set() });
  const previousSelectionKey = useRef(null);
  const previousDecisionStage = useRef(null);
  const pendingDecisionFocusKey = useRef(null);
  const basisRef = useRef(null);
  const selectedId = selectedConceptId(portfolio.selection);
  const selectionKey = portfolio.selection
    ? `${portfolio.selection.selectionId}:${portfolio.selection.conceptId}`
    : null;
  const selectedConcept = portfolio.concepts.find((concept) => concept.conceptId === selectedId);
  const comparedConcepts = portfolio.concepts.filter((concept) => compared.includes(concept.conceptId));
  const candidateInputs = candidateRequests(portfolio.inputRequests);
  const supportedInputs = candidateInputs.filter((request) => candidateFieldOptions(request).length > 0);
  const unsupportedInputs = candidateInputs.filter((request) => candidateFieldOptions(request).length === 0);
  const unmatchedInputs = supportedInputs.filter((request) => !portfolio.concepts.some((concept) => concept.candidateId === request.candidateId));
  const hypothesisMap = useMemo(() => Object.fromEntries(portfolio.hypotheses.map((item) => [item.hypothesisType, item])), [portfolio.hypotheses]);
  const readyToReview = portfolio.concepts.length > 0;
  const preGeneration = !portfolio.run && !readyToReview;
  const decisionStage = businessDecisionStage(portfolio.selection);
  const validationPrepRequested = searchParams.get('view') === 'validation-prep';
  const decisionReachability = businessDecisionReachability({ concepts: portfolio.concepts, selection: portfolio.selection, report: portfolio.report, validationPrepReached: validationPrepRequested });
  const activeDecisionView = validationPrepRequested ? 'VALIDATION_PREP' : decisionView ?? decisionStage;
  const validationPrep = activeDecisionView === 'VALIDATION_PREP';
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
    if (selectionKey && selectionKey !== previousSelectionKey.current) {
      pendingDecisionFocusKey.current = selectionKey;
      // 서버 refresh로 확인된 선택 identity가 바뀔 때만 탐색 화면을 접는다.
      setShowGallery(false);
      setSelectionChangeMode(false);
      setView('BROWSE');
      setDecisionView(null);
    }
    previousSelectionKey.current = selectionKey;
  }, [selectionKey]);

  useEffect(() => {
    if (!selectionKey || showGallery || view !== 'BROWSE'
      || pendingDecisionFocusKey.current !== selectionKey || !basisRef.current) return;
    scrollPageToTop();
    basisRef.current.focus?.({ preventScroll: true });
    pendingDecisionFocusKey.current = null;
  }, [selectionKey, showGallery, view]);

  useEffect(() => {
    const order = ['PROPOSAL_SELECTION', 'BUSINESS_BASIS', 'LEGAL_REVIEW', 'VALIDATION_PREP'];
    const previous = previousDecisionStage.current;
    if (previous !== decisionStage) {
      // 서버 단계가 바뀔 때만 로컬 탐색 위치를 새 authority에 맞춘다.
      setDecisionView(decisionStage);
    }
    if (previous && order.indexOf(decisionStage) > order.indexOf(previous)) scrollPageToTop();
    previousDecisionStage.current = decisionStage;
  }, [decisionStage]);

  const draft = (request) => drafts[request.inputRequestId] ?? createCandidateDraft(request);
  const updateDraft = (request, next) => setDrafts((current) => ({ ...current, [request.inputRequestId]: { ...draft(request), ...next } }));
  const submitInput = (request) => {
    const payload = serializeCandidateFacts(request, draft(request));
    if (payload) portfolio.respond(request.inputRequestId, payload, request.question ?? '');
  };
  const selectConcept = async (conceptId) => {
    setSelectingConceptId(conceptId);
    try { await portfolio.select(conceptId); }
    finally { setSelectingConceptId(null); }
  };
  const continueCurrentSelection = () => {
    pendingDecisionFocusKey.current = selectionKey;
    setShowGallery(false);
    setSelectionChangeMode(false);
    setView('BROWSE');
  };
  const navigateDecisionStep = (nextView) => {
    if (!decisionReachability[nextView]) return;
    setView('BROWSE');
    setDecisionView(nextView);
    setShowGallery(nextView === 'PROPOSAL_SELECTION');
    setSelectionChangeMode(false);
    setSearchParams(nextView === 'VALIDATION_PREP' ? { view: 'validation-prep' } : {});
    scrollPageToTop();
  };
  const openSelectionChange = () => { setShowGallery(true); setSelectionChangeMode(true); setView('BROWSE'); scrollPageToTop(); };
  const openValidationPrep = () => { setView('BROWSE'); setDecisionView('VALIDATION_PREP'); setShowGallery(false); setSelectionChangeMode(false); setSearchParams({ view: 'validation-prep' }); scrollPageToTop(); };
  const closeValidationPrep = () => navigateDecisionStep('LEGAL_REVIEW');

  if (portfolio.loading) return <main className="business-proposal" aria-busy="true"><p>검토된 사업안을 불러오고 있습니다.</p></main>;
  const header = readyToReview && view === 'COMPARE'
    ? { eyebrow: '사업안 선택', title: '두 사업안 비교', description: '핵심 차이를 먼저 확인하고, 필요한 세부 내용만 펼쳐보세요.' }
    : readyToReview
      ? { eyebrow: '사업안 선택', title: '생성된 사업안을 살펴보세요', description: '각 사업안이 어떤 고객과 방식에 초점을 두는지 살펴보고 실행할 방향을 선택하세요. 비슷한 두 사업안을 직접 비교할 수도 있습니다.' }
      : { eyebrow: '사업안 생성', title: '사업안 생성 및 검토', description: '확정한 아이디어를 바탕으로 서로 다른 방향의 사업안을 만들고, 법률·규제 검토를 거친 뒤 비교할 수 있는 결과를 준비합니다.' };

  return <ProjectWorkspace as="main" mode="decide" className="business-proposal">
    <ProjectStageHeader step={1} {...header} />
    {portfolio.error && <section className="business-proposal__error" role="alert"><span>{getUserErrorMessage(portfolio.error)}</span><button type="button" onClick={portfolio.refresh}>다시 시도</button></section>}
    {preGeneration && <PreGeneration onStart={portfolio.start} busy={portfolio.busy} />}
    {portfolio.run && !readyToReview && <PortfolioStatus run={portfolio.run} busy={portfolio.busy} onRestart={portfolio.start} events={progressEvents.events} now={clock} onDetail={progressJobId && outlet.openWorkCenterJob ? () => outlet.openWorkCenterJob(progressJobId) : undefined} />}
    {recoveredNotice && <p className="business-proposal__notice" role="status">추가 사업안이 준비되었습니다. 현재 선택은 유지됩니다.</p>}

    {readyToReview && view === 'COMPARE' && <ComparisonFocus concepts={comparedConcepts} selectedId={selectedId} onBack={() => setView('BROWSE')} onSelect={selectConcept} onContinueCurrent={continueCurrentSelection} busy={portfolio.busy} selectingConceptId={selectingConceptId} selectionReadOnly={Boolean(portfolio.selection) && !selectionChangeMode} />}
    {readyToReview && view === 'BROWSE' && <>
      <DecisionProgress stage={activeDecisionView} reachability={decisionReachability} onNavigate={navigateDecisionStep} />
      {portfolio.selection && validationPrep
        ? <BusinessValidationPreparation projectId={projectId} portfolio={portfolio} onBack={closeValidationPrep} />
        : portfolio.selection && !showGallery && <><FlowRationale /><div className="business-decision-stack"><SelectedSummary concept={selectedConcept}
          canChange={activeDecisionView === 'BUSINESS_BASIS' && canChangeSelection(portfolio.selection)} onShow={openSelectionChange} /><section ref={basisRef} tabIndex="-1" className="business-decision__current">
          {activeDecisionView === 'BUSINESS_BASIS' && <BusinessBasis portfolio={portfolio} hypothesisMap={hypothesisMap} edits={edits} setEdits={setEdits}
            reviewMode={decisionStage !== 'BUSINESS_BASIS'} onForward={decisionStage === 'LEGAL_REVIEW' || decisionStage === 'VALIDATION_PREP' ? () => navigateDecisionStep('LEGAL_REVIEW') : undefined} />}
          {activeDecisionView !== 'BUSINESS_BASIS' && <BasisSummary hypotheses={portfolio.hypotheses} />}
          {activeDecisionView === 'LEGAL_REVIEW' && <LegalWorkspace portfolio={portfolio} projectId={projectId} onBack={() => navigateDecisionStep('BUSINESS_BASIS')} onPrepare={openValidationPrep} canReturnToPreparation={decisionStage === 'VALIDATION_PREP'} />}
        </section></div></>}
      {!validationPrep && galleryVisible && <ProposalGallery concepts={portfolio.concepts} selectedId={selectedId} compared={compared}
        requests={supportedInputs} drafts={drafts} busy={portfolio.busy}
        onDraft={updateDraft} onRespond={submitInput} onRetry={portfolio.retryContinuation}
        onCompare={(conceptId) => setCompared((value) => toggleComparedConcept(value, conceptId))}
        onOpenComparison={() => setView('COMPARE')} onSelect={selectConcept} onContinueCurrent={continueCurrentSelection} selectingConceptId={selectingConceptId}
        selectionReadOnly={Boolean(portfolio.selection) && !selectionChangeMode} canChangeSelection={canChangeSelection(portfolio.selection)} onEnableSelectionChange={() => setSelectionChangeMode(true)} />}
      {!validationPrep && galleryVisible && unmatchedInputs.length > 0 && <InputGroup title="추가 정보가 있으면 검토를 이어갈 수 있는 사업안" description="아래 사업안을 확인하지 않아도 이미 준비된 사업안은 선택할 수 있습니다." requests={unmatchedInputs} drafts={drafts} onDraft={updateDraft} onSubmit={submitInput} onRetry={portfolio.retryContinuation} busy={portfolio.busy} />}
      {!validationPrep && galleryVisible && unsupportedInputs.length > 0 && <WithheldCandidates requests={unsupportedInputs} />}
    </>}
    {portfolio.concepts.length === 0 && supportedInputs.length > 0 && <InputGroup title="추가 정보가 있으면 검토를 이어갈 수 있는 사업안" description="확인 가능한 실제 사업정보만 입력해 주세요." requests={supportedInputs} drafts={drafts} onDraft={updateDraft} onSubmit={submitInput} onRetry={portfolio.retryContinuation} busy={portfolio.busy} />}
    {portfolio.concepts.length === 0 && unsupportedInputs.length > 0 && <WithheldCandidates requests={unsupportedInputs} />}
  </ProjectWorkspace>;
}

export function PreGeneration({ onStart, busy }) {
  const phases = [['사업안 생성', '서로 다른 사업 방향으로 후보를 만듭니다.'], ['법률·규제 검토', '각 사업안에서 확인해야 할 법률·규제 요소를 검토합니다.'], ['비교 및 선택', '검토를 거친 사업안을 같은 기준으로 비교하고 선택합니다.']];
  return <section className="business-proposal__pre-generation"><div className="business-proposal__process" aria-label="사업안 생성 및 검토 과정">{phases.map(([title, description], index) => <article key={title}><span>{index + 1}</span><div><h2>{title}</h2><p>{description}</p></div></article>)}</div><button type="button" className="bp-button bp-button--primary" disabled={busy} onClick={onStart}>사업안 생성 및 법률 검토 시작</button></section>;
}

export function PortfolioStatus({ run, busy, onRestart, onDetail, events = [], now = 0 }) {
  const presentation = portfolioRunPresentation(run);
  const running = run.productStatus === 'RUNNING';
  const started = Date.parse(events[0]?.occurredAt ?? run.updatedAt ?? '');
  const elapsed = Number.isFinite(started) ? Math.max(0, Math.floor((now - started) / 1000)) : 0;
  const latest = Date.parse(events.at(-1)?.occurredAt ?? '');
  const failed = run.productStatus === 'FAILED';
  const needsInput = run.productStatus === 'NEEDS_INPUT';
  const metric = businessProposalSummaryMetric(run, events);
  return <ProjectExecutionExperience title={running ? '사업안을 생성하고 검토하고 있습니다' : presentation.title} {...businessProposalExecutionPresentation(run, events)} elapsedSeconds={running ? elapsed : undefined} latestUpdate={running && Number.isFinite(latest) ? `마지막 업데이트 ${Math.max(0, Math.floor((now - latest) / 1000))}초 전` : undefined} metric={metric} failureMessage="사업안 생성을 완료하지 못했습니다." needsInputMessage="사업안을 계속 검토하려면 추가 정보가 필요합니다." onDetail={(failed || running || needsInput) ? onDetail : undefined}>{presentation.restart && <button type="button" disabled={busy} onClick={onRestart}>{presentation.action}</button>}</ProjectExecutionExperience>;
}

function ProposalGallery({ concepts, selectedId, compared, requests, drafts, busy, onDraft, onRespond, onRetry, onCompare, onOpenComparison, onSelect, onContinueCurrent, selectingConceptId, selectionReadOnly = false, canChangeSelection: selectionCanChange = false, onEnableSelectionChange }) {
  const comparedConcepts = concepts.filter((concept) => compared.includes(concept.conceptId));
  const comparisonAvailable = concepts.length >= 2;
  return <section className="proposal-gallery" aria-labelledby="proposal-gallery-title">
    {selectionReadOnly && <div className="proposal-gallery__review"><div><strong>사업안 선택 결과</strong><span>선택한 사업안을 강조해 표시하고 있습니다.</span></div>{selectionCanChange && <button type="button" className="bp-button bp-button--secondary" onClick={onEnableSelectionChange}>선택 변경</button>}</div>}
    {comparisonAvailable && <header className="comparison-picker"><div><span>비교</span><strong>{compared.length}/2</strong><div className="comparison-picker__selection">{comparedConcepts.map((concept) => <em key={concept.conceptId}>{concept.conceptName}</em>)}<p>{compared.length === 0 ? '두 사업안을 골라 직접 비교할 수 있습니다.' : compared.length === 1 ? '한 개 더 선택하세요.' : '선택한 두 사업안이 준비됐습니다.'}</p></div></div>{canOpenComparison(compared) && <button type="button" className="bp-button bp-button--secondary" onClick={onOpenComparison}>두 사업안 비교<AppIcon name="arrowRight" size={15} /></button>}</header>}
    <h2 id="proposal-gallery-title" className="sr-only">생성된 사업안</h2>
    <div className="proposal-grid">{concepts.map((concept) => <ProposalCard key={concept.conceptId} concept={concept} allConcepts={concepts} selected={concept.conceptId === selectedId} selecting={concept.conceptId === selectingConceptId} compared={compared.includes(concept.conceptId)} compareDisabled={!compared.includes(concept.conceptId) && compared.length >= 2} comparisonAvailable={comparisonAvailable} requests={candidateRequests(requests, concept.candidateId)} drafts={drafts} onDraft={onDraft} onRespond={onRespond} onRetry={onRetry} onCompare={() => onCompare(concept.conceptId)} onSelect={() => onSelect(concept.conceptId)} onContinueCurrent={onContinueCurrent} busy={busy} selectionReadOnly={selectionReadOnly} />)}</div>
  </section>;
}

function ProposalCard({ concept, allConcepts, selected, selecting, compared, compareDisabled, comparisonAvailable, requests, drafts, onDraft, onRespond, onRetry, onCompare, onSelect, onContinueCurrent, busy, selectionReadOnly = false }) {
  const preview = buildProposalPreview(concept, allConcepts);
  return <article className="proposal-card" data-selected={selected}><header><div><h3>{concept.conceptName}</h3><span>{selected && <AppIcon name="check" size={13} />}{selected ? '현재 선택' : selectionReadOnly ? '검토한 후보' : '선택 가능'}</span></div>{comparisonAvailable && <label><input type="checkbox" checked={compared} disabled={compareDisabled} onChange={onCompare} /> 비교에 추가</label>}</header><p className="proposal-card__definition">{preview.definition}</p><dl>{preview.highlights.map((item) => <div key={item.key}><dt>{item.label}</dt><dd>{item.value}</dd></div>)}</dl><p className="proposal-card__legal"><AppIcon name="check" size={15} /> 법률·규제 사전 검토 완료</p>{!selectionReadOnly && requests.map((request) => <CandidateInput key={request.inputRequestId} request={request} draft={drafts[request.inputRequestId] ?? createCandidateDraft(request)} onDraft={(next) => onDraft(request, next)} onSubmit={() => onRespond(request)} onRetry={() => onRetry(request.inputRequestId)} busy={busy} />)}{!selectionReadOnly && <button type="button" className="bp-button bp-button--primary proposal-card__select" disabled={busy} onClick={selected ? onContinueCurrent : onSelect}>{selecting ? <><span className="bp-button__spinner" aria-hidden="true" />선택 중...</> : selected ? <>현재 선택으로 계속<AppIcon name="arrowRight" size={16} /></> : <>이 사업안 선택<AppIcon name="arrowRight" size={16} /></>}</button>}</article>;
}

function ComparisonFocus({ concepts, selectedId, onBack, onSelect, onContinueCurrent, busy, selectingConceptId, selectionReadOnly = false }) {
  if (!canOpenComparison(concepts.map((item) => item.conceptId))) return <section className="proposal-comparison"><button type="button" className="bp-button bp-button--tertiary proposal-comparison__back" aria-label="사업안으로 돌아가기" onClick={onBack}><AppIcon name="chevronLeft" /> 사업안으로 돌아가기</button><p>비교할 사업안 두 개를 먼저 선택해 주세요.</p></section>;
  const rows = comparisonRows(concepts);
  const groups = detailedComparisonGroups(concepts);
  return <section className="proposal-comparison"><button type="button" className="bp-button bp-button--tertiary proposal-comparison__back" aria-label="사업안으로 돌아가기" onClick={onBack}><AppIcon name="chevronLeft" /> 사업안으로 돌아가기</button><ComparisonMatrix concepts={concepts} rows={rows} selectedId={selectedId} onSelect={onSelect} onContinueCurrent={onContinueCurrent} busy={busy} selectingConceptId={selectingConceptId} readOnly={selectionReadOnly} /> <div className="proposal-comparison__details"><h3>세부 내용 보기</h3>{groups.map((group) => <Disclosure key={group.title} title={group.title}><ComparisonMatrix concepts={concepts} rows={group.rows} compact /></Disclosure>)}</div></section>;
}

function ComparisonMatrix({ concepts, rows, selectedId, onSelect, onContinueCurrent, busy, selectingConceptId, compact = false, readOnly = false }) {
  return <div className={`proposal-comparison__matrix${compact ? ' is-compact' : ''}`}><div className="proposal-comparison__corner">비교 항목</div>{concepts.map((concept) => <header key={concept.conceptId}><strong>{concept.conceptName}</strong>{!compact && !readOnly && <button type="button" className="bp-button bp-button--primary" disabled={busy} onClick={concept.conceptId === selectedId ? onContinueCurrent : () => onSelect(concept.conceptId)}>{selectingConceptId === concept.conceptId ? '선택 중...' : concept.conceptId === selectedId ? '현재 선택으로 계속' : '이 사업안 선택'}</button>}{!compact && readOnly && concept.conceptId === selectedId && <span className="proposal-comparison__selected"><AppIcon name="check" size={14} />현재 선택</span>}</header>)}{rows.flatMap((row) => [<strong className="proposal-comparison__label" key={`${row.label}-label`}>{row.label}</strong>, ...row.values.map((value, index) => <p key={`${row.label}-${concepts[index].conceptId}`}><span>{concepts[index].conceptName}</span>{value}</p>)])}</div>;
}

export function DecisionProgress({ stage, reachability, onNavigate }) {
  const stages = [['PROPOSAL_SELECTION', '사업안 선택'], ['BUSINESS_BASIS', '분석 기준 확정'], ['LEGAL_REVIEW', '법률·규제 확인'], ['VALIDATION_PREP', '사업 검증 준비']];
  return <ol className="decision-progress" aria-label="사업안 결정 과정">{stages.map(([key, label], index) => {
    const current = key === stage;
    const reached = Boolean(reachability?.[key]);
    const state = current ? 'current' : reached ? 'completed' : 'upcoming';
    return <li key={key} data-state={state}><button type="button" aria-label={label} disabled={!reached} aria-disabled={!reached} aria-current={current ? 'step' : undefined} onClick={() => onNavigate(key)}><span>{reached && !current ? <AppIcon name="check" size={14} /> : index + 1}</span><strong>{label}</strong></button></li>;
  })}</ol>;
}

function FlowRationale() {
  return <aside className="business-decision-rationale"><strong>왜 이 과정을 거치나요?</strong><p>선택한 사업안을 같은 기준으로 검증하기 위해 지역·가격·수익 방식·시장 목표를 먼저 확정하고, 이 조건에 따라 달라질 수 있는 법률·규제를 마지막으로 확인합니다.</p></aside>;
}

function SelectedSummary({ concept, canChange, onShow }) {
  const preview = buildProposalPreview(concept, [concept]);
  return <section className="selected-proposal-summary"><div><span><AppIcon name="check" size={16} /> 선택한 사업안</span><h2>{concept?.conceptName ?? '선택한 사업안'}</h2><p>{preview.definition}</p></div><div><Disclosure className="disclosure--tertiary" title="선택한 사업안 보기"><dl>{preview.highlights.map((item) => <div key={item.key}><dt>{item.label}</dt><dd>{item.value}</dd></div>)}</dl></Disclosure>{canChange && <button type="button" className="bp-button bp-button--secondary" onClick={onShow}>선택 변경</button>}</div></section>;
}

function BusinessBasis({ portfolio, hypothesisMap, edits, setEdits, reviewMode = false, onForward }) {
  const [confirming, setConfirming] = useState(false);
  const [confirmationNotice, setConfirmationNotice] = useState('');
  const disabled = portfolio.busy || Boolean(portfolio.selection.activeTaskRunId);
  const field = (type) => <HypothesisField key={type} type={type} value={hypothesisMap[type]} edit={edits[type]} resetKey={`${hypothesisMap[type]?.decisionStatus ?? ''}:${JSON.stringify(hypothesisMap[type]?.finalValue ?? null)}`} onEdit={(next) => setEdits((current) => ({ ...current, [type]: next }))} onAlternative={() => portfolio.alternative(type)} disabled={disabled || reviewMode} readOnly={reviewMode} />;
  const inputCount = hypothesisInputCount(portfolio.hypotheses, edits);
  const missing = HYPOTHESIS_TYPES.filter((type) => !hypothesisHasValue(
    edits[type] ?? hypothesisMap[type]?.finalValue ?? hypothesisMap[type]?.proposedValue));
  const presentations = HYPOTHESIS_TYPES.map((type) => ({ type,
    ...hypothesisPresentation(hypothesisMap[type], edits[type]) }));
  const invalid = presentations.find((item) => item.hasCurrentValue && !item.confirmable);
  const terminalBlocker = presentations.find((item) => !item.confirmable);
  const terminalBlockerType = terminalBlocker?.type;
  const terminalBlockerReason = terminalBlocker?.blockingReason;
  const focusField = (type) => setTimeout(() => {
    const node = document.getElementById(`business-basis-${type}`);
    node?.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
    node?.focus?.({ preventScroll: true });
  }, 0);
  const confirm = async () => {
    setConfirming(true); setConfirmationNotice('');
    try {
      if (missing.length) {
        setConfirmationNotice(`${HYPOTHESIS_LABELS[missing[0]]} 값이 비어 있습니다.`);
        focusField(missing[0]);
        return;
      }
      if (invalid) {
        setConfirmationNotice(`${HYPOTHESIS_LABELS[invalid.type]}: ${invalid.blockingReason}`);
        focusField(invalid.type);
        return;
      }
      await portfolio.confirm(buildHypothesisChanges(portfolio.hypotheses, edits));
    } catch (error) {
      setConfirmationNotice(invalid
        ? `${HYPOTHESIS_LABELS[invalid.type]} 값을 사용할 수 없습니다. ${invalid.blockingReason ?? getUserErrorMessage(error)}`
        : getUserErrorMessage(error));
      if (invalid) focusField(invalid.type);
    } finally { setConfirming(false); }
  };
  useEffect(() => {
    if (!portfolio.confirmationEvents?.terminal
      || portfolio.selection.status !== 'PENDING_HYPOTHESIS_CONFIRMATION') return undefined;
    if (!terminalBlockerType) return undefined;
    const timer = setTimeout(() => {
      setConfirmationNotice(`${HYPOTHESIS_LABELS[terminalBlockerType]}: ${terminalBlockerReason}`);
      const node = document.getElementById(`business-basis-${terminalBlockerType}`);
      node?.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
      node?.focus?.({ preventScroll: true });
    }, 0);
    return () => clearTimeout(timer);
  }, [portfolio.confirmationEvents?.terminal, portfolio.selection.status,
    terminalBlockerType, terminalBlockerReason]);
  const readyForReport = portfolio.selection.status === 'READY_FOR_LEGAL_REPORT' && !portfolio.report;
  const confirmableCount = presentations.filter((item) => item.confirmable).length;
  const processing = portfolio.selection.status === 'PENDING_HYPOTHESIS_CONFIRMATION'
    && (confirming || Boolean((portfolio.confirmationTaskRunId || portfolio.selection.activeTaskRunId)
      && !portfolio.confirmationEvents?.terminal));
  return <section className="business-basis" data-review-mode={reviewMode}><header><div><p>{reviewMode ? '분석 기준 확인' : '분석 기준 확정'}</p><h2>시장 분석에 사용할 기준값</h2><span>{reviewMode ? '확정한 분석 기준을 확인하고 법률·규제 결과로 돌아갈 수 있습니다.' : '시장 규모와 경쟁 환경을 같은 기준으로 분석하기 위해 지역·가격·수익 방식·시장 목표를 먼저 확인합니다.'}</span></div><strong>{processing ? '처리 중' : `${inputCount}/7 입력 완료`}</strong></header>{!reviewMode && missing.length > 0 && <p className="business-basis__pending" role="status"><b>값이 필요한 항목</b>{missing.map((type) => HYPOTHESIS_LABELS[type]).join(' · ')}</p>}{!reviewMode && inputCount === 7 && confirmableCount < 7 && <p className="business-basis__pending" role="status"><b>확정할 수 없는 항목</b>{presentations.filter((item) => !item.confirmable).map((item) => HYPOTHESIS_LABELS[item.type]).join(' · ')}</p>}<section className="business-basis__core"><h3>사업 기본 조건</h3>{BUSINESS_BASIS_TYPES.map(field)}</section><section className="business-basis__targets"><h3>시장 목표</h3>{MARKET_TARGET_TYPES.map(field)}</section>{confirmationNotice && <p className="business-basis__confirmation-error" role="alert">{confirmationNotice}</p>}{processing && <p className="business-basis__status" role="status" aria-live="polite">기준값을 확인하고 있습니다.</p>}{!reviewMode && portfolio.selection.status === 'DELTA_LEGAL_PENDING' && <p className="business-basis__status" role="status"><AppIcon name="check" size={15} />기준값은 확정되었습니다. 변경한 기준이 법률·규제에 미치는 영향을 확인하고 있습니다.</p>}{!reviewMode && readyForReport && <p className="business-basis__status">확정한 기준을 바탕으로 최종 법률·규제 검토 결과를 준비합니다.</p>}<div className="business-basis__actions">{!reviewMode && portfolio.selection.status === 'PENDING_HYPOTHESIS_CONFIRMATION' && <button type="button" className="bp-button bp-button--primary" disabled={disabled || processing} onClick={confirm}>{processing ? '기준값을 확인하고 있습니다...' : '기준값 확정'}</button>}{!reviewMode && readyForReport && <button type="button" className="bp-button bp-button--primary" disabled={portfolio.busy} onClick={portfolio.finalizeReport}>현재 값으로 진행<AppIcon name="arrowRight" size={16} /></button>}{!reviewMode && portfolio.selection.nextAction === 'REVISE_OR_RETRY' && <button type="button" className="bp-button bp-button--secondary" disabled={portfolio.busy} onClick={portfolio.retryDelta}>법률·규제 재검토 다시 시도</button>}{reviewMode && onForward && <button type="button" className="bp-button bp-button--primary" onClick={onForward}>법률·규제 결과로 돌아가기<AppIcon name="arrowRight" size={16} /></button>}</div></section>;
}

export function HypothesisField({ type, value, edit, resetKey, onEdit, onAlternative, disabled, readOnly = false }) {
  const [editSession, setEditSession] = useState({ open: false, resetKey });
  const editing = !readOnly && editSession.open && editSession.resetKey === resetKey;
  const locked = value?.locked;
  const source = value?.finalValue ?? value?.proposedValue;
  const current = edit ?? source;
  const updateObject = (field, next) => onEdit({ ...(typeof current === 'object' && current ? current : {}), [field]: next });
  let editor;
  if (type === 'PRE_MARKET_SOM_SHARE') editor = <div className="hypothesis-structured hypothesis-structured--share"><label><span>목표 점유율</span><span className="input-with-suffix"><input aria-label="목표 점유율" type="number" min="0.01" step="0.01" disabled={locked || disabled} value={current?.targetSharePercent ?? ''} onChange={(event) => updateObject('targetSharePercent', Number(event.target.value))} /><i>%</i></span></label><label><span>목표 기간</span><span className="input-with-suffix"><input aria-label="목표 기간" type="number" min="1" step="1" disabled={locked || disabled} value={current?.horizonYears ?? ''} onChange={(event) => updateObject('horizonYears', Number(event.target.value))} /><i>년</i></span></label><label className="is-wide"><span>가정 근거</span><textarea disabled={locked || disabled} value={(current?.assumptions ?? []).join('\n')} onChange={(event) => updateObject('assumptions', event.target.value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean))} /></label></div>;
  else if (type === 'PRE_MARKET_SOM') editor = <div className="hypothesis-structured hypothesis-structured--som"><label><span>목표 규모</span><input aria-label="초기 목표 시장 규모" type="number" min="0" disabled={locked || disabled} value={current?.amount ?? ''} onChange={(event) => updateObject('amount', Number(event.target.value))} /></label><label><span>통화</span><select aria-label="통화" disabled={locked || disabled} value={current?.currency ?? 'KRW'} onChange={(event) => updateObject('currency', event.target.value)}>{!["KRW", "USD", "EUR", "JPY"].includes(current?.currency) && current?.currency && <option value={current.currency}>{current.currency}</option>}<option value="KRW">KRW</option><option value="USD">USD</option><option value="EUR">EUR</option><option value="JPY">JPY</option></select></label><label><span>기준 기간</span><input aria-label="시장 규모 기간" disabled={locked || disabled} value={current?.period ?? ''} onChange={(event) => updateObject('period', event.target.value)} /></label><label className="is-wide"><span>계산 기준</span><textarea disabled={locked || disabled} value={current?.calculationBasis ?? ''} onChange={(event) => updateObject('calculationBasis', event.target.value)} /></label><label className="is-wide"><span>근거</span><textarea disabled={locked || disabled} value={(current?.assumptions ?? []).join('\n')} onChange={(event) => updateObject('assumptions', event.target.value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean))} /></label></div>;
  else editor = <textarea aria-label={HYPOTHESIS_LABELS[type]} disabled={locked || disabled} value={edit ?? hypothesisValueText(source)} onChange={(event) => onEdit(event.target.value)} />;
  const presentation = hypothesisPresentation(value, edit);
  const needsInput = !presentation.hasCurrentValue;
  return <article id={`business-basis-${type}`} tabIndex={needsInput || !presentation.confirmable ? -1 : undefined} className={`hypothesis-field hypothesis-field--${type.toLowerCase()}`} data-editing={editing} data-needs-input={needsInput} data-blocked={!presentation.confirmable || undefined}><header><strong>{HYPOTHESIS_LABELS[type]}</strong><span>{editing ? '수정 중' : !presentation.confirmable ? '확정할 수 없음' : hypothesisDecisionLabel(value)}</span></header><HypothesisReadValue type={type} value={current} />{!presentation.confirmable && presentation.blockingReason && <p className="hypothesis-field__blocking" role="status">{presentation.blockingReason}</p>}{!readOnly && !locked && !editing && <button type="button" className="bp-button bp-button--tertiary" disabled={disabled || !value} onClick={() => setEditSession({ open: true, resetKey })}>수정<AppIcon name="chevronRight" size={15} /></button>}{editing && <div className="hypothesis-field__editor">{editor}<div><button type="button" className="bp-button bp-button--tertiary" onClick={() => setEditSession({ open: false, resetKey })}>편집 닫기</button>{!locked && <button type="button" className="bp-button bp-button--secondary" disabled={disabled} onClick={onAlternative}>다른 값 추천받기</button>}</div></div>}</article>;
}

function HypothesisReadValue({ type, value }) {
  if (type === 'PRE_MARKET_SOM_SHARE' && value && typeof value === 'object') return <dl className="hypothesis-field__read hypothesis-field__read--structured"><div><dt>목표</dt><dd>{value.targetSharePercent == null ? '미입력' : `${value.targetSharePercent}%`}</dd></div><div><dt>기간</dt><dd>{value.horizonYears == null ? '미입력' : `${value.horizonYears}년`}</dd></div><div className="is-wide"><dt>근거</dt><dd data-empty={!value.assumptions?.length}>{value.assumptions?.length ? value.assumptions.join(' · ') : '근거가 아직 없습니다.'}</dd></div></dl>;
  if (type === 'PRE_MARKET_SOM' && value && typeof value === 'object') {
    const amount = Number(value.amount);
    const canonical = Number.isFinite(amount) ? `${new Intl.NumberFormat('ko-KR').format(amount)} ${value.currency ?? ''}`.trim() : '미입력';
    return <dl className="hypothesis-field__read hypothesis-field__read--structured"><div><dt>금액</dt><dd>{canonical}<small> · {formatKoreanCurrencyAmount(amount, value.currency)}</small></dd></div><div><dt>기준 기간</dt><dd>{value.period || '미입력'}</dd></div><div className="is-wide"><dt>계산 기준</dt><dd data-empty={!value.calculationBasis}>{value.calculationBasis || '계산 기준이 아직 없습니다.'}</dd></div><div className="is-wide"><dt>근거</dt><dd data-empty={!value.assumptions?.length}>{value.assumptions?.length ? value.assumptions.join(' · ') : '근거가 아직 없습니다.'}</dd></div></dl>;
  }
  return <div className="hypothesis-field__read"><small>현재 기준값</small><strong>{hypothesisDisplay(type, value) || '값을 준비하고 있습니다.'}</strong></div>;
}

function BasisSummary({ hypotheses }) {
  const values = Object.fromEntries(hypotheses.map((item) => [item.hypothesisType, item.finalValue ?? item.proposedValue]));
  return <section className="basis-summary"><div><span><AppIcon name="check" size={16} /> 사업 기준값 확인 완료</span><h2>시장 분석 기준값</h2></div><dl><div><dt>사업 대상 지역</dt><dd>{hypothesisDisplay('TARGET_REGION', values.TARGET_REGION) || '확인 완료'}</dd></div><div><dt>수익 방식</dt><dd>{hypothesisDisplay('REVENUE_MODEL', values.REVENUE_MODEL) || '확인 완료'}</dd></div><div><dt>시장 목표</dt><dd>{hypothesisDisplay('PRE_MARKET_SOM_SHARE', values.PRE_MARKET_SOM_SHARE) || '확인 완료'}</dd></div></dl></section>;
}

function LegalWorkspace({ portfolio, projectId, onBack, onPrepare, canReturnToPreparation }) {
  return <section className="legal-workspace"><button type="button" className="bp-button bp-button--tertiary legal-workspace__back" onClick={onBack}><AppIcon name="chevronLeft" size={16} />분석 기준 확정으로 돌아가기</button><header><div><p>법률·규제 확인</p><h2>법률·규제 검토 결과를 확인하세요</h2><span>앞에서 확정한 가격, 제공 방식, 대상 지역 등의 조건이 법률·규제상 어떤 영향을 주는지 최종 확인합니다.</span></div><div className="legal-workspace__header-actions"><Link className="bp-button bp-button--secondary" to={projectRoutes.legalReport(projectId)} onClick={() => scrollPageToTop({ smooth: false })}>법률·규제 보고서 PDF<AppIcon name="arrowUpRight" size={15} /></Link>{(portfolio.selection.nextAction === 'FINALIZE_MARKET_SEED' || canReturnToPreparation) && <button type="button" className="bp-button bp-button--primary" disabled={portfolio.busy} onClick={onPrepare}>{canReturnToPreparation ? '사업 검증 준비로 돌아가기' : '시장 분석 준비하기'}<AppIcon name="arrowRight" size={16} /></button>}</div></header>{portfolio.report && <LegalReport report={portfolio.report} />}</section>;
}

export function CandidateInput({ request, draft, onDraft, onSubmit, onRetry, busy }) {
  const [open, setOpen] = useState(false);
  if (request.status === 'ANSWERED' && request.nextAction === 'RETRY_CONTINUATION') return <section className="candidate-input"><strong>제출한 정보의 반영을 완료하지 못했습니다.</strong><p>같은 정보를 다시 입력하지 않고 반영 작업만 다시 시도합니다.</p><button type="button" disabled={busy} onClick={onRetry}>추가 사업정보 반영 다시 시도</button></section>;
  const options = candidateFieldOptions(request);
  const payload = serializeCandidateFacts(request, draft);
  const unresolved = request.nextAction === 'INPUT_TARGET_UNRESOLVED' || options.length === 0;
  if (unresolved) return null;
  return <section className="candidate-input" data-editor-open={open}><header><div><small>추가 확인 필요</small><strong>{request.candidateDisplayName ?? '사업안 세부 검토'}</strong></div>{request.candidateOneLineSummary && <p>{request.candidateOneLineSummary}</p>}</header><div className="candidate-input__preview"><strong>무엇을 확인하나요?</strong><ul>{options.map((field) => <li key={field}>{CANDIDATE_FACT_FIELDS[field].question}</li>)}</ul><strong>왜 필요한가요?</strong><p>이 정보에 따라 적용되는 계약·자격·규제 조건이 달라질 수 있어 확인이 필요합니다.</p></div>{!open && <button type="button" className="bp-button bp-button--secondary candidate-input__open" onClick={() => setOpen(true)}>정보 입력해서 검토 계속<AppIcon name="chevronDown" size={16} /></button>}{open && <><div className="candidate-input__fields">{options.map((field) => { const contract = CANDIDATE_FACT_FIELDS[field]; return <label key={field}><strong>{contract.label}</strong><span>{contract.help}</span><textarea aria-label={contract.label} value={draft.values?.[field] ?? ''} onChange={(event) => onDraft({ values: { ...(draft.values ?? {}), [field]: event.target.value } })} /></label>; })}</div><div className="candidate-input__actions"><button type="button" className="bp-button bp-button--tertiary" onClick={() => setOpen(false)}>입력 닫기</button><button type="button" className="bp-button bp-button--primary" disabled={busy || !payload} onClick={onSubmit}>정보 제출</button></div></>}</section>;
}

function InputGroup({ title, description, requests, drafts, onDraft, onSubmit, onRetry, busy }) { return <section className="business-proposal__input-first"><h2>{title}</h2>{description && <p>{description}</p>}{requests.map((request) => <CandidateInput key={request.inputRequestId} request={request} draft={drafts[request.inputRequestId] ?? createCandidateDraft(request)} onDraft={(next) => onDraft(request, next)} onSubmit={() => onSubmit(request)} onRetry={() => onRetry(request.inputRequestId)} busy={busy} />)}</section>; }

function WithheldCandidates({ requests }) {
  return <section className="candidate-withheld"><Disclosure title={`이번에 이어서 검토하지 못한 사업안 ${requests.length}개`}>{requests.map((request) => <article key={request.inputRequestId ?? request.candidateId}><strong>{request.candidateDisplayName ?? '추가 확인이 필요한 사업안'}</strong><p>법률·규제 검토 중 추가 사실 확인이 필요했지만, 현재 서비스가 받을 수 있는 정보 항목으로 연결되지 않아 이번 선택 후보에는 포함하지 않았습니다.</p></article>)}</Disclosure></section>;
}

const asList = (value) => Array.isArray(value) ? value : value == null || value === '' ? [] : [value];
function BulletSection({ title, values, empty = '해당 사항이 없습니다.' }) { const items = asList(values); return <article><h3>{title}</h3>{items.length === 0 ? <p>{empty}</p> : <ul>{items.map((item, index) => <li key={`${title}-${index}`}>{typeof item === 'string' ? item : item.safeSummary ?? item.title ?? String(item)}</li>)}</ul>}</article>; }
function FlowSection({ title, values }) { const items = asList(values); return <article><h3>{title}</h3>{items.length === 0 ? <p>확인된 흐름이 없습니다.</p> : <ol className="legal-flow">{items.map((item, index) => <li key={`${title}-${index}`}><span>{index + 1}</span>{typeof item === 'string' ? item : item.safeSummary ?? item.title ?? String(item)}</li>)}</ol>}</article>; }

function Disclosure({ title, children, className = '' }) {
  const [open, setOpen] = useState(false);
  const reactId = useId();
  const id = `disclosure-${String(title).replace(/[^a-zA-Z0-9가-힣]+/g, '-')}-${reactId.replace(/[^a-zA-Z0-9]/g, '')}`;
  return <section className={`disclosure ${className}`}><button type="button" className="bp-button bp-button--tertiary" aria-expanded={open} aria-controls={id} onClick={() => setOpen((value) => !value)}><span>{title}</span><AppIcon name={open ? 'chevronUp' : 'chevronDown'} size={17} /></button><div id={id} hidden={!open}>{children}</div></section>;
}

function EvidenceSection({ values }) {
  const groups = groupLegalEvidence(values);
  return <article className="legal-evidence"><h3>관련 법률·규제</h3>{groups.length === 0 ? <p>표시할 공식 근거가 없습니다.</p> : <div>{groups.map((group) => <section className="legal-evidence__law" key={group.lawName}><header><strong>{group.lawName}</strong><div>{group.articles.map((article, index) => article.articleReference && <span key={`${article.articleReference}-${index}`}>{article.articleReference}</span>)}</div></header>{group.articles.map((article, index) => <Disclosure key={article.contentHash ?? `${article.articleReference}-${article.officialSourceUri}-${index}`} title={`${article.articleReference ?? '관련 근거'}${article.title ? ` ${article.title}` : ''}`}><div className="legal-evidence__article">{article.boundedProvisionSummary && <><strong>주요 내용</strong><p>{article.boundedProvisionSummary}</p></>}{article.effectiveDate && <small>시행일 {article.effectiveDate}</small>}{article.officialSourceUri && <a href={article.officialSourceUri} target="_blank" rel="noreferrer">법령 원문 보기</a>}</div></Disclosure>)}</section>)}</div>}</article>;
}

export function LegalReport({ report }) {
  const body = report.report ?? {};
  const rawConclusion = body.finalLegalConclusion;
  const conclusion = rawConclusion && typeof rawConclusion === 'object' ? rawConclusion : { safeSummary: rawConclusion };
  const sourcePartial = conclusion.legalSourceStatus === 'SOURCE_PARTIAL' || conclusion.sourceStatus === 'SOURCE_PARTIAL' || conclusion.evidenceDiagnostics?.coverageStatus === 'SOURCE_PARTIAL';
  const roles = body.businessRoles ?? {};
  const roleRows = [['플랫폼 역할', roles.platformRole], ['판매 주체', roles.sellerRole], ['서비스 제공 주체', roles.providerRole], ['중개 주체', roles.intermediaryRole]].filter(([, value]) => value);
  const advertising = body.advertisingExpressionCautions ?? {};
  const attentionGroups = legalAttentionGroups(body);
  const advertisingDisclosures = advertisingOnlyDisclosures(body);
  const attentionItems = attentionGroups.flatMap((group) => group.values);
  const allowedClaims = excludeLegalItems(advertising.allowedClaims, attentionItems);
  const prohibitedVariants = excludeLegalItems(body.prohibitedVariants, attentionItems, allowedClaims, advertisingDisclosures);
  const deltaItems = asList(body.deltaLegalHistory).map((item) => item.legalReview?.safeSummary ?? item.safeSummary ?? (item.status ? legalStatusLabel(item.status) : '')).filter(Boolean);
  const delta = excludeLegalItems(deltaItems, attentionItems, allowedClaims, advertisingDisclosures, prohibitedVariants);
  const structureGroups = [
    ['거래 흐름', body.transactionFlow, 'flow'],
    ['결제·수취 흐름', body.paymentFlow, 'flow'],
    ['개인정보 이용', body.personalDataUsage, 'list'],
    ['물리 활동', body.physicalActivities, 'list'],
  ].map(([title, values, type]) => ({ title, values: uniqueLegalItems(values), type })).filter((group) => group.values.length > 0);
  const status = conclusion.status ?? conclusion.legalStatus ?? conclusion.productionStatus ?? conclusion.route;
  return <section className="final-legal-report"><article className="legal-conclusion"><h3>한눈에 보는 검토 결과</h3><strong>{legalStatusLabel(status)}</strong><p>{conclusion.safeSummary ?? '법률·규제 검토 결과가 준비되었습니다.'}</p>{sourcePartial && <div role="alert">공식 근거를 확인했지만 일부 법률 소스의 조회 범위에는 제한이 있습니다.</div>}<small>검토 기준일 {report.basisDate}</small></article><section className="legal-report__attention"><h3>특히 확인할 사항</h3>{attentionGroups.map((group) => group.values.length > 0
    ? <BulletSection key={group.title} title={group.title} values={group.values} />
    : group.meaningfulWhenEmpty ? <BulletSection key={group.title} title={group.title} values={[]} empty="현재 추가로 확인할 사항은 없습니다." /> : null)}</section><section className="legal-report__structure"><h3>사업 구조 검토</h3>{roleRows.length > 0 && <dl>{roleRows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>}{structureGroups.length > 0 && <div className="legal-report__structure-details">{structureGroups.map((group) => group.type === 'flow' ? <FlowSection key={group.title} title={group.title} values={group.values} /> : <BulletSection key={group.title} title={group.title} values={group.values} />)}</div>}{roleRows.length === 0 && structureGroups.length === 0 && <p>표시할 사업 구조 정보가 없습니다.</p>}</section><EvidenceSection values={body.officialEvidenceReferences} /><section className="legal-report__advertising"><h3>광고·표현 주의사항</h3>{allowedClaims.length > 0 && <BulletSection title="사용 가능한 표현" values={allowedClaims} />}{advertisingDisclosures.length > 0 && <BulletSection title="광고에서 함께 표시할 내용" values={advertisingDisclosures} />}{prohibitedVariants.length > 0 && <BulletSection title="피해야 할 표현" values={prohibitedVariants} />}</section><Disclosure title="상세 검토 내용"><div className="legal-report__details"><article><h3>변경사항 재검토 이력</h3>{delta.length === 0 ? <p>이번 확정 과정에서 별도로 표시할 재검토 이력이 없습니다.</p> : <ol>{delta.map((item, index) => <li key={`${item}-${index}`}>{item}</li>)}</ol>}</article></div></Disclosure></section>;
}
