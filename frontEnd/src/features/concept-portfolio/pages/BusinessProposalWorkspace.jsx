import { useMemo, useState } from 'react';
import { Link, useOutletContext, useParams } from 'react-router-dom';

import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import {
  HYPOTHESIS_LABELS, HYPOTHESIS_TYPES, canOpenComparison, openCandidateRequests,
  selectedConceptId, toggleComparedConcept,
} from '../businessProposalModel.js';
import { useConceptPortfolio } from '../hooks/useConceptPortfolio.js';
import '../styles/business-proposal.css';

const valueText = (value) => typeof value === 'string' ? value : JSON.stringify(value ?? '', null, 2);
const parseValue = (value) => { try { return JSON.parse(value); } catch { return value; } };
const confirmedFacts = (request, value) => {
  const fields = Array.isArray(request.affectedFields) ? request.affectedFields
    : request.affectedFields && typeof request.affectedFields === 'object' ? Object.keys(request.affectedFields) : [];
  const unknown = Array.isArray(request.unknownFacts) ? request.unknownFacts.filter((item) => typeof item === 'string') : [];
  return { [fields[0] ?? unknown[0] ?? 'actualBusinessFact']: value };
};

export default function BusinessProposalWorkspace({ initialMode = 'list' }) {
  const { projectId } = useParams();
  const outlet = useOutletContext() ?? {};
  const portfolio = useConceptPortfolio(projectId, outlet.liveRevision);
  const [mode, setMode] = useState(initialMode);
  const [compared, setCompared] = useState([]);
  const [facts, setFacts] = useState({});
  const [edits, setEdits] = useState({});
  const selectedId = selectedConceptId(portfolio.selection);
  const comparedConcepts = portfolio.concepts.filter((concept) => compared.includes(concept.conceptId));
  const openInputs = openCandidateRequests(portfolio.inputRequests);
  const unmatchedInputs = openInputs.filter((request) => !portfolio.concepts.some((concept) => concept.candidateId === request.candidateId));
  const hypothesisMap = useMemo(() => Object.fromEntries(portfolio.hypotheses.map((item) => [item.hypothesisType, item])), [portfolio.hypotheses]);

  if (portfolio.loading) return <main className="business-proposal" aria-busy="true"><p>검토된 사업안을 불러오고 있습니다.</p></main>;
  return <main className="business-proposal">
    <header className="business-proposal__hero">
      <div><p>BUSINESS PROPOSAL</p><h1>검토된 사업안</h1><span>법률·규제 검토를 통과한 사업안은 1개부터 5개까지 모두 정상 결과입니다.</span></div>
      <div className="business-proposal__mode"><button type="button" aria-pressed={mode === 'list'} onClick={() => setMode('list')}>사업안 목록</button><button type="button" aria-pressed={mode === 'compare'} onClick={() => setMode('compare')}>비교</button></div>
    </header>

    {portfolio.error && <section className="business-proposal__error" role="alert"><span>{getUserErrorMessage(portfolio.error)}</span><button type="button" onClick={portfolio.refresh}>다시 시도</button></section>}
    {!portfolio.run && <section className="business-proposal__empty"><h2>사업안 검토를 시작할 수 있습니다.</h2><p>확정된 아이디어를 바탕으로 최대 5개의 사업안을 검토합니다.</p><button type="button" disabled={portfolio.busy} onClick={portfolio.start}>사업안 검토 시작</button></section>}
    {portfolio.run && <PortfolioStatus run={portfolio.run} />}
    {portfolio.selection && portfolio.concepts.some((concept) => concept.conceptId !== selectedId)
      && <p className="business-proposal__notice" role="status">추가 사업안이 준비되었습니다. 현재 선택은 유지됩니다.</p>}

    {mode === 'compare' && <Comparison concepts={comparedConcepts} onSelect={portfolio.select} busy={portfolio.busy} />}
    {portfolio.concepts.length > 0 && <section className="proposal-grid" aria-label="사업안 목록">
      {portfolio.concepts.map((concept) => <ProposalCard key={concept.conceptId} concept={concept}
        selected={concept.conceptId === selectedId} compared={compared.includes(concept.conceptId)}
        compareDisabled={!compared.includes(concept.conceptId) && compared.length >= 3}
        requests={openCandidateRequests(portfolio.inputRequests, concept.candidateId)}
        facts={facts} setFacts={setFacts} onRespond={portfolio.respond}
        onCompare={() => setCompared((value) => toggleComparedConcept(value, concept.conceptId))}
        onSelect={() => portfolio.select(concept.conceptId)} busy={portfolio.busy} />)}
    </section>}
    {portfolio.concepts.length > 0 && unmatchedInputs.length > 0 && <section className="business-proposal__input-first"><h2>추가 검토 중인 사업안</h2><p>아래 정보는 기존에 검토 완료된 사업안의 선택을 막지 않습니다.</p>{unmatchedInputs.map((request) => <CandidateInput key={request.inputRequestId} request={request} value={facts[request.inputRequestId] ?? ''} onChange={(value) => setFacts((current) => ({ ...current, [request.inputRequestId]: value }))} onSubmit={() => portfolio.respond(request.inputRequestId, confirmedFacts(request, facts[request.inputRequestId]), facts[request.inputRequestId])} busy={portfolio.busy} />)}</section>}
    {portfolio.concepts.length === 0 && openInputs.length > 0 && <InputFirst requests={openInputs} facts={facts} setFacts={setFacts} onRespond={portfolio.respond} busy={portfolio.busy} />}

    {portfolio.selection && <section className="validation-assumptions">
      <header><p>다음 분석에 사용할 검증 가정을 확인해 주세요.<br />현재 사업안을 바탕으로 AI가 제안한 값입니다. 실제 계획과 다르면 수정할 수 있습니다.</p><span>{portfolio.selection.activeTaskRunId ? '처리 중 · 잠시 기다려 주세요' : `${portfolio.selection.hypothesisConfirmedCount}/7 확인`}</span></header>
      <div>{HYPOTHESIS_TYPES.map((type) => <HypothesisField key={type} type={type} value={hypothesisMap[type]}
        edit={edits[type]} onEdit={(next) => setEdits((current) => ({ ...current, [type]: next }))}
        onAlternative={() => portfolio.alternative(type)} disabled={portfolio.busy || Boolean(portfolio.selection.activeTaskRunId)} />)}</div>
      {portfolio.selection.deltaLegalStatus === 'PENDING' || portfolio.selection.status === 'DELTA_LEGAL_PENDING'
        ? <p role="status">변경사항의 법률·규제 영향을 다시 확인하고 있습니다.</p> : null}
      <div className="validation-assumptions__actions">
        <button type="button" disabled={portfolio.busy || Boolean(portfolio.selection.activeTaskRunId)} onClick={() => portfolio.confirm(Object.fromEntries(HYPOTHESIS_TYPES.map((type) => [type, parseValue(edits[type] ?? valueText(hypothesisMap[type]?.finalValue ?? hypothesisMap[type]?.proposedValue))])))}>7개 검증 가정 확인</button>
        {portfolio.selection.nextAction === 'REVIEW_LEGAL_REPORT' && <button type="button" onClick={portfolio.finalizeReport}>최종 법률·규제 보고서 확정</button>}
        {portfolio.selection.nextAction === 'FINALIZE_MARKET_SEED' && <button type="button" onClick={portfolio.finalizeMarketSeed}>다음 분석 준비</button>}
      </div>
    </section>}
    {portfolio.report && <LegalReport report={portfolio.report} />}
    {portfolio.selection?.status === 'READY_FOR_MARKET' && <section className="business-proposal__ready"><strong>다음 분석 준비 완료</strong><span>확정된 사업안과 검증 가정, 최종 법률 결과가 Market Seed에 고정되었습니다.</span><Link to={projectRoutes.market(projectId)}>시장 분석으로 이동</Link></section>}
  </main>;
}

