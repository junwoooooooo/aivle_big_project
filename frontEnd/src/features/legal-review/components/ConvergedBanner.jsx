import { Alert, Button } from '../../../shared/ui/index.js';

/**
 * 수렴(CONVERGED) 안내 + 발행 버튼. 수렴 조건은 수정 요청 0 · 미답변 질문 0이며,
 * 할 일(체크리스트) 완료 여부는 발행을 막지 않는다 (§2).
 */
export function ConvergedBanner({ cycle, pendingTodoCount, onPublish, busy }) {
  if (!cycle || cycle.status !== 'CONVERGED') return null;
  return (
    <Alert title="검토가 수렴했습니다" tone="success">
      <p>
        미해결 수정 요청과 미답변 질문이 없습니다. 정식 보고서를 발행할 수 있습니다.
        {pendingTodoCount > 0 && (
          <> 미완료 할 일 {pendingTodoCount}건은 발행물에 &ldquo;이행 예정 사항&rdquo;으로 수록됩니다.</>
        )}
      </p>
      <Button disabled={busy || !cycle.canPublish} onClick={onPublish}>
        정식 보고서 발행
      </Button>
    </Alert>
  );
}
