import { useState } from 'react';
import { Link } from 'react-router-dom';

import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { groupJobEvents, jobEventMessage, traceDetailForDisplay } from '../../shared/async-events/index.js';
import { formatLocalTime } from '../../shared/async-events/formatLocalTime.js';
import { jobTaskLabel } from './jobPresentation.js';
import { useProjectJobs } from './useProjectJobs.js';

const STATUS_LABELS = {
  QUEUED: '대기 중', READY: '실행 대기', RUNNING: '진행 중', NEEDS_INPUT: '입력 필요',
  RESOLVED_INPUT: '입력 반영 완료', SUCCEEDED: '완료', COMPLETED: '완료', FAILED: '실패',
  CANCELLED: '취소됨', TIMED_OUT: '시간 초과',
};
const displayStatus = (job) => job.presentationStatus ?? job.status;
const targetHref = (projectId, route) => `/app/projects/${encodeURIComponent(projectId)}${route || '/overview'}`;
const recentJobMessage = (job) => ({
  QUEUED: '작업 시작을 준비하고 있습니다.', READY: '실행을 기다리고 있습니다.',
  RUNNING: '작업을 처리하고 있습니다.', NEEDS_INPUT: '사용자 입력이 필요합니다.',
  SUCCEEDED: '작업을 완료했습니다.', COMPLETED: '작업을 완료했습니다.',
  FAILED: '작업을 완료하지 못했습니다.', TIMED_OUT: '처리 시간이 제한을 초과했습니다.',
}[displayStatus(job)] ?? '작업 상태가 갱신되었습니다.');

function JobList({ title, jobs, selectedJobId, onSelect, projectId, links = true }) {
  return <section className="job-center__group"><h3>{title}</h3>{jobs.length === 0 ? <p>해당 작업이 없습니다.</p> : <ul>{jobs.map((job) => <li key={job.jobId} data-status={displayStatus(job)}><button type="button" aria-pressed={selectedJobId === job.jobId} onClick={(event) => onSelect(job.jobId, event.currentTarget)}><strong>{jobTaskLabel(job.taskType)}</strong><span>{STATUS_LABELS[displayStatus(job)] ?? displayStatus(job)}</span><small>{formatLocalTime(job.startedAt ?? job.updatedAt)} · {recentJobMessage(job)}</small></button>{links && <Link to={targetHref(projectId, job.targetRoute)}>이동</Link>}</li>)}</ul>}</section>;
}

function JobGroups({ jobs, projectId, onSelect, links = true }) {
  const props = { selectedJobId: jobs.selectedJobId, onSelect, projectId, links };
  const running = jobs.active.filter((job) => ['QUEUED', 'READY', 'RUNNING'].includes(job.status));
  const needsInput = jobs.active.filter((job) => job.status === 'NEEDS_INPUT' && job.actionable !== false);
  return <><div className="job-center__summary" aria-label="작업 요약"><span>현재 진행 <strong>{running.length}</strong></span><span>입력 필요 <strong>{needsInput.length}</strong></span><span>최근 완료 <strong>{jobs.recent.length}</strong></span></div><div className="job-center__groups"><JobList title="현재 진행" jobs={running} {...props} /><JobList title="입력 필요" jobs={needsInput} {...props} /><JobList title="최근 작업" jobs={jobs.recent.slice(0, 10)} {...props} /></div></>;
}

