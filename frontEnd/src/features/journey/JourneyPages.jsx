import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { createJourneyApi } from './journeyApi.js';
import { journeyFailureMessage, legalSourceMessage } from './journeyFailure.js';
import { ConversationalIdeaWorkspace } from '../conversational-idea/ConversationalIdeaWorkspace.jsx';
import './journey.css';

const readinessLabel = {
  UNDER_SPECIFIED: '정보 보완 필요', APPROPRIATE: '검토 준비됨', OVER_SPECIFIED: '범위 정리 필요',
};
const legalLabel = {
  PASS: '통과', PASS_WITH_CONDITIONS: '조건부 통과', REVISION_REQUIRED: '수정 필요',
  PROHIBITED: '진행 금지', INSUFFICIENT_INFORMATION: '정보 부족', EXPERT_REVIEW_REQUIRED: '전문가 검토 필요',
};
const originFieldLabel = {
  productServiceDescription: '제품·서비스 설명', problem: '해결할 문제', target: '목표 고객',
  solution: '해결 방식', coreValue: '핵심 가치', primaryCategory: '주 카테고리',
  targetRegion: '초기 대상 지역', fixedValues: '변경할 수 없는 요소', pricingIntent: '가격 의도',
  revenueModelIntent: '수익모델 의도', salesChannelIntent: '판매 채널 의도', knownUnitCost: '알고 있는 원가',
  alternatives: '현재 대안', knownCompetitors: '알고 있는 경쟁사', differentiationIntent: '차별화 의도',
  internalConstraints: '내부 제약',
};
const readinessText = { READY: 'READY', NEEDS_INPUT: 'NEEDS_INPUT', BLOCKED: 'BLOCKED' };

function displayValue(value) {
  if (value == null || value === '') return '미확정';
  if (Array.isArray(value)) return value.length ? value.map(displayValue).join(', ') : '미확정';
  if (typeof value === 'object') return Object.entries(value).filter(([, item]) => item != null && displayValue(item) !== '미확정').map(([key, item]) => `${key}: ${displayValue(item)}`).join(' · ') || '미확정';
  return String(value);
}

function ErrorBanner({ message }) {
  return message ? <div className="journey-error" role="alert"><strong>요청을 완료하지 못했습니다.</strong><span>{message}</span><button type="button" onClick={() => window.location.reload()}>현재 단계 다시 불러오기</button></div> : null;
}

function BusyOverlay({ label }) {
  return <div className="journey-overlay" role="status" aria-live="polite"><span className="journey-spinner" /><strong>{label}</strong><p>진행 상태와 완료 결과는 저장되며 새로고침 후에도 복원됩니다.</p></div>;
}

function ResultList({ title, items }) {
  return <section className="journey-result-section"><h3>{title}</h3>{items?.length
    ? <ul>{items.map((item, index) => <li key={`${title}-${index}`}>{typeof item === 'string' ? item : Object.entries(item || {}).map(([key, value]) => `${key}: ${Array.isArray(value) ? value.join(', ') : String(value)}`).join(' · ')}</li>)}</ul>
    : <p className="journey-muted">해당 항목이 없습니다.</p>}</section>;
}

export function IdeaJourneyPage() {
  if (import.meta.env.VITE_CONVERSATIONAL_VALIDATION_WORKSPACE_ENABLED === 'true') {
    return <ConversationalIdeaWorkspace />;
  }
  return <LegacyIdeaJourneyPage />;
}

