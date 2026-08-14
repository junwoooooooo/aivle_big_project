import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Link } from 'react-router-dom';

import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { groupJobEvents, jobEventMessage, traceDetailForDisplay } from '../../shared/async-events/index.js';
import { formatLocalTime } from '../../shared/async-events/formatLocalTime.js';
import { AppIcon } from '../../shared/ui/index.js';
import { jobModuleLabel, jobTaskLabel } from './jobPresentation.js';
import { useProjectJobs } from './useProjectJobs.js';

const STATUS_LABELS = {
  QUEUED: '대기 중', READY: '실행 대기', RUNNING: '진행 중', NEEDS_INPUT: '입력 필요',
  RESOLVED_INPUT: '입력 반영 완료', SUCCEEDED: '완료', COMPLETED: '완료', FAILED: '확인 필요',
  CANCELLED: '종료됨', TIMED_OUT: '확인 필요',
};
const displayStatus = (job) => job.presentationStatus ?? job.status;
const statusLabel = (status) => STATUS_LABELS[status] ?? '상태 확인 필요';
const targetHref = (projectId, route) => `/app/projects/${encodeURIComponent(projectId)}${route || '/overview'}`;
const recentJobMessage = (job) => ({
  QUEUED: '작업 시작을 준비하고 있습니다.', READY: '실행을 기다리고 있습니다.',
  RUNNING: '작업을 처리하고 있습니다.', NEEDS_INPUT: '계속하려면 입력이 필요합니다.',
  SUCCEEDED: '작업을 완료했습니다.', COMPLETED: '작업을 완료했습니다.',
  FAILED: '작업을 완료하지 못했습니다.', TIMED_OUT: '작업을 완료하지 못했습니다.',
  CANCELLED: '작업이 종료되었습니다.', RESOLVED_INPUT: '입력 내용을 반영했습니다.',
}[displayStatus(job)] ?? '작업 상태가 업데이트되었습니다.');

function splitJobs(jobs) {
  return {
    running: jobs.active.filter((job) => ['QUEUED', 'READY', 'RUNNING'].includes(job.status)),
    needsInput: jobs.active.filter((job) => job.status === 'NEEDS_INPUT' && job.actionable !== false),
  };
}

function JobList({ title, jobs, selectedJobId, onSelect, limit }) {
  const visible = typeof limit === 'number' ? jobs.slice(0, limit) : jobs;
  const remaining = Math.max(0, jobs.length - visible.length);
  return <section className="job-center__group"><h3><span>{title}</span><strong>{jobs.length}</strong></h3>{jobs.length === 0 ? <p>해당 작업이 없습니다.</p> : <><ul>{visible.map((job) => <li key={job.jobId} data-status={displayStatus(job)}><button type="button" aria-pressed={selectedJobId === job.jobId} onClick={(event) => onSelect(job.jobId, event.currentTarget)}><strong>{jobTaskLabel(job.taskType, job.subjectType)}</strong><span>{statusLabel(displayStatus(job))}</span><small>{formatLocalTime(job.startedAt ?? job.updatedAt)} · {recentJobMessage(job)}</small></button></li>)}</ul>{remaining > 0 && <p className="job-center__more">+ 외 {remaining}건</p>}</>}</section>;
}

function QuickJobGroups({ jobs, onSelect }) {
  const { running, needsInput } = splitJobs(jobs);
  const props = { selectedJobId: jobs.selectedJobId, onSelect };
  return <><div className="job-center__summary" aria-label="작업 요약"><span>현재 진행 <strong>{running.length}</strong></span><span>입력 필요 <strong>{needsInput.length}</strong></span><span>최근 작업 <strong>{jobs.recent.length}</strong></span></div><div className="job-center__groups"><JobList title="현재 진행" jobs={running} limit={1} {...props} /><JobList title="입력 필요" jobs={needsInput} limit={1} {...props} /><JobList title="최근 작업" jobs={jobs.recent} limit={3} {...props} /></div></>;
}

function historyMatches(job, filter) {
  const status = displayStatus(job);
  if (filter === 'active') return ['QUEUED', 'READY', 'RUNNING'].includes(status);
  if (filter === 'input') return status === 'NEEDS_INPUT';
  if (filter === 'closed') return ['SUCCEEDED', 'COMPLETED', 'RESOLVED_INPUT', 'FAILED', 'CANCELLED', 'TIMED_OUT'].includes(status);
  return true;
}

