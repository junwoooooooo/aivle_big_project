import { JobTimeline } from '../../shared/async-events/index.js';
import { ConceptSlotCard } from './ConceptSlotCard.jsx';
import {
  BATCH_MESSAGES,
  domainState,
  publicConceptGate,
  sortSlots,
} from './conceptWorkboardModel.js';
import { PublicConceptCard } from './PublicConceptCard.jsx';
import './conceptWorkboard.css';

export function ConceptWorkboard({ workboard, brief, boundary, messages = [], onReturnToBrief }) {
  const { batch, slots, concepts, job, network, error } = workboard;
  const currentDomain = domainState(batch);
  const gate = publicConceptGate(batch, slots, concepts);
  return <section className="concept-workboard" aria-label="Concept Workboard">
    <aside className="concept-workboard__summary" aria-label="Concept 입력 요약">
      <details open>
        <summary>확정 기준 요약</summary>
        <section><h3>Opportunity Brief</h3><p>Version {brief?.version} · CONFIRMED</p><small>Hash {shortHash(brief?.hash)}</small>
          <SummaryFields fields={brief?.fields} /></section>
        <section><h3>Regulatory Boundary</h3><p>Version {boundary?.version?.versionNumber} · READY</p><small>Hash {shortHash(boundary?.version?.regulatoryBoundaryHash)}</small>
          <ul>{(boundary?.version?.rules || []).slice(0, 5).map((rule) => <li key={rule.ruleId}>{rule.title}</li>)}</ul></section>
      </details>
      <details>
        <summary>대화 이력 접근</summary>
        <ol className="concept-workboard__conversation">{messages.slice(-5).map((message) => <li key={message.id}><strong>{message.role === 'USER' ? '나' : 'AI'}</strong><span>{message.text}</span></li>)}</ol>
      </details>
      <button type="button" onClick={onReturnToBrief}>대화형 Idea Workspace로 돌아가기</button>
    </aside>
    <main className="concept-workboard__board">
      <header className="concept-batch-header">
        <div><span>2단계 · Concept 생성</span><h2>검증 가능한 세 방향을 준비하고 있습니다</h2><p>{BATCH_MESSAGES[batch?.status] || 'Concept 탐색 상태를 불러오고 있습니다.'}</p></div>
        <strong className={`concept-batch-status status-${(batch?.status || 'queued').toLowerCase()}`} aria-live="polite">{batch?.status || 'QUEUED'}</strong>
      </header>
      {error && <div className="concept-workboard__error" role="alert">{error}</div>}
      {batch?.stale && <div className="concept-workboard__stale" role="alert">아이디어 또는 규제 경계가 변경되었습니다. 현재 Concept는 이전 기준이므로 다시 탐색해야 합니다.</div>}
      <div className="concept-workboard__network"><span>연결 상태: {networkLabel(network, job.transport)}</span><button type="button" onClick={() => void workboard.load()}>현재 상태 새로고침</button></div>
      <section className="concept-slots" aria-label="Concept Slot 목록">
        {sortSlots(slots).map((slot) => <ConceptSlotCard key={slot.slotId} slot={slot} events={job.events} />)}
        {!slots.length && currentDomain === 'RUNNING' && [0, 1, 2].map((index) => <div className="concept-slot concept-slot--placeholder" key={index} aria-live="polite"><strong>Slot {index + 1}</strong><p>작업 상태를 기다리고 있습니다.</p></div>)}
      </section>
      {job.events.length > 0 && <details className="concept-workboard__timeline"><summary>전체 작업 Timeline</summary><JobTimeline events={job.events} title="Concept 탐색 전체 기록" /></details>}
      {currentDomain === 'NEEDS_INPUT' && <ActionPanel role="status" title="추가 확인이 필요합니다" text="Brief 또는 규제 경계에서 확인할 정보를 보완한 뒤 새 탐색을 시작해 주세요." action="Brief 수정으로 돌아가기" onAction={onReturnToBrief} />}
      {currentDomain === 'FAILED' && <ActionPanel role="alert" title="Concept 탐색을 완료하지 못했습니다" text={batch.retryable ? '같은 입력으로 안전하게 다시 시도할 수 있습니다.' : '입력 또는 서비스 설정을 확인한 뒤 다시 시작해 주세요.'} action={batch.retryable ? '다시 실행' : 'Brief와 Boundary 확인'} onAction={batch.retryable ? workboard.retry : onReturnToBrief} />}
      {currentDomain === 'STALE' && <ActionPanel role="alert" title="이전 기준의 Concept입니다" text="새 Brief와 Regulatory Boundary를 준비해 다시 탐색해 주세요." action="현재 Brief 확인" onAction={onReturnToBrief} />}
      {batch?.status === 'COMPLETED' && !gate.allowed && <div className="concept-workboard__error" role="alert">검증된 Concept 공개 계약을 확인하지 못했습니다. 상세 후보는 안전하게 숨겼습니다.</div>}
      {gate.allowed && <section className="concept-public-grid" aria-label="검증 완료 Concept 3개">
        <header><h2>검증된 Concept 3개</h2><p>세 후보를 같은 시점에 공개합니다. Quick Assessment와 선택은 다음 단계에서 진행합니다.</p></header>
        <div>{concepts.map((concept, index) => <PublicConceptCard key={concept.conceptId} concept={concept} index={index} />)}</div>
      </section>}
    </main>
  </section>;
}

function SummaryFields({ fields = [] }) {
  const visible = new Set(['problem', 'targetCustomer', 'beneficiaries', 'desiredOutcome', 'targetRegion', 'fixedConstraints', 'openDecisions']);
  return <dl>{fields.filter((field) => visible.has(field.fieldKey)).map((field) => <div key={field.fieldKey}><dt>{field.fieldKey}</dt><dd>{display(field.value)}</dd></div>)}</dl>;
}
function ActionPanel({ role, title, text, action, onAction }) {
  return <section className="concept-workboard__action" role={role}><h3>{title}</h3><p>{text}</p><button type="button" onClick={() => void onAction()}>{action}</button></section>;
}
function networkLabel(network, transport) {
  if (network === 'ERROR') return '연결 오류';
  if (network === 'LOADING') return '상태 확인 중';
  return transport === 'POLLING' ? 'Polling 복구 중' : '실시간 연결';
}
function shortHash(value) { return value ? `${value.slice(0, 15)}…` : '확인 중'; }
function display(value) { return typeof value === 'string' ? value : JSON.stringify(value ?? ''); }
