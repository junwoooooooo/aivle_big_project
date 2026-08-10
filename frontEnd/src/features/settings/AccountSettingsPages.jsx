import { useMemo, useState } from 'react';
import { Navigate, NavLink, Outlet } from 'react-router-dom';

import { useAuth } from '../auth/AuthProvider.jsx';
import { createAuthApi } from '../auth/api/authApi.js';
import usePasswordChecks from '../auth/hooks/usePasswordChecks.js';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { Alert, AppIcon, Button, PageHeader, TextInput } from '../../shared/ui/index.js';
import { appRoutes } from '../../app/routing/projectRoutes.js';
import { ProfileAvatar } from '../../app/layouts/AppShell.jsx';
import { useServicePolicy } from '../service-policy/useServicePolicy.js';
import { getWriteRestriction, isServicePolicyError } from '../service-policy/servicePolicyRestrictions.js';
import { useAuthTransition } from '../../app/transitions/AuthTransitionProvider.jsx';
import AccountDeletionDialog from './AccountDeletionDialog.jsx';
import './settings.css';

function SettingsLayout() {
  return <section className="account-settings"><PageHeader eyebrow="Account settings" title="계정 설정" description="프로필과 보안 정보를 관리합니다." /><nav aria-label="계정 설정"><NavLink to={appRoutes.profileSettings}><AppIcon name="user" />Profile</NavLink><NavLink to={appRoutes.securitySettings}><AppIcon name="lock" />Security</NavLink></nav><Outlet /></section>;
}

export function AccountSettingsLayout() { return <SettingsLayout />; }
export function AccountSettingsRedirect() { return <Navigate to={appRoutes.profileSettings} replace />; }

export function ProfileSettingsPage() {
  const client = useApiClient();
  const servicePolicy = useServicePolicy();
  const restriction = getWriteRestriction(servicePolicy);
  const { user, updateUser } = useAuth();
  const initial = useMemo(() => ({ displayName: user?.displayName || '', email: user?.email || '', organizationName: user?.organizationName || '', departmentName: user?.departmentName || '', jobTitle: user?.jobTitle || '' }), [user]);
  const [values, setValues] = useState(initial);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const changed = JSON.stringify(values) !== JSON.stringify(initial);
  const update = (field) => (event) => setValues((current) => ({ ...current, [field]: event.target.value }));
  const save = async (event) => {
    event.preventDefault(); if (!changed || saving || restriction.blocked) return;
    setSaving(true); setError(''); setMessage('');
    try { const next = await createAuthApi(client).updateProfile(values); updateUser(next); setMessage('프로필 변경사항을 저장했습니다.'); }
    catch (nextError) { if (isServicePolicyError(nextError)) void servicePolicy.refresh().catch(() => undefined); setError(getUserErrorMessage(nextError)); } finally { setSaving(false); }
  };
  return <form className="settings-form" onSubmit={save}><div className="profile-settings__avatar"><ProfileAvatar user={user} size="large" /><div><strong>프로필 이미지</strong><p>이미지 업로드는 아직 지원하지 않습니다.</p></div></div>{error && <Alert tone="danger" title="프로필을 저장하지 못했습니다">{error}</Alert>}{message && <Alert tone="success" title="저장됨">{message}</Alert>}{restriction.blocked && <Alert tone={restriction.code === 'POLICY_UNAVAILABLE' ? 'danger' : 'warning'} title="프로필을 변경할 수 없습니다"><p>{restriction.message}</p>{restriction.code === 'POLICY_UNAVAILABLE' && <Button type="button" variant="outline" size="small" onClick={() => void servicePolicy.refresh().catch(() => undefined)}>다시 시도</Button>}</Alert>}<TextInput id="settings-name" label="표시 이름" value={values.displayName} onChange={update('displayName')} disabled={restriction.blocked} required maxLength="50" /><TextInput id="settings-username" label="아이디" value={user?.username || ''} readOnly description="아이디는 변경할 수 없습니다." /><TextInput id="settings-email" label="선택 이메일" value={values.email} onChange={update('email')} disabled={restriction.blocked} type="email" maxLength="254" /><TextInput id="settings-organization" label="소속·조직" value={values.organizationName} onChange={update('organizationName')} disabled={restriction.blocked} maxLength="120" /><TextInput id="settings-department" label="부서·팀" value={values.departmentName} onChange={update('departmentName')} disabled={restriction.blocked} maxLength="120" /><TextInput id="settings-job-title" label="직급·역할" value={values.jobTitle} onChange={update('jobTitle')} disabled={restriction.blocked} maxLength="120" /><Button type="submit" disabled={!changed || saving || restriction.blocked} loading={saving}>변경사항 저장</Button></form>;
}

