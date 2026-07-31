import { Alert, Card } from '../../../shared/ui/index.js';
import { LEGAL_CATEGORY_LABELS, PLAN_ORIGIN_LABELS } from '../model/legalReviewViewModel.js';

function parseSnapshot(publication) {
  try {
    return JSON.parse(publication?.snapshotJson ?? 'null');
  } catch {
    return null;
  }
}

/**
 * 발행물 요약 — 발행 시점 스냅샷 기준으로 보존된다.
 * 최종 버전, 해결 이력(v1→vN), 미완료 할 일(이행 예정 사항)을 수록한다 (§4-5).
 */
export function PublicationSummary({ publication }) {
  const snapshot = parseSnapshot(publication);
  if (!snapshot) return null;
  const versions = snapshot.versions ?? [];
  const resolutions = snapshot.resolutions ?? [];
  const pendingTodos = snapshot.pendingTodos ?? [];
  return (
    <Card className="legal-publication">
      <p className="legal-kicker">정식 보고서 발행됨</p>
      <h2>기획서 v{snapshot.finalVersionNumber} 기준 발행</h2>
      <p className="legal-muted">
        발행 시각: {publication.publishedAt
          ? new Date(publication.publishedAt).toLocaleString('ko-KR') : '—'}
        {' · '}이후 기획서가 수정되어도 이 발행물은 그대로 보존됩니다.
      </p>

      <h3>버전 이력</h3>
      <ol className="legal-version-list">
        {versions.map((version) => (
          <li key={version.versionNo}>
            <strong>v{version.versionNo}</strong>
            <span className="legal-version-origin">
              {PLAN_ORIGIN_LABELS[version.origin] ?? version.origin}
            </span>
          </li>
        ))}
      </ol>

      {resolutions.length > 0 && (
        <>
          <h3>해결 이력</h3>
          <ul>
            {resolutions.map((entry) => (
              <li key={entry.requestId}>
                {LEGAL_CATEGORY_LABELS[entry.category] ?? entry.category} 수정 요청
                {entry.acceptedSuggestionLabel && ` — ${entry.acceptedSuggestionLabel}안 반영`}
                <span className="legal-resolved-badge">v{entry.resolvedInVersion}에서 해결</span>
              </li>
            ))}
          </ul>
        </>
      )}

      {pendingTodos.length > 0 && (
        <Alert title="이행 예정 사항" tone="warning" live={false}>
          <p>발행 시점에 완료되지 않은 할 일입니다. 판매 개시 전 이행하세요.</p>
          <ul>{pendingTodos.map((todo) => <li key={todo}>{todo}</li>)}</ul>
        </Alert>
      )}
    </Card>
  );
}
