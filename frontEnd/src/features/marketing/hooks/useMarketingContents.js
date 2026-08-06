import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createMarketingApi } from '../api/marketingApi.js';

export default function useMarketingContents(projectId) {
  const client = useApiClient();
  const api = useMemo(() => createMarketingApi(client), [client]);
  const aborter = useRef(null);
  const [state, setState] = useState({ items: [], loading: true, error: null });

  const refresh = useCallback(async () => {
    aborter.current?.abort();
    const controller = new AbortController();
    aborter.current = controller;
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      const items = await api.list(projectId, { signal: controller.signal });
      setState({ items, loading: false, error: null });
      return items;
    } catch (error) {
      if (error?.code !== 'REQUEST_ABORTED') {
        setState((current) => ({ ...current, loading: false, error }));
      }
      return null;
    }
  }, [api, projectId]);

  useEffect(() => {
    void refresh();
    return () => aborter.current?.abort();
  }, [refresh]);

  return { ...state, refresh, api };
}
