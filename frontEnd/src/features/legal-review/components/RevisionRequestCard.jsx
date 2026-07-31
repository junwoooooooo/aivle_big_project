import { useState } from 'react';
import { Alert, Button, Card } from '../../../shared/ui/index.js';
import { LEGAL_CATEGORY_LABELS } from '../model/legalReviewViewModel.js';
import { SECTION_LABELS } from '../../structured-plan/model/structuredPlanViewModel.js';

/**
 * 미해결 수정 요청 카드. AI 수정안은 사용자가 [이 수정안 적용]을 눌렀을 때만 반영된다 —
 * 자동 반영·자동 재검토 경로는 존재하지 않는다 (§9 금지 사항).
 */
export function RevisionRequestCard({ request, onAccept, onDismiss, busy }) {
  const [selectedId, setSelectedId] = useState(request.suggestions?.[0]?.id ?? null);
  const sectionLabel = SECTION_LABELS[request.anchorSectionCode] ?? request.anchorSectionCode;
  return (
    <Card className="legal-revision-card">
      <div className="legal-finding__heading">
        <h3>{LEGAL_CATEGORY_LABELS[request.category] ?? request.category} 수정 요청</h3>
        <span className="legal-timing-badge">{sectionLabel}</span>
      </div>
      <blockquote className="legal-revision-quote">{request.anchorQuote}</blockquote>
      {request.rationale && <p>{request.rationale}</p>}
      <fieldset className="legal-revision-suggestions" disabled={busy}>
        <legend>AI 수정안 — 적용할 안을 선택하세요</legend>
        {(request.suggestions ?? []).map((suggestion) => (
          <label key={suggestion.id} className="legal-revision-suggestion">
            <input
              type="radio"
              name={`revision-${request.id}`}
              checked={selectedId === suggestion.id}
              onChange={() => setSelectedId(suggestion.id)}
            />
            <span><strong>{suggestion.label}안</strong> {suggestion.newText}</span>
          </label>
        ))}
      </fieldset>
      <Alert title="자동 반영되지 않습니다" tone="info" live={false}>
        적용을 누르면 기획서 새 버전이 만들어질 뿐, 재검토는 별도로 실행해야 합니다.
      </Alert>
      <div className="legal-dialog-actions">
        <Button
          variant="outline"
          disabled={busy}
          onClick={() => onDismiss(request.id)}
        >
          무시
        </Button>
        <Button
          disabled={busy || selectedId == null}
          onClick={() => onAccept(request.id, selectedId)}
        >
          이 수정안 적용
        </Button>
      </div>
    </Card>
  );
}

/** v{n}에서 해결된 수정 요청 — 삭제하지 않고 회색·접힘으로 이력을 남긴다. */
export function ResolvedRevisionCard({ request }) {
  return (
    <details className="legal-revision-card legal-revision-card--resolved">
      <summary>
        <span className="legal-resolved-badge">v{request.resolvedInVersion}에서 해결</span>
        {LEGAL_CATEGORY_LABELS[request.category] ?? request.category} 수정 요청
      </summary>
      <blockquote className="legal-revision-quote">{request.anchorQuote}</blockquote>
      {request.rationale && <p className="legal-muted">{request.rationale}</p>}
    </details>
  );
}
