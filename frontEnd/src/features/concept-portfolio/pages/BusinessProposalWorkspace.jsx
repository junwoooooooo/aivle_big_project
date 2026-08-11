import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useOutletContext, useParams } from 'react-router-dom';

import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { jobEventMessage, useJobEvents } from '../../../shared/async-events/index.js';
import { formatLocalTime } from '../../../shared/async-events/formatLocalTime.js';
import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import {
  CANDIDATE_FACT_FIELDS, HYPOTHESIS_LABELS, HYPOTHESIS_TYPES, buildHypothesisChanges,
  canOpenComparison, candidateFieldOptions, candidateRequests, createCandidateDraft,
  comparisonRows, hypothesisDecisionLabel, hypothesisDisplay, hypothesisValueText, portfolioRunPresentation,
  selectedConceptId, serializeCandidateFacts, toggleComparedConcept,
} from '../businessProposalModel.js';
import { useConceptPortfolio } from '../hooks/useConceptPortfolio.js';
import '../styles/business-proposal.css';
import '../styles/business-proposal-polish.css';

export default function BusinessProposalWorkspace({ initialMode = 'list' }) {
  const { projectId } = useParams();
  const outlet = useOutletContext() ?? {};
  const portfolio = useConceptPortfolio(projectId, outlet.liveRevision);
  const progressJobId = portfolio.run?.activeTaskRunId ?? portfolio.run?.initialTaskRunId ?? null;
  const progressEvents = useJobEvents(progressJobId);
  const [clock, setClock] = useState(Date.now());
  const [mode, setMode] = useState(initialMode);
  const [compared, setCompared] = useState([]);
  const [drafts, setDrafts] = useState({});
  const [edits, setEdits] = useState({});
  const [recoveredNotice, setRecoveredNotice] = useState(false);
  const selectionBaseline = useRef({ selectionId: null, conceptIds: new Set() });
  const selectedId = selectedConceptId(portfolio.selection);
  const comparedConcepts = portfolio.concepts.filter((concept) => compared.includes(concept.conceptId));
  const actionableInputs = candidateRequests(portfolio.inputRequests);
  const unmatchedInputs = actionableInputs.filter((request) => !portfolio.concepts.some((concept) => concept.candidateId === request.candidateId));
  const hypothesisMap = useMemo(() => Object.fromEntries(portfolio.hypotheses.map((item) => [item.hypothesisType, item])), [portfolio.hypotheses]);

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
      setRecoveredNotice(false);
      return;
    }
    if (selectionBaseline.current.selectionId !== selectionId) {
      selectionBaseline.current = { selectionId, conceptIds: currentIds };
      setRecoveredNotice(false);
      return;
    }
    const recovered = [...currentIds].some((id) => !selectionBaseline.current.conceptIds.has(id));
    if (recovered) setRecoveredNotice(true);
    selectionBaseline.current = { selectionId, conceptIds: currentIds };
  }, [portfolio.concepts, portfolio.selection?.selectionId]);

  const draft = (request) => drafts[request.inputRequestId] ?? createCandidateDraft(request);
  const updateDraft = (request, next) => setDrafts((current) => ({
    ...current, [request.inputRequestId]: { ...draft(request), ...next },
  }));
  const submitInput = (request) => {
    const current = draft(request);
    const payload = serializeCandidateFacts(request, current);
    if (payload) portfolio.respond(request.inputRequestId, payload, request.question ?? '');
  };

  if (portfolio.loading) return <main className="business-proposal" aria-busy="true"><p>검토된 사업안을 불러오고 있습니다.</p></main>;
  return <main className="business-proposal">
    <header className="business-proposal__hero">
      <div><p>BUSINESS PROPOSAL</p><h1>검토된 사업안</h1><span>법률·규제 검토를 통과한 사업안은 1개부터 5개까지 모두 정상 결과입니다.</span></div>
      <div className="business-proposal__mode"><button type="button" aria-pressed={mode === 'list'} onClick={() => setMode('list')}>사업안 목록</button><button type="button" aria-pressed={mode === 'compare'} onClick={() => setMode('compare')}>비교</button></div>
    </header>

    {portfolio.error && <section className="business-proposal__error" role="alert"><span>{getUserErrorMessage(portfolio.error)}</span><button type="button" onClick={portfolio.refresh}>다시 시도</button></section>}
    {!portfolio.run && <section className="business-proposal__empty"><h2>사업안 검토를 시작할 수 있습니다.</h2><p>확정된 아이디어를 바탕으로 최대 5개의 사업안을 검토합니다.</p><button type="button" disabled={portfolio.busy} onClick={portfolio.start}>사업안 검토 시작</button></section>}
    {portfolio.run && <PortfolioStatus run={portfolio.run} busy={portfolio.busy} onRestart={portfolio.start} events={progressEvents.events} now={clock} onDetail={() => outlet.openWorkCenterJob?.(progressJobId)} />}
    {recoveredNotice && <p className="business-proposal__notice" role="status">추가 사업안이 준비되었습니다. 현재 선택은 유지됩니다.</p>}

    {mode === 'compare' && <Comparison concepts={comparedConcepts} onSelect={portfolio.select} busy={portfolio.busy} />}
    {portfolio.concepts.length > 0 && <section className="proposal-grid" aria-label="사업안 목록">
      {portfolio.concepts.map((concept) => <ProposalCard key={concept.conceptId} concept={concept}
        selected={concept.conceptId === selectedId} compared={compared.includes(concept.conceptId)}
        compareDisabled={!compared.includes(concept.conceptId) && compared.length >= 3}
        requests={candidateRequests(portfolio.inputRequests, concept.candidateId)}
        drafts={drafts} onDraft={updateDraft} onRespond={submitInput} onRetry={portfolio.retryContinuation} onExplore={portfolio.start}
        onCompare={() => setCompared((value) => toggleComparedConcept(value, concept.conceptId))}
        onSelect={() => portfolio.select(concept.conceptId)} busy={portfolio.busy} />)}
    </section>}
    {portfolio.concepts.length > 0 && unmatchedInputs.length > 0 && <InputGroup title="추가 검토 중인 사업안" description="아래 정보는 검토 완료된 다른 사업안의 선택을 막지 않습니다." requests={unmatchedInputs} drafts={drafts} onDraft={updateDraft} onSubmit={submitInput} onRetry={portfolio.retryContinuation} onExplore={portfolio.start} busy={portfolio.busy} />}
    {portfolio.concepts.length === 0 && actionableInputs.length > 0 && <InputGroup title="사업안을 완성하려면 실제 사업정보가 필요합니다." requests={actionableInputs} drafts={drafts} onDraft={updateDraft} onSubmit={submitInput} onRetry={portfolio.retryContinuation} onExplore={portfolio.start} busy={portfolio.busy} />}

    {portfolio.selection && <section className="validation-assumptions">
      <header><p>다음 분석에 사용할 검증 가정을 확인해 주세요.<br />현재 사업안을 바탕으로 AI가 제안한 값입니다. 실제 계획과 다르면 수정할 수 있습니다.</p><span>{portfolio.selection.activeTaskRunId ? '처리 중 · 잠시 기다려 주세요' : `${portfolio.selection.hypothesisConfirmedCount}/7 확인`}</span></header>
      <div>{HYPOTHESIS_TYPES.map((type) => <HypothesisField key={type} type={type} value={hypothesisMap[type]}
        edit={edits[type]} onEdit={(next) => setEdits((current) => ({ ...current, [type]: next }))}
        onAlternative={() => portfolio.alternative(type)} disabled={portfolio.busy || Boolean(portfolio.selection.activeTaskRunId)} />)}</div>
      {portfolio.selection.status === 'DELTA_LEGAL_PENDING' && <p role="status">변경사항의 법률·규제 영향을 다시 확인하고 있습니다.</p>}
      <div className="validation-assumptions__actions">
        <button type="button" disabled={portfolio.busy || Boolean(portfolio.selection.activeTaskRunId)} onClick={() => portfolio.confirm(buildHypothesisChanges(portfolio.hypotheses, edits))}>7개 검증 가정 확인</button>
        {portfolio.selection.nextAction === 'REVISE_OR_RETRY' && <button type="button" disabled={portfolio.busy} onClick={portfolio.retryDelta}>변경사항 법률·규제 재검토 다시 시도</button>}
        {portfolio.selection.nextAction === 'REVIEW_LEGAL_REPORT' && <button type="button" disabled={portfolio.busy} onClick={portfolio.finalizeReport}>최종 법률·규제 보고서 확정</button>}
        {portfolio.selection.nextAction === 'FINALIZE_MARKET_SEED' && <button type="button" disabled={portfolio.busy} onClick={portfolio.finalizeMarketSeed}>다음 분석 준비</button>}
      </div>
    </section>}
    {portfolio.report && <LegalReport report={portfolio.report} />}
    {portfolio.selection?.status === 'READY_FOR_MARKET' && <section className="business-proposal__ready"><strong>다음 분석 준비 완료</strong><span>확정된 사업안과 검증 가정, 최종 법률 결과가 Market Seed에 고정되었습니다.</span><Link to={projectRoutes.market(projectId)}>시장 분석으로 이동</Link></section>}
  </main>;
}

