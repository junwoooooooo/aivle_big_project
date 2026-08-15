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
  if (factory.loading) return <section className="concept-workboard" aria-busy="true"><p>사업안 만들기를 불러오고 있습니다.</p></section>;
  if (factory.error) return <section className="concept-workboard" role="alert"><h1>사업안 만들기를 불러오지 못했습니다.</h1><button type="button" onClick={factory.refresh}>다시 시도</button></section>;
  if (!factory.run) return <section className="concept-workboard concept-workboard--empty">
    <h1>5개 사업안 만들기</h1><p>확정한 사업 아이디어를 기준으로 공식 법률 근거와 구현 가능성을 검토합니다.</p>
    {factory.confirmedSnapshotId ? <button type="button" disabled={factory.actionPending} onClick={factory.start}>5개 사업안 만들기</button> : <Link to={projectRoutes.idea(projectId)}>사업 아이디어 확정하기</Link>}
  </section>;

  const summary = workboardSummary(factory.run, factory.slots, factory.jobEvents.events);
  const reveal = evaluateRevealGate(factory.run, factory.slots, factory.concepts);
  const terminal = ['FAILED', 'NEEDS_INPUT', 'STALE'].includes(factory.run.status);
  return <main className="concept-workboard">
    <header className="concept-workboard__heading"><div><p>이 작업은 화면을 벗어나도 계속됩니다.</p><h1>5개 사업안 만들기</h1><span>공식 근거 기반 법률 구현 가능성 검토</span></div>
      <button type="button" onClick={factory.refresh}>새로고침</button></header>
    {terminal && <div className="concept-workboard__alert" role="alert">
      <strong>{factory.run.status === 'NEEDS_INPUT' ? '추가 정보가 필요합니다.' : '작업을 완료하지 못했습니다.'}</strong>
      <span>{factory.run.failureCode === 'REQUEST_CONTRACT_INVALID'
        ? '컨셉 생성 중 내부 입력 계약 오류가 발생했습니다. 새로 시도해도 같은 문제가 반복될 수 있습니다.'
        : '현재까지 통과된 결과는 보존되었습니다.'}</span>
      {factory.run.status === 'NEEDS_INPUT' && <Link to={projectRoutes.idea(projectId)}>사업 아이디어 확인</Link>}
      {factory.run.canResume && <button type="button" disabled={factory.actionPending} onClick={factory.retry}>이어서 시도</button>}
      {factory.run.canStartNew && <button type="button" disabled={factory.actionPending} onClick={factory.startNew}>처음부터 새로 만들기</button>}
    </div>}
    <section className="concept-summary" aria-label="사업안 생성 요약" aria-live="polite">
      <Metric label="법률검토 통과" value={`${summary.eligible} / 5`} />
      <Metric label="신규 후보 생성" value={summary.initialGenerated} />
      <Metric label="대체 후보 생성" value={summary.replaced} />
      <Metric label="재설계 성공" value={summary.redesigned} />
      <Metric label="검토 완료 후보" value={summary.inspected} />
      <Metric label="폐기 후보" value={summary.discarded} />
      <Metric label="생성/시스템 실패" value={summary.generationFailed} />
      <Metric label="AI 서비스 재시도" value={summary.providerRetries} />
    </section>
    {!reveal.canReveal && <p className="concept-workboard__gate" aria-live="polite">5개 컨셉이 모두 준비되면 상세를 동시에 공개합니다.</p>}
    <div className="concept-workboard__body"><section className="concept-slots" aria-label="사업안 후보 다섯 개">
      {factory.slots.map((slot) => <ConceptSlotCard key={slot.slotNumber} slot={slot} />)}
    </section><ConceptTimeline events={factory.jobEvents.events} connectionState={factory.jobEvents.connectionState} transport={factory.jobEvents.transport} onReconnect={factory.jobEvents.reconnect} /></div>
    {reveal.canReveal && <ConceptReveal concepts={factory.concepts} />}
  </main>;
}

function Metric({ label, value }) { return <div><span>{label}</span><strong>{value}</strong></div>; }
