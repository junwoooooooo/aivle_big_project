import { useCallback, useEffect, useMemo, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createMarketIntegrationApi } from '../api/marketIntegrationApi.js';

export default function useMarketIntegration(projectId) {
  const client = useApiClient();
  const api = useMemo(() => createMarketIntegrationApi(client), [client]);
  const [state, setState] = useState({ loading: true, selection: null, runs: [], handoff: null, error: null, preparing: false });
  const refresh = useCallback(async () => {
    try {
      const [selection, runPayload] = await Promise.all([
        api.currentSelection(projectId).then((payload) => payload.data).catch((error) => error?.status === 404 ? null : Promise.reject(error)),
        api.runs(projectId),
      ]);
      setState((value) => ({ ...value, loading: false, selection, runs: runPayload.data?.runs ?? [], error: null }));
    } catch (error) {
      setState((value) => ({ ...value, loading: false, error }));
    }
  }, [api, projectId]);
  useEffect(() => { const timer = setTimeout(refresh, 0); return () => clearTimeout(timer); }, [refresh]);
  const prepare = async () => {
    if (!state.selection) return;
    setState((value) => ({ ...value, preparing: true, error: null }));
    try {
      const payload = await api.prepare(projectId, state.selection.snapshot.snapshotId);
      setState((value) => ({ ...value, preparing: false, handoff: payload.data,
        runs: [payload.data.moduleRun, ...value.runs.filter((run) => run.runId !== payload.data.moduleRun.runId)] }));
    } catch (error) {
      setState((value) => ({ ...value, preparing: false, error }));
    }
  };
  return { ...state, refresh, prepare };
}
