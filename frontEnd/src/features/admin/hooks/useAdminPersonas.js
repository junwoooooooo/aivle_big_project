import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createAdminApi } from '../api/adminApi.js';
import { isAdminRequestAborted } from '../api/adminErrorResolver.js';

export default function useAdminPersonas() {
  const client = useApiClient();
  const api = useMemo(() => createAdminApi(client), [client]);
  const aborter = useRef(null);
  const [state, setState] = useState({
    data: null,
    loading: true,
    refreshing: false,
    error: null,
  });

  const refresh = useCallback(async () => {
    aborter.current?.abort();
    const controller = new AbortController();
    aborter.current = controller;
    setState((current) => ({
      ...current,
      loading: current.data == null,
      refreshing: current.data != null,
      error: null,
    }));
    try {
      const data = await api.personas({ signal: controller.signal });
      setState({ data, loading: false, refreshing: false, error: null });
      return data;
    } catch (error) {
      if (!isAdminRequestAborted(error)) {
        setState((current) => ({
          ...current,
          loading: false,
          refreshing: false,
          error,
        }));
      }
      throw error;
    }
  }, [api]);

  useEffect(() => {
    void refresh().catch(() => undefined);
    return () => aborter.current?.abort();
  }, [refresh]);

  return { ...state, refresh, api };
}
