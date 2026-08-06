import { useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { JobTimeline, useJobEvents } from '../../shared/async-events/index.js';
import { formatLocalTime } from '../../shared/async-events/formatLocalTime.js';
import { ConceptWorkboard } from '../concept-workboard/ConceptWorkboard.jsx';
import { useConceptWorkboard } from '../concept-workboard/useConceptWorkboard.js';
import { createConversationalIdeaApi } from './conversationalIdeaApi.js';
import './conversationalIdea.css';

const labels = {
  problem: '문제 또는 기회', targetCustomer: '대상 고객', beneficiaries: '수혜자', usageContext: '사용 상황',
  desiredOutcome: '원하는 결과', targetRegion: '대상 국가·지역', fixedConstraints: '고정 조건',
  preferredConstraints: '선호 조건', openDecisions: '열린 결정', assumptions: '확인되지 않은 가정',
  prohibitedApproaches: '금지 조건', regulatorySensitiveActivities: '규제 민감 활동 후보',
};
const allFields = Object.keys(labels);
const statusLabels = { LOCKED: '고정', PREFERRED: '선호', OPEN: '열림', ASSUMPTION: '가정' };
const CONVERSATION_REFRESH_EVENTS = new Set([
  'job.idea.information.extraction.completed',
  'job.idea.brief.draft.completed',
  'job.idea.questions.completed',
  'job.completed',
  'job.failed',
]);

export function ConversationalIdeaWorkspace() {
  const { projectId } = useParams();
  const client = useApiClient();
  const api = useMemo(() => createConversationalIdeaApi(client, projectId), [client, projectId]);
  const [workspace, setWorkspace] = useState(null);
  const [text, setText] = useState('');
  const [answers, setAnswers] = useState({});
  const [jobId, setJobId] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [briefOpen, setBriefOpen] = useState(false);
  const [boundary, setBoundary] = useState(null);
  const [showIdeaWorkspace, setShowIdeaWorkspace] = useState(false);
  const lastConversationRefresh = useRef({ jobId: null, sequence: 0 });
  const job = useJobEvents(jobId);
  const conceptReady = workspace?.brief?.state === 'CONFIRMED'
    && boundary?.version?.status === 'READY' && !boundary?.stale;
  // The current batch must remain recoverable even after its Brief or Boundary becomes stale.
  const workboard = useConceptWorkboard(projectId, true);

  const load = async () => {
    const [current, currentBoundary] = await Promise.all([api.current(), api.currentBoundary()]);
    setWorkspace(current);
    setBoundary(currentBoundary);
    setJobId(current?.activeJobId || currentBoundary?.run?.jobId || null);
  };
  useEffect(() => { let live = true; Promise.all([api.current(), api.currentBoundary()]).then(([value, currentBoundary]) => {
    if (!live) return; setWorkspace(value); setBoundary(currentBoundary);
    setJobId(value?.activeJobId || currentBoundary?.run?.jobId || null);
  }).catch((failure) => live && setError(getUserErrorMessage(failure)));
  return () => { live = false; }; }, [api]);

  const lastEvent = job.events.at(-1);
  useEffect(() => {
    const refreshed = lastConversationRefresh.current;
    const refreshEvent = lastEvent && (CONVERSATION_REFRESH_EVENTS.has(lastEvent.messageKey)
      || CONVERSATION_REFRESH_EVENTS.has(lastEvent.eventType));
    if (!refreshEvent
        || (refreshed.jobId === jobId && lastEvent.sequence <= refreshed.sequence)) return;
    lastConversationRefresh.current = { jobId, sequence: lastEvent.sequence };
    // Loading persisted state is the external synchronization triggered by a durable event.
    load().catch((failure) => setError(getUserErrorMessage(failure)));
    // The durable terminal event is the refresh trigger for the persisted workspace.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobId, lastEvent?.sequence, lastEvent?.status]);

  async function ensureConversation() {
    if (workspace) return workspace;
    const created = await api.create(true);
    setWorkspace(created); return created;
  }
  async function sendMessage() {
    if (!text.trim() && !Object.keys(answers).length) return;
    setBusy(true); setError('');
    try {
      const active = await ensureConversation();
      const structured = Object.entries(answers).map(([fieldKey, value]) => ({
        fieldKey, value: value === '__OPEN__' ? null : value,
        decisionStatus: value === '__OPEN__' ? 'OPEN' : 'PREFERRED', undecided: value === '__OPEN__',
      }));
      const accepted = await api.send(active.id, text.trim(), structured);
      setWorkspace((current) => ({ ...current, domainState: 'PROCESSING',
        messages: [...(current?.messages || []), accepted.message], activeJobId: accepted.jobId }));
      setText(''); setAnswers({}); setJobId(accepted.jobId);
    } catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setBusy(false); }
  }
  async function upload(file) {
    if (!file) return;
    setBusy(true); setError('');
    try {
      const active = await ensureConversation();
      const saved = await api.attach(active.id, file);
      setWorkspace((current) => ({ ...current, attachments: [...(current?.attachments || []), saved] }));
      setJobId(saved.jobId);
    } catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setBusy(false); }
  }
  async function updateField(fieldKey, raw, decisionStatus) {
    try {
      const value = raw.trim().startsWith('[') || raw.trim().startsWith('{') ? JSON.parse(raw) : raw;
      const brief = await api.editField(workspace.id, fieldKey, value, decisionStatus);
      setWorkspace((current) => ({ ...current, brief }));
    } catch (failure) { setError(getUserErrorMessage(failure)); }
  }
  async function decide(fieldKey, action) {
    setError('');
    try {
      const brief = await api[`${action}Field`](workspace.id, fieldKey);
      setWorkspace((current) => ({ ...current, brief }));
    } catch (failure) { setError(getUserErrorMessage(failure)); }
  }
  async function confirm() {
    setBusy(true); setError('');
    try {
      const brief = await api.confirm(workspace.id);
      setWorkspace((current) => ({ ...current, brief, domainState: 'CONFIRMED' }));
    } catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setBusy(false); }
  }
  async function startBoundary() {
    if (!workspace?.brief?.id) return;
    setBusy(true); setError('');
    try {
      const started = await api.startBoundary(workspace.brief.id);
      setBoundary((current) => ({ ...current, run: started, version: null, stale: false }));
      setJobId(started.jobId || null);
      if (started.status === 'NEEDS_INPUT') setError(started.userMessage || '확정된 Brief가 필요합니다.');
    } catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setBusy(false); }
  }
  async function startConceptExploration() {
    const boundaryId = boundary?.version?.boundaryVersionId;
    if (!conceptReady || !workspace?.brief?.id || !boundaryId || workboard.hasBatch) return;
    const started = await workboard.start(workspace.brief.id, boundaryId);
    if (started?.batchId) setShowIdeaWorkspace(false);
  }

  const fields = new Map((workspace?.brief?.fields || []).map((field) => [field.fieldKey, field]));
  const contradictions = workspace?.messages?.at(-1)?.contradictions || [];
  return <div className="idea-workspace">
    <header className="idea-workspace__heading">
      <div><span>1단계 · 아이디어</span><h2>대화로 사업 기회를 구체화하세요</h2><p>짧은 설명에서 시작해 질문에 답하고, 확인된 Opportunity Brief를 다음 단계의 기준으로 만듭니다.</p></div>
      <span className={`idea-workspace__state state-${(workspace?.domainState || 'EMPTY').toLowerCase()}`}>{workspace?.domainState || 'EMPTY'}</span>
    </header>
    {error && <div className="idea-workspace__error" role="alert">{error}</div>}
    {workboard.hasBatch && showIdeaWorkspace && <button className="idea-workspace__workboard-return" type="button" onClick={() => setShowIdeaWorkspace(false)}>Concept Workboard 보기</button>}
    {!workboard.hasBatch || showIdeaWorkspace ? <>
    <button className="idea-workspace__brief-toggle" type="button" onClick={() => setBriefOpen((value) => !value)} aria-expanded={briefOpen}>Opportunity Brief {briefOpen ? '닫기' : '보기'}</button>
    <div className="idea-workspace__columns">
      <main className="idea-workspace__conversation" aria-label="아이디어 대화">
        {!workspace?.messages?.length && <section className="idea-workspace__empty"><h3>어떤 문제를 해결하고 싶으신가요?</h3><p>한두 문장으로 시작해도 충분합니다. 기존 Idea Source가 있다면 첫 대화 시 안전하게 연결합니다.</p></section>}
        <div className="idea-workspace__messages">
          {(workspace?.messages || []).map((message) => <article key={message.id} className={`idea-message idea-message--${message.role.toLowerCase()}`}>
            <span className="idea-message__role">{message.role === 'USER' ? '나' : 'AI'}</span><p>{message.text}</p>
            {message.type === 'QUESTION_SET' && <div className="idea-question-set">{message.questions.map((question) => <fieldset key={question.id}>
              <legend>{question.prompt}</legend>
              {question.options?.map((option) => <label key={option}><input type={question.type === 'MULTI_SELECT' ? 'checkbox' : 'radio'} name={question.id} onChange={() => setAnswers((current) => ({ ...current, [question.fieldKey]: option }))} />{option}</label>)}
              {question.type === 'FREE_TEXT' && <input aria-label={`${question.fieldKey} 답변`} onChange={(event) => setAnswers((current) => ({ ...current, [question.fieldKey]: event.target.value }))} />}
              {question.allowUndecided && <label><input type="radio" name={question.id} onChange={() => setAnswers((current) => ({ ...current, [question.fieldKey]: '__OPEN__' }))} />아직 결정하지 않음</label>}
            </fieldset>)}</div>}
            <time dateTime={message.occurredAt}>{formatLocalTime(message.occurredAt)}</time>
          </article>)}
        </div>
        {(workspace?.attachments || []).map((file) => <div className="idea-attachment" key={file.id}><span>{file.filename}</span><small>{file.status}{file.failureCode ? ' · 처리 실패' : ''}</small></div>)}
        {jobId && <div className="idea-workspace__timeline"><JobTimeline events={job.events} title="실제 작업 진행 상황" /><small>연결: {job.transport || 'SSE'} · {job.connectionState}</small></div>}
        <div className="idea-composer">
          <textarea value={text} rows={4} onChange={(event) => setText(event.target.value)} placeholder="문제, 고객, 원하는 결과를 자유롭게 적어주세요." />
          <div><label className="idea-composer__file">TXT/DOCX 첨부<input type="file" accept=".txt,.docx,text/plain,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={(event) => void upload(event.target.files?.[0])} /></label><button type="button" disabled={busy || (!text.trim() && !Object.keys(answers).length)} onClick={() => void sendMessage()}>전송</button></div>
        </div>
      </main>
      <aside className={`idea-workspace__brief ${briefOpen ? 'is-open' : ''}`} aria-label="Opportunity Brief">
        <header><div><h3>Opportunity Brief</h3><p>{workspace?.brief ? `Version ${workspace.brief.version} · ${workspace.brief.state}` : '아직 작성 전'}</p></div>{workspace?.brief?.hash && <small>Hash 확인됨</small>}</header>
        <div className="idea-brief-fields">{allFields.map((fieldKey) => <BriefField key={`${fieldKey}-${workspace?.brief?.version || 0}`} fieldKey={fieldKey} field={fields.get(fieldKey)} disabled={!workspace || workspace.brief?.state === 'CONFIRMED'} onSave={updateField} onDecide={decide} />)}</div>
        {!!workspace?.brief?.missingFields?.length && <div className="idea-brief-missing" role="status"><strong>확정 전 필요한 정보</strong><ul>{workspace.brief.missingFields.map((field) => <li key={field}>{labels[field] || field}</li>)}</ul></div>}
        {!!contradictions.length && <div className="idea-brief-missing" role="status"><strong>해결되지 않은 모순</strong><ul>{contradictions.map((item) => <li key={item}>{item}</li>)}</ul></div>}
        <button className="idea-brief-confirm" type="button" disabled={busy || !workspace?.brief || workspace.brief.missingFields.length > 0 || contradictions.length > 0 || workspace.brief.state === 'CONFIRMED'} onClick={() => void confirm()}>Brief 전체 확인</button>
        <BoundarySummary boundary={boundary} confirmedBrief={workspace?.brief?.state === 'CONFIRMED'}
          busy={busy || workboard.network === 'LOADING'} onStart={startBoundary}
          conceptAction={conceptReady && !workboard.hasBatch ? <button type="button" onClick={() => void startConceptExploration()}>Concept 탐색 시작</button> : null} />
      </aside>
    </div></> : <ConceptWorkboard workboard={workboard} brief={workspace?.brief} boundary={boundary}
      messages={workspace?.messages} onReturnToBrief={() => setShowIdeaWorkspace(true)} />}
  </div>;
}

