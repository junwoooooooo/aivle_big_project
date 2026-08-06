import { useId, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

import { Button, SideSheet } from '../../../shared/ui/index.js';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { useAuth } from '../../auth/AuthProvider.jsx';
import { createAdminApi } from '../api/adminApi.js';
import { getAdminErrorMessage } from '../api/adminErrorResolver.js';
import useAdminUserDetail from '../hooks/useAdminUserDetail.js';
import AdminActionConfirmDialog from './AdminActionConfirmDialog.jsx';
import AdminStatusBadge from './AdminStatusBadge.jsx';

function valueOrDash(value) {
  return value || '—';
}

function formatDate(value) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function createAction(type, value, label, secure = false, purpose = '') {
  return { type, value, label, secure, purpose };
}

export default function AdminUserDetailSheet({ userId, onRequestClose, onChanged }) {
  const { user: actor } = useAuth();
  const client = useApiClient();
  const api = useMemo(() => createAdminApi(client), [client]);
  const { data: user, loading, error, refresh } = useAdminUserDetail(userId);
  const [pending, setPending] = useState(null);
  const [busy, setBusy] = useState(false);
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
    if (busy || pending) return;
    setPhase('exiting');
    closeTimerRef.current = window.setTimeout(finishClose, 350);
  }

  async function confirm({ reason, password }) {
    if (!pending || !user) return;
    const action = pending;
    setBusy(true);
    try {
      let actionToken;
      if (action.secure) {
        actionToken = (await api.reauthenticateAdmin({
          password,
          purpose: action.purpose,
        })).actionToken;
      }
      if (action.type === 'role') {
        await api.updateRole(user.id, { role: action.value, reason }, actionToken);
      } else if (action.type === 'status') {
        await api.updateStatus(user.id, { status: action.value, reason }, actionToken);
      } else if (action.type === 'delete') {
        await api.deleteUser(user.id, { reason }, actionToken);
      } else {
        await api.revokeSessions(user.id, { reason });
      }
      setPending(null);
      onChanged(`${action.label} 작업이 완료되었습니다.`);
      if (action.type === 'delete') {
        setPhase('exiting');
        closeTimerRef.current = window.setTimeout(finishClose, 350);
      } else {
        await refresh();
      }
    } finally {
      setBusy(false);
    }
  }

  const isSelf = user && String(actor?.id) === String(user.id);
  const protectsAdmin = user?.lastActiveAdmin;
  const protectedReason = isSelf
    ? '현재 로그인한 관리자 계정에는 실행할 수 없습니다.'
    : protectsAdmin
      ? '마지막 활성 관리자에게는 실행할 수 없습니다.'
      : '';
  const deleteAction = createAction('delete', 'DELETED', '사용자 삭제', true, 'USER_DELETE');
  const actions = [];
  if (user?.accountStatus === 'ACTIVE') {
    actions.push(createAction('status', 'LOCKED', '계정 잠금'));
  }
  if (user?.accountStatus === 'LOCKED' || user?.accountStatus === 'DISABLED') {
    actions.push(createAction('status', 'ACTIVE', '계정 활성화'));
  }
  if (user && user.accountStatus !== 'DISABLED') {
    actions.push(createAction('sessions', '', '모든 세션 종료'));
    actions.push(createAction(
      'role',
      user.role === 'ADMIN' ? 'USER' : 'ADMIN',
      user.role === 'ADMIN' ? 'USER 강등' : 'ADMIN 승격',
      true,
      'USER_ROLE_CHANGE',
    ));
    actions.push(createAction('status', 'DISABLED', '계정 비활성화', true, 'USER_DISABLE'));
  }

  function actionBlocked(action) {
    if (!user) return true;
    if (action.type === 'sessions') return isSelf;
    if (action.type === 'delete') return isSelf || protectsAdmin;
    if (action.type === 'role' && action.value === 'USER') return isSelf || protectsAdmin;
    if (action.type === 'status' && ['LOCKED', 'DISABLED'].includes(action.value)) return isSelf || protectsAdmin;
    return false;
  }

  const errorMessage = error ? getAdminErrorMessage(error) : '';
  return (
    <>
      <SideSheet
        open
        phase={phase}
        onExited={finishClose}
        onClose={close}
        title={user ? `${user.displayName || user.username} 사용자 상세` : '사용자 상세'}
        label="관리자 사용자 상세"
        describedBy={descriptionId}
    >
      <nav className="admin-breadcrumb" aria-label="현재 위치">
        <Link to="/admin">Admin</Link>
        <span aria-hidden="true"> / </span>
        <Link to="/admin/users">Users</Link>
        <span aria-hidden="true"> / </span>
        <span aria-current="page">사용자 상세</span>
      </nav>
      <p id={descriptionId} className="admin-sheet-description">
          계정 정보와 현재 허용되는 운영 작업을 확인합니다.
        </p>
        {loading && <p className="admin-sheet-state" role="status">사용자 정보를 불러오는 중입니다.</p>}
        {!loading && error && (
          <div className="admin-sheet-state admin-error" role="alert">
            <p>{errorMessage}</p>
            {error?.status === 404 && <Button size="small" onClick={close}>목록으로 돌아가기</Button>}
            {error?.status === 403 && <Link className="admin-detail-link" to="/app">사용자 워크스페이스로 이동</Link>}
            {!error?.status || error.status >= 500 ? <Button size="small" variant="outline" onClick={refresh}>다시 시도</Button> : null}
          </div>
        )}
        {!loading && user && (
          <div className="admin-user-detail">
            <header className="admin-user-detail__header">
              <div>
                <strong>{user.displayName || user.username}</strong>
                <span>@{user.username}</span>
              </div>
              <div className="admin-user-detail__badges">
                <AdminStatusBadge value={user.role} />
                <AdminStatusBadge value={user.accountStatus} />
              </div>
            </header>

            <section>
              <h3>기본 정보</h3>
              <dl className="admin-detail-list">
                <dt>Email</dt><dd>{valueOrDash(user.email)}</dd>
                <dt>조직</dt><dd>{valueOrDash(user.organizationName)}</dd>
                <dt>부서</dt><dd>{valueOrDash(user.departmentName)}</dd>
                <dt>직책</dt><dd>{valueOrDash(user.jobTitle)}</dd>
                <dt>가입일</dt><dd>{formatDate(user.createdAt)}</dd>
                <dt>최근 로그인</dt><dd>{formatDate(user.lastLoginAt)}</dd>
              </dl>
            </section>

            <section>
              <h3>운영 정보</h3>
              <dl className="admin-detail-list">
                <dt>프로젝트 수</dt><dd>{user.projectCount.toLocaleString()}</dd>
                <dt>세션 보안</dt><dd>권한·상태 변경 시 기존 세션이 자동으로 무효화됩니다.</dd>
                {user.lockedAt && <><dt>잠금 시각</dt><dd>{formatDate(user.lockedAt)}</dd></>}
                {user.lockedReason && <><dt>잠금 사유</dt><dd>{user.lockedReason}</dd></>}
                {user.disabledAt && <><dt>비활성화 시각</dt><dd>{formatDate(user.disabledAt)}</dd></>}
                {user.disabledReason && <><dt>비활성화 사유</dt><dd>{user.disabledReason}</dd></>}
              </dl>
            </section>

            <section>
              <h3>운영 작업</h3>
              {protectedReason && <p className="admin-action-restriction">{protectedReason}</p>}
              <div className="admin-actions">
                {actions.map((action) => (
                  <Button
                    key={`${action.type}-${action.value}`}
                    size="small"
                    variant={action.secure ? 'outline' : 'ghost'}
                    disabled={actionBlocked(action)}
                    title={actionBlocked(action) ? protectedReason : undefined}
                    onClick={() => setPending(action)}
                  >
                    {action.label}
                  </Button>
                ))}
              </div>
            </section>

            <section className="admin-danger-zone">
              <h3>위험 작업</h3>
              <p>
                잠금은 일시적인 로그인 제한이고 비활성화는 다시 활성화할 수 있습니다.
                삭제는 계정을 탈퇴 상태로 전환하고 직접 식별정보를 비식별화합니다.
              </p>
              <Button
                size="small"
                variant="danger"
                disabled={actionBlocked(deleteAction)}
                title={actionBlocked(deleteAction) ? protectedReason : undefined}
                onClick={() => setPending(deleteAction)}
              >
                사용자 삭제
              </Button>
            </section>
          </div>
        )}
      </SideSheet>
      {pending && user && (
        <AdminActionConfirmDialog
          open
          title={pending.label}
          description={pending.type === 'delete'
            ? '계정을 탈퇴 상태로 전환하고 직접 식별정보를 비식별화합니다. 프로젝트와 감사 기록은 보존됩니다.'
            : pending.secure
              ? '이 작업은 관리자 재인증 후 실행되며 대상 사용자의 기존 세션은 종료됩니다.'
              : '운영 기록에 남길 변경 사유를 입력해 주세요.'}
          targetLabel={`${user.displayName || user.username} (@${user.username})`}
          currentState={`${user.role} / ${user.accountStatus}`}
          nextState={pending.value || '모든 세션 종료'}
          purpose={pending.purpose}
          requiresReauthentication={pending.secure}
          busy={busy}
          onCancel={() => setPending(null)}
          onConfirm={confirm}
          confirmLabel={pending.type === 'delete' ? '사용자 삭제' : '확인'}
        />
      )}
    </>
  );
}