export function PortfolioStatus({ run, busy, onRestart, onDetail, events = [], now = Date.now() }) {
  const view = portfolioRunPresentation(run);
  const running = run.productStatus === 'RUNNING';
  const recent = events.slice(-5);
  const started = Date.parse(events[0]?.occurredAt ?? run.updatedAt ?? '');
  const elapsed = Number.isFinite(started) ? Math.max(0, Math.floor((now - started) / 1000)) : 0;
  const latest = Date.parse(recent.at(-1)?.occurredAt ?? '');
  const summary = [...events].reverse().find((event) => event.stage === 'SUMMARY')?.messageParams ?? {};
  const reviewed = summary.reviewed ?? run.runSummary?.candidateGenerated;
  const failed = run.productStatus === 'FAILED';
  const needsInput = run.productStatus === 'NEEDS_INPUT';
  const failureEvent = [...events].reverse().find((event) => event.status === 'FAILED');
  const outcome = needsInput && reviewed != null
    ? `${reviewed}개의 사업안 후보를 검토했습니다. 현재 바로 선택 가능한 사업안은 없으며, ${run.openInputCount ?? summary.needsInput ?? 0}개의 사업안은 실제 운영정보 확인 후 검토를 계속할 수 있습니다.`
    : failed && reviewed != null
      ? `${reviewed}개의 사업안 후보를 검토했지만 최종 결과를 확정하지 못했습니다.`
      : `${run.producedConceptCount ?? 0}개 사업안 · 추가 검토 ${run.openInputCount ?? 0}건`;
  return <section className="portfolio-status"><div><strong>{view.title}{running ? '.'.repeat((Math.floor(now / 700) % 3) + 1) : ''}</strong>{view.detail && <span>{view.detail}</span>}{running && <small>경과 {String(Math.floor(elapsed / 60)).padStart(2, '0')}:{String(elapsed % 60).padStart(2, '0')}{Number.isFinite(latest) ? ` · 마지막 업데이트 ${Math.max(0, Math.floor((now - latest) / 1000))}초 전` : ''}</small>}</div><span>{running ? '검토 결과를 준비하고 있습니다.' : outcome}</span>{failed && run.failureCode !== 'NO_ACCEPTED_CONCEPTS' && failureEvent && <span className="portfolio-status__failure-reason"><strong>원인</strong> {jobEventMessage(failureEvent)}</span>}<div className="portfolio-status__actions">{view.restart && <button type="button" disabled={busy} onClick={onRestart}>{view.action}</button>}{(failed || running) && onDetail && <button type="button" onClick={onDetail}>작업 상세 보기</button>}</div>{running && recent.length > 0 && <ol className="portfolio-status__events">{recent.map((event) => <li key={event.eventId ?? event.sequence}><time>{formatLocalTime(event.occurredAt)}</time><span>{jobEventMessage(event)}</span></li>)}</ol>}</section>;
}

