import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { createJourneyApi } from './journeyApi.js';
import './journey.css';

function useJourney(){const {projectId}=useParams();const client=useApiClient();return{projectId,api:useMemo(()=>createJourneyApi(client,projectId),[client,projectId])};}
function ErrorBanner({error}){return error?<div className="journey-error" role="alert"><strong>요청을 완료하지 못했습니다.</strong><span>{error}</span><button type="button" onClick={()=>window.location.reload()}>현재 단계 다시 불러오기</button></div>:null;}
function Busy({children}){return <div className="journey-overlay" role="status"><span className="journey-spinner"/><strong>{children}</strong></div>;}
function Items({items}){return items?.length?<ul>{items.map((v,i)=><li key={i}>{v}</li>)}</ul>:<p className="journey-muted">기록된 항목이 없습니다.</p>;}
const PERSONA_COLORS=['persona-mint','persona-blue','persona-amber'];

function PersonaCard({persona,checked,onToggle}){
 const role=persona.roleAndContext||{},needs=persona.problemAndNeeds||{},behavior=persona.behaviorAndDecision||{};
 return <article className={`journey-card persona-card ${PERSONA_COLORS[(persona.displayOrder-1)%3]} ${checked?'is-selected':''}`}>
  <header><div><span className="journey-badge">합성 Persona {persona.displayOrder}</span><h3>{persona.name}</h3><p>{persona.shortLabel}</p></div>{onToggle&&<label><input type="checkbox" checked={checked} onChange={()=>onToggle(persona.id)}/> 인터뷰 선택</label>}</header>
  <section><h4>1. Role and Context</h4><dl><div><dt>역할</dt><dd>{role.role}</dd></div><div><dt>상황</dt><dd>{role.situation}</dd></div></dl><strong>목표</strong><Items items={role.goals}/><strong>제약</strong><Items items={role.constraints}/></section>
  <section><h4>2. Problem and Needs</h4><strong>문제</strong><Items items={needs.problems}/><strong>충족되지 않은 필요</strong><Items items={needs.unmetNeeds}/><strong>원하는 결과</strong><Items items={needs.desiredOutcomes}/></section>
  <section><h4>3. Behavior and Decision</h4><strong>현재 행동</strong><Items items={behavior.currentBehavior}/><strong>의사결정 기준</strong><Items items={behavior.decisionCriteria}/><strong>장벽</strong><Items items={behavior.barriers}/><strong>정보원</strong><Items items={behavior.informationSources}/></section>
  <footer><strong>Interview Focus</strong><Items items={persona.interviewFocus}/></footer>
 </article>;
}

