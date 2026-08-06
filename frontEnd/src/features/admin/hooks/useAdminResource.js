import { useCallback, useEffect, useState } from 'react';

import { isAdminRequestAborted } from '../api/adminErrorResolver.js';

export default function useAdminResource(request) {
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
    request(controller.signal)
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
  }, [request, revision]);

  return { ...state, refresh };
}