function ProposalCard({ concept, selected, compared, compareDisabled, requests, drafts, onDraft, onRespond, onRetry, onExplore, onCompare, onSelect, busy }) {
  return <article className="proposal-card" data-selected={selected}><header><div><h2>{concept.conceptName}</h2><span>{selected ? '현재 선택' : '선택 가능'}</span></div><label><input type="checkbox" checked={compared} disabled={compareDisabled} onChange={onCompare} /> 비교에 담기</label></header><p>{concept.summary}</p><LegalSummary review={concept.legalReview} />{requests.map((request) => <CandidateInput key={request.inputRequestId} request={request} draft={drafts[request.inputRequestId] ?? createCandidateDraft(request)} onDraft={(next) => onDraft(request, next)} onSubmit={() => onRespond(request)} onRetry={() => onRetry(request.inputRequestId)} onExplore={onExplore} busy={busy} />)}<button type="button" className="proposal-card__select" disabled={busy || selected} onClick={onSelect}>{selected ? '선택됨' : '이 사업안 선택'}</button></article>;
}

function LegalSummary({ review }) { const value = review ?? {}; return <section className="legal-summary"><strong>선택 전 법률·규제 요약</strong><p>{value.safeSummary ?? value.summary ?? value.conclusion ?? '검토 결과가 사업안에 반영되었습니다.'}</p></section>; }