function LegacyIdeaJourneyPage() {
  const { projectId } = useParams();
  const client = useApiClient();
  const api = useMemo(() => createJourneyApi(client, projectId), [client, projectId]);
  const [tab, setTab] = useState('text');
  const [title, setTitle] = useState('');
  const [text, setText] = useState('');
  const [file, setFile] = useState(null);
  const [source, setSource] = useState(null);
  const [interpretation, setInterpretation] = useState(null);
  const [origin, setOrigin] = useState(null);
  const [answers, setAnswers] = useState({});
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    // Reset the project-scoped form before synchronizing the new project data.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setTab('text'); setTitle(''); setText(''); setFile(null);
    setSource(null); setInterpretation(null); setOrigin(null); setAnswers({}); setBusy(''); setError('');
    Promise.all([api.currentIdea(), api.currentInterpretation(), api.currentIdeaOrigin()]).then(([saved, run, workspace]) => {
      if (!active) return;
      setSource(saved); setInterpretation(run?.ideaSourceId === saved?.id ? run : null);
      setOrigin(workspace);
      setAnswers(Object.fromEntries((workspace?.questions || []).map((question) => [question.id, { answer: question.answer || '', answerSource: question.answerSource || '사용자 직접 입력' }])));
      setTitle(saved?.title || ''); setText(saved?.originalText || ''); setTab(saved?.sourceType === 'FILE' ? 'file' : 'text');
    }).catch((failure) => active && setError(getUserErrorMessage(failure)));
    return () => { active = false; };
  }, [api]);

  const activeInterpretation = ['PENDING', 'RUNNING'].includes(interpretation?.state);
  useEffect(() => {
    if (!activeInterpretation) return undefined;
    const timer = window.setInterval(async () => {
      try {
        const run = await api.currentInterpretation();
        setInterpretation(run);
        if (run?.state === 'SUCCEEDED') {
          const workspace = await api.currentIdeaOrigin();
          setOrigin(workspace);
          setAnswers(Object.fromEntries((workspace?.questions || []).map((question) => [question.id, { answer: question.answer || '', answerSource: question.answerSource || '사용자 직접 입력' }])));
        }
      } catch (failure) { setError(getUserErrorMessage(failure)); }
    }, 2000);
    return () => window.clearInterval(timer);
  }, [activeInterpretation, interpretation?.taskRunId, api]);

  async function save() {
    setError(''); setBusy('아이디어를 저장하고 있습니다');
    try {
      const saved = tab === 'file' ? await api.saveFile(title.trim(), file) : await api.saveText({ title: title.trim() || null, text });
      setSource(saved); setInterpretation(null); setOrigin(null); setAnswers({}); return saved;
    } catch (failure) { setError(getUserErrorMessage(failure)); throw failure; }
    finally { setBusy(''); }
  }

  async function interpret() {
    let activeSource = source;
    try {
      if (!source || (tab === 'text' && source.originalText !== text) || (tab === 'file' && file)) activeSource = await save();
      setError(''); setBusy('AI가 아이디어를 해석하고 있습니다');
      setInterpretation(await api.interpret());
      const workspace = await api.currentIdeaOrigin();
      setOrigin(workspace);
      setAnswers(Object.fromEntries((workspace?.questions || []).map((question) => [question.id, { answer: question.answer || '', answerSource: question.answerSource || '사용자 직접 입력' }])));
    } catch (failure) {
      if (failure?.code === 'RESOURCE_VERSION_CONFLICT') {
        try {
          const recovered = await api.currentInterpretation();
          if (recovered?.state === 'SUCCEEDED' && recovered?.result
              && recovered?.ideaSourceId === activeSource?.id) {
            setInterpretation(recovered);
            const workspace = await api.currentIdeaOrigin();
            setOrigin(workspace);
            setAnswers(Object.fromEntries((workspace?.questions || []).map((question) => [question.id, { answer: question.answer || '', answerSource: question.answerSource || '사용자 직접 입력' }])));
            setError('');
            return;
          }
        } catch {
          // Keep the original conflict because it is the actionable failure.
        }
      }
      setError(getUserErrorMessage(failure));
      try { setInterpretation(await api.currentInterpretation()); } catch { /* Keep the original actionable error. */ }
    }
    finally { setBusy(''); }
  }

  async function retryInterpretation() {
    if (!interpretation?.retryable || !interpretation?.taskRunId) return;
    setError(''); setBusy('아이디어 해석을 다시 요청하고 있습니다');
    try {
      await api.retryTaskRun(interpretation.taskRunId);
      setInterpretation((current) => ({ ...current, state: 'RUNNING', retryable: false, error: null }));
    } catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setBusy(''); }
  }

  async function saveAnswer(question) {
    const value = answers[question.id] || {};
    setError(''); setBusy('질문 답변을 저장하고 있습니다');
    try {
      const saved = await api.answerIdeaOriginQuestion(question.id, { answer: value.answer || '', answerSource: value.answerSource || '' });
      setOrigin((current) => ({ ...current, questions: current.questions.map((item) => item.id === saved.id ? saved : item) }));
    } catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setBusy(''); }
  }

  async function applyOrigin() {
    setError(''); setBusy('보완 내용을 반영해 Idea Origin Version을 만들고 있습니다');
    try { setOrigin(await api.applyIdeaOrigin(origin.draft.id)); }
    catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setBusy(''); }
  }

  const result = interpretation?.result;
  const draft = origin?.draft;
  const confirmed = origin?.confirmed;
  const snapshot = confirmed?.snapshot || draft?.snapshot;
  const unansweredRequired = (origin?.questions || []).some((question) => question.status !== 'USER_CONFIRMED');
  const understoodFields = ['productServiceDescription', 'problem', 'target', 'solution', 'coreValue', 'primaryCategory', 'targetRegion', 'fixedValues'];
  return <div className="journey-page">
    {busy && <BusyOverlay label={busy} />}
    <header className="journey-page__heading"><div><span>1단계 · 아이디어</span><h2>아이디어를 명확한 검토 입력으로 만드세요</h2><p>원문을 저장한 뒤 실제 AI가 사실, 가정, 제약과 추가 질문을 분리합니다.</p></div><span className={`journey-save-state ${source ? 'is-saved' : ''}`}>{source ? '저장됨' : '저장 전'}</span></header>
    <ErrorBanner message={error} />
    <section className="journey-card journey-intake">
      <div className="journey-tabs" role="tablist" aria-label="아이디어 입력 방식">
        <button type="button" role="tab" aria-selected={tab === 'text'} onClick={() => setTab('text')}>텍스트 입력</button>
        <button type="button" role="tab" aria-selected={tab === 'file'} onClick={() => setTab('file')}>파일 업로드</button>
      </div>
      <label>아이디어 제목 또는 요약<input value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} placeholder="예: 소상공인 재고 예측 서비스" /></label>
      {tab === 'text' ? <label>아이디어 내용<textarea value={text} maxLength={200000} rows={10} onChange={(event) => setText(event.target.value)} placeholder="누구의 어떤 문제를 어떻게 해결하는지 적어 주세요." /><small>{text.length.toLocaleString()} / 200,000자</small></label>
        : <label>DOCX 또는 TXT 파일<input type="file" accept=".docx,.txt,text/plain,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={(event) => setFile(event.target.files?.[0] || null)} /><small>{file?.name || source?.originalFileReference || '선택된 파일 없음'}</small></label>}
      <div className="journey-actions"><button className="journey-button secondary" type="button" disabled={busy || (tab === 'text' ? !text.trim() : !file)} onClick={() => void save()}>아이디어 저장</button><button className="journey-button" type="button" disabled={busy || activeInterpretation || interpretation?.state === 'FAILED' || (!source && (tab === 'text' ? !text.trim() : !file))} onClick={() => void interpret()}>{activeInterpretation ? 'AI 해석 진행 중' : interpretation?.state === 'FAILED' ? '실패 상태 확인 필요' : 'AI 해석 실행'}</button></div>
    </section>
    {interpretation?.state === 'FAILED' && <section className="journey-card journey-run-card"><div><h3>Idea 해석 실패</h3><p>{journeyFailureMessage(interpretation.error)}</p><small>{interpretation.error || 'AI_SERVICE_UNAVAILABLE'}</small></div>{interpretation.retryable ? <button className="journey-button" disabled={busy} onClick={() => void retryInterpretation()}>동일 입력으로 재시도</button> : interpretation.error === 'AI_CONFIGURATION_INVALID' ? <button className="journey-button" disabled={busy} onClick={() => void interpret()}>설정 수정 후 새 실행</button> : <p className="journey-muted">입력을 수정해 새 Idea Source로 다시 실행하세요.</p>}</section>}
    {!result || !draft ? <section className="journey-empty"><span>AI</span><h3>아직 Idea Origin Draft가 없습니다.</h3><p>아이디어를 저장하고 AI 해석을 실행하면 구조화 Draft와 부족한 질문이 표시됩니다.</p></section> : <>
      <section className="journey-card journey-readiness"><div><h3>진행 준비도</h3><p>법률 실행은 이 묶음에서 시작하지 않으며 준비 상태만 계산합니다.</p></div><div className="journey-readiness-grid">
        {[['Idea Origin', origin.readiness.ideaOrigin], ['Legal Precheck', origin.readiness.legalPrecheck], ['Concept Build', origin.readiness.conceptBuild]].map(([label, state]) => <div key={label}><span>{label}</span><strong className={`readiness-${state?.toLowerCase()}`}>{readinessText[state] || state}</strong></div>)}
      </div></section>
      <section className="journey-card journey-result">
        <div className="journey-result__header"><div><span className={`journey-badge ${result.readiness?.toLowerCase()}`}>{readinessLabel[result.readiness] || result.readiness}</span><h2>이해한 사업 · 필수 Origin 필드 · {confirmed ? `v${confirmed.versionNumber}` : 'Draft'}</h2></div><button type="button" className="journey-text-button" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}>원문 편집(보조)</button></div>
        <section className="journey-highlight"><h3>AI가 이해한 설명</h3><p>{result.normalizedDescription}</p></section>
        <div className="journey-origin-grid">{understoodFields.map((field) => <article key={field} className={displayValue(snapshot?.[field]) === '미확정' ? 'is-missing' : ''}><span>{originFieldLabel[field]}</span><p>{displayValue(snapshot?.[field])}</p></article>)}</div>
      </section>
      <section className="journey-card journey-result">
        <div className="journey-result__header"><div><span className="journey-badge">질문별 저장</span><h2>필수 보완 질문</h2></div><span className="journey-muted">원문 전체를 다시 작성하지 않아도 됩니다.</span></div>
        {origin.questions.length ? <div className="journey-question-list">{origin.questions.map((question) => <article key={question.id} className={question.status === 'USER_CONFIRMED' ? 'is-answered' : ''}>
          <div><span className="journey-question-requirement">{question.requirement}</span><h3>{question.question}</h3><p>{question.reason}</p></div>
          <label>답변<textarea rows={3} disabled={Boolean(confirmed)} value={answers[question.id]?.answer || ''} onChange={(event) => setAnswers((current) => ({ ...current, [question.id]: { ...current[question.id], answer: event.target.value } }))} /></label>
          <label>확인 출처<input disabled={Boolean(confirmed)} value={answers[question.id]?.answerSource || ''} onChange={(event) => setAnswers((current) => ({ ...current, [question.id]: { ...current[question.id], answerSource: event.target.value } }))} placeholder="예: 창업자 결정, 내부 운영 문서" /></label>
          <button className="journey-button secondary" type="button" disabled={Boolean(confirmed) || busy || !answers[question.id]?.answer?.trim() || !answers[question.id]?.answerSource?.trim()} onClick={() => void saveAnswer(question)}>{question.status === 'USER_CONFIRMED' ? '답변 수정 저장' : '이 답변 저장'}</button>
        </article>)}</div> : <p className="journey-muted">필수 보완 질문이 없습니다. Draft를 확인하고 Origin을 확정하세요.</p>}
      </section>
      <div className="journey-separation-grid">
        <section className="journey-card journey-result confirmed-values"><h2>사용자 확정값</h2><p>확인 출처와 함께 잠기며 AI가 덮어쓰지 않습니다.</p><ResultList title="확정된 값" items={Object.entries(confirmed?.confirmedValues || {}).map(([key, value]) => `${originFieldLabel[key] || key}: ${displayValue(value?.value ?? value)} · 출처: ${value?.source || '사용자 입력'}`)} /></section>
        <section className="journey-card journey-result assumptions"><h2>AI 가정</h2><p>사용자가 확정한 사실과 분리되어 저장됩니다.</p><ResultList title="가정" items={draft.assumptions || []} /></section>
        <section className="journey-card journey-result missing-values"><h2>누락값</h2><p>답변 전까지 확정값으로 사용하지 않습니다.</p><ResultList title="미확정 필드" items={origin.questions.filter((question) => question.status !== 'USER_CONFIRMED').map((question) => originFieldLabel[question.targetField] || question.targetField)} /></section>
      </div>
      <section className="journey-next"><div><strong>{confirmed ? `Idea Origin v${confirmed.versionNumber}이 저장되었습니다.` : unansweredRequired ? '필수 질문에 개별 답변해 주세요.' : '보완 내용을 새 Idea Origin Version으로 확정할 수 있습니다.'}</strong><p>답변은 원문에 삽입되지 않고 구조화된 확정값으로 반영됩니다.</p></div>{confirmed ? <span className="journey-badge">새로고침 복원 가능</span> : <button className="journey-button" type="button" disabled={busy || unansweredRequired} onClick={() => void applyOrigin()}>{origin.questions.length ? '보완 내용 반영' : 'Idea Origin 확정'}</button>}</section>
    </>}
  </div>;
}

