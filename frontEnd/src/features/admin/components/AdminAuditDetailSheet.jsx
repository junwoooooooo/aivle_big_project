import { useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

import { Button, SideSheet } from '../../../shared/ui/index.js';
import { getAdminErrorMessage } from '../api/adminErrorResolver.js';
import useAdminAuditDetail from '../hooks/useAdminAuditDetail.js';
import { getAuditActionLabel } from '../model/auditLabels.js';
import AdminStatusBadge from './AdminStatusBadge.jsx';

function date(value) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value));
}

function StructuredValue({ value }) {
  if (value === null || value === undefined) return <span>—</span>;
  if (Array.isArray(value)) {
    if (value.length === 0) return <span>없음</span>;
    return <ul className="admin-structured-list">{value.map((item, index) => <li key={index}><StructuredValue value={item} /></li>)}</ul>;
  }
  if (typeof value === 'object') return <KeyValueList values={value} />;
  if (typeof value === 'boolean') return <span>{value ? '예' : '아니요'}</span>;
  return <span>{String(value)}</span>;
}

function KeyValueList({ values }) {
  const entries = Object.entries(values || {});
  if (entries.length === 0) return <p className="admin-empty admin-empty--compact">기록된 값이 없습니다.</p>;
  return (
    <dl className="admin-structured-values">
      {entries.map(([key, value]) => (
        <div key={key}>
          <dt>{key}</dt>
          <dd><StructuredValue value={value} /></dd>
        </div>
      ))}
    </dl>
  );
}

function targetPath(target) {
  if (!target.exists || !target.id) return null;
  if (target.type === 'USER') return `/admin/users/${target.id}`;
  if (target.type === 'PROJECT') return `/admin/projects/${target.id}`;
  return null;
}

export default function AdminAuditDetailSheet({ auditId, onRequestClose }) {
  const { data: audit, loading, error, refresh } = useAdminAuditDetail(auditId);
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

  const target = audit ? targetPath(audit.target) : null;
  return (
    <SideSheet
      open
      phase={phase}
      onExited={finishClose}
      onClose={close}
      title={audit ? getAuditActionLabel(audit.action) : '감사 기록 상세'}
      label="관리자 감사 기록 상세"
      describedBy={descriptionId}
    >
      <nav className="admin-breadcrumb" aria-label="현재 위치">
        <Link to="/admin">Admin</Link>
        <span aria-hidden="true"> / </span>
        <Link to="/admin/audit">Audit</Link>
        <span aria-hidden="true"> / </span>
        <span aria-current="page">감사 상세</span>
      </nav>
      <p id={descriptionId} className="admin-sheet-description">관리자 작업의 변경 내용과 요청 환경을 확인합니다.</p>
      {loading && <p className="admin-sheet-state" role="status">감사 기록을 불러오는 중입니다.</p>}
      {!loading && error && (
        <div className="admin-sheet-state admin-error" role="alert">
          <p>{getAdminErrorMessage(error)}</p>
          {error?.status === 404 && <Button size="small" onClick={close}>목록으로 돌아가기</Button>}
          {error?.status === 403 && <Link className="admin-detail-link" to="/app">사용자 워크스페이스로 이동</Link>}
          {!error?.status || error.status >= 500 ? <Button size="small" variant="outline" onClick={refresh}>다시 시도</Button> : null}
        </div>
      )}
      {!loading && audit && (
        <div className="admin-audit-detail">
          <header className="admin-user-detail__header">
            <div><strong>{getAuditActionLabel(audit.action)}</strong><span>{date(audit.occurredAt)}</span></div>
            <AdminStatusBadge value={audit.result} />
          </header>

          <section>
            <h3>수행 관리자</h3>
            <dl className="admin-detail-list">
              <dt>이름</dt><dd>{audit.actor.displayName || '—'}</dd>
              <dt>Username</dt><dd>{audit.actor.username ? `@${audit.actor.username}` : '—'}</dd>
              <dt>Role</dt><dd>{audit.actor.role || '—'}</dd>
            </dl>
          </section>

          <section>
            <h3>대상</h3>
            <dl className="admin-detail-list">
              <dt>유형</dt><dd>{audit.target.type}</dd>
              <dt>ID</dt><dd>{audit.target.id || '—'}</dd>
              <dt>표시명</dt><dd>{audit.target.label || '—'}</dd>
              {target && <><dt>상세</dt><dd><Link className="admin-detail-link" to={target}>대상 상세로 이동</Link></dd></>}
            </dl>
          </section>

          <section><h3>변경 전</h3><KeyValueList values={audit.before} /></section>
          <section><h3>변경 후</h3><KeyValueList values={audit.after} /></section>

          {audit.result === 'FAILED' && (
            <section>
              <h3>실패 정보</h3>
              <dl className="admin-detail-list">
                <dt>오류 코드</dt><dd>{audit.errorCode || '—'}</dd>
                <dt>안내</dt><dd>{audit.errorCode ? getAdminErrorMessage({ code: audit.errorCode }) : '상세 오류 코드가 기록되지 않았습니다.'}</dd>
              </dl>
            </section>
          )}

          <section>
            <h3>요청 정보</h3>
            <dl className="admin-detail-list">
              <dt>Request ID</dt><dd><code className="admin-code-value">{audit.requestId || '—'}</code></dd>
              <dt>IP</dt><dd><code className="admin-code-value">{audit.ipAddress || '—'}</code></dd>
              <dt>User Agent</dt><dd><code className="admin-code-value">{audit.userAgent || '—'}</code></dd>
              <dt>사유</dt><dd>{audit.reason || '—'}</dd>
            </dl>
          </section>

          <section>
            <h3>기타 Metadata</h3>
            {audit.metadataParseError && <p className="admin-error">이전 형식 Metadata를 구조화하지 못했습니다.</p>}
            <KeyValueList values={audit.metadata} />
          </section>
        </div>
      )}
    </SideSheet>
  );
}
