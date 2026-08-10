import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useOutletContext, useParams } from 'react-router-dom';

import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import {
  CANDIDATE_FACT_FIELDS, HYPOTHESIS_LABELS, HYPOTHESIS_TYPES, buildHypothesisChanges,
  canOpenComparison, candidateDefaultField, candidateFieldOptions, candidateRequests,
  hypothesisDecisionLabel, hypothesisValueText, portfolioRunPresentation,
  selectedConceptId, serializeCandidateFact, toggleComparedConcept,
} from '../businessProposalModel.js';
import { useConceptPortfolio } from '../hooks/useConceptPortfolio.js';
import '../styles/business-proposal.css';

export default function BusinessProposalWorkspace({ initialMode = 'list' }) {
  const { projectId } = useParams();
  const outlet = useOutletContext() ?? {};
  const portfolio = useConceptPortfolio(projectId, outlet.liveRevision);
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

  const draft = (request) => drafts[request.inputRequestId] ?? {
    field: candidateDefaultField(request), value: '',
  };
  const updateDraft = (request, next) => setDrafts((current) => ({
    ...current, [request.inputRequestId]: { ...draft(request), ...next },
  }));
  const submitInput = (request) => {
    const current = draft(request);
    const payload = serializeCandidateFact(current.field, current.value);
    if (payload) portfolio.respond(request.inputRequestId, payload, current.value);
  };

  if (portfolio.loading) return <main className="business-proposal" aria-busy="true"><p>검토된 사업안을 불러오고 있습니다.</p></main>;
  return <main className="business-proposal">
    <header className="business-proposal__hero">
      <div><p>BUSINESS PROPOSAL</p><h1>검토된 사업안</h1><span>법률·규제 검토를 통과한 사업안은 1개부터 5개까지 모두 정상 결과입니다.</span></div>
      <div className="business-proposal__mode"><button type="button" aria-pressed={mode === 'list'} onClick={() => setMode('list')}>사업안 목록</button><button type="button" aria-pressed={mode === 'compare'} onClick={() => setMode('compare')}>비교</button></div>
    </header>

    {portfolio.error && <section className="business-proposal__error" role="alert"><span>{getUserErrorMessage(portfolio.error)}</span><button type="button" onClick={portfolio.refresh}>다시 시도</button></section>}
    {!portfolio.run && <section className="business-proposal__empty"><h2>사업안 검토를 시작할 수 있습니다.</h2><p>확정된 아이디어를 바탕으로 최대 5개의 사업안을 검토합니다.</p><button type="button" disabled={portfolio.busy} onClick={portfolio.start}>사업안 검토 시작</button></section>}
    {portfolio.run && <PortfolioStatus run={portfolio.run} busy={portfolio.busy} onRestart={portfolio.start} />}
    {recoveredNotice && <p className="business-proposal__notice" role="status">추가 사업안이 준비되었습니다. 현재 선택은 유지됩니다.</p>}

    {mode === 'compare' && <Comparison concepts={comparedConcepts} onSelect={portfolio.select} busy={portfolio.busy} />}
    {portfolio.concepts.length > 0 && <section className="proposal-grid" aria-label="사업안 목록">
      {portfolio.concepts.map((concept) => <ProposalCard key={concept.conceptId} concept={concept}
        selected={concept.conceptId === selectedId} compared={compared.includes(concept.conceptId)}
        compareDisabled={!compared.includes(concept.conceptId) && compared.length >= 3}
        requests={candidateRequests(portfolio.inputRequests, concept.candidateId)}
        drafts={drafts} onDraft={updateDraft} onRespond={submitInput} onRetry={portfolio.retryContinuation}
        onCompare={() => setCompared((value) => toggleComparedConcept(value, concept.conceptId))}
        onSelect={() => portfolio.select(concept.conceptId)} busy={portfolio.busy} />)}
    </section>}
    {portfolio.concepts.length > 0 && unmatchedInputs.length > 0 && <InputGroup title="추가 검토 중인 사업안" description="아래 정보는 검토 완료된 다른 사업안의 선택을 막지 않습니다." requests={unmatchedInputs} drafts={drafts} onDraft={updateDraft} onSubmit={submitInput} onRetry={portfolio.retryContinuation} busy={portfolio.busy} />}
    {portfolio.concepts.length === 0 && actionableInputs.length > 0 && <InputGroup title="사업안을 완성하려면 실제 사업정보가 필요합니다." requests={actionableInputs} drafts={drafts} onDraft={updateDraft} onSubmit={submitInput} onRetry={portfolio.retryContinuation} busy={portfolio.busy} />}

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

export function PortfolioStatus({ run, busy, onRestart }) {
  const view = portfolioRunPresentation(run);
  return <section className="portfolio-status"><div><strong>{view.title}</strong>{view.detail && <span>{view.detail}</span>}</div><span>{run.producedConceptCount ?? 0}개 사업안 · 추가정보 {run.openInputCount ?? 0}건</span>{view.restart && <button type="button" disabled={busy} onClick={onRestart}>{view.action}</button>}</section>;
}

function ProposalCard({ concept, selected, compared, compareDisabled, requests, drafts, onDraft, onRespond, onRetry, onCompare, onSelect, busy }) {
  return <article className="proposal-card" data-selected={selected}><header><div><h2>{concept.conceptName}</h2><span>{selected ? '현재 선택' : '선택 가능'}</span></div><label><input type="checkbox" checked={compared} disabled={compareDisabled} onChange={onCompare} /> 비교에 담기</label></header><p>{concept.summary}</p><LegalSummary review={concept.legalReview} />{requests.map((request) => <CandidateInput key={request.inputRequestId} request={request} draft={drafts[request.inputRequestId] ?? { field: candidateDefaultField(request), value: '' }} onDraft={(next) => onDraft(request, next)} onSubmit={() => onRespond(request)} onRetry={() => onRetry(request.inputRequestId)} busy={busy} />)}<button type="button" className="proposal-card__select" disabled={busy || selected} onClick={onSelect}>{selected ? '선택됨' : '이 사업안 선택'}</button></article>;
}

function LegalSummary({ review }) { const value = review ?? {}; return <section className="legal-summary"><strong>선택 전 법률·규제 요약</strong><p>{value.safeSummary ?? value.summary ?? value.conclusion ?? '검토 결과가 사업안에 반영되었습니다.'}</p></section>; }

export function CandidateInput({ request, draft, onDraft, onSubmit, onRetry, busy }) {
  if (request.status === 'ANSWERED' && request.nextAction === 'RETRY_CONTINUATION') return <section className="candidate-input"><strong>제출한 정보의 반영을 완료하지 못했습니다.</strong><p>같은 정보를 다시 입력하지 않고 반영 작업만 다시 시도합니다.</p><button type="button" disabled={busy} onClick={onRetry}>추가 사업정보 반영 다시 시도</button></section>;
  const options = candidateFieldOptions(request);
  const selectedField = draft.field;
  const contract = CANDIDATE_FACT_FIELDS[selectedField];
  const payload = serializeCandidateFact(selectedField, draft.value);
  return <section className="candidate-input"><strong>추가 사업정보가 필요합니다.</strong><p>{request.question}</p>{candidateDefaultField(request) ? <p>답변 항목: {CANDIDATE_FACT_FIELDS[candidateDefaultField(request)].label}</p> : <label>답변할 사업정보<select aria-label="답변할 사업정보" value={selectedField} onChange={(event) => onDraft({ field: event.target.value, value: '' })}><option value="">항목을 선택해 주세요</option>{options.map((field) => <option key={field} value={field}>{CANDIDATE_FACT_FIELDS[field].label}</option>)}</select></label>}<label>{contract?.type === 'list' ? '한 줄에 한 항목씩 입력해 주세요.' : '실제 사업 사실을 입력해 주세요.'}<textarea value={draft.value} onChange={(event) => onDraft({ value: event.target.value })} /></label><button type="button" disabled={busy || !payload} onClick={onSubmit}>정보 제출</button></section>;
}

function InputGroup({ title, description, requests, drafts, onDraft, onSubmit, onRetry, busy }) { return <section className="business-proposal__input-first"><h2>{title}</h2>{description && <p>{description}</p>}{requests.map((request) => <CandidateInput key={request.inputRequestId} request={request} draft={drafts[request.inputRequestId] ?? { field: candidateDefaultField(request), value: '' }} onDraft={(next) => onDraft(request, next)} onSubmit={() => onSubmit(request)} onRetry={() => onRetry(request.inputRequestId)} busy={busy} />)}</section>; }

function Comparison({ concepts, onSelect, busy }) { if (!canOpenComparison(concepts.map((item) => item.conceptId))) return <section className="proposal-comparison"><h2>사업안 비교</h2><p>비교할 사업안을 2개 이상, 최대 3개까지 선택해 주세요. 비교하지 않고도 바로 선택할 수 있습니다.</p></section>; return <section className="proposal-comparison"><h2>사업안 비교</h2><div>{concepts.map((concept) => <article key={concept.conceptId}><h3>{concept.conceptName}</h3><p>{concept.summary}</p><button type="button" disabled={busy} onClick={() => onSelect(concept.conceptId)}>이 사업안 선택</button></article>)}</div></section>; }

export function HypothesisField({ type, value, edit, onEdit, onAlternative, disabled }) {
  const locked = value?.locked;
  const current = edit ?? hypothesisValueText(value?.finalValue ?? value?.proposedValue);
  return <article><header><strong>{HYPOTHESIS_LABELS[type]}</strong><span>{hypothesisDecisionLabel(value)}</span></header><textarea aria-label={HYPOTHESIS_LABELS[type]} disabled={locked || disabled} value={current} onChange={(event) => onEdit(event.target.value)} />{!locked && <button type="button" disabled={disabled} onClick={onAlternative}>다른 값 제안받기</button>}</article>;
}

const REPORT_SECTIONS = [
  ['최종 결론', [['finalLegalConclusion', '최종 결론']]],
  ['사업자 역할', [['businessRoles', '사업자 역할']]],
  ['거래/결제 흐름', [['transactionFlow', '거래 흐름'], ['paymentFlow', '결제·수취 흐름']]],
  ['개인정보', [['personalDataUsage', '개인정보 이용']]],
  ['물리 활동', [['physicalActivities', '물리 활동']]],
  ['파트너/자격/인허가', [['partnerRequirements', '파트너 요건'], ['qualificationRequirements', '자격·인허가 요건'], ['requiredPartnersAndQualifications', '필수 파트너·자격']]],
  ['필수 통제', [['requiredControls', '필수 통제']]],
  ['필수 고지', [['requiredDisclosures', '필수 고지']]],
  ['금지/회피 형태', [['prohibitedVariants', '금지·회피 형태']]],
  ['광고·표현 주의', [['advertisingExpressionCautions', '광고·표현 주의']]],
  ['미확정 사실', [['unknownFacts', '미확정 사실']]],
  ['공식 근거', [['officialEvidenceReferences', '공식 근거']]],
  ['Delta 변경 이력', [['deltaLegalHistory', 'Delta 변경 이력']]],
  ['정본 해시', [['sourceHashes', '정본 해시']]],
];

export function LegalReport({ report }) {
  const body = report.report ?? {};
  return <section className="final-legal-report"><header><div><p>FINAL LEGAL REGULATORY REPORT</p><h2>최종 법률·규제 보고서</h2></div><span>검토 기준일 {report.basisDate}</span></header>{REPORT_SECTIONS.map(([label, fields]) => { const present = fields.filter(([key]) => body[key] != null); return <article key={label}><h3>{label}</h3>{present.length === 0 ? <p>해당 없음</p> : present.map(([key, fieldLabel]) => <div key={key}><strong>{fieldLabel}</strong><pre>{hypothesisValueText(body[key])}</pre></div>)}</article>; })}</section>;
}
