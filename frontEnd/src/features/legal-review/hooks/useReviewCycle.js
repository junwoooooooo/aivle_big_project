import { useCallback, useEffect, useRef, useState } from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createLegalReviewApi } from '../api/legalReviewApi.js';

/**
 * 검토 사이클 + 버전 히스토리 + 최신 발행물.
 * 활성 사이클이 없으면(첫 검토 전, 또는 발행 직후) cycle=null — 오류가 아니다.
 */
export function useReviewCycle(projectId) {
  const client = useApiClient();
  const mounted = useRef(false);
  const [state, setState] = useState({ cycle: null, versions: [], publication: null });

  const refresh = useCallback(async () => {
    const api = createLegalReviewApi(client);
    const [cycle, versions, publication] = await Promise.all([
      api.activeCycle(projectId).catch(() => null),
      api.planVersions(projectId).catch(() => []),
      api.latestPublication(projectId).catch(() => null),
    ]);
    if (mounted.current) setState({ cycle, versions: versions ?? [], publication });
  }, [client, projectId]);

  useEffect(() => {
    if (!projectId) return undefined;
    mounted.current = true;
    refresh();
    return () => { mounted.current = false; };
  }, [projectId, refresh]);

  return { ...state, refresh };
}