export function BoundarySummary({ boundary, confirmedBrief, busy, onStart, conceptAction = null }) {
  const status = boundary?.version?.status || boundary?.run?.status;
  const rules = boundary?.version?.rules || [];
  const byType = (type) => rules.filter((rule) => rule.ruleType === type);
  return <section className="idea-boundary" aria-label="Regulatory Boundary">
    <header><h3>Regulatory Boundary</h3>{status && <span className={`idea-boundary__status status-${status.toLowerCase()}`}>{status}</span>}</header>
    {!status && <><p>확정된 Brief를 기준으로 공식 근거와 Concept 실행 경계를 생성합니다.</p>
      <button type="button" disabled={!confirmedBrief || busy} onClick={() => void onStart()}>규제 경계 생성</button></>}
    {boundary?.stale && <div className="idea-boundary__notice" role="status">Brief가 변경되어 이전 경계는 STALE입니다. 새 경계를 생성해 주세요.</div>}
    {status === 'READY' && <div className="idea-boundary__groups">
      <RuleGroup title="허용 가능한 구현 방향" rules={byType('ALLOWED_PATTERN')} />
      <RuleGroup title="피해야 할 역할·활동" rules={[...byType('PROHIBITED_ROLE'), ...byType('PROHIBITED_ACTIVITY')]} />
      <RuleGroup title="필수 통제" rules={byType('REQUIRED_CONTROL')} />
      <RuleGroup title="파트너·자격" rules={byType('REQUIRED_PARTNER')} />
      <RuleGroup title="필수 고지" rules={byType('REQUIRED_DISCLOSURE')} />
      {!!boundary.version.sourceWarnings?.length && <div className="idea-boundary__notice"><strong>Source Warning</strong><ul>{boundary.version.sourceWarnings.map((warning) => <li key={warning}>{warning}</li>)}</ul></div>}
      {conceptAction && <div className="idea-boundary__concept-action"><p>확정된 Brief와 READY Boundary를 기준으로 검증 가능한 Concept 3개를 탐색합니다.</p>{conceptAction}</div>}
    </div>}
    {status === 'NEEDS_INPUT' && <div className="idea-boundary__notice"><strong>추가 확인이 필요합니다</strong>
      {(boundary?.version?.questions || []).map((question) => <article key={question.questionId}><h4>{question.question}</h4><p>{question.reason}</p><small>관련 Brief Field: {question.fieldKey}</small></article>)}</div>}
    {status === 'BLOCKED' && <div className="idea-boundary__blocked"><strong>고정 조건과 규제 경계가 충돌합니다</strong>
      {(boundary?.version?.conflicts || []).map((conflict) => <article key={conflict.conflictId}><h4>{conflict.affectedFieldKey}</h4><p>{conflict.reason}</p><ul>{conflict.userActionOptions?.map((option) => <li key={option}>{option}</li>)}</ul></article>)}
      <p>Brief 수정으로 돌아가 새 Version을 확인해 주세요.</p></div>}
    {status === 'FAILED' && <div className="idea-boundary__notice" role="alert">규제 경계를 생성하지 못했습니다. {boundary?.run?.retryable ? '잠시 후 다시 시도할 수 있습니다.' : '입력과 설정을 확인해 주세요.'}
      {boundary?.run?.retryable && <button type="button" disabled={busy} onClick={() => void onStart()}>규제 경계 다시 시도</button>}</div>}
  </section>;
}

