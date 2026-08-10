import { useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, SideSheet } from '../../../shared/ui/index.js';
import { getAdminErrorMessage } from '../api/adminErrorResolver.js';
import useAdminProjectDetail from '../hooks/useAdminProjectDetail.js';
import AdminStatusBadge from './AdminStatusBadge.jsx';

function date(value) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

export default function AdminProjectDetailSheet({ projectId, onRequestClose }) {
  const { data: project, loading, error, refresh } = useAdminProjectDetail(projectId);
  const [phase, setPhase] = useState('entered');
  const closeTimerRef = useRef(null);
  const closedRef = useRef(false);
  const descriptionId = useId();
  function finishClose() { if (!closedRef.current) { closedRef.current = true; window.clearTimeout(closeTimerRef.current); onRequestClose(); } }
  function close() { setPhase('exiting'); closeTimerRef.current = window.setTimeout(finishClose, 350); }

  return <SideSheet open phase={phase} onExited={finishClose} onClose={close}
    title={project ? `${project.title} 프로젝트 상세` : '프로젝트 상세'} label="관리자 프로젝트 상세" describedBy={descriptionId}>
    <nav className="admin-breadcrumb" aria-label="현재 위치"><Link to="/admin">Admin</Link> / <Link to="/admin/projects">Projects</Link> / 상세</nav>
    <p id={descriptionId} className="admin-sheet-description">공통 프로젝트 정보와 신규 Pipeline Module 상태를 확인합니다.</p>
    {loading && <p className="admin-sheet-state" role="status">프로젝트 정보를 불러오는 중입니다.</p>}
    {!loading && error && <div className="admin-sheet-state admin-error" role="alert">
      <p>{getAdminErrorMessage(error)}</p>
      {error?.status === 404 ? <Button size="small" onClick={close}>목록으로 돌아가기</Button> : null}
      {!error?.status || error.status >= 500 ? <Button size="small" variant="outline" onClick={refresh}>다시 시도</Button> : null}
    </div>}
    {!loading && project && <div className="admin-project-detail">
      <header className="admin-user-detail__header"><strong>{project.title}</strong><AdminStatusBadge value={project.status} /></header>
      <section><h3>소유자</h3><dl className="admin-detail-list">
        <dt>이름</dt><dd>{project.owner.displayName || '—'}</dd><dt>Username</dt><dd>{project.owner.username ? `@${project.owner.username}` : '—'}</dd>
      </dl></section>
      <section><h3>프로젝트 정보</h3><dl className="admin-detail-list">
        <dt>설명</dt><dd>{project.description || '—'}</dd><dt>업종</dt><dd>{project.industryCategory || '—'}</dd>
        <dt>생성일</dt><dd>{date(project.createdAt)}</dd><dt>최근 수정일</dt><dd>{date(project.updatedAt)}</dd>
      </dl></section>
      <section><h3>Pipeline Modules</h3><div className="admin-analysis-list">
        {project.modules.map((module) => <div className="admin-analysis-row" key={module.module}>
          <strong>{module.module}</strong><AdminStatusBadge value={module.status} />
          {module.activeRunId && <small>Run: {module.activeRunId}</small>}
          {module.sourceSnapshotId && <small>Snapshot: {module.sourceSnapshotId}</small>}
          {module.updatedAt && <small>Updated: {date(module.updatedAt)}</small>}
        </div>)}
      </div></section>
      <section><h3>운영 식별자</h3><dl className="admin-detail-list"><dt>Project ID</dt><dd>{project.id}</dd></dl></section>
    </div>}
  </SideSheet>;
}
