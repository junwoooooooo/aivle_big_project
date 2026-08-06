import { useCallback, useEffect, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createReportApi } from '../api/reportApi.js';
import { toIntegratedReportViewModel } from '../model/reportViewModel.js';

export function useIntegratedReport(project) {
  const client = useApiClient();
  const [state, setState] = useState({
    status: 'loading',
    report: null,
    error: null,
  });

  const load = useCallback(async (signal) => {
    try {
      // StrictMode의 첫 effect가 즉시 정리되면 네트워크 요청을 시작하지 않는다.
      await Promise.resolve();
      if (signal?.aborted) return;
      setState((current) => ({ ...current, status: 'loading', error: null }));
      const resources = await createReportApi(client).load(project.projectId, { signal });
      if (signal?.aborted) return;
      setState({
        status: 'success',
        report: toIntegratedReportViewModel(project, resources),
        error: null,
      });
    } catch (error) {
      if (signal?.aborted || error?.code === 'REQUEST_ABORTED') return;
      setState({ status: 'error', report: null, error });
    }
  }, [client, project]);

  const retry = useCallback(() => {
    const controller = new AbortController();
    load(controller.signal);
    return () => controller.abort();
  }, [load]);

  useEffect(() => {
    const controller = new AbortController();
    queueMicrotask(() => load(controller.signal));
    return () => controller.abort();
  }, [load]);

  return { ...state, retry };
}