export function PersonaJourneyPage(){
 const {projectId,api}=useJourney();const [selection,setSelection]=useState(null);const [study,setStudy]=useState(null);const [personas,setPersonas]=useState([]);const [selected,setSelected]=useState([]);const [busy,setBusy]=useState('');const [error,setError]=useState('');
 useEffect(()=>{let active=true;Promise.all([api.currentSelection(),api.currentPersonaStudy(),api.personaCards()]).then(([s,st,p])=>{if(active){setSelection(s);setStudy(st);setPersonas(p||[]);setSelected((p||[]).filter(v=>v.selected).map(v=>v.id));}}).catch(e=>active&&setError(getUserErrorMessage(e)));return()=>{active=false;};},[api]);
 async function generate(){setBusy('실제 AI가 합성 Persona 3개를 설계하고 있습니다.');setError('');try{if(!study)setStudy(await api.createPersonaStudy());const cards=await api.generatePersonas();setPersonas(cards);setSelected(cards.filter(v=>v.selected).map(v=>v.id));}catch(e){setError(getUserErrorMessage(e));}finally{setBusy('');}}
 function toggle(id){setSelected(old=>old.includes(id)?old.filter(v=>v!==id):[...old,id]);}
 if(!selection)return <div className="journey-page"><section className="journey-empty"><h3>최종 Concept 선택이 필요합니다.</h3><p>Detailed Analysis를 마친 후보 중 하나를 먼저 확정하세요.</p><Link className="journey-button" to={`/app/projects/${projectId}/journey/concept-selection`}>Concept 선택</Link></section></div>;
 return <div className="journey-page persona-page">{busy&&<Busy>{busy}</Busy>}<header className="journey-page__heading"><div><span>6단계 · Persona</span><h2>{selection.conceptName}의 사용자 맥락을 세 가지 관점으로 탐색하세요</h2><p>역할과 상황, 문제와 필요, 행동과 의사결정을 중심으로 연구 가설을 구성합니다.</p></div><span className={`journey-save-state ${personas.length?'is-saved':''}`}>{personas.length?'3개 저장됨':'생성 전'}</span></header><ErrorBanner error={error}/>
  <aside className="synthetic-notice"><strong>합성 Persona 안내</strong><p>{study?.syntheticNotice||'AI가 생성한 연구용 합성 사용자이며 실제 고객 데이터가 아닙니다.'} 구매확률·시장점유율·확정적 수요 예측을 제공하지 않습니다.</p></aside>
  <section className="journey-card journey-run-card"><div><h3>Persona Card Generation</h3><p>최종 선택된 정확한 ConceptVersion을 기준으로 세 Persona를 생성하고 DB에 저장합니다.</p></div><button className="journey-button" disabled={busy||personas.length>0} onClick={()=>void generate()}>{personas.length?'생성 완료':'Persona 3개 생성'}</button></section>
  {personas.length?<><div className="persona-grid">{personas.map(p=><PersonaCard key={p.id} persona={p} checked={selected.includes(p.id)} onToggle={toggle}/>)}</div><div className="journey-next"><div><strong>{selected.length?`${selected.length}개 Persona 선택됨`:'인터뷰할 Persona를 선택하세요.'}</strong><p>각 Persona Interview는 다른 Persona 응답을 보지 않고 독립 실행됩니다.</p></div><Link className={`journey-button ${selected.length?'':'disabled'}`} aria-disabled={!selected.length} state={{selectedPersonaIds:selected}} to={selected.length?`/app/projects/${projectId}/journey/interview`:'#'}>Interview로 이동</Link></div></>:<section className="journey-empty"><h3>생성된 Persona가 없습니다.</h3><p>실제 Provider 응답이 저장되기 전에는 Persona를 표시하지 않습니다.</p></section>}
 </div>;
}

const CATEGORY_LABEL={ROLE_AND_CONTEXT:'역할·맥락',PROBLEM_AND_NEEDS:'문제·필요',BEHAVIOR_AND_DECISION:'행동·의사결정'};
const SYNTHESIS_SECTIONS=[['commonThemes','공통 Theme'],['conflictingViews','상충 의견'],['criticalNeeds','핵심 Need'],['decisionBarriers','의사결정 Barrier'],['implications','Concept / Marketing 시사점'],['researchNeeds','추가 조사 필요']];

