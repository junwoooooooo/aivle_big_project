import { useCallback, useEffect, useMemo, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createAdminApi } from '../api/adminApi.js';
import { isAdminRequestAborted } from '../api/adminErrorResolver.js';

export default function useAdminSettings() {
  const client = useApiClient();
  const api = useMemo(() => createAdminApi(client), [client]);
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState({
    data: null,
    loading: true,
    refreshing: false,
    error: null,
  });

  const refresh = useCallback(() => {
    setState((current) => ({
      ...current,
      loading: current.data == null,
      refreshing: current.data != null,
      error: null,
    }));
    setRevision((current) => current + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    api.settings({ signal: controller.signal })
      .then((data) => setState({
        data,
        loading: false,
        refreshing: false,
        error: null,
      }))
      .catch((error) => {
        if (isAdminRequestAborted(error)) return;
        setState((current) => ({
          ...current,
          loading: false,
          refreshing: false,
          error,
        }));
      });
    return () => controller.abort();
  }, [api, revision]);

  return { ...state, refresh, api };
}