export function CandidateInput({ request, draft, onDraft, onSubmit, onRetry, onExplore, busy }) {
  if (request.status === 'ANSWERED' && request.nextAction === 'RETRY_CONTINUATION') return <section className="candidate-input"><strong>제출한 정보의 반영을 완료하지 못했습니다.</strong><p>같은 정보를 다시 입력하지 않고 반영 작업만 다시 시도합니다.</p><button type="button" disabled={busy} onClick={onRetry}>추가 사업정보 반영 다시 시도</button></section>;
  const options = candidateFieldOptions(request);
  const payload = serializeCandidateFacts(request, draft);
  const unresolved = request.nextAction === 'INPUT_TARGET_UNRESOLVED' || options.length === 0;
  return <section className="candidate-input"><header><div><small>추가 검토 중인 사업안</small><strong>{request.candidateDisplayName ?? '사업안 세부 검토'}</strong></div>{request.candidateOneLineSummary && <p>{request.candidateOneLineSummary}</p>}</header><div><strong>확인이 필요한 내용</strong><p>{request.question ?? request.safeSummary ?? '실제 사업정보를 확인해 주세요.'}</p></div>{request.reason && <div><strong>왜 필요한가</strong><p>{request.reason}</p></div>}{unresolved ? <div role="alert"><strong>필요한 사업정보 항목을 자동으로 특정하지 못했습니다.</strong><p>{(request.unknownFacts ?? []).join(' ') || '질문 내용을 다시 확인해야 합니다.'}</p><button type="button" disabled={busy} onClick={onExplore}>다른 방향 다시 탐색</button></div> : <><div className="candidate-input__fields">{options.map((field) => { const contract = CANDIDATE_FACT_FIELDS[field]; return <label key={field}><strong>{contract.label}</strong><span>{contract.type === 'list' ? '한 줄에 한 항목씩 입력해 주세요. 해당 사항이 없으면 ‘해당 없음’을 입력할 수 있습니다.' : '실제 사업 사실을 입력해 주세요.'}</span><textarea aria-label={contract.label} value={draft.values?.[field] ?? ''} onChange={(event) => onDraft({ values: { ...(draft.values ?? {}), [field]: event.target.value } })} /></label>; })}</div><div className="candidate-input__actions"><button type="button" disabled={busy || !payload} onClick={onSubmit}>정보 제출</button><button type="button" disabled={busy} onClick={onExplore}>다른 방향 다시 탐색</button></div></>}</section>;
}

function InputGroup({ title, description, requests, drafts, onDraft, onSubmit, onRetry, onExplore, busy }) { return <section className="business-proposal__input-first"><h2>{title}</h2>{description && <p>{description}</p>}{requests.map((request) => <CandidateInput key={request.inputRequestId} request={request} draft={drafts[request.inputRequestId] ?? createCandidateDraft(request)} onDraft={(next) => onDraft(request, next)} onSubmit={() => onSubmit(request)} onRetry={() => onRetry(request.inputRequestId)} onExplore={onExplore} busy={busy} />)}</section>; }

