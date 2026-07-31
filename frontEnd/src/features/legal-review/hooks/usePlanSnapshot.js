import { useEffect, useState } from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createLegalReviewApi } from '../api/legalReviewApi.js';

/**
 * 정식 보고서 Ⅱ(사실관계)용 확정 계획 스냅샷.
 * 검토가 입력으로 삼은 그 버전(structuredPlanId)을 by-id로 조회한다 —
 * 재검토 루프에서는 latest가 검토 입력과 다른 버전일 수 있기 때문이다.
 */
export function usePlanSnapshot(projectId, structuredPlanId) {
  const client = useApiClient();
  const [plan, setPlan] = useState(null);

  useEffect(() => {
    if (!projectId || !structuredPlanId) return undefined;
    const aborter = new AbortController();
    let mounted = true;
    createLegalReviewApi(client)
      .planById(projectId, structuredPlanId, { signal: aborter.signal })
      .then((snapshot) => {
        if (mounted && snapshot?.planId === structuredPlanId) setPlan(snapshot);
      })
      .catch(() => {
        // 보고서 Ⅱ는 있으면 좋은 보강 정보다 — 실패해도 페이지는 정상 동작한다.
      });
    return () => {
      mounted = false;
      aborter.abort();
    };
  }, [client, projectId, structuredPlanId]);

  return plan;
}
