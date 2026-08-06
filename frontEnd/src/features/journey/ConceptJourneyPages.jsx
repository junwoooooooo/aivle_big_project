import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { createJourneyApi } from './journeyApi.js';
import { journeyFailureMessage } from './journeyFailure.js';
import './journey.css';

function useConceptApi() {
  const { projectId } = useParams();
  const client = useApiClient();
  return { projectId, api: useMemo(() => createJourneyApi(client, projectId), [client, projectId]) };
}

function ErrorBanner({ error }) { return error ? <div className="journey-error" role="alert"><strong>요청을 완료하지 못했습니다.</strong><span>{error}</span><button type="button" onClick={() => window.location.reload()}>현재 단계 다시 불러오기</button></div> : null; }

function Busy({ children }) {
  return <div className="journey-overlay" role="status"><span className="journey-spinner" /><strong>{children}</strong></div>;
}

function List({ items }) {
  return items?.length ? <ul>{items.map((item, index) => <li key={index}>{item}</li>)}</ul> : <p className="journey-muted">등록된 항목이 없습니다.</p>;
}

function ConceptCard({ concept, selected, onSelect }) {
  const [details, setDetails] = useState(false);
  return <article className={`journey-card concept-card ${selected ? 'is-selected' : ''}`}>
    {onSelect && <label className="concept-check"><input type="checkbox" checked={selected} onChange={() => onSelect(concept.id)} /> Shortlist</label>}
    <span className="journey-badge">후보 {concept.displayOrder}</span><h3>{concept.name}</h3><p className="concept-summary">{concept.oneLineSummary}</p>
    <dl><div><dt>대상 고객</dt><dd>{concept.targetCustomer}</dd></div><div><dt>핵심 가치</dt><dd>{concept.valueProposition}</dd></div><div><dt>수익 모델</dt><dd>{concept.revenueModel}</dd></div></dl>
    <section><strong>주요 위험</strong><List items={concept.risks} /></section>
    {concept.eligibilityStatus === 'ELIGIBLE' && <><button className="journey-button secondary" type="button" onClick={() => setDetails((value) => !value)}>{details ? '검증 내역 닫기' : 'Origin·Guardrail 반영 내역'}</button>{details && <div className="concept-trace"><section><strong>Origin 보존</strong>{(concept.originTrace || []).map((trace, index) => <p key={`origin-${index}`}><b>{trace.structureKey}</b> · {JSON.stringify(trace.conceptValue)}</p>)}</section><section><strong>Legal Guardrail 반영</strong>{(concept.legalTrace || []).map((trace, index) => <p key={`legal-${index}`}><b>{trace.guardrailType}</b> · {trace.constraint}<br />{trace.implementation}</p>)}</section><section><strong>새 사업 활동</strong><List items={concept.newBusinessActivities} /></section></div>}</>}
  </article>;
}

