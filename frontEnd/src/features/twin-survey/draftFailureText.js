import { getUserErrorMessage } from '../../shared/api/apiError.js';

/**
 * 자극 초안 실패 문구.
 *
 * ⚠ **「잠시 후 다시 시도해 주세요」는 재시도로 풀리는 실패에만 맞는 말이다.**
 * 「확정된 컨셉이 없다」·「팔 수 있는 쌍이 0개다」는 기다려도 안 변하고, 사용자가 해야 할
 * 일이 따로 있다. 서버는 그것이 무엇인지 알고 `message` 에 담아 보내는데
 * (`retryable:false` + safeMessage), `getUserErrorMessage` 는 **코드로만** 매핑해서
 * 그 문구를 버린다. 실측(2026-08-11): 컨셉 없는 프로젝트에서 「잠시 후 다시 시도」가 나왔다.
 *
 * 그래서 재시도로 안 풀리는 실패는 **서버 문구를 그대로** 보인다. 여기에 같은 말을 다시
 * 적지 않는 이유는 그것이 `ErrorCode` 와 갈라지기 때문이다 — 문구의 정본은 서버다.
 * 손으로 만드는 길은 문구가 아니라 **화면에 남아 있는 「직접 만들기」 버튼**이 연다.
 */
export function draftFailureText(failure) {
  if (failure?.retryable === false && failure?.message) return failure.message;
  return getUserErrorMessage(failure);
}
