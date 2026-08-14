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

const activeAnalysisTask = (analysis) => ['QUEUED', 'RUNNING'].includes(analysis?.status)
  ? analysis.taskRunId : null;

export default function useFinance(projectId, liveRevision = 0) {
  const client = useApiClient();
  const api = useMemo(() => createFinanceApi(client), [client]);
  const [state, setState] = useState({ loading: true, busy: null, preparation: null, snapshot: null, run: null, analysis: null, error: null });
  const estimateEvents = useJobEvents(activeEstimateTask(state.preparation));
  const analysisEvents = useJobEvents(activeAnalysisTask(state.analysis));
  const refresh = useCallback(async ({ preserveView = false } = {}) => {
    setState((value) => ({ ...value, loading: preserveView ? value.loading : true, error: null }));
    try {
      let preparation;
      try { preparation = await api.preparation(projectId); }
      catch (error) {
        if (![404, 409, 422].includes(error?.status)) throw error;
        preparation = await api.initialize(projectId);
      }
      const [snapshotResult, runsResult, analysisResult] = await Promise.allSettled([
        preparation.inputSnapshotId ? api.currentSnapshot(projectId) : Promise.resolve(null),
        api.runs(projectId), api.currentAnalysis(projectId),
      ]);
      const snapshot = snapshotResult.status === 'fulfilled' ? snapshotResult.value : null;
      const runs = runsResult.status === 'fulfilled' ? runsResult.value.runs ?? [] : [];
      const analysis = analysisResult.status === 'fulfilled' ? analysisResult.value : null;
      setState((value) => ({ ...value, loading: false, busy: null, preparation, snapshot, analysis,
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
    if (!analysisEvents.terminal) return undefined;
    const timer = setTimeout(() => void refresh({ preserveView: true }), 0);
    return () => clearTimeout(timer);
  }, [analysisEvents.terminal, refresh]);
  useEffect(() => {
    if (!liveRevision) return undefined;
    const timer = setTimeout(() => void refresh({ preserveView: true }), 0);
    return () => clearTimeout(timer);
  }, [liveRevision, refresh]);

  const act = async (busy, action) => {
    setState((value) => ({ ...value, busy, error: null }));
    try { const result = await action(); await refresh({ preserveView: true }); return result; }
    catch (error) { setState((value) => ({ ...value, busy: null, error })); throw error; }
  };
  return {
    ...state, estimateEvents, analysisEvents, refresh,
    save: (values) => act('save', () => api.patchFields(projectId, values)),
    generateEstimate: (fieldKey) => act(`estimate:${fieldKey}`, () => api.generateEstimate(projectId, fieldKey, commandOptions())),
    generateEstimates: (fieldKeys) => act('estimate:group', async () => {
      const outcomes = await Promise.allSettled(
        fieldKeys.map((fieldKey) => api.generateEstimate(projectId, fieldKey, commandOptions())),
      );
      const rejected = outcomes.find((outcome) => outcome.status === 'rejected');
      if (rejected) {
        await refresh({ preserveView: true });
        throw rejected.reason;
      }
      return outcomes.map((outcome) => outcome.value);
    }),
    decideEstimate: (fieldKey, payload) => act(`estimate:${fieldKey}`, () => api.decideEstimate(projectId, fieldKey, payload, commandOptions())),
    finalize: () => act('finalize', () => api.finalize(projectId)),
    reopen: () => act('reopen', () => api.reopen(projectId, commandOptions())),
    analyze: () => act('analysis', () => api.startAnalysis(projectId, commandOptions())),
    handoff: () => act('handoff', () => api.handoff(projectId, state.snapshot?.snapshotId)),
  };
}
