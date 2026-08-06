import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createValidationApi } from '../api/validationApi.js';

export default function useValidationData(projectId, type, id) {
  const client = useApiClient();
  const api = useMemo(() => createValidationApi(client), [client]);
  const aborter = useRef(null);
  const [state, setState] = useState({
    items: [],
    personas: [],
    detail: null,
    loading: true,
    error: null,
  });

  const refresh = useCallback(async () => {
    aborter.current?.abort();
    const controller = new AbortController();
    aborter.current = controller;
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      const listRequest = type === 'interview'
        ? api.interviews(projectId, { signal: controller.signal })
        : api.marketResponses(projectId, { signal: controller.signal });
      const detailRequest = id
        ? type === 'interview'
          ? api.interview(projectId, id, { signal: controller.signal })
          : api.marketResponse(projectId, id, { signal: controller.signal })
        : Promise.resolve(null);
      const [items, personaResponse, detail] = await Promise.all([
        listRequest,
        api.personas(projectId, { signal: controller.signal }),
        detailRequest,
      ]);
      setState({
        items,
        personas: personaResponse.items,
        detail,
        loading: false,
        error: null,
      });
      return detail;
    } catch (error) {
      if (error?.code !== 'REQUEST_ABORTED') {
        setState((current) => ({ ...current, loading: false, error }));
      }
      return null;
    }
  }, [api, id, projectId, type]);

  useEffect(() => {
    void refresh();
    return () => aborter.current?.abort();
  }, [refresh]);

  const setDetail = useCallback((detail) => {
    setState((current) => ({ ...current, detail }));
  }, []);

  return { ...state, api, refresh, setDetail };
}