export function SecuritySettingsPage() {
  const client = useApiClient();
  const servicePolicy = useServicePolicy();
  const restriction = getWriteRestriction(servicePolicy);
  const { user, logout } = useAuth();
  const { start: startAuthTransition } = useAuthTransition();
  const [values, setValues] = useState({ currentPassword: '', newPassword: '', confirmation: '' });
  const [saving, setSaving] = useState(false); const [error, setError] = useState('');
  const [deletionOpen, setDeletionOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const checks = usePasswordChecks(values.newPassword, values.confirmation, user?.username, user?.displayName);
  const update = (field) => (event) => setValues((current) => ({ ...current, [field]: event.target.value }));
  const rules = [
    ['15~64자', checks.hasMinimumLength && checks.isWithinMaximumLength],
    ['아이디 또는 표시 이름이 그대로 포함되지 않음', !checks.matchesUsername && !checks.matchesDisplayName && values.newPassword.length > 0],
    ['같은 문자나 짧은 문자열을 반복하지 않음', !checks.hasRepeatedPattern && values.newPassword.length > 0],
    ['흔히 쓰는 비밀번호나 연속 패턴이 아님', checks.isNotCommonOrSimilar],
    ['새 비밀번호 확인과 일치', checks.confirmationMatches],
  ];
  const save = async (event) => {
    event.preventDefault();
    if (saving || restriction.blocked) return;
    if (!checks.isValid || !checks.confirmationMatches) {
      setError('새 비밀번호 조건을 모두 충족해 주세요.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      await createAuthApi(client).changePassword({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      });
      await startAuthTransition({
        destination: '/auth/login',
        message: '비밀번호를 변경했습니다. 다시 로그인해 주세요.',
        onCovered: () => logout().catch(() => undefined),
      });
    } catch (nextError) {
      if (isServicePolicyError(nextError)) void servicePolicy.refresh().catch(() => undefined);
      setError(getUserErrorMessage(nextError));
    } finally {
      setSaving(false);
    }
  };
  const deleteAccount = async (input) => {
    setDeleting(true);
    try {
      await createAuthApi(client).deleteAccount(input);
      setDeletionOpen(false);
      await startAuthTransition({
        destination: '/auth/login',
        message: '회원 탈퇴가 완료되었습니다.',
        onCovered: () => logout().catch(() => undefined),
      });
    } catch (nextError) {
      if (isServicePolicyError(nextError)) void servicePolicy.refresh().catch(() => undefined);
      throw nextError;
    } finally {
      setDeleting(false);
    }
  };
  return (
    <>
      <form className="settings-form" onSubmit={save}>
        {error && <Alert tone="danger" title="비밀번호를 변경하지 못했습니다">{error}</Alert>}
        {restriction.blocked && (
          <Alert tone={restriction.code === 'POLICY_UNAVAILABLE' ? 'danger' : 'warning'} title="비밀번호를 변경할 수 없습니다">
            <p>{restriction.message}</p>
            {restriction.code === 'POLICY_UNAVAILABLE' && <Button type="button" variant="outline" size="small" onClick={() => void servicePolicy.refresh().catch(() => undefined)}>다시 시도</Button>}
          </Alert>
        )}
        <TextInput id="current-password" label="현재 비밀번호" type="password" autoComplete="current-password" value={values.currentPassword} onChange={update('currentPassword')} disabled={restriction.blocked} required />
        <TextInput id="new-password" label="새 비밀번호" type="password" autoComplete="new-password" value={values.newPassword} onChange={update('newPassword')} disabled={restriction.blocked} required />
        <TextInput id="confirm-password" label="새 비밀번호 확인" type="password" autoComplete="new-password" value={values.confirmation} onChange={update('confirmation')} disabled={restriction.blocked} required />
        <ul className="password-check-list">{rules.map(([label, valid]) => <li key={label} data-valid={valid}>{label}</li>)}</ul>
        <Button type="submit" disabled={saving || restriction.blocked} loading={saving}>비밀번호 변경 후 다시 로그인</Button>
      </form>
      <section className="settings-danger-zone" aria-labelledby="account-deletion-title">
        <div>
          <span className="settings-danger-zone__eyebrow">Danger zone</span>
          <h2 id="account-deletion-title">회원 탈퇴</h2>
          <p>계정 접근과 모든 세션이 즉시 종료되고 직접 식별정보가 비식별화됩니다.</p>
          <p>프로젝트와 기존 분석 결과는 운영·감사·보존 정책에 따라 유지됩니다.</p>
        </div>
        {user?.role === 'ADMIN' ? (
          <p className="settings-danger-zone__notice">관리자 계정은 다른 관리자가 관리자 콘솔에서 처리해야 합니다.</p>
        ) : (
          <Button
            variant="danger"
            disabled={restriction.blocked}
            title={restriction.blocked ? restriction.message : undefined}
            onClick={() => setDeletionOpen(true)}
          >
            회원 탈퇴
          </Button>
        )}
      </section>
      <AccountDeletionDialog
        open={deletionOpen}
        busy={deleting}
        onCancel={() => setDeletionOpen(false)}
        onConfirm={deleteAccount}
      />
    </>
  );
}