export function ConceptGenerationPage() {
  const { projectId, api } = useConceptApi();
  const [legal, setLegal] = useState(null); const [batch, setBatch] = useState(null); const [concepts, setConcepts] = useState([]);
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const load = async () => { const [l,b,c]=await Promise.all([api.currentLegalPrecheck(),api.currentConceptGeneration(),api.concepts()]);setLegal(l);setBatch(b);setConcepts(c||[]); };
  // Loading is the external synchronization performed by this effect.
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { let active = true; load().catch((e) => active && setError(getUserErrorMessage(e))); return () => { active = false; }; }, [api]); // eslint-disable-line react-hooks/exhaustive-deps
  const activeBatch = ['GENERATING','VALIDATING_ORIGIN','VALIDATING_LEGAL'].includes(batch?.state);
  useEffect(() => { if(!activeBatch)return undefined;const timer=window.setInterval(()=>load().catch((e)=>setError(getUserErrorMessage(e))),2000);return()=>window.clearInterval(timer); }, [activeBatch,batch?.id]); // eslint-disable-line react-hooks/exhaustive-deps
  const allowed = !!legal?.version?.conceptBuilderAllowed && !legal?.stale;
  const failedBatch = batch?.state === 'FAILED' && !batch?.stale;
  const canGenerate = !batch || batch.stale;
  async function generate() { setBusy(true); setError(''); try { const created=await api.generateConcepts();setBatch(created);setConcepts(created?.concepts || []); } catch (e) { setError(getUserErrorMessage(e)); } finally { setBusy(false); } }
  return <div className="journey-page concept-page">
    {(busy || activeBatch) && <Busy>초안 생성 → Origin 검증 → 법률 검증 → 필요한 대체 후보 생성을 진행합니다.</Busy>}
    <header className="journey-page__heading"><div><span>3단계 · Concept Eligibility</span><h2>Origin과 Legal Guardrail을 통과한 Concept만 확인하세요</h2><p>실패 Draft는 내부 검증 기록으로만 보관하며 후보 카드로 노출하지 않습니다.</p></div><span className={`journey-save-state ${batch?.state === 'COMPLETED' ? 'is-saved' : ''}`}>{batch?.stale ? 'STALE' : batch?.state || '생성 전'}</span></header>
    <ErrorBanner error={error} />
    {!allowed ? <section className="journey-empty"><h3>법률 검토 통과가 필요합니다.</h3><p>PASS 또는 PASS_WITH_CONDITIONS 결과에서만 콘셉트를 생성할 수 있습니다.</p><Link className="journey-button" to={`/app/projects/${projectId}/legal`}>법률 검토로 이동</Link></section>
      : <section className="journey-card journey-run-card"><div><h3>적격 Concept 3개 확보</h3><p>최초 3개를 검사하고 실패한 수만큼 최대 2라운드·전체 9개 범위에서 대체합니다.</p></div><button className="journey-button" disabled={busy || activeBatch || !canGenerate} onClick={() => void generate()}>{batch?.stale ? '현재 입력으로 다시 생성' : failedBatch ? '아래 실패 상태 확인' : batch ? '생성 완료' : 'Concept 3개 생성'}</button></section>}
    {batch && <section className="journey-card"><h3>Eligibility 진행 상태</h3><p>Round {batch.currentRound} · 검사 {batch.inspectedCandidates}/{batch.maxInspectedCandidates} · 적격 {batch.eligibleCandidates}/{batch.targetEligibleCount}</p>{batch.stale && <p className="journey-warning">Origin 또는 Guardrail이 변경되어 이 Batch는 STALE입니다. 이전 후보는 current 결과로 표시하지 않습니다.</p>}</section>}
    {failedBatch && <section className="journey-card journey-run-card"><div><h3>Concept Batch 실행 실패</h3><p>{journeyFailureMessage(batch.errorCode)}</p><small>{batch.errorCode || 'AI_SERVICE_UNAVAILABLE'}</small></div>{batch.errorCode === 'AI_RESULT_INVALID' ? <button className="journey-button" disabled={busy || activeBatch} onClick={() => void generate()}>AI 응답 구조 오류 · 동일 입력으로 새 Batch 실행</button> : batch.retryable ? <button className="journey-button" disabled={busy || activeBatch} onClick={() => void generate()}>동일 입력으로 재시도</button> : batch.errorCode === 'AI_CONFIGURATION_INVALID' ? <button className="journey-button" disabled={busy || activeBatch} onClick={() => void generate()}>설정 수정 후 새 Batch 실행</button> : <Link className="journey-button secondary" to={`/app/projects/${projectId}/legal`}>Origin·Legal 확인</Link>}</section>}
    {batch?.state === 'NEEDS_INPUT' && <section className="journey-empty"><h3>적격 Concept 3개를 확보하지 못했습니다.</h3><p>실패 후보는 표시하지 않습니다. 아래 Origin 또는 Legal 조건을 보완한 뒤 다시 실행하세요.</p><List items={batch.needsInput} /><Link className="journey-button" to={`/app/projects/${projectId}/legal`}>Origin·Legal 보완</Link></section>}
    {concepts.length === 3 ? <><div className="concept-grid">{concepts.map((c) => <ConceptCard key={c.id} concept={c} />)}</div><div className="journey-next"><div><strong>ELIGIBLE Concept 3개가 동결·저장되었습니다.</strong><p>현재 구축 범위는 여기까지이며 후속 분석으로 자동 이동하지 않습니다.</p></div></div></> : allowed && !batch && <section className="journey-empty"><h3>아직 생성된 후보가 없습니다.</h3><p>생성 버튼을 누르면 내부 검증을 통과한 후보만 한 번에 표시합니다.</p></section>}
  </div>;
}

