import { useCallback, useEffect, useMemo, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createAdminApi } from '../api/adminApi.js';
import { isAdminRequestAborted } from '../api/adminErrorResolver.js';

export default function useAdminUserDetail(userId) {
  const client = useApiClient();
  const api = useMemo(() => createAdminApi(client), [client]);
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const refresh = useCallback(() => setRevision((current) => current + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    Promise.resolve().then(() => api.user(userId, { signal: controller.signal }))
      .then((data) => {
        if (active) setState({ data, loading: false, error: null });
      })
      .catch((error) => {
        if (!active || isAdminRequestAborted(error)) return;
        setState({ data: null, loading: false, error });
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [api, revision, userId]);

  return { ...state, refresh };
}