function FullWorkList({ jobs, onSelect }) {
  const [filter, setFilter] = useState('all');
  const { running, needsInput } = splitJobs(jobs);
  const visible = useMemo(() => jobs.history.items.filter((job) => historyMatches(job, filter)), [filter, jobs.history.items]);
  const emptyMessage = { active: '현재 진행 중인 작업이 없습니다.', input: '지금 입력이 필요한 작업이 없습니다.', closed: '완료되거나 종료된 작업이 없습니다.' }[filter] ?? '조건에 맞는 작업이 없습니다.';
  return <section className="work-history" aria-labelledby="work-history-title">
    <div className="work-history__metrics" aria-label="작업 요약"><span>현재 진행 <strong>{running.length}</strong></span><span>입력 필요 <strong>{needsInput.length}</strong></span><span>전체 이력 <strong>{jobs.history.totalElements}</strong></span></div>
    <div className="work-history__filters" role="group" aria-label="작업 상태 필터">{[['all', '전체'], ['active', '진행 중'], ['input', '입력 필요'], ['closed', '완료·종료']].map(([value, label]) => <button key={value} type="button" className={filter === value ? 'is-active' : ''} onClick={() => setFilter(value)}>{label}</button>)}</div>
    {jobs.history.error && <div role="alert" className="work-history__error"><span>{getUserErrorMessage(jobs.history.error)}</span><button type="button" onClick={() => jobs.loadHistory({ reset: jobs.history.page < 0 })}>다시 시도</button></div>}
    {jobs.history.page < 0 && jobs.history.loading ? <p>작업 이력을 불러오고 있습니다.</p> : visible.length > 0 ? <ol>{visible.map((job) => <li key={job.jobId} data-status={displayStatus(job)}><button type="button" onClick={() => onSelect(job.jobId)}><i aria-hidden="true" /><span><strong>{jobTaskLabel(job.taskType, job.subjectType)}</strong><small>{jobModuleLabel(job.module)} · {recentJobMessage(job)}</small></span><time dateTime={job.updatedAt}>{formatLocalTime(job.updatedAt ?? job.startedAt)}</time><em>{statusLabel(displayStatus(job))}</em><AppIcon name="chevronRight" size={16} /></button></li>)}</ol> : jobs.history.totalElements === 0 && filter === 'all' ? <div className="work-history__empty"><strong>아직 실행한 작업이 없습니다.</strong><p>각 업무 단계에서 분석을 시작하면 여기에 진행 상황과 기록이 표시됩니다.</p></div> : <p className="work-history__empty">{emptyMessage}</p>}
    {jobs.history.hasMore && <button type="button" className="work-history__more" disabled={jobs.history.loading} onClick={() => jobs.loadHistory()}>{jobs.history.loading ? '불러오는 중' : '이전 작업 더 보기'}</button>}
  </section>;
}