function Comparison({ concepts, onSelect, busy }) { if (!canOpenComparison(concepts.map((item) => item.conceptId))) return <section className="proposal-comparison"><h2>사업안 비교</h2><p>비교할 사업안을 2개 이상, 최대 3개까지 선택해 주세요. 비교하지 않고도 바로 선택할 수 있습니다.</p></section>; const rows = comparisonRows(concepts); return <section className="proposal-comparison"><h2>사업안 비교</h2><div className="proposal-comparison__matrix" style={{ '--proposal-columns': concepts.length }}><div className="proposal-comparison__corner">비교 항목</div>{concepts.map((concept) => <header key={concept.conceptId}><strong>{concept.conceptName}</strong><button type="button" disabled={busy} onClick={() => onSelect(concept.conceptId)}>이 사업안 선택</button></header>)}{rows.flatMap((row) => [<strong className="proposal-comparison__label" key={`${row.label}-label`}>{row.label}</strong>, ...row.values.map((value, index) => <p key={`${row.label}-${concepts[index].conceptId}`}>{value}</p>)])}</div></section>; }

export function HypothesisField({ type, value, edit, onEdit, onAlternative, disabled }) {
  const locked = value?.locked;
  const source = value?.finalValue ?? value?.proposedValue;
  const current = edit ?? source;
  const updateObject = (field, next) => onEdit({ ...(typeof current === 'object' && current ? current : {}), [field]: next });
  let editor;
  if (type === 'PRE_MARKET_SOM_SHARE') editor = <div className="hypothesis-structured"><label>목표 점유율 (%)<input type="number" min="0.01" step="0.01" disabled={locked || disabled} value={current?.targetSharePercent ?? ''} onChange={(event) => updateObject('targetSharePercent', Number(event.target.value))} /></label><label>기간 (년)<input type="number" min="1" step="1" disabled={locked || disabled} value={current?.horizonYears ?? ''} onChange={(event) => updateObject('horizonYears', Number(event.target.value))} /></label><label>가정 근거<textarea disabled={locked || disabled} value={(current?.assumptions ?? []).join('\n')} onChange={(event) => updateObject('assumptions', event.target.value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean))} /></label></div>;
  else if (type === 'PRE_MARKET_SOM') editor = <div className="hypothesis-structured"><label>규모<input type="number" min="0" disabled={locked || disabled} value={current?.amount ?? ''} onChange={(event) => updateObject('amount', Number(event.target.value))} /></label><label>통화<input disabled={locked || disabled} value={current?.currency ?? ''} onChange={(event) => updateObject('currency', event.target.value)} /></label><label>기간<input disabled={locked || disabled} value={current?.period ?? ''} onChange={(event) => updateObject('period', event.target.value)} /></label><label>계산 기준<textarea disabled={locked || disabled} value={current?.calculationBasis ?? ''} onChange={(event) => updateObject('calculationBasis', event.target.value)} /></label><label>가정 근거<textarea disabled={locked || disabled} value={(current?.assumptions ?? []).join('\n')} onChange={(event) => updateObject('assumptions', event.target.value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean))} /></label></div>;
  else editor = <textarea aria-label={HYPOTHESIS_LABELS[type]} disabled={locked || disabled} value={edit ?? hypothesisValueText(source)} onChange={(event) => onEdit(event.target.value)} />;
  return <article><header><strong>{HYPOTHESIS_LABELS[type]}</strong><span>{hypothesisDecisionLabel(value)}</span></header><p className="hypothesis-value-preview">{hypothesisDisplay(type, current)}</p>{editor}{!locked && <button type="button" disabled={disabled} onClick={onAlternative}>다른 값 제안받기</button>}</article>;
}

const asList = (value) => Array.isArray(value) ? value : value == null || value === '' ? [] : [value];
function BulletSection({ title, values, empty = '해당 사항이 없습니다.' }) { const items = asList(values); return <article><h3>{title}</h3>{items.length === 0 ? <p>{empty}</p> : <ul>{items.map((item, index) => <li key={`${title}-${index}`}>{typeof item === 'string' ? item : item.safeSummary ?? item.title ?? String(item)}</li>)}</ul>}</article>; }
function FlowSection({ title, values }) { const items = asList(values); return <article><h3>{title}</h3>{items.length === 0 ? <p>확인된 흐름이 없습니다.</p> : <ol className="legal-flow">{items.map((item, index) => <li key={`${title}-${index}`}><span>{index + 1}</span>{typeof item === 'string' ? item : item.safeSummary ?? item.title ?? String(item)}</li>)}</ol>}</article>; }
function EvidenceSection({ values }) { const items = asList(values); return <article className="legal-evidence"><h3>공식 법률 근거</h3>{items.length === 0 ? <p>표시할 공식 근거가 없습니다.</p> : <div>{items.map((item, index) => <section key={item.contentHash ?? item.officialSourceUri ?? index}><strong>{item.lawName ?? item.title ?? '공식 근거'}</strong>{item.articleReference && <span>{item.articleReference}</span>}{item.title && item.lawName && <p>{item.title}</p>}{item.boundedProvisionSummary && <p>{item.boundedProvisionSummary}</p>}{item.effectiveDate && <small>시행일 {item.effectiveDate}</small>}{item.officialSourceUri && <a href={item.officialSourceUri} target="_blank" rel="noreferrer">법령 원문 보기</a>}</section>)}</div>}</article>; }

