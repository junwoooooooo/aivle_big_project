import { Button, Card } from '../../../shared/ui/index.js';

export default function MarketingVersionPanel({
  versions,
  onCreate,
  onPreview,
  onClone,
  saving,
  disabled,
}) {
  return (
    <Card className="marketing-versions">
      <div className="marketing-panel__heading">
        <div><h2>버전</h2><p>저장된 시안 이력을 보존합니다.</p></div>
        <Button size="small" variant="outline" loading={saving} disabled={disabled} onClick={onCreate}>새 버전 저장</Button>
      </div>
      <ol>
        {versions.map((version) => (
          <li key={version.id}>
            <div>
              <strong>v{version.versionNumber}</strong>
              <span>{new Date(version.createdAt).toLocaleString('ko-KR')}</span>
              <small>
                Snapshot v{version.sourceSnapshotVersion}
                {version.sourceChanged ? ' · Source 변경' : ''}
                {version.copyChanged ? ' · 카피 변경' : ''}
                {' · '}{version.layoutTemplate}
              </small>
            </div>
            <div>
              <Button size="small" variant="ghost" onClick={() => onPreview(version)}>보기</Button>
              <Button size="small" variant="ghost" disabled={disabled} onClick={() => onClone(version)}>복제</Button>
            </div>
          </li>
        ))}
      </ol>
    </Card>
  );
}
