import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { projectRoutes } from '../../app/routing/projectRoutes.js';
import { Drawer } from '../../shared/ui/index.js';
import { JOB_CENTER_CHANGED, readJobs } from './jobCenterStore.js';

export default function JobCenter() {
  const [open, setOpen] = useState(false);
  const [jobs, setJobs] = useState(readJobs);
  useEffect(() => {
    const update = () => setJobs(readJobs());
    globalThis.addEventListener?.(JOB_CENTER_CHANGED, update);
    globalThis.addEventListener?.('storage', update);
    return () => { globalThis.removeEventListener?.(JOB_CENTER_CHANGED, update); globalThis.removeEventListener?.('storage', update); };
  }, []);
  const active = jobs.filter((job) => !['COMPLETED', 'FAILED', 'STALE'].includes(job.status)).length;
  return <>
    <button type="button" className="app-job-center-trigger" aria-label={`작업 센터, 진행 중 ${active}개`} onClick={() => setOpen(true)}>작업 센터 {active > 0 && <span>{active}</span>}</button>
    <Drawer open={open} onClose={() => setOpen(false)} title="작업 센터">
      <section aria-live="polite"><h2 className="visually-hidden">비동기 작업</h2>{jobs.length === 0 ? <p>표시할 작업이 없습니다.</p> : <ul>{jobs.map((job) => <li key={job.jobId}>
        <Link to={projectRoutes.concepts(job.projectId)} onClick={() => setOpen(false)}><strong>Concept Factory</strong><span>{job.status}</span></Link>
      </li>)}</ul>}</section>
    </Drawer>
  </>;
}