export function LegalReport({ report }) {
  const body = report.report ?? {};
  const rawConclusion = body.finalLegalConclusion;
  const conclusion = rawConclusion && typeof rawConclusion === 'object'
    ? rawConclusion : { safeSummary: rawConclusion };
  const sourcePartial = conclusion.legalSourceStatus === 'SOURCE_PARTIAL'
    || conclusion.sourceStatus === 'SOURCE_PARTIAL'
    || conclusion.evidenceDiagnostics?.coverageStatus === 'SOURCE_PARTIAL';
  const roles = body.businessRoles ?? {};
  const roleRows = [['플랫폼 역할', roles.platformRole], ['판매 주체', roles.sellerRole], ['서비스 제공 주체', roles.providerRole], ['중개 주체', roles.intermediaryRole]].filter(([, value]) => value);
  const advertising = body.advertisingExpressionCautions ?? {};
  const delta = asList(body.deltaLegalHistory);
  return <section className="final-legal-report"><header><div><p>FINAL LEGAL REGULATORY REPORT</p><h2>최종 법률·규제 보고서</h2></div><span>검토 기준일 {report.basisDate}</span></header>
    <article className="legal-conclusion"><h3>결론</h3><strong>{conclusion.productionStatus ?? conclusion.route ?? '검토 완료'}</strong><p>{conclusion.safeSummary ?? '최종 법률·규제 검토 결과가 확정되었습니다.'}</p>{sourcePartial && <div role="alert">공식 근거를 확인했지만 일부 법률 소스의 조회 범위에는 제한이 있습니다.</div>}</article>
    <article><h3>사업 구조와 역할</h3>{roleRows.length === 0 ? <p>표시할 역할 정보가 없습니다.</p> : <dl>{roleRows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>}</article>
    <BulletSection title="반드시 해야 할 조치" values={body.requiredControls} />
    <BulletSection title="필수 고지" values={body.requiredDisclosures} />
    <BulletSection title="파트너·자격·인허가" values={[...asList(body.partnerRequirements), ...asList(body.qualificationRequirements), ...asList(body.requiredPartnersAndQualifications)]} />
    <BulletSection title="개인정보 이용" values={body.personalDataUsage} />
    <BulletSection title="물리 활동" values={body.physicalActivities} />
    <FlowSection title="거래 흐름" values={body.transactionFlow} />
    <FlowSection title="결제·수취 흐름" values={body.paymentFlow} />
    <BulletSection title="금지·회피 형태" values={body.prohibitedVariants} />
    <BulletSection title="허용 가능한 광고·표현" values={advertising.allowedClaims} />
    <BulletSection title="광고·표현 시 필수 고지" values={advertising.requiredDisclosures} />
    <BulletSection title="아직 확인되지 않은 사항" values={body.unknownFacts} />
    <EvidenceSection values={body.officialEvidenceReferences} />
    <article><h3>변경사항 법률·규제 재검토 이력</h3>{delta.length === 0 ? <p>이번 확정 과정에서 법률·규제 재검토가 필요한 변경은 없었습니다.</p> : <ol>{delta.map((item, index) => <li key={item.reviewToken ?? index}>{item.legalReview?.safeSummary ?? item.safeSummary ?? item.status ?? `재검토 ${index + 1}`}</li>)}</ol>}</article>
    {body.sourceHashes && <details className="legal-technical"><summary>정본 검증 정보</summary><dl>{Object.entries(body.sourceHashes).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl></details>}
  </section>;
}