function PortfolioStatus({ run }) {
  const active = Boolean(run.activeTaskRunId);
  return <section className="portfolio-status"><strong>{active ? '사업안을 검토하고 있습니다.' : '검토 완료'}</strong><span>{run.producedConceptCount ?? 0}개 사업안 · 추가정보 {run.openInputCount ?? 0}건</span></section>;
}

function ProposalCard({ concept, selected, compared, compareDisabled, requests, facts, setFacts, onRespond, onCompare, onSelect, busy }) {
  return <article className="proposal-card" data-selected={selected}>
    <header><div><h2>{concept.conceptName}</h2><span>{selected ? '현재 선택' : '선택 가능'}</span></div><label><input type="checkbox" checked={compared} disabled={compareDisabled} onChange={onCompare} /> 비교에 담기</label></header>
    <p>{concept.summary}</p><LegalSummary review={concept.legalReview} />
    {requests.map((request) => <CandidateInput key={request.inputRequestId} request={request} value={facts[request.inputRequestId] ?? ''} onChange={(value) => setFacts((current) => ({ ...current, [request.inputRequestId]: value }))} onSubmit={() => onRespond(request.inputRequestId, confirmedFacts(request, facts[request.inputRequestId]), facts[request.inputRequestId])} busy={busy} />)}
    <button type="button" className="proposal-card__select" disabled={busy || selected} onClick={onSelect}>{selected ? '선택됨' : '이 사업안 선택'}</button>
  </article>;
}

