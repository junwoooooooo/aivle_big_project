import { Link, useParams } from 'react-router-dom';

import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import ConceptReveal from '../components/ConceptReveal.jsx';
import ConceptSlotCard from '../components/ConceptSlotCard.jsx';
import ConceptTimeline from '../components/ConceptTimeline.jsx';
import useConceptFactory from '../hooks/useConceptFactory.js';
import { evaluateRevealGate, workboardSummary } from '../model/conceptFactoryModel.js';
import '../styles/concept-factory.css';

export default function ConceptFactoryPage() {
  const { projectId } = useParams();
  const factory = useConceptFactory(projectId);
  if (factory.loading) return <section className="concept-workboard" aria-busy="true"><p>Concept Factory를 불러오고 있습니다.</p></section>;
  if (factory.error) return <section className="concept-workboard" role="alert"><h1>Concept Factory를 불러오지 못했습니다.</h1><button type="button" onClick={factory.refresh}>다시 시도</button></section>;
  if (!factory.run) return <section className="concept-workboard concept-workboard--empty">
    <h1>5 Slot Concept Factory</h1><p>확정된 Idea Brief를 기준으로 공식 근거 기반 법률 구현 가능성 검토를 시작합니다.</p>
    {factory.confirmedSnapshotId ? <button type="button" onClick={factory.start}>5개 컨셉 만들기</button> : <Link to={projectRoutes.idea(projectId)}>Idea Brief 확정하기</Link>}
  </section>;

  const summary = workboardSummary(factory.run, factory.slots, factory.jobEvents.events);
  const reveal = evaluateRevealGate(factory.run, factory.slots, factory.concepts);
  const terminal = ['FAILED', 'NEEDS_INPUT', 'STALE'].includes(factory.run.status);
  return <main className="concept-workboard">
    <header className="concept-workboard__heading"><div><p>이 작업은 화면을 벗어나도 계속됩니다.</p><h1>5 Slot Concept Factory</h1><span>공식 근거 기반 법률 구현 가능성 검토</span></div>
      <button type="button" onClick={factory.refresh}>새로고침</button></header>
    {terminal && <div className="concept-workboard__alert" role="alert">
      <strong>{factory.run.status === 'NEEDS_INPUT' ? '추가 정보가 필요합니다.' : '작업을 완료하지 못했습니다.'}</strong>
      <span>현재까지 통과된 결과는 보존되었습니다.</span>
      {factory.run.status === 'NEEDS_INPUT' && <Link to={projectRoutes.idea(projectId)}>Idea Brief 확인</Link>}
      {factory.run.status !== 'STALE' && <button type="button" onClick={factory.retry}>작업 다시 시도</button>}
    </div>}
    <section className="concept-summary" aria-label="Concept Factory 요약" aria-live="polite">
      <Metric label="법률검토 통과" value={`${summary.eligible} / 5`} />
      <Metric label="검토 후보" value={summary.inspected} />
      <Metric label="재설계" value={summary.redesigned} />
      <Metric label="대체 후보" value={summary.replaced} />
      <Metric label="폐기 후보" value={summary.discarded} />
    </section>
    {!reveal.canReveal && <p className="concept-workboard__gate" aria-live="polite">5개 컨셉이 모두 준비되면 상세를 동시에 공개합니다.</p>}
    <div className="concept-workboard__body"><section className="concept-slots" aria-label="다섯 개 Concept Slot">
      {factory.slots.map((slot) => <ConceptSlotCard key={slot.slotNumber} slot={slot} />)}
    </section><ConceptTimeline events={factory.jobEvents.events} connectionState={factory.jobEvents.connectionState} transport={factory.jobEvents.transport} onReconnect={factory.jobEvents.reconnect} /></div>
    {reveal.canReveal && <ConceptReveal concepts={factory.concepts} />}
  </main>;
}

function Metric({ label, value }) { return <div><span>{label}</span><strong>{value}</strong></div>; }
