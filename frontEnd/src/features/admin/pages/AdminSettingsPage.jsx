import { useMemo, useState } from 'react';

import { Button } from '../../../shared/ui/index.js';
import { useServicePolicy } from '../../service-policy/useServicePolicy.js';
import { getAdminErrorMessage } from '../api/adminErrorResolver.js';
import AdminActionConfirmDialog from '../components/AdminActionConfirmDialog.jsx';
import AdminSettingSection from '../components/AdminSettingSection.jsx';
import AdminClusterPersonaSettings from '../components/AdminClusterPersonaSettings.jsx';
import useAdminSettings from '../hooks/useAdminSettings.js';
import '../admin.css';

const SECTION_DEFINITIONS = [
  {
    id: 'registration',
    title: '가입 및 접근',
    description: '사용자가 서비스에 새 계정을 만들 수 있는지 관리합니다.',
    keys: ['REGISTRATION_ENABLED'],
  },
  {
    id: 'documents',
    title: '문서 및 분석',
    description: '새 문서와 분석 작업의 시작 가능 여부를 관리합니다.',
    keys: ['DOCUMENT_PROCESSING_ENABLED'],
  },
  {
    id: 'personas',
    title: 'Persona 운영',
    description: '프로젝트 사용자가 선택할 수 있는 공통 군집 Persona를 관리합니다.',
    keys: ['CLUSTER_PERSONA_ENABLED'],
  },
  {
    id: 'operations',
    title: '서비스 운영',
    description: '일반 사용자 쓰기 작업을 중지하는 운영 정책입니다.',
    keys: ['MAINTENANCE_MODE'],
    severity: 'danger',
  },
];

function changeDescription(key, nextValue) {
  if (key === 'REGISTRATION_ENABLED') {
    return nextValue
      ? '신규 회원가입을 다시 허용합니다.'
      : '신규 회원가입을 중지합니다. 기존 사용자는 계속 로그인할 수 있습니다.';
  }
  if (key === 'DOCUMENT_PROCESSING_ENABLED') {
    return nextValue
      ? '새 문서 업로드와 분석 시작을 다시 허용합니다.'
      : '새 문서 업로드와 새 분석 시작이 중지됩니다. 기존 문서와 분석 결과 조회는 유지됩니다.';
  }
  if (key === 'CLUSTER_PERSONA_ENABLED') {
    return nextValue
      ? '관리자가 허용한 군집 페르소나를 프로젝트의 선택 후보로 표시합니다.'
      : '추가 군집 페르소나 Section을 숨깁니다. 기존 추천 결과와 저장된 선택 기록은 유지됩니다.';
  }
  return nextValue
    ? '일반 사용자의 프로젝트 변경, 문서 처리, 분석 시작과 계정 변경이 중지됩니다. 기존 데이터 조회와 관리자 콘솔 접근은 유지됩니다.'
    : '일반 사용자의 변경 작업을 다시 허용합니다.';
}

function stateLabel(key, value) {
  if (key === 'MAINTENANCE_MODE') return value ? '점검 중' : '정상 운영';
  if (key === 'CLUSTER_PERSONA_ENABLED') return value ? '사용 중' : '숨김';
  return value ? '허용 중' : '중지됨';
}

export default function AdminSettingsPage() {
  const { data, loading, refreshing, error, refresh, api } = useAdminSettings();
  const { refresh: refreshPolicy } = useServicePolicy();
  const [pending, setPending] = useState(null);
  const [notice, setNotice] = useState('');

  const settingsByKey = useMemo(
    () => new Map((data || []).map((setting) => [setting.key, setting])),
    [data],
  );

  function beginChange(setting) {
    setNotice('');
    setPending({
      setting,
      nextValue: !setting.value,
    });
  }

  async function confirmChange({ reason, password }) {
    const requiresReauthentication =
      pending.setting.key === 'MAINTENANCE_MODE' && pending.nextValue;
    const actionToken = requiresReauthentication
      ? (await api.reauthenticateAdmin({
        password,
        purpose: 'MAINTENANCE_MODE_ENABLE',
      })).actionToken
      : undefined;

    const changed = await api.updateSetting(
      pending.setting.key,
      { value: String(pending.nextValue), reason },
      actionToken,
    );
    setPending(null);
    setNotice(`${changed.displayName} 설정이 변경되었습니다.`);
    refresh();
    await refreshPolicy().catch(() => undefined);
  }

  return (
    <div className="admin-page">
      <header className="admin-page-header">
        <h1>Settings</h1>
        <p>서비스 기능과 운영 모드를 정책별로 안전하게 변경합니다.</p>
      </header>

      {notice && <p className="admin-success" role="status">{notice}</p>}
      {refreshing && <p className="admin-query-progress" role="status">최신 설정을 확인하고 있습니다.</p>}

      {loading && <section className="admin-panel" aria-busy="true">설정을 불러오는 중입니다.</section>}
      {error && !data && (
        <section className="admin-error-state" role="alert">
          <p>{getAdminErrorMessage(error)}</p>
          <Button size="small" variant="outline" onClick={refresh}>다시 시도</Button>
        </section>
      )}

      {data && (
        <div className="admin-settings">
          {SECTION_DEFINITIONS.map((section) => (
            <AdminSettingSection
              key={section.id}
              {...section}
              settings={section.keys.map((key) => settingsByKey.get(key)).filter(Boolean)}
              onChange={beginChange}
            />
          ))}
          <AdminClusterPersonaSettings onChanged={setNotice} />
        </div>
      )}

      {pending && (
        <AdminActionConfirmDialog
          open
          title={`${pending.setting.displayName} 설정 변경`}
          description={changeDescription(pending.setting.key, pending.nextValue)}
          targetLabel={pending.setting.displayName}
          currentState={stateLabel(pending.setting.key, pending.setting.value)}
          nextState={stateLabel(pending.setting.key, pending.nextValue)}
          purpose="MAINTENANCE_MODE_ENABLE"
          requiresReauthentication={
            pending.setting.key === 'MAINTENANCE_MODE' && pending.nextValue
          }
          confirmLabel="변경"
          onCancel={() => setPending(null)}
          onConfirm={confirmChange}
        />
      )}
    </div>
  );
}
