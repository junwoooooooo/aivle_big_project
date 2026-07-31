import { PLAN_ORIGIN_LABELS } from '../model/legalReviewViewModel.js';

/** 상단 버전 표시 + 히스토리. 각 버전의 생성 사유(origin)와 시각을 보여준다. */
export function VersionHistoryDropdown({ versions, currentVersionNumber }) {
  if (!versions || versions.length === 0) return null;
  const current = currentVersionNumber
    ?? versions[0]?.versionNumber;
  return (
    <details className="legal-version-history">
      <summary>
        기획서 <strong>v{current}</strong>
        <span className="legal-muted"> · 버전 {versions.length}개</span>
      </summary>
      <ol className="legal-version-list">
        {versions.map((version) => (
          <li key={version.planId}>
            <strong>v{version.versionNumber}</strong>
            <span className="legal-version-origin">
              {PLAN_ORIGIN_LABELS[version.origin] ?? version.origin}
            </span>
            <span className="legal-muted">
              {version.createdAt ? new Date(version.createdAt).toLocaleString('ko-KR') : ''}
            </span>
          </li>
        ))}
      </ol>
    </details>
  );
}
