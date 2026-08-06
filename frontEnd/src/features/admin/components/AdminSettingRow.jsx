import { AppIcon, Button } from '../../../shared/ui/index.js';

function updatedByLabel(updatedBy) {
  if (!updatedBy) return '변경 기록 없음';
  return updatedBy.displayName || updatedBy.username || `사용자 #${updatedBy.id}`;
}

function stateLabel(setting) {
  if (setting.key === 'MAINTENANCE_MODE') return setting.value ? '점검 중' : '정상 운영';
  return setting.value ? '허용 중' : '중지됨';
}

export default function AdminSettingRow({ setting, onChange }) {
  return (
    <article className="admin-setting-row">
      <div className="admin-setting-row__body">
        <div className="admin-setting-row__title">
          <h3>{setting.displayName}</h3>
          <span className={`admin-setting-state ${setting.value ? 'is-enabled' : 'is-disabled'}`}>
            {stateLabel(setting)}
          </span>
        </div>
        <p>{setting.description}</p>
        <dl className="admin-setting-meta">
          <div>
            <dt>마지막 변경 관리자</dt>
            <dd>{updatedByLabel(setting.updatedBy)}</dd>
          </div>
          <div>
            <dt>마지막 변경 시각</dt>
            <dd>{setting.updatedAt ? new Date(setting.updatedAt).toLocaleString('ko-KR') : '변경 기록 없음'}</dd>
          </div>
        </dl>
      </div>
      <Button size="small" variant="outline" onClick={() => onChange(setting)}>
        <AppIcon name="settings" />
        변경
      </Button>
    </article>
  );
}