export function LegalJourneyPage() {
  const { projectId } = useParams();
  const client = useApiClient();
  const api = useMemo(() => createJourneyApi(client, projectId), [client, projectId]);
  const [origin, setOrigin] = useState(null);
  const [precheck, setPrecheck] = useState(null);
  const [answers, setAnswers] = useState({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const load = async () => {
    const [workspace, legal] = await Promise.all([api.currentIdeaOrigin(), api.currentLegalPrecheck()]);
    setOrigin(workspace); setPrecheck(legal);
    setAnswers(Object.fromEntries((workspace?.questions || []).filter((question) => question.requirement === 'REQUIRED_FOR_LEGAL_PRECHECK')
      .map((question) => [question.id, { answer: question.answer || '', answerSource: question.answerSource || '사용자 직접 입력' }])));
  };
  // Loading is the external synchronization performed by this effect.
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { let active = true; load().catch((failure) => active && setError(getUserErrorMessage(failure))); return () => { active = false; }; }, [api]); // eslint-disable-line react-hooks/exhaustive-deps
  const activeRun = ['QUEUED', 'RUNNING'].includes(precheck?.run?.state);
  useEffect(() => {
    if (!activeRun) return undefined;
    const poll = async () => {
      const legal = await api.currentLegalPrecheck();
      setPrecheck(legal);
      if (!['QUEUED', 'RUNNING'].includes(legal?.run?.state)) {
        const workspace = await api.currentIdeaOrigin();
        setOrigin(workspace);
        setAnswers(Object.fromEntries((workspace?.questions || []).filter((question) => question.requirement === 'REQUIRED_FOR_LEGAL_PRECHECK')
          .map((question) => [question.id, { answer: question.answer || '', answerSource: question.answerSource || '사용자 직접 입력' }])));
      }
    };
    const timer = window.setInterval(() => poll().catch((failure) => setError(getUserErrorMessage(failure))), 2000);
    return () => window.clearInterval(timer);
  }, [activeRun, precheck?.run?.taskRunId]); // eslint-disable-line react-hooks/exhaustive-deps
  const confirmed = origin?.confirmed;
  async function run() {
    setBusy(true); setError('');
    try { const started = await api.startLegalPrecheck(); setPrecheck({ run: started, version: null, stale: false }); }
    catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setBusy(false); }
  }
  async function answer(question) {
    const value = answers[question.id]; setBusy(true); setError('');
    try { await api.answerIdeaOriginQuestion(question.id, value); await load(); }
    catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setBusy(false); }
  }
  async function applyAnswers() {
    setBusy(true); setError('');
    try { await api.applyLegalAnswersAndRestart(confirmed.id); await load(); }
    catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setBusy(false); }
  }
  async function retry() {
    if (!precheck?.run?.retryable) return;
    setBusy(true); setError('');
    try {
      await api.retryTaskRun(precheck.run.taskRunId);
      await load();
    }
    catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setBusy(false); }
  }
  async function refreshSources() {
    setBusy(true); setError('');
    try { await api.refreshLegalPrecheckSources(); await load(); }
    catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setBusy(false); }
  }
  async function acceptRevisions() {
    setBusy(true); setError('');
    try {
      const indexes = (precheck.version.revisionSuggestions || []).map((_, index) => index);
      await api.acceptLegalRevisionsAndRestart(precheck.version.id, indexes);
      await load();
    }
    catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setBusy(false); }
  }
  const result = precheck?.version;
  const legalQuestions = (origin?.questions || []).filter((question) => question.requirement === 'REQUIRED_FOR_LEGAL_PRECHECK'
    && question.originDraftVersionId === confirmed?.id);
  const unanswered = legalQuestions.filter((question) => question.status !== 'USER_CONFIRMED');
  const advance = !!result?.conceptBuilderAllowed && !precheck?.stale && !unanswered.length;
  const failedCurrentRun = precheck?.run?.state === 'FAILED' && !precheck?.stale;
  const sourceNotice = legalSourceMessage(result?.sourceStatus);
  return <div className="journey-page">
    {(busy || activeRun) && <BusyOverlay label={activeRun ? '법제처 공식 Source와 조문을 확인하고 있습니다' : '변경 내용을 저장하고 있습니다'} />}
    <header className="journey-page__heading"><div><span>2단계 · Legal Precheck</span><h2>공식 법령 근거와 Concept Guardrail을 확인하세요</h2><p>확정된 Idea Origin만 입력으로 사용하며 결과와 Guardrail은 Version으로 저장됩니다.</p></div><span className={`journey-save-state ${result ? 'is-saved' : ''}`}>{precheck?.stale ? 'STALE' : result ? `v${result.versionNumber} 저장됨` : '실행 전'}</span></header>
    <ErrorBanner message={error} />
    <aside className="journey-legal-notice"><strong>사업기획 단계의 사전검토 · 공식 법률 자문 아님</strong><p>법제처 공식 Source를 사용하지만, Source가 일부이거나 Registry 밖 영역이면 전문가 검토가 필요합니다.</p></aside>
    {!confirmed ? <section className="journey-empty"><h3>확정된 Idea Origin이 필요합니다.</h3><Link className="journey-button" to={`/app/projects/${projectId}`}>Idea Origin 확정하기</Link></section> : <>
      <section className="journey-card journey-run-card"><div><h3>Idea Origin v{confirmed.versionNumber}</h3><p>{displayValue(confirmed.snapshot?.productServiceDescription)}</p>{precheck?.stale && <p className="journey-warning">Origin이 변경되어 이전 Precheck와 Guardrail은 STALE입니다. 아래 과거 결과는 현재 Guardrail로 사용되지 않습니다.</p>}</div><button className="journey-button" type="button" disabled={busy || activeRun || failedCurrentRun || (!!result && !precheck?.stale)} onClick={() => void run()}>{precheck?.stale ? '현재 Origin으로 다시 실행' : result ? '검토 완료' : failedCurrentRun ? '실패 상태 확인 필요' : 'Legal Precheck 실행'}</button></section>
      {failedCurrentRun && <section className="journey-card journey-run-card"><div><h3>검토 실행 실패</h3><p>{journeyFailureMessage(precheck.run.errorCode)}</p><small>{precheck.run.errorCode || 'AI_SERVICE_UNAVAILABLE'}</small></div>{precheck.run.retryable ? <button className="journey-button" disabled={busy} onClick={() => void retry()}>동일 입력으로 재시도</button> : precheck.run.errorCode === 'AI_CONFIGURATION_INVALID' ? <button className="journey-button" disabled={busy} onClick={() => void run()}>설정 수정 후 새 실행</button> : ['AI_RESULT_INVALID', 'AI_SERVICE_UNAVAILABLE'].includes(precheck.run.errorCode) ? <button className="journey-button" disabled={busy} onClick={() => void refreshSources()}>Origin 유지하고 새 Legal 검토 생성</button> : <Link className="journey-button secondary" to={`/app/projects/${projectId}`}>Idea·입력 확인</Link>}</section>}
      {!result ? <section className="journey-empty"><h3>{activeRun ? 'Legal Precheck 실행 중입니다.' : '저장된 Precheck 결과가 없습니다.'}</h3><p>완료 결과는 새로고침 후에도 복원됩니다.</p></section> : <section className="journey-card journey-result legal">
        <div className="journey-result__header"><div><span className={`journey-badge legal-${result.status?.toLowerCase()}`}>{legalLabel[result.status] || result.status}</span><h2>Legal Precheck v{result.versionNumber}</h2></div><span className={result.sourceVerified ? 'journey-verified' : 'journey-unverified'}>{result.sourceVerified ? '공식 Source 확인' : result.sourceStatus}</span></div>
        <section className="journey-highlight"><h3>종합 결과</h3><p>{result.summary}</p>{sourceNotice && <p className="journey-warning">{sourceNotice}</p>}<p><strong>Concept Builder:</strong> {advance ? '진행 가능' : '진행 차단'}</p>{result.sourceStatus !== 'SOURCE_COMPLETE' && <button className="journey-button secondary" disabled={busy || activeRun} onClick={() => void refreshSources()}>현재 Origin 유지하고 공식 Source 다시 확인</button>}</section>
        <section className="journey-result-section"><h3>Category별 Finding과 5단 Reasoning</h3><div className="legal-finding-list">{(result.findings || []).map((finding) => <article key={finding.category}><header><strong>{finding.category}</strong><span>{finding.applicability}</span></header><p>{finding.summary}</p><ol><li><b>입력 근거</b> {(finding.reasoning?.inputBasis || []).join(' · ') || '추가 확인 필요'}</li><li><b>규제 영역</b> {finding.reasoning?.regulatoryArea}</li><li><b>의무</b> {finding.reasoning?.obligation}</li><li><b>위반 결과</b> {finding.reasoning?.consequence}</li><li><b>필요 조치</b> {finding.reasoning?.requiredAction}</li></ol></article>)}</div></section>
        <section className="journey-result-section"><h3>공식 Evidence</h3><div className="legal-evidence-list">{(result.evidence || []).map((item) => <article key={item.evidenceId}><header><strong>{item.lawName} {item.article}</strong><span>{item.role}</span></header><p>{item.plainSummary}</p><small>{item.whyRelevant}</small><blockquote>{item.excerpt}</blockquote><footer><span>시행일 {item.effectiveDate || '확인 필요'} · 조회 {item.verifiedAt}</span><a href={item.lawUrl} target="_blank" rel="noreferrer">법제처 공식 원문</a></footer></article>)}</div></section>
        {legalQuestions.length > 0 && <section className="journey-result-section"><h3>추가 확인 질문</h3>{legalQuestions.map((question) => <article className="origin-question" key={question.id}><strong>{question.question}</strong><p>{question.reason}</p><textarea rows="3" disabled={question.status === 'USER_CONFIRMED'} value={answers[question.id]?.answer || ''} onChange={(event) => setAnswers((current) => ({ ...current, [question.id]: { ...current[question.id], answer: event.target.value } }))} />{question.status === 'USER_CONFIRMED' ? <span className="journey-badge">답변 저장됨</span> : <button className="journey-button secondary" disabled={busy || !answers[question.id]?.answer?.trim()} onClick={() => void answer(question)}>답변 저장</button>}</article>)}{!unanswered.length && <button className="journey-button" disabled={busy} onClick={() => void applyAnswers()}>모든 답변 반영 후 자동 재검토</button>}</section>}
        {(result.revisionSuggestions || []).length > 0 && <section className="journey-result-section"><h3>Origin 통합 수정 계획</h3><p className="journey-muted">같은 법률 Category의 근거를 하나의 변경안으로 묶었습니다. 아래 계획 전체를 한 Origin Version에 반영하고 Legal Precheck를 한 번만 다시 실행합니다.</p>{result.revisionSuggestions.map((suggestion, index) => <article className="origin-question" key={`${suggestion.category || suggestion.targetField}-${index}`}><strong>{suggestion.category || suggestion.targetField}</strong><p><b>문제가 된 Origin 근거:</b> {suggestion.originEvidence}</p><p>{suggestion.reason}</p><blockquote>{suggestion.proposedValue}</blockquote>{(suggestion.evidenceIds || []).length > 0 && <small>공식 근거 {suggestion.evidenceIds.length}건 통합</small>}</article>)}<button className="journey-button" disabled={busy || precheck.stale} onClick={() => void acceptRevisions()}>모든 수정 계획 일괄 적용 후 자동 재검토</button></section>}
        <section className="journey-result-section"><h3>최종 Legal Guardrail Set v{result.guardrails?.versionNumber}</h3><div className="journey-result-grid"><ResultList title="Hard Constraints" items={result.guardrails?.hardConstraints} /><ResultList title="Prohibited Patterns" items={result.guardrails?.prohibitedPatterns} /><ResultList title="Conditional Constraints" items={result.guardrails?.conditionalConstraints} /><ResultList title="Required Disclosures" items={result.guardrails?.requiredDisclosures} /><ResultList title="Operational Controls" items={result.guardrails?.requiredOperationalControls} /></div></section>
        <div className="journey-next"><div><strong>{advance ? '현재 Guardrail로 Concept Builder에 진입할 수 있습니다.' : '질문·수정 또는 Source 보완 전까지 Concept Builder가 차단됩니다.'}</strong><p>Registry {result.registryVersion} · {result.sourceStatus}</p></div><Link className={`journey-button ${advance ? '' : 'disabled'}`} aria-disabled={!advance} onClick={(event) => !advance && event.preventDefault()} to={advance ? `/app/projects/${projectId}/journey/concept` : '#'}>Concept Builder로</Link></div>
      </section>}
    </>}
  </div>;
}
