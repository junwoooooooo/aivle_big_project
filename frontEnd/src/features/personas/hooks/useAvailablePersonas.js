import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { useServicePolicy } from '../../service-policy/useServicePolicy.js';
import { isServicePolicyError } from '../../service-policy/servicePolicyRestrictions.js';
import { createPersonaApi } from '../api/personaApi.js';

export default function useAvailablePersonas(projectId, enabled) {
  const client = useApiClient();
  const api = useMemo(() => createPersonaApi(client), [client]);
  const { refresh: refreshPolicy } = useServicePolicy();
  const aborter = useRef(null);
  const [state, setState] = useState({
    data: null,
    loading: false,
    error: null,
    savingId: null,
  });

  const refresh = useCallback(async () => {
    if (!enabled) {
      setState({ data: null, loading: false, error: null, savingId: null });
      return null;
    }
    aborter.current?.abort();
    const controller = new AbortController();
    aborter.current = controller;
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      const data = await api.available(projectId, { signal: controller.signal });
      setState({ data, loading: false, error: null, savingId: null });
      return data;
    } catch (error) {
      if (error?.code !== 'REQUEST_ABORTED') {
        setState((current) => ({ ...current, loading: false, error }));
      }
      return null;
    }
  }, [api, enabled, projectId]);

  const select = useCallback(async (personaId) => {
    if (!enabled || state.savingId != null) return;
    setState((current) => ({ ...current, savingId: personaId, error: null }));
    try {
      const data = await api.select(projectId, personaId);
      setState({ data, loading: false, error: null, savingId: null });
    } catch (error) {
      if (isServicePolicyError(error) || error?.code === 'CLUSTER_PERSONA_DISABLED') {
        void refreshPolicy().catch(() => undefined);
      }
      setState((current) => ({
        ...current,
        savingId: null,
        error: new Error(getUserErrorMessage(error), { cause: error }),
      }));
    }
  }, [api, enabled, projectId, refreshPolicy, state.savingId]);

  useEffect(() => {
    void refresh();
    return () => aborter.current?.abort();
  }, [refresh]);

  useEffect(() => {
    if (!enabled) return undefined;
    const onFocus = () => void refresh();
    window.addEventListener('focus', onFocus);
    return () => window.removeEventListener('focus', onFocus);
  }, [enabled, refresh]);

  return { ...state, refresh, select };
}
