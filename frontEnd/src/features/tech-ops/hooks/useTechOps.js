import { useCallback, useEffect, useMemo, useState } from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createTechOpsApi } from '../api/techOpsApi.js';

export default function useTechOps(projectId) {
  const client = useApiClient();
  const api = useMemo(() => createTechOpsApi(client), [client]);
  const [state, setState] = useState({ loading: true, busy: null, preparation: null, snapshot: null, run: null, error: null });
  const refresh = useCallback(async () => {
    setState((value) => ({ ...value, loading: true, error: null }));
    try {
      let preparation;
      try { preparation = await api.preparation(projectId); }
      catch (error) {
        if (![404, 409, 422].includes(error?.status)) throw error;
        preparation = await api.initialize(projectId);
      }
      const [snapshotResult, runsResult] = await Promise.allSettled([api.currentSnapshot(projectId), api.runs(projectId)]);
      const snapshot = snapshotResult.status === 'fulfilled' ? snapshotResult.value : null;
      const runs = runsResult.status === 'fulfilled' ? runsResult.value.runs ?? [] : [];
      setState({ loading: false, busy: null, preparation, snapshot,
        run: runs.find((item) => item.module === 'TECH_OPS') ?? null, error: null });
    } catch (error) { setState((value) => ({ ...value, loading: false, busy: null, error })); }
  }, [api, projectId]);
  useEffect(() => { const timer = setTimeout(() => void refresh(), 0); return () => clearTimeout(timer); }, [refresh]);

  const act = async (busy, action) => {
    setState((value) => ({ ...value, busy, error: null }));
    try { const result = await action(); await refresh(); return result; }
    catch (error) { setState((value) => ({ ...value, busy: null, error })); throw error; }
  };
  return {
    ...state, refresh,
    saveFacts: (values) => act('facts', () => api.patchFacts(projectId, values)),
    decide: (fieldKey, body) => act(fieldKey, () => api.decide(projectId, fieldKey, body)),
    addEvidence: (body) => act('evidence', () => api.addEvidence(projectId, body)),
    removeEvidence: (id) => act(`evidence-${id}`, () => api.removeEvidence(projectId, id)),
    finalize: () => act('finalize', () => api.finalize(projectId)),
    handoff: () => act('handoff', () => api.handoff(projectId, state.snapshot?.snapshotId)),
  };
}