const DEFAULT_FINANCE = { unitPrice: 50000, monthlyCustomers: 100, variableCostPerCustomer: 15000, monthlyFixedCost: 2000000, initialInvestment: 10000000 };
const SCORE_FIELDS = [['market', '시장'], ['customerValue', '고객 가치'], ['feasibility', '실행 가능성'], ['differentiation', '차별성'], ['revenuePotential', '수익 잠재력'], ['legalRisk', '법률 위험 대응']];

export function ConceptAnalysisPage() {
  const { projectId, api } = useConceptApi();
  const [concepts, setConcepts] = useState([]); const [quick, setQuick] = useState(null); const [shortlist, setShortlist] = useState(null); const [detailed, setDetailed] = useState(null);
  const [selected, setSelected] = useState([]); const [reason, setReason] = useState(''); const [finance, setFinance] = useState({}); const [tab, setTab] = useState(null);
  const [busy, setBusy] = useState(''); const [error, setError] = useState('');
  useEffect(() => { let active = true; Promise.all([api.concepts(), api.currentQuick(), api.currentShortlist(), api.currentDetailed()]).then(([c, q, s, d]) => { if (!active) return; const values = c || []; setConcepts(values); setQuick(q); setShortlist(s); setDetailed(d); const ids = s?.conceptVersionIds || []; setSelected(ids); setReason(s?.reason || ''); setTab(ids[0] || null); const restored = {}; ids.forEach((id) => { restored[id] = d?.financials?.[id] || { ...DEFAULT_FINANCE }; }); setFinance(restored); }).catch((e) => active && setError(getUserErrorMessage(e))); return () => { active = false; }; }, [api]);
  function toggle(id) { setSelected((old) => old.includes(id) ? old.filter((v) => v !== id) : [...old, id]); }
  async function assess() { setBusy('후보별 Quick Assessment를 실행하고 있습니다.'); setError(''); try { setQuick(await api.quickAssessment()); } catch (e) { setError(getUserErrorMessage(e)); } finally { setBusy(''); } }
  async function saveShortlist() { setBusy('Shortlist를 저장하고 있습니다.'); setError(''); try { const saved = await api.saveShortlist({ conceptVersionIds: selected, reason }); setShortlist(saved); setTab(saved.conceptVersionIds[0]); setFinance(Object.fromEntries(saved.conceptVersionIds.map((id) => [id, finance[id] || { ...DEFAULT_FINANCE }]))); } catch (e) { setError(getUserErrorMessage(e)); } finally { setBusy(''); } }
  function setMoney(id, field, value) { setFinance((old) => ({ ...old, [id]: { ...(old[id] || DEFAULT_FINANCE), [field]: Number(value) } })); }
  async function analyze() { setBusy('선택 후보를 심층 분석하고 재무를 계산하고 있습니다.'); setError(''); try { setDetailed(await api.detailedAnalysis({ financials: shortlist.conceptVersionIds.map((id) => ({ conceptVersionId: id, ...(finance[id] || DEFAULT_FINANCE) })) })); } catch (e) { setError(getUserErrorMessage(e)); } finally { setBusy(''); } }
  if (!concepts.length) return <div className="journey-page"><section className="journey-empty"><h3>먼저 콘셉트를 생성하세요.</h3><Link className="journey-button" to={`/app/projects/${projectId}/journey/concept`}>콘셉트 생성</Link></section></div>;
  const activeDetail = detailed?.analyses?.find((a) => a.conceptVersionId === tab); const activeFinance = detailed?.financials?.[tab];
  return <div className="journey-page concept-page">
    {busy && <Busy>{busy}</Busy>}<header className="journey-page__heading"><div><span>4단계 · 콘셉트 분석</span><h2>빠르게 비교하고, 선택 후보를 깊게 검증하세요</h2><p>Quick Assessment와 Detailed Analysis는 실제 AI를 사용하며 재무 결과는 Spring이 계산합니다.</p></div></header><ErrorBanner error={error} />
    <section className="journey-card journey-run-card"><div><h3>Quick Assessment</h3><p>시장·가치·실행·차별·수익·법률 대응을 100점 기준으로 비교합니다.</p></div><button className="journey-button" disabled={busy || quick?.state === 'SUCCEEDED'} onClick={() => void assess()}>{quick?.state === 'SUCCEEDED' ? '평가 완료' : 'Quick Assessment 실행'}</button></section>
    {quick?.assessments?.length ? <section className="journey-card"><h3>후보 비교</h3><div className="score-table">{quick.assessments.map((a) => <article key={a.conceptVersionId}><header><strong>{a.conceptName}</strong><span>{a.overallScore}점</span></header>{SCORE_FIELDS.map(([key, label]) => <div className="score-row" key={key}><small>{label}</small><span><i style={{ width: `${a[key]}%` }} /></span><b>{a[key]}</b></div>)}<p>{a.summary}</p><div className="score-notes"><div><strong>장점</strong><List items={a.strengths} /></div><div><strong>약점</strong><List items={a.weaknesses} /></div></div></article>)}</div></section> : <section className="journey-empty"><h3>Quick 결과가 없습니다.</h3><p>실제 평가 결과가 저장되기 전에는 비교 점수를 표시하지 않습니다.</p></section>}
    {quick?.state === 'SUCCEEDED' && <section className="journey-card"><div className="section-heading"><div><span>Shortlist</span><h3>심층 분석할 후보를 하나 이상 선택하세요</h3></div></div><div className="concept-grid compact">{concepts.map((c) => <ConceptCard key={c.id} concept={c} selected={selected.includes(c.id)} onSelect={toggle} />)}</div><label>선택 이유<textarea rows="3" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="선택 기준이나 추가로 확인할 내용을 입력하세요." /></label><button className="journey-button" disabled={busy || !selected.length} onClick={() => void saveShortlist()}>Shortlist 저장</button></section>}
    {shortlist && !detailed?.analyses?.length && <section className="journey-card"><h3>재무 가정 입력</h3><p className="journey-muted">AI가 숫자를 정하지 않습니다. 아래 값을 검토·수정하면 Spring이 계산합니다.</p>{shortlist.conceptVersionIds.map((id) => { const c = concepts.find((v) => v.id === id); const f = finance[id] || DEFAULT_FINANCE; return <fieldset className="finance-inputs" key={id}><legend>{c?.name}</legend>{[['unitPrice','예상 판매가격'],['monthlyCustomers','월 예상 고객 수'],['variableCostPerCustomer','고객당 변동비'],['monthlyFixedCost','월 고정비'],['initialInvestment','초기 투자비']].map(([key,label]) => <label key={key}>{label}<input type="number" min="0" value={f[key]} onChange={(e) => setMoney(id,key,e.target.value)} /></label>)}</fieldset>;})}<button className="journey-button" disabled={busy} onClick={() => void analyze()}>Detailed Analysis 실행</button></section>}
    {detailed?.analyses?.length ? <section className="journey-card"><div className="detail-tabs" role="tablist">{detailed.analyses.map((a) => <button key={a.conceptVersionId} className={tab === a.conceptVersionId ? 'active' : ''} onClick={() => setTab(a.conceptVersionId)}>{a.conceptName}</button>)}</div>{activeDetail && <div className="detail-grid"><section><h4>시장</h4><p>{activeDetail.marketAnalysis}</p></section><section><h4>고객</h4><p>{activeDetail.customerAnalysis}</p></section><section><h4>사업 모델</h4><p>{activeDetail.businessModelAnalysis}</p></section><section><h4>운영</h4><p>{activeDetail.operationAnalysis}</p></section><section><h4>위험</h4><p>{activeDetail.riskAnalysis}</p></section><section><h4>추천</h4><p>{activeDetail.recommendation}</p></section><section><h4>가정</h4><List items={activeDetail.assumptions} /></section><section><h4>추가 조사</h4><List items={activeDetail.researchNeeds} /></section></div>}{activeFinance && <section className="finance-result"><h3>Spring 재무 계산</h3><dl>{[['monthlyRevenue','월 매출'],['monthlyVariableCost','월 변동비'],['monthlyTotalCost','월 총비용'],['monthlyOperatingProfit','월 영업이익'],['breakEvenCustomers','손익분기 고객 수'],['paybackMonths','투자 회수 개월']].map(([key,label]) => <div key={key}><dt>{label}</dt><dd>{activeFinance[key] == null ? '회수 불가' : Number(activeFinance[key]).toLocaleString()}</dd></div>)}</dl></section>}<div className="journey-next"><strong>심층 분석이 저장되었습니다.</strong><Link className="journey-button" to={`/app/projects/${projectId}/journey/concept-selection`}>최종 콘셉트 선택</Link></div></section> : null}
  </div>;
}