function RuleGroup({ title, rules }) {
  if (!rules.length) return null;
  return <section><h4>{title}</h4><ul>{rules.map((rule) => <li key={rule.ruleId}><strong>{rule.title}</strong><p>{rule.normalizedRequirement}</p></li>)}</ul></section>;
}

function BriefField({ fieldKey, field, disabled, onSave, onDecide }) {
  const [value, setValue] = useState(display(field?.value));
  const [status, setStatus] = useState(field?.decisionStatus || 'OPEN');
  const valueRef = useRef(null);
  return <section className={`idea-brief-field ${field?.userConfirmed ? 'is-confirmed' : 'is-proposed'}`}>
    <div><label htmlFor={`brief-${fieldKey}`}>{labels[fieldKey]}</label><span>{field?.userConfirmed ? '사용자 확인' : field ? 'AI/자료 제안' : '누락'}</span></div>
    <textarea ref={valueRef} id={`brief-${fieldKey}`} rows={2} value={value} disabled={disabled} onChange={(event) => setValue(event.target.value)} />
    <div><select aria-label={`${labels[fieldKey]} 결정 상태`} value={status} disabled={disabled} onChange={(event) => setStatus(event.target.value)}>{Object.entries(statusLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select><button type="button" disabled={disabled || !value.trim()} onClick={() => onSave(fieldKey, valueRef.current?.value || '', status)}>직접 저장</button></div>
    {field && !field.userConfirmed && <div className="idea-brief-field__decision"><button type="button" disabled={disabled} onClick={() => onDecide(fieldKey, 'adopt')}>제안 채택</button><button type="button" disabled={disabled} onClick={() => onDecide(fieldKey, 'reject')}>거절</button></div>}
    {field?.sourceType && <small>출처: {field.sourceType} · 상태: {field.decisionStatus}</small>}
  </section>;
}
function display(value) { if (value == null) return ''; return typeof value === 'string' ? value : JSON.stringify(value); }