export function InterviewJourneyPage(){
 const {projectId,api}=useJourney();const location=useLocation();const [personas,setPersonas]=useState([]);const [selected,setSelected]=useState([]);const [interviews,setInterviews]=useState([]);const [synthesis,setSynthesis]=useState(null);const [tab,setTab]=useState(null);const [busy,setBusy]=useState('');const [error,setError]=useState('');
 useEffect(()=>{let active=true;Promise.all([api.personaCards(),api.personaInterviews(),api.currentInterviewSynthesis()]).then(([p,i,s])=>{if(!active)return;const cards=p||[],runs=i||[];setPersonas(cards);const persisted=cards.filter(v=>v.selected).map(v=>v.id);const requested=(location.state?.selectedPersonaIds||[]).filter(id=>cards.some(v=>v.id===id));const initial=persisted.length?persisted:requested;setSelected(initial);setInterviews(runs);setSynthesis(s);setTab(runs[0]?.personaCardVersionId||initial[0]||cards[0]?.id||null);}).catch(e=>active&&setError(getUserErrorMessage(e)));return()=>{active=false;};},[api,location.state]);
 function toggle(id){setSelected(old=>old.includes(id)?old.filter(v=>v!==id):[...old,id]);}
 async function run(){setBusy('선택한 Persona별 독립 Interview를 실행하고 있습니다.');setError('');try{const values=await api.runPersonaInterviews({personaCardVersionIds:selected});setInterviews(values);setPersonas(await api.personaCards());setTab(values[0]?.personaCardVersionId||null);}catch(e){setError(getUserErrorMessage(e));try{setInterviews(await api.personaInterviews());setPersonas(await api.personaCards());}catch{/* 원래 오류 유지 */}}finally{setBusy('');}}
 async function synthesize(){setBusy('독립 Interview의 공통점과 차이점을 종합하고 있습니다.');setError('');try{setSynthesis(await api.synthesizeInterviews());}catch(e){setError(getUserErrorMessage(e));}finally{setBusy('');}}
 const active=interviews.find(v=>v.personaCardVersionId===tab);const selectedCompleted=selected.length>0&&selected.every(id=>interviews.some(v=>v.personaCardVersionId===id&&v.state==='SUCCEEDED'));
 if(!personas.length)return <div className="journey-page"><section className="journey-empty"><h3>먼저 Persona를 생성하세요.</h3><Link className="journey-button" to={`/app/projects/${projectId}/journey/persona`}>Persona 생성</Link></section></div>;
 return <div className="journey-page interview-page">{busy&&<Busy>{busy}</Busy>}<header className="journey-page__heading"><div><span>7단계 · Interview</span><h2>Persona별 독립 Research Interview</h2><p>각 실행은 단일 Persona의 맥락만 전달하며 최소 5개 질문·답변을 저장합니다.</p></div><span className={`journey-save-state ${synthesis?.state==='SUCCEEDED'?'is-saved':''}`}>{synthesis?.state==='SUCCEEDED'?'종합 완료':`${interviews.filter(v=>v.state==='SUCCEEDED').length}개 완료`}</span></header><ErrorBanner error={error}/>
  <section className="journey-card"><h3>Interview 대상 선택</h3><div className="interview-persona-picker">{personas.map((p,i)=><label className={`${PERSONA_COLORS[i%3]} ${selected.includes(p.id)?'selected':''}`} key={p.id}><input type="checkbox" checked={selected.includes(p.id)} onChange={()=>toggle(p.id)}/><span><strong>{p.name}</strong><small>{p.shortLabel}</small></span></label>)}</div><button className="journey-button" disabled={busy||!selected.length||selectedCompleted} onClick={()=>void run()}>{selectedCompleted?'선택 Interview 완료':'독립 Interview 실행'}</button></section>
  {interviews.length?<section className="journey-card research-workspace"><div className="detail-tabs" role="tablist">{interviews.map(v=><button key={v.id} className={tab===v.personaCardVersionId?'active':''} onClick={()=>setTab(v.personaCardVersionId)}>{v.personaName}<small>{v.state==='SUCCEEDED'?'완료':v.state}</small></button>)}</div>{active?.messages?.length?<div className="interview-timeline">{active.messages.map(m=><article key={m.sequenceNumber}><span>{m.sequenceNumber}</span><div><small className="category-badge">{CATEGORY_LABEL[m.category]||m.category}</small><h4>{m.question}</h4><p>{m.answer}</p></div></article>)}</div>:<section className="journey-empty"><h3>저장된 질문·답변이 없습니다.</h3><p>{active?.error||'Interview 실행을 완료해 주세요.'}</p></section>}</section>:<section className="journey-empty"><h3>Interview 결과가 없습니다.</h3><p>Persona를 선택하고 실제 AI Interview를 실행하세요.</p></section>}
  {selectedCompleted&&<section className="journey-card journey-run-card"><div><h3>Interview Synthesis</h3><p>완료된 독립 결과만 사용해 공통점, 차이점과 후속 조사 필요를 도출합니다.</p></div><button className="journey-button" disabled={busy||synthesis?.state==='SUCCEEDED'} onClick={()=>void synthesize()}>{synthesis?.state==='SUCCEEDED'?'종합 완료':'Synthesis 실행'}</button></section>}
  {synthesis?.state==='SUCCEEDED'&&<section className="journey-card synthesis-result"><h3>Interview Synthesis 결과</h3><div className="synthesis-grid">{SYNTHESIS_SECTIONS.map(([key,label])=><section key={key}><h4>{label}</h4><Items items={synthesis[key]}/></section>)}</div><div className="journey-next"><div><strong>사용자 연구 가설이 저장되었습니다.</strong><p>다음 Marketing 단계에서 이 시사점을 활용할 수 있습니다.</p></div><Link className="journey-button" to={`/app/projects/${projectId}/journey/marketing`}>Marketing 단계로 이동</Link></div></section>}
 </div>;
}
