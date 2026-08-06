import { useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

import { Button, SideSheet } from '../../../shared/ui/index.js';
import { getAdminErrorMessage } from '../api/adminErrorResolver.js';
import useAdminProjectDetail from '../hooks/useAdminProjectDetail.js';
import AdminStatusBadge from './AdminStatusBadge.jsx';

const STATUS_LABELS = {
  NOT_STARTED: '미시작',
  SUCCEEDED: '완료',
  COMPLETED: '완료',
  RUNNING: '진행 중',
  QUEUED: '대기 중',
  FAILED: '실패',
  PARTIAL: '부분 완료',
  DRAFT: '초안',
  NEEDS_INPUT: '입력 필요',
  CONFIRMED: '확정',
};
const JOURNEY_STAGE = { DOCUMENT:'아이디어',STRUCTURING:'아이디어',LEGAL_REVIEW:'법률 검토',FEASIBILITY:'콘셉트 생성',FINANCIAL:'콘셉트 분석',PERSONA_CONFIGURATION:'콘셉트 선택',PANEL_SURVEY:'페르소나',PANEL_DISCUSSION:'인터뷰',MARKETING:'마케팅',REPORT:'최종 보고서',COMPLETED:'최종 보고서' };

function date(value) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function AnalysisRow({ label, value }) {
  return (
    <div className="admin-analysis-row">
      <strong>{label}</strong>
      <span>{STATUS_LABELS[value.status] || value.status}</span>
      {value.startedAt && <small>시작 {date(value.startedAt)}</small>}
      {value.completedAt && <small>완료 {date(value.completedAt)}</small>}
      {value.errorCode && <small className="admin-error">오류 코드 {value.errorCode}</small>}
    </div>
  );
}

export default function AdminProjectDetailSheet({ projectId, onRequestClose }) {
  const { data: project, loading, error, refresh } = useAdminProjectDetail(projectId);
  const [phase, setPhase] = useState('entered');
  const closeTimerRef = useRef(null);
  const closedRef = useRef(false);
  const descriptionId = useId();

  function finishClose() {
    if (closedRef.current) return;
    closedRef.current = true;
    window.clearTimeout(closeTimerRef.current);
    onRequestClose();
  }

  function close() {
    setPhase('exiting');
    closeTimerRef.current = window.setTimeout(finishClose, 350);
  }

  return (
    <SideSheet
      open
      phase={phase}
      onExited={finishClose}
      onClose={close}
      title={project ? `${project.title} 프로젝트 상세` : '프로젝트 상세'}
      label="관리자 프로젝트 상세"
      describedBy={descriptionId}
    >
      <nav className="admin-breadcrumb" aria-label="현재 위치">
        <Link to="/admin">Admin</Link>
        <span aria-hidden="true"> / </span>
        <Link to="/admin/projects">Projects</Link>
        <span aria-hidden="true"> / </span>
        <span aria-current="page">프로젝트 상세</span>
      </nav>
      <p id={descriptionId} className="admin-sheet-description">
        프로젝트의 운영 상태를 읽기 전용으로 확인합니다.
      </p>
      {loading && <p className="admin-sheet-state" role="status">프로젝트 정보를 불러오는 중입니다.</p>}
      {!loading && error && (
        <div className="admin-sheet-state admin-error" role="alert">
          <p>{getAdminErrorMessage(error)}</p>
          {error?.status === 404 && <Button size="small" onClick={close}>목록으로 돌아가기</Button>}
          {error?.status === 403 && <Link className="admin-detail-link" to="/app">사용자 워크스페이스로 이동</Link>}
          {!error?.status || error.status >= 500 ? <Button size="small" variant="outline" onClick={refresh}>다시 시도</Button> : null}
        </div>
      )}
      {!loading && project && (
        <div className="admin-project-detail">
          <header className="admin-user-detail__header">
            <div>
              <strong>{project.title}</strong>
              <span>{JOURNEY_STAGE[project.stage] || project.stage} / {project.stage}</span>
            </div>
            <AdminStatusBadge value={project.status} />
          </header>

          <section>
            <h3>소유자</h3>
            <dl className="admin-detail-list">
              <dt>이름</dt><dd>{project.owner.displayName || '—'}</dd>
              <dt>Username</dt><dd>{project.owner.username ? `@${project.owner.username}` : '—'}</dd>
              <dt>사용자 상세</dt>
              <dd>
                {project.owner.deleted
                  ? `탈퇴한 사용자 · User ID: ${project.owner.id}`
                  : <Link className="admin-detail-link" to={`/admin/users/${project.owner.id}`}>사용자 상세로 이동</Link>}
              </dd>
            </dl>
          </section>

          <section>
            <h3>프로젝트 정보</h3>
            <dl className="admin-detail-list">
              <dt>설명</dt><dd>{project.description || '—'}</dd>
              <dt>Area</dt><dd>{project.area}</dd>
              <dt>현재 Journey 단계</dt><dd>{JOURNEY_STAGE[project.stage] || project.stage} ({project.stage})</dd>
              <dt>업종</dt><dd>{project.industryCategory || '—'}</dd>
              <dt>생성일</dt><dd>{date(project.createdAt)}</dd>
              <dt>최근 수정일</dt><dd>{date(project.updatedAt)}</dd>
            </dl>
          </section>

          <section>
            <h3>문서</h3>
            {project.document.available ? (
              <dl className="admin-detail-list">
                <dt>최신 Version</dt><dd>{project.document.version}</dd>
                <dt>파일명</dt><dd>{project.document.originalFilename}</dd>
                <dt>업로드 시각</dt><dd>{date(project.document.uploadedAt)}</dd>
                <dt>처리 상태</dt><dd>{STATUS_LABELS[project.document.processingStatus] || project.document.processingStatus}</dd>
                <dt>구조화 상태</dt><dd>{STATUS_LABELS[project.document.structuredStatus] || project.document.structuredStatus}</dd>
              </dl>
            ) : <p className="admin-empty admin-empty--compact">업로드된 활성 문서가 없습니다.</p>}
          </section>

          <section>
            <h3>분석</h3>
            <div className="admin-analysis-list">
              <AnalysisRow label="법률 검토" value={project.analyses.legalReview} />
              <AnalysisRow label="사업성 평가" value={project.analyses.feasibility} />
              <AnalysisRow label="Persona 추천" value={project.analyses.personaRecommendation} />
            </div>
          </section>

          <section>
            <h3>운영 진단</h3>
            <dl className="admin-detail-list">
              <dt>Project ID</dt><dd>{project.id}</dd>
              <dt>삭제 여부</dt><dd>{project.deleted ? '삭제됨' : '정상'}</dd>
            </dl>
          </section>
        </div>
      )}
    </SideSheet>
  );
}