function trapDialogFocus(event) {
  if (event.key !== 'Tab') return;
  const focusable = Array.from(event.currentTarget.querySelectorAll('a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])'));
  if (focusable.length === 0) return;
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

function JobDetail({ jobs, selectedJob, onBack, projectId, onRetryJob, onNavigate }) {
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
  const [clock, setClock] = useState(0);
  useEffect(() => {
    if (!selectedJob || !['QUEUED', 'READY', 'RUNNING'].includes(selectedJob.status)) return undefined;
    const timer = setInterval(() => setClock(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [selectedJob]);
  const elapsedSeconds = Number.isFinite(startedAt) && clock > 0 ? Math.max(0, Math.floor((clock - startedAt) / 1000)) : 0;
  const validationFields = latest?.messageParams?.validationFields ?? [];
  return <section className="job-center__timeline" aria-live="polite"><header className="job-detail__header"><div><button type="button" aria-label="전체 작업으로 돌아가기" onClick={onBack}><AppIcon name="chevronLeft" size={21} /></button><div><h3>{selectedJob ? jobTaskLabel(selectedJob.taskType, selectedJob.subjectType) : '작업 상세'}</h3>{selectedJob && <small>{statusLabel(displayStatus(selectedJob))}</small>}</div></div>{selectedJob && <Link to={targetHref(projectId, selectedJob.targetRoute)} onClick={onNavigate}>업무 화면 열기<AppIcon name="arrowUpRight" size={15} /></Link>}</header>{selectedJob && <dl className="job-center__current"><div><dt>현재 작업</dt><dd>{jobTaskLabel(selectedJob.taskType, selectedJob.subjectType)}</dd></div><div><dt>최근 처리</dt><dd>{latest ? jobEventMessage(latest) : recentJobMessage(selectedJob)}</dd></div><div><dt>경과 시간</dt><dd>{Math.floor(elapsedSeconds / 60)}분 {elapsedSeconds % 60}초</dd></div><div><dt>마지막 변경</dt><dd>{latest ? formatLocalTime(latest.occurredAt) : formatLocalTime(selectedJob.updatedAt)}</dd></div></dl>}{failed && <div className="job-center__failure"><strong>작업을 완료하지 못했습니다.</strong><span>입력 내용을 확인하거나 잠시 후 다시 시도해 주세요.</span><span>{selectedJob.retryable === false ? '새 실행 가능 여부를 업무 화면에서 확인해 주세요.' : '새 작업으로 다시 시도할 수 있습니다.'}</span>{canRetryHere && <button type="button" disabled={retrying} onClick={retry}>{retrying ? '다시 시도 중' : '다시 시도'}</button>}{(validationFields.length > 0 || traceDetailForDisplay(latest)) && <details><summary>기술 정보</summary>{traceDetailForDisplay(latest) && <p>{traceDetailForDisplay(latest)}</p>}{validationFields.length > 0 && <ul>{validationFields.slice(0, 5).map((field, index) => <li key={`${field.path}-${index}`}>{field.path} · {field.expectedType}</li>)}</ul>}</details>}{retryError && <span role="alert">{retryError}</span>}</div>}{selectedJob && displayStatus(selectedJob) === 'NEEDS_INPUT' && <div className="job-center__input-action"><p>계속하려면 업무 화면에서 필요한 내용을 입력해 주세요.</p></div>}{jobs.events.loading ? <p>저장된 처리 기록을 불러오고 있습니다.</p> : jobs.events.error ? <button type="button" onClick={jobs.events.reconnect}>상태 다시 확인</button> : groupedEvents.length === 0 ? <p>{jobs.events.transport === 'REST' ? '이 작업에는 저장된 처리 기록이 없습니다.' : '아직 표시할 처리 기록이 없습니다.'}</p> : <ol>{groupedEvents.map((event) => <li key={`${event.eventId ?? event.sequence}-${event.occurredAt}`}><time>{event.groupCount > 1 ? `${formatLocalTime(event.groupStartedAt)}~${formatLocalTime(event.occurredAt)}` : formatLocalTime(event.occurredAt)}</time><div><strong>{jobEventMessage(event)}{event.groupCount > 1 ? ` × ${event.groupCount}` : ''}</strong></div><span>{statusLabel(event.status)}</span></li>)}</ol>}</section>;
}

export default function JobCenter({ projectId, onTerminal, onRetryJob, refreshKey = 0, compact = false,
  quickOpen = true, quickContainerId, sheet, onOpenList, onOpenJob, onCloseSheet, onShowList, onNavigate }) {
  const sheetRef = useRef(null);
  const projectJobs = useProjectJobs(projectId, { onTerminal, refreshKey });
  const jobs = {
    ...projectJobs,
    history: projectJobs.history ?? { items: [], page: -1, hasMore: false, totalElements: 0, loading: false, error: null },
    loadHistory: projectJobs.loadHistory ?? (() => undefined),
  };
  const selectedJob = [...jobs.active, ...jobs.recent, ...jobs.history.items].find((job) => job.jobId === jobs.selectedJobId);
  const select = (jobId, trigger) => { jobs.selectJob(jobId); if (compact) onOpenJob?.(jobId, trigger); };
  const { history, loadHistory } = jobs;
  useEffect(() => {
    if (sheet?.mounted && sheet.view === 'list' && history.page < 0 && !history.loading) loadHistory({ reset: true });
  }, [history.loading, history.page, loadHistory, sheet?.mounted, sheet?.view]);
  useEffect(() => {
    if (sheet?.mounted) sheetRef.current?.querySelector('button')?.focus();
  }, [sheet?.mounted]);

  const rootClass = compact ? 'job-center job-center--compact' : 'pipeline-task-center job-center';
  const quick = <section id="project-task-center" className={rootClass} aria-labelledby="task-center-title">
      <header><div><p>작업 센터</p><h2 id="task-center-title">프로젝트 작업</h2></div><button type="button" onClick={jobs.refresh}>새로고침</button></header>
      {jobs.loading && <p>작업 목록을 불러오고 있습니다.</p>}
      {jobs.error && <div role="alert"><span>{getUserErrorMessage(jobs.error)}</span><button type="button" onClick={jobs.refresh}>다시 시도</button></div>}
      {!jobs.loading && !jobs.error && <QuickJobGroups jobs={jobs} onSelect={select} />}
      {compact && <button type="button" className="job-center__detail-button" onClick={onOpenList}>전체 작업 보기</button>}
    </section>;
  const full = sheet?.mounted && <div className="work-center-sheet__backdrop" data-phase={sheet.phase} onMouseDown={(event) => { if (event.target === event.currentTarget) onCloseSheet(); }}>
      <section ref={sheetRef} className="work-center-sheet" data-phase={sheet.phase} role="dialog" aria-modal="true" aria-labelledby="work-center-sheet-title" onKeyDown={trapDialogFocus}>
        <header><div><p>작업 센터</p><h2 id="work-center-sheet-title">{sheet.view === 'detail' ? '작업 상세' : '프로젝트 작업'}</h2></div><button type="button" aria-label="작업 센터 닫기" onClick={onCloseSheet}><AppIcon name="close" size={20} /></button></header>
        <div key={sheet.view} className="work-center-sheet__content" data-direction={sheet.direction}>
          {sheet.view === 'detail' ? <JobDetail jobs={jobs} selectedJob={selectedJob} onBack={onShowList} projectId={projectId} onRetryJob={onRetryJob} onNavigate={onNavigate} />
            : <FullWorkList jobs={jobs} onSelect={(jobId) => { jobs.selectJob(jobId); onOpenJob(jobId); }} />}
        </div>
      </section>
    </div>;
  return <>{compact ? quickOpen && !sheet?.mounted && <div id={quickContainerId} className="project-tool-popover project-work-popover">{quick}</div> : quick}{full && createPortal(full, document.body)}</>;
}