function LegalSummary({ review }) { const value = review ?? {}; return <section className="legal-summary"><strong>선택 전 법률·규제 요약</strong><p>{value.safeSummary ?? value.summary ?? value.conclusion ?? '검토 결과가 사업안에 반영되었습니다.'}</p></section>; }
function CandidateInput({ request, value, onChange, onSubmit, busy }) { return <section className="candidate-input"><strong>추가 사업정보가 필요합니다.</strong><p>{request.question}</p><textarea value={value} onChange={(event) => onChange(event.target.value)} /><button type="button" disabled={busy || !value.trim()} onClick={onSubmit}>정보 제출</button></section>; }
function InputFirst(props) { return <section className="business-proposal__input-first"><h2>사업안을 완성하려면 실제 사업정보가 필요합니다.</h2>{props.requests.map((request) => <CandidateInput key={request.inputRequestId} request={request} value={props.facts[request.inputRequestId] ?? ''} onChange={(value) => props.setFacts((current) => ({ ...current, [request.inputRequestId]: value }))} onSubmit={() => props.onRespond(request.inputRequestId, confirmedFacts(request, props.facts[request.inputRequestId]), props.facts[request.inputRequestId])} busy={props.busy} />)}</section>; }

function Comparison({ concepts, onSelect, busy }) {
  if (!canOpenComparison(concepts.map((item) => item.conceptId))) return <section className="proposal-comparison"><h2>사업안 비교</h2><p>비교할 사업안을 2개 이상, 최대 3개까지 선택해 주세요. 비교하지 않고도 바로 선택할 수 있습니다.</p></section>;
  return <section className="proposal-comparison"><h2>사업안 비교</h2><div>{concepts.map((concept) => <article key={concept.conceptId}><h3>{concept.conceptName}</h3><p>{concept.summary}</p><button type="button" disabled={busy} onClick={() => onSelect(concept.conceptId)}>이 사업안 선택</button></article>)}</div></section>;
}

function HypothesisField({ type, value, edit, onEdit, onAlternative, disabled }) {
  const locked = value?.locked;
  const current = edit ?? valueText(value?.finalValue ?? value?.proposedValue);
  return <article><header><strong>{HYPOTHESIS_LABELS[type]}</strong><span>{locked ? '고정된 값' : value?.decisionStatus === 'CONFIRMED' ? '확인됨' : '제안값'}</span></header><textarea aria-label={HYPOTHESIS_LABELS[type]} disabled={locked || disabled} value={current} onChange={(event) => onEdit(event.target.value)} />{!locked && <button type="button" disabled={disabled} onClick={onAlternative}>다른 값 제안받기</button>}</article>;
}

const REPORT_SECTIONS = [
  ['최종 결론', ['finalConclusion', 'conclusion']], ['사업자 역할', ['businessRoles', 'operatorRoles']],
  ['거래/결제 흐름', ['transactionAndPaymentFlow', 'transactionFlow']], ['개인정보', ['personalInformation', 'privacy']],
  ['물리 활동', ['physicalActivities']], ['파트너/자격/인허가', ['partnersQualificationsLicenses', 'licenses']],
  ['필수 통제', ['requiredControls']], ['필수 고지', ['requiredDisclosures']], ['금지/회피 형태', ['prohibitedPatterns']],
  ['광고·표현 주의', ['advertisingCautions']], ['미확정 사실', ['unresolvedFacts']], ['공식 근거', ['officialEvidence']],
  ['Delta 변경 이력', ['deltaHistory', 'deltaLegalHistory']],
];
export function LegalReport({ report }) { const body = report.report ?? {}; return <section className="final-legal-report"><header><div><p>FINAL LEGAL REGULATORY REPORT</p><h2>최종 법률·규제 보고서</h2></div><span>검토 기준일 {report.basisDate}</span></header>{REPORT_SECTIONS.map(([label, keys]) => { const value = keys.map((key) => body[key]).find((item) => item != null); return <article key={label}><h3>{label}</h3><pre>{value == null ? '해당 없음' : valueText(value)}</pre></article>; })}</section>; }
