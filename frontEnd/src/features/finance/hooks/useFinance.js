import { useCallback, useEffect, useMemo, useState } from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createFinanceApi } from '../api/financeApi.js';

export default function useFinance(projectId) {
  const client = useApiClient();
  const api = useMemo(() => createFinanceApi(client), [client]);
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
        run: runs.find((item) => item.module === 'FINANCIAL_ANALYSIS') ?? null, error: null });
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
    save: (values) => act('save', () => api.patchFields(projectId, values)),
    finalize: () => act('finalize', () => api.finalize(projectId)),
    handoff: () => act('handoff', () => api.handoff(projectId, state.snapshot?.snapshotId)),
  };
}
