import { Link } from 'react-router-dom';

import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { jobEventMessage } from '../../shared/async-events/index.js';
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

function JobList({ title, jobs, selectedJobId, onSelect, projectId, links = true }) {
  return <section className="job-center__group"><h3>{title}</h3>{jobs.length === 0 ? <p>해당 작업이 없습니다.</p> : <ul>{jobs.map((job) => <li key={job.jobId} data-status={displayStatus(job)}><button type="button" aria-pressed={selectedJobId === job.jobId} onClick={(event) => onSelect(job.jobId, event.currentTarget)}><strong>{jobTaskLabel(job.taskType)}</strong><span>{STATUS_LABELS[displayStatus(job)] ?? displayStatus(job)}</span><small>{formatLocalTime(job.startedAt ?? job.updatedAt)}</small></button>{links && <Link to={targetHref(projectId, job.targetRoute)}>이동</Link>}</li>)}</ul>}</section>;
}

function JobGroups({ jobs, projectId, onSelect, links = true }) {
  const props = { selectedJobId: jobs.selectedJobId, onSelect, projectId, links };
  const running = jobs.active.filter((job) => ['QUEUED', 'READY', 'RUNNING'].includes(job.status));
  const needsInput = jobs.active.filter((job) => job.status === 'NEEDS_INPUT' && job.actionable !== false);
  return <div className="job-center__groups"><JobList title="현재 진행" jobs={running} {...props} /><JobList title="입력 필요" jobs={needsInput} {...props} /><JobList title="최근 작업" jobs={jobs.recent.slice(0, 10)} {...props} /></div>;
}

function JobDetail({ jobs, selectedJob, onBack }) {
  return <section className="job-center__timeline" aria-live="polite"><header><div><button type="button" onClick={onBack}>← 전체 작업</button><h3>작업 상세</h3>{selectedJob && <small>{jobTaskLabel(selectedJob.taskType)} · {STATUS_LABELS[displayStatus(selectedJob)] ?? displayStatus(selectedJob)} · 시작 {formatLocalTime(selectedJob.startedAt ?? selectedJob.updatedAt)}</small>}</div><span>연결 {jobs.events.transport ?? '준비 중'}</span></header>{jobs.events.error && <button type="button" onClick={jobs.events.reconnect}>연결 재시도</button>}{jobs.events.events.length === 0 ? <p>수신된 상세 이벤트가 없습니다. 서버의 작업 상태를 기준으로 표시합니다.</p> : <ol>{jobs.events.events.map((event) => <li key={`${event.eventId ?? event.sequence}-${event.occurredAt}`}><time>{formatLocalTime(event.occurredAt)}</time><strong>{jobEventMessage(event)}</strong><span>{STATUS_LABELS[event.status] ?? event.status}</span></li>)}</ol>}</section>;
}

export default function JobCenter({ projectId, onTerminal, refreshKey = 0, compact = false,
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
        <header><div><p>WORK CENTER</p><h2 id="work-center-sheet-title">{sheet.focusJobId ? '작업 상세' : '프로젝트 작업 전체'}</h2></div><button type="button" aria-label="작업 센터 닫기" onClick={onCloseSheet}>닫기</button></header>
        {sheet.focusJobId ? <JobDetail jobs={jobs} selectedJob={selectedJob} onBack={onShowList} />
          : <JobGroups jobs={jobs} projectId={projectId} onSelect={(jobId, trigger) => { jobs.selectJob(jobId); onOpenJob(jobId, trigger); }} />}
      </section>
    </div>}
  </>;
}
