import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createMarketingApi } from '../api/marketingApi.js';

export default function useMarketingContent(projectId, contentId) {
  const client = useApiClient();
  const api = useMemo(() => createMarketingApi(client), [client]);
  const aborter = useRef(null);
  const [state, setState] = useState({
    data: null,
    versions: [],
    loading: true,
    error: null,
  });

  const refresh = useCallback(async () => {
    if (!contentId) return null;
    aborter.current?.abort();
    const controller = new AbortController();
    aborter.current = controller;
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      const [data, versions] = await Promise.all([
        api.detail(projectId, contentId, { signal: controller.signal }),
        api.versions(projectId, contentId, { signal: controller.signal }),
      ]);
      setState({ data, versions, loading: false, error: null });
      return data;
    } catch (error) {
      if (error?.code !== 'REQUEST_ABORTED') {
        setState((current) => ({ ...current, loading: false, error }));
      }
      return null;
    }
  }, [api, contentId, projectId]);

  useEffect(() => {
    void refresh();
    return () => aborter.current?.abort();
  }, [refresh]);

  const setData = useCallback((data) => {
    setState((current) => ({ ...current, data }));
  }, []);

  return { ...state, api, refresh, setData };
}
