import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useOutletContext, useParams } from 'react-router-dom';
import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { useJobEvents } from '../../../shared/async-events/index.js';
import { AppIcon, ProjectStageHeader, ProjectWorkspace } from '../../../shared/ui/index.js';
import { createLaunchReadinessApi } from '../api/launchReadinessApi.js';
import '../styles/launch-readiness.css';

const ACTIVE = new Set(['QUEUED', 'READY', 'RUNNING']);
const WORKFLOW = [
  ['입력 템플릿 받기', '출시 승인에 필요한 항목과 작성 기준을 확인합니다.'],
  ['출시 계획 작성·업로드', '현재 계획과 미해결 위험을 DOCX에 작성합니다.'],
  ['독립 분석 실행', '업로드한 문서를 기준으로 출시 게이트와 보완 과제를 확인합니다.'],
  ['결과·보고서 확인', '결과를 검토하고 보고서를 출력하거나 내려받습니다.'],
];
const ambiguousMutation = (error) => !error?.status || error.status >= 500;

function downloadDocumentBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.hidden = true;
  document.body.append(anchor);
  try { anchor.click(); } finally { anchor.remove(); setTimeout(() => URL.revokeObjectURL(url), 30_000); }
}

function ExecutionStatus({ jobId, events, onDetail }) {
  const latest = events.events?.at(-1);
  const activity = ({ DOCUMENT_ACCEPTED: '입력 문서를 확인했습니다.', QUEUED: '분석 작업을 준비하고 있습니다.',
    ANALYZING: '출시 범위, 승인 기준과 미해결 위험을 분석하고 있습니다.', COMPLETED: '출시 준비 결과를 정리했습니다.',
    FAILED: '분석을 완료하지 못했습니다.' })[latest?.stage] ?? '분석 작업을 준비하고 있습니다.';
  const reached = latest?.stage === 'COMPLETED' ? 4 : latest?.stage === 'ANALYZING' ? 2 : 1;
  return <section className="launch-execution" aria-live="polite" aria-busy={latest?.stage !== 'COMPLETED'}>
    <div className="launch-execution__rail" aria-label="출시 준비 분석 진행 단계">{['입력 확인', '독립 분석', '결과 검토', '보고서 구성'].map((label, index) => <span key={label} className={index < reached ? 'is-reached' : ''}>{label}</span>)}</div>
    <p><AppIcon name="sparkles" size={16} />{activity}</p>
    {jobId ? <button className="launch-link-button" type="button" onClick={() => onDetail?.(jobId)}>작업센터에서 상세 기록 보기</button> : null}
  </section>;
}

function ResultSummary({ current, onViewReport }) {
  const result = current?.analysis;
  if (!result) return null;
  const decision = ({ READY: '출시 준비', CONDITIONAL: '조건부 준비', REVISE: '보완 후 재검토' })[result.decision] ?? '검토 필요';
  return <section className="launch-result" aria-labelledby="launch-result-title"><div><span>분석 결론</span><strong id="launch-result-title">{decision}</strong><small>AI 출시 준비도 평가 {result.score}점</small>
    {current.quality?.passed === true ? <small className="launch-quality-pass"><AppIcon name="check" size={13} />독립 AI 검증 통과</small> : null}</div>
    <div className="launch-result__summary"><p>{result.summary}</p><small>작성한 출시 계획을 바탕으로 한 의사결정 지원 결과이며 인증이나 성과를 보장하지 않습니다.</small></div>
    <ul>{(result.actions ?? []).slice(0, 3).map((action) => <li key={`${action.priority}-${action.title}`}><b>{action.priority}</b><span>{action.title}</span></li>)}</ul>
    {current.stale ? <p className="launch-warning">새 입력 문서가 있어 이 결과는 이전 입력 기준입니다.</p> : null}
    {!current.stale ? <button type="button" className="launch-button is-tertiary" onClick={onViewReport}>보고서 보기</button> : null}
  </section>;
}

