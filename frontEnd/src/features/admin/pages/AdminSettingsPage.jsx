import { useMemo, useState } from 'react';
import { Button } from '../../../shared/ui/index.js';
import { useServicePolicy } from '../../service-policy/useServicePolicy.js';
import { getAdminErrorMessage } from '../api/adminErrorResolver.js';
import AdminActionConfirmDialog from '../components/AdminActionConfirmDialog.jsx';
import AdminSettingSection from '../components/AdminSettingSection.jsx';
import useAdminSettings from '../hooks/useAdminSettings.js';
import '../admin.css';

const SECTIONS = [
  { id: 'registration', title: '가입 및 접근', description: '신규 사용자 가입 허용 여부를 관리합니다.', keys: ['REGISTRATION_ENABLED'] },
  { id: 'operations', title: '서비스 운영', description: '일반 사용자 쓰기 작업을 제어합니다.', keys: ['MAINTENANCE_MODE'], severity: 'danger' },
];

export default function AdminSettingsPage() {
  const { data, loading, refreshing, error, refresh, api } = useAdminSettings();
  const { refresh: refreshPolicy } = useServicePolicy();
  const [pending, setPending] = useState(null);
  const [notice, setNotice] = useState('');
  const settings = useMemo(() => new Map((data || []).map((item) => [item.key, item])), [data]);
  async function confirmChange({ reason, password }) {
    const reauth = pending.setting.key === 'MAINTENANCE_MODE' && pending.nextValue;
    const token = reauth ? (await api.reauthenticateAdmin({ password, purpose: 'MAINTENANCE_MODE_ENABLE' })).actionToken : undefined;
    const changed = await api.updateSetting(pending.setting.key, { value: String(pending.nextValue), reason }, token);
    setPending(null); setNotice(`${changed.displayName} 설정이 변경되었습니다.`); refresh();
    await refreshPolicy().catch(() => undefined);
  }
  return <div className="admin-page">
    <header className="admin-page-header"><h1>Settings</h1><p>공통 접근 및 운영 정책을 관리합니다.</p></header>
    {notice && <p className="admin-success" role="status">{notice}</p>}
    {refreshing && <p className="admin-query-progress" role="status">최신 설정을 확인하고 있습니다.</p>}
    {loading && <section className="admin-panel" aria-busy="true">설정을 불러오는 중입니다.</section>}
    {error && !data && <section className="admin-error-state" role="alert"><p>{getAdminErrorMessage(error)}</p><Button onClick={refresh}>다시 시도</Button></section>}
    {data && <div className="admin-settings">{SECTIONS.map((section) => <AdminSettingSection key={section.id} {...section}
      settings={section.keys.map((key) => settings.get(key)).filter(Boolean)}
      onChange={(setting) => setPending({ setting, nextValue: !setting.value })} />)}</div>}
    {pending && <AdminActionConfirmDialog open title={`${pending.setting.displayName} 설정 변경`}
      description="운영 정책 값을 변경합니다." targetLabel={pending.setting.displayName}
      currentState={pending.setting.value ? '사용 중' : '중지'} nextState={pending.nextValue ? '사용 중' : '중지'}
      purpose="MAINTENANCE_MODE_ENABLE" requiresReauthentication={pending.setting.key === 'MAINTENANCE_MODE' && pending.nextValue}
      confirmLabel="변경" onCancel={() => setPending(null)} onConfirm={confirmChange} />}
  </div>;
}
