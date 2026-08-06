import PlanningChangeCard from '../../market-integration/components/PlanningChangeCard.jsx';
import usePlanningRevision from '../hooks/usePlanningRevision.js';
import '../styles/planning-revision.css';
export default function PlanningRevisionPanel({projectId}) {
 const state=usePlanningRevision(projectId); if(state.loading)return <section aria-busy="true">기획 반영안을 불러오고 있습니다.</section>;
 if(state.error&&!state.current)return <section role="alert">기획 상태를 불러오지 못했습니다. <button type="button" onClick={state.refresh}>다시 시도</button></section>;
 const c=state.current; return <section className="planning-revision" aria-labelledby="planning-revision-title">
  <header><div><p>결정론적 변경 적용</p><h2 id="planning-revision-title">{c.finalizedPlanning?.displayLabel ?? c.appliedLabel}</h2></div>{c.staleMarketResult&&<strong>이전 Snapshot 기준 제안</strong>}</header>
  <ol className="planning-revision__stages"><li><strong>선택한 원안</strong><span>확정 컨셉 Snapshot</span></li><li><strong>시장분석 제안</strong><span>{c.marketProposals.length}건</span></li><li><strong>{c.appliedLabel}</strong><span>{c.allDecided?'결정 완료':'검토 중'}</span></li><li><strong>최종 확정 기획</strong><span>{c.finalizedPlanning?'확정됨':'확정 전'}</span></li><li><strong>이전 기획</strong><span>{c.previousPlanning.length}건</span></li></ol>
  <div className="planning-change-list">{c.marketProposals.map(p=><PlanningChangeCard key={p.proposalId} proposal={p} busy={state.busyId===p.proposalId} onDecision={(a,m)=>state.decide(p.proposalId,a,m)}/>)}</div>
  <section className="planning-revision__preview"><h3>{c.finalizedPlanning?'최종 확정 기획':'시장분석 반영 미리보기'}</h3><pre>{JSON.stringify(c.finalizedPlanning?.planning??c.appliedPreview,null,2)}</pre></section>
  {!c.finalizedPlanning&&<button className="planning-revision__finalize" type="button" disabled={!c.allDecided||c.staleMarketResult||state.finalizing} onClick={state.finalize}>{state.finalizing?'확정 중…':'이 반영안으로 기획 확정'}</button>}
  {c.previousPlanning.length>0&&<details><summary>이전 기획 {c.previousPlanning.length}건</summary><ul>{c.previousPlanning.map(p=><li key={p.snapshotId}>{p.displayLabel} · {new Date(p.finalizedAt).toLocaleString('ko-KR')}</li>)}</ul></details>}
  {state.error&&<p role="alert">{state.error.message}</p>}
 </section>;
}