export function ConceptSelectionPage() {
  const { projectId, api } = useConceptApi(); const [concepts,setConcepts]=useState([]); const [detailed,setDetailed]=useState(null); const [selection,setSelection]=useState(null);
  const [choice,setChoice]=useState(null); const [reason,setReason]=useState(''); const [confirm,setConfirm]=useState(false); const [busy,setBusy]=useState(false); const [error,setError]=useState('');
  useEffect(()=>{let active=true;Promise.all([api.concepts(),api.currentDetailed(),api.currentSelection()]).then(([c,d,s])=>{if(active){setConcepts(c||[]);setDetailed(d);setSelection(s);setChoice(s?.conceptVersionId||null);setReason(s?.reason||'');}}).catch((e)=>active&&setError(getUserErrorMessage(e)));return()=>{active=false;};},[api]);
  const eligible=new Set(detailed?.analyses?.map((a)=>a.conceptVersionId)||[]);
  async function save(){setBusy(true);setError('');try{setSelection(await api.selectConcept({conceptVersionId:choice,reason}));setConfirm(false);}catch(e){setError(getUserErrorMessage(e));}finally{setBusy(false);}}
  return <div className="journey-page concept-page">{busy&&<Busy>최종 선택을 저장하고 있습니다.</Busy>}<header className="journey-page__heading"><div><span>5단계 · 콘셉트 선택</span><h2>검증 결과를 바탕으로 최종 방향을 확정하세요</h2><p>확정 후 Persona 단계가 활성화됩니다.</p></div><span className={`journey-save-state ${selection?'is-saved':''}`}>{selection?'선택 완료':'선택 전'}</span></header><ErrorBanner error={error}/>
    {!detailed?.analyses?.length?<section className="journey-empty"><h3>Detailed Analysis가 필요합니다.</h3><Link className="journey-button" to={`/app/projects/${projectId}/journey/concept-analysis`}>콘셉트 분석</Link></section>:<section className="journey-card"><div className="selection-list">{concepts.filter((c)=>eligible.has(c.id)).map((c)=><label key={c.id} className={choice===c.id?'selected':''}><input type="radio" name="concept" checked={choice===c.id} onChange={()=>setChoice(c.id)}/><span><strong>{c.name}</strong><small>{c.oneLineSummary}</small></span></label>)}</div><label>최종 선택 이유<textarea rows="4" value={reason} disabled={!!selection} onChange={(e)=>setReason(e.target.value)} placeholder="이 콘셉트를 선택한 근거를 기록하세요."/></label>{selection?<div className="journey-next"><div><strong>{selection.conceptName} 선택 완료</strong><p>{selection.reason}</p></div><Link className="journey-button" to={`/app/projects/${projectId}/journey/persona`}>Persona 단계로 이동</Link></div>:<button className="journey-button" disabled={!choice||!reason.trim()} onClick={()=>setConfirm(true)}>최종 선택</button>}</section>}
    {confirm&&<div className="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="confirm-title"><div><h3 id="confirm-title">이 콘셉트를 최종 선택할까요?</h3><p>선택 결과와 이유가 프로젝트에 저장됩니다.</p><div className="journey-actions"><button className="journey-button secondary" onClick={()=>setConfirm(false)}>취소</button><button className="journey-button" onClick={()=>void save()}>확정</button></div></div></div>}
  </div>;
}