function JobDetail({ jobs, selectedJob, onBack, projectId, onRetryJob }) {
  const failed = selectedJob && ['FAILED', 'TIMED_OUT'].includes(displayStatus(selectedJob));
  const [retrying, setRetrying] = useState(false);
  const [retryError, setRetryError] = useState('');
  const retry = async () => {
    if (retrying) return;
    setRetrying(true); setRetryError('');
    try { await onRetryJob(selectedJob); }
    catch (error) { setRetryError(getUserErrorMessage(error)); }
    finally { setRetrying(false); }
  };
  const canRetryHere = failed && selectedJob.taskType === 'CONCEPT_PORTFOLIO_V2_RUN' && onRetryJob;
  const groupedEvents = groupJobEvents(jobs.events.events);
  const latest = groupedEvents.at(-1);
  const startedAt = Date.parse(selectedJob?.startedAt ?? selectedJob?.updatedAt ?? '');
  const elapsedSeconds = Number.isFinite(startedAt) ? Math.max(0, Math.floor((Date.now() - startedAt) / 1000)) : 0;
  const validationFields = latest?.messageParams?.validationFields ?? [];
  return <section className="job-center__timeline" aria-live="polite"><header><div><button type="button" onClick={onBack}>← 전체 작업</button><h3>{selectedJob ? jobTaskLabel(selectedJob.taskType) : '작업 상세'}</h3>{selectedJob && <small>{STATUS_LABELS[displayStatus(selectedJob)] ?? displayStatus(selectedJob)}</small>}</div><span>연결 {jobs.events.transport ?? '준비 중'}</span></header>{selectedJob && <dl className="job-center__current"><div><dt>현재 작업</dt><dd>{jobTaskLabel(selectedJob.taskType)}</dd></div><div><dt>현재 처리</dt><dd>{latest ? jobEventMessage(latest) : recentJobMessage(selectedJob)}</dd></div><div><dt>경과 시간</dt><dd>{Math.floor(elapsedSeconds / 60)}분 {elapsedSeconds % 60}초</dd></div><div><dt>마지막 이벤트</dt><dd>{latest ? formatLocalTime(latest.occurredAt) : '-'}</dd></div></dl>}{failed && <div className="job-center__failure"><strong>작업을 완료하지 못했습니다.</strong><span>실패 시각 {formatLocalTime(selectedJob.updatedAt)}</span><span>{latest ? jobEventMessage(latest) : recentJobMessage(selectedJob)}</span><span>{selectedJob.retryable === false ? '새 실행 가능 여부를 확인해 주세요.' : '새 작업으로 다시 시도할 수 있습니다.'}</span>{canRetryHere ? <button type="button" disabled={retrying} onClick={retry}>{retrying ? '다시 시도 중' : '다시 시도'}</button> : <Link to={targetHref(projectId, selectedJob.targetRoute)}>작업 화면으로 이동</Link>}{validationFields.length > 0 && <details><summary>기술 정보 보기</summary><p>오류 분류: 결과 검증</p><ul>{validationFields.slice(0, 5).map((field, index) => <li key={`${field.path}-${index}`}>검증 위치: {field.path} · 기대 형식: {field.expectedType}</li>)}</ul><p>재시도 가능: {latest?.messageParams?.retryable === false ? '아니오' : '예'}</p></details>}{retryError && <span role="alert">{retryError}</span>}</div>}{selectedJob && displayStatus(selectedJob) === 'NEEDS_INPUT' && <div className="job-center__input-action"><p>사업안 화면에서 필요한 정보를 입력할 수 있습니다.</p><Link to={targetHref(projectId, '/concepts')}>정보 입력하러 가기</Link></div>}{jobs.events.error && <button type="button" onClick={jobs.events.reconnect}>연결 재시도</button>}{groupedEvents.length === 0 ? <p>수신된 상세 이벤트가 없습니다. 서버의 작업 상태를 기준으로 표시합니다.</p> : <ol>{groupedEvents.map((event) => <li key={`${event.eventId ?? event.sequence}-${event.occurredAt}`}><time>{event.groupCount > 1 ? `${formatLocalTime(event.groupStartedAt)}~${formatLocalTime(event.occurredAt)}` : formatLocalTime(event.occurredAt)}</time><div><strong>{jobEventMessage(event)}{event.groupCount > 1 ? ` × ${event.groupCount}` : ''}</strong>{traceDetailForDisplay(event) && <small>{traceDetailForDisplay(event)}</small>}{event.status === 'FAILED' && <small>실패 단계 {event.messageParams?.traceStage ?? event.stage ?? '처리'} · {event.messageParams?.retryable === false ? '재시도 불가' : '재시도 가능'}</small>}</div><span>{STATUS_LABELS[event.status] ?? event.status}</span></li>)}</ol>}</section>;
}

export default function JobCenter({ projectId, onTerminal, onRetryJob, refreshKey = 0, compact = false,
  sheet, onOpenList, onOpenJob, onCloseSheet, onShowList }) {
  const jobs = useProjectJobs(projectId, { onTerminal, refreshKey });
  const selectedJob = [...jobs.active, ...jobs.recent].find((job) => job.jobId === jobs.selectedJobId);
  const select = (jobId, trigger) => { jobs.selectJob(jobId); if (compact) onOpenJob?.(jobId, trigger); };

  return <>
    <section id="project-task-center" className={`pipeline-task-center job-center${compact ? ' job-center--compact' : ''}`} aria-labelledby="task-center-title">
      <header><div><p>WORK CENTER</p><h2 id="task-center-title">프로젝트 작업</h2></div><button type="button" onClick={jobs.refresh}>새로고침</button></header>
      {jobs.loading && <p>작업 목록을 불러오고 있습니다.</p>}
      {jobs.error && <div role="alert"><span>{getUserErrorMessage(jobs.error)}</span><button type="button" onClick={jobs.refresh}>다시 시도</button></div>}
      {!jobs.loading && !jobs.error && <JobGroups jobs={jobs} projectId={projectId} onSelect={select} links={!compact} />}
      {compact && <button type="button" className="job-center__detail-button" onClick={onOpenList}>전체 작업 보기</button>}
    </section>
    {sheet?.mounted && <div className="work-center-sheet__backdrop" data-phase={sheet.phase} onMouseDown={(event) => { if (event.target === event.currentTarget) onCloseSheet(); }}>
      <section className="work-center-sheet" data-phase={sheet.phase} role="dialog" aria-modal="true" aria-labelledby="work-center-sheet-title">
        <header><div><p>WORK CENTER</p><h2 id="work-center-sheet-title">{sheet.view === 'detail' ? '작업 상세' : '프로젝트 작업 전체'}</h2></div><button type="button" aria-label="작업 센터 닫기" onClick={onCloseSheet}>닫기</button></header>
        <div key={sheet.view} className="work-center-sheet__content" data-direction={sheet.direction}>
          {sheet.view === 'detail' ? <JobDetail jobs={jobs} selectedJob={selectedJob} onBack={onShowList} projectId={projectId} onRetryJob={onRetryJob} />
            : <JobGroups jobs={jobs} projectId={projectId} onSelect={(jobId, trigger) => { jobs.selectJob(jobId); onOpenJob(jobId, trigger); }} />}
        </div>
      </section>
    </div>}
  </>;
}