export default function LaunchReadinessPage() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const outlet = useOutletContext();
  const client = useApiClient();
  const api = useMemo(() => createLaunchReadinessApi(client), [client]);
  const input = useRef(null);
  const [state, setState] = useState({ current: null, busy: false, error: null });
  const activeJobId = ACTIVE.has(state.current?.status) ? state.current?.taskRunId : null;
  const job = useJobEvents(activeJobId);
  const refresh = useCallback(async () => {
    try {
      const current = await api.professionalCurrent(projectId, 'launch');
      setState((value) => ({ ...value, current, busy: false, error: null }));
    }
    catch (error) { setState((value) => ({ ...value, busy: false, error })); }
  }, [api, projectId]);
  useEffect(() => { void refresh(); }, [refresh]);
  useEffect(() => { if (job.terminal) void refresh(); }, [job.terminal, refresh]);

  const start = async (event) => {
    const file = event.target.files?.[0]; if (!file) return;
    setState((value) => ({ ...value, busy: true, error: null }));
    try {
      const action = await api.startProfessional(projectId, 'launch', file);
      setState((value) => ({ ...value, busy: false, current: { ...value.current, ...action, sourceDocumentName: file.name }, error: null }));
    } catch (error) { if (ambiguousMutation(error)) await refresh(); else setState((value) => ({ ...value, busy: false, error })); }
    finally { event.target.value = ''; }
  };
  const retry = async () => {
    setState((value) => ({ ...value, busy: true, error: null }));
    try { await api.retryProfessional(projectId, 'launch'); await refresh(); }
    catch (error) { if (ambiguousMutation(error)) await refresh(); else setState((value) => ({ ...value, busy: false, error })); }
  };
  const viewReport = () => navigate(projectRoutes.launchReadinessReport(projectId, 'launch', ['launch']));

  return <ProjectWorkspace as="div" mode="data" className="launch-readiness-page">
    <ProjectStageHeader step={3} eyebrow="출시 준비" title="출시 결정을 위한 준비 상태를 확인하세요" description="작성한 출시 계획 DOCX를 기준으로 승인 게이트, 미해결 위험과 우선 보완 과제를 독립적으로 분석합니다." />
    <section id="launch-launch" className="launch-module" aria-labelledby="launch-launch-title"><div className="launch-module__heading"><div><p>출시 준비 분석</p><h2 id="launch-launch-title">출시 전에 최종 준비 상태를 확인하세요</h2><span>출시 범위, 승인 기준, 고객 준비, 모니터링과 롤백 계획을 하나의 DOCX로 분석합니다.</span></div>
      {state.current?.analysis && !state.current.stale ? <span className="launch-status is-complete"><AppIcon name="check" size={14} />완료</span> : null}</div>
      <ol className="launch-workflow launch-workflow--vertical">{WORKFLOW.map(([title, helper], index) => <li key={title}><b>{index + 1}</b><span>{title}<small>{helper}</small></span></li>)}</ol>
      <div className="launch-actions"><button type="button" className="launch-button is-secondary" onClick={async () => downloadDocumentBlob(await api.professionalTemplate(projectId, 'launch'), 'launch-readiness-input.docx')}><AppIcon name="download" size={16} />입력 템플릿 다운로드</button>
        <input ref={input} type="file" aria-label="출시 준비 DOCX 업로드" accept=".docx" onChange={start} disabled={state.busy} />
        <button type="button" className="launch-button is-primary" disabled={state.busy} onClick={() => input.current?.click()}>{state.busy ? '문서를 확인하고 있습니다…' : state.current?.analysis ? '새 DOCX로 재실행' : '작성한 DOCX로 분석 시작'}</button>
        {state.current?.status === 'FAILED' && state.current?.retryAvailable ? <button type="button" className="launch-button is-secondary" disabled={state.busy} onClick={retry}>다시 시도</button> : null}</div>
      {state.current?.sourceDocumentName ? <p className="launch-document"><AppIcon name="file" size={15} />{state.current.sourceDocumentName}</p> : null}
      {ACTIVE.has(state.current?.status) ? <ExecutionStatus jobId={state.current?.taskRunId} events={job} onDetail={outlet?.openWorkCenterJob} /> : null}
      {state.error ? <p className="launch-error" role="alert">{getUserErrorMessage(state.error)}</p> : null}
      <ResultSummary current={state.current} onViewReport={viewReport} />
    </section>
  </ProjectWorkspace>;
}
