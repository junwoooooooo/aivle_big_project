import { useCallback, useEffect, useMemo, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createAdminApi } from '../api/adminApi.js';
import { isAdminRequestAborted } from '../api/adminErrorResolver.js';

export default function useAdminProjects(query) {
  const client = useApiClient();
  const api = useMemo(() => createAdminApi(client), [client]);
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState({ data: null, loading: true, refreshing: false, error: null });
  const queryKey = JSON.stringify(query);
  const refresh = useCallback(() => setRevision((current) => current + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    Promise.resolve().then(() => {
      if (!active) return null;
      setState((current) => ({
        ...current,
        loading: current.data === null,
        refreshing: current.data !== null,
        error: null,
      }));
      return api.projects(query, { signal: controller.signal });
    }).then((data) => {
      if (active && data !== null) setState({ data, loading: false, refreshing: false, error: null });
    }).catch((error) => {
      if (!active || isAdminRequestAborted(error)) return;
      setState((current) => ({ ...current, loading: false, refreshing: false, error }));
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [api, query, queryKey, revision]);

  return { ...state, refresh };
}
