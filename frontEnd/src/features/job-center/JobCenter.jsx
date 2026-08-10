import { Link } from 'react-router-dom';

import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { jobEventMessage } from '../../shared/async-events/index.js';
import { formatLocalTime } from '../../shared/async-events/formatLocalTime.js';
import { useProjectJobs } from './useProjectJobs.js';

const STATUS_LABELS = {
  QUEUED: '대기 중', READY: '실행 대기', RUNNING: '진행 중', NEEDS_INPUT: '입력 필요',
  RESOLVED_INPUT: '입력 반영 완료',
  SUCCEEDED: '완료', COMPLETED: '완료', FAILED: '실패', CANCELLED: '취소됨', TIMED_OUT: '시간 초과',
};

function displayStatus(job) {
  return job.presentationStatus ?? job.status;
}

function targetHref(projectId, route) {
  return `/app/projects/${encodeURIComponent(projectId)}${route || '/overview'}`;
}

function JobList({ title, jobs, selectedJobId, onSelect, projectId }) {
  return <section className="job-center__group">
    <h3>{title}</h3>
    {jobs.length === 0 ? <p>해당 작업이 없습니다.</p> : <ul>{jobs.map((job) => <li key={job.jobId} data-status={displayStatus(job)}>
      <button type="button" aria-pressed={selectedJobId === job.jobId} onClick={() => onSelect(job.jobId)}>
        <strong>{job.taskType.replaceAll('_', ' ')}</strong>
        <span>{STATUS_LABELS[displayStatus(job)] ?? displayStatus(job)}</span>
        <small>서버 상태: {STATUS_LABELS[job.rawStatus ?? job.status] ?? (job.rawStatus ?? job.status)}</small>
        <small>{job.latestForSubject ? '현재 실행' : '이전 실행'} · {formatLocalTime(job.startedAt ?? job.updatedAt)}</small>
      </button>
      <Link to={targetHref(projectId, job.targetRoute)}>모듈로 이동</Link>
    </li>)}</ul>}
  </section>;
}

export default function JobCenter({ projectId, onTerminal }) {
  const jobs = useProjectJobs(projectId, { onTerminal });
  const selectedJob = [...jobs.active, ...jobs.recent].find((job) => job.jobId === jobs.selectedJobId);
  const props = { selectedJobId: jobs.selectedJobId, onSelect: jobs.selectJob, projectId };
  const running = jobs.active.filter((job) => job.status === 'RUNNING');
  const queued = jobs.active.filter((job) => ['QUEUED', 'READY'].includes(job.status));
  const needsInput = jobs.active.filter((job) => job.status === 'NEEDS_INPUT' && job.actionable !== false);
  const completed = jobs.recent.filter((job) => ['SUCCEEDED', 'COMPLETED', 'RESOLVED_INPUT'].includes(displayStatus(job)));
  const failed = jobs.recent.filter((job) => ['FAILED', 'CANCELLED', 'TIMED_OUT'].includes(job.status));

  return <section id="project-task-center" className="pipeline-task-center job-center" aria-labelledby="task-center-title">
    <header><div><p>작업 센터</p><h2 id="task-center-title">프로젝트 비동기 작업</h2></div><button type="button" onClick={jobs.refresh}>수동 새로고침</button></header>
    {jobs.notice && <p className="job-center__notice" role="status" aria-live="polite">
      {(jobs.notice.taskType ?? selectedJob?.taskType ?? '선택한 작업').replaceAll('_', ' ')} 작업이 {STATUS_LABELS[jobs.notice.status] ?? jobs.notice.status} 상태로 종료되었습니다.
    </p>}
    {jobs.loading && <p>서버에서 작업 목록을 복원하고 있습니다.</p>}
    {jobs.error && <div role="alert"><span>{getUserErrorMessage(jobs.error)}</span><button type="button" onClick={jobs.refresh}>다시 시도</button></div>}
    {!jobs.loading && !jobs.error && <div className="job-center__groups">
      <JobList title="진행 중인 작업" jobs={running} {...props} />
      <JobList title="대기 중인 작업" jobs={queued} {...props} />
      <JobList title="입력이 필요한 작업" jobs={needsInput} {...props} />
      <JobList title="최근 완료" jobs={completed} {...props} />
      <JobList title="최근 실패" jobs={failed} {...props} />
    </div>}
    {jobs.selectedJobId && <section className="job-center__timeline" aria-live="polite">
      <header><div><h3>선택한 작업 타임라인</h3>{selectedJob && <small>
        {selectedJob.taskType.replaceAll('_', ' ')} · {STATUS_LABELS[displayStatus(selectedJob)] ?? displayStatus(selectedJob)} · {formatLocalTime(selectedJob.startedAt ?? selectedJob.updatedAt)}
      </small>}</div><span>{jobs.events.transport ?? '연결 준비'}</span></header>
      {jobs.events.error && <button type="button" onClick={jobs.events.reconnect}>연결 재시도</button>}
      {jobs.events.events.length === 0 ? <p>수신된 이벤트가 없습니다. 작업 상태는 서버 조회 결과를 기준으로 표시합니다.</p>
        : <ol>{jobs.events.events.map((event) => <li key={event.sequence}><strong>{jobEventMessage(event)}</strong><span>{STATUS_LABELS[event.status] ?? event.status}</span></li>)}</ol>}
    </section>}
  </section>;
}
