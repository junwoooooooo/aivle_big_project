import { useCallback, useEffect, useMemo, useState } from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { useJobEvents } from '../../../shared/async-events/index.js';
import { createFinanceApi } from '../api/financeApi.js';

const commandOptions = () => {
  const key = globalThis.crypto?.randomUUID?.()
    ?? `finance-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return { headers: { 'Idempotency-Key': key }, requestId: key };
};

const activeEstimateTask = (preparation) => Object.values(preparation?.assistance ?? {})
  .find((item) => ['QUEUED', 'RUNNING'].includes(item?.estimateStatus) && item?.activeTaskRunId)
  ?.activeTaskRunId ?? null;

export default function useFinance(projectId) {
  const client = useApiClient();
  const api = useMemo(() => createFinanceApi(client), [client]);
  const [state, setState] = useState({ loading: true, busy: null, preparation: null, snapshot: null, run: null, analysis: null, error: null });
  const estimateEvents = useJobEvents(activeEstimateTask(state.preparation));
  const refresh = useCallback(async ({ preserveView = false } = {}) => {
    setState((value) => ({ ...value, loading: preserveView ? value.loading : true, error: null }));
    try {
      let preparation;
      try { preparation = await api.preparation(projectId); }
      catch (error) {
        if (![404, 409, 422].includes(error?.status)) throw error;
        preparation = await api.initialize(projectId);
      }
      const [snapshotResult, runsResult] = await Promise.allSettled([
        preparation.inputSnapshotId ? api.currentSnapshot(projectId) : Promise.resolve(null), api.runs(projectId),
      ]);
      const snapshot = snapshotResult.status === 'fulfilled' ? snapshotResult.value : null;
      const runs = runsResult.status === 'fulfilled' ? runsResult.value.runs ?? [] : [];
      setState((value) => ({ ...value, loading: false, busy: null, preparation, snapshot,
        run: runs.find((item) => item.module === 'FINANCIAL_ANALYSIS') ?? null, error: null }));
    } catch (error) { setState((value) => ({ ...value, loading: false, busy: null, error })); }
  }, [api, projectId]);
  useEffect(() => { const timer = setTimeout(() => void refresh(), 0); return () => clearTimeout(timer); }, [refresh]);
  useEffect(() => {
    if (!estimateEvents.terminal) return undefined;
    const timer = setTimeout(() => void refresh({ preserveView: true }), 0);
    return () => clearTimeout(timer);
  }, [estimateEvents.terminal, refresh]);
  useEffect(() => {
    if (!activeEstimateTask(state.preparation)) return undefined;
    const timer = setInterval(() => void refresh({ preserveView: true }), 2500);
    return () => clearInterval(timer);
  }, [state.preparation, refresh]);

  const act = async (busy, action) => {
    setState((value) => ({ ...value, busy, error: null }));
    try { const result = await action(); await refresh({ preserveView: true }); return result; }
    catch (error) { setState((value) => ({ ...value, busy: null, error })); throw error; }
  };
  return {
    ...state, estimateEvents, refresh,
    save: (values) => act('save', () => api.patchFields(projectId, values)),
    generateEstimate: (fieldKey) => act(`estimate:${fieldKey}`, () => api.generateEstimate(projectId, fieldKey, commandOptions())),
    generateEstimates: (fieldKeys) => act('estimate-group', async () => Promise.all(
      fieldKeys.map((fieldKey) => api.generateEstimate(projectId, fieldKey, commandOptions())))),
    decideEstimate: (fieldKey, payload) => act(`estimate:${fieldKey}`, () => api.decideEstimate(projectId, fieldKey, payload, commandOptions())),
    finalize: () => act('finalize', () => api.finalize(projectId)),
    reopen: () => act('reopen', () => api.reopen(projectId, commandOptions())),
    analyze: () => act('analysis', async () => {
      const analysis = await api.analyze(projectId);
      setState((value) => ({ ...value, analysis }));
      return analysis;
    }),
    demo: () => act('demo', async () => {
      const analysis = await api.demo(projectId);
      setState((value) => ({ ...value, analysis }));
      return analysis;
    }),
    handoff: () => act('handoff', () => api.handoff(projectId, state.snapshot?.snapshotId)),
  };
}
