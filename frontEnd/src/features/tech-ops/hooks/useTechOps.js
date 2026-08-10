import { useCallback, useEffect, useMemo, useState } from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { useJobEvents } from '../../../shared/async-events/index.js';
import { createTechOpsApi } from '../api/techOpsApi.js';

const commandOptions = () => {
  const key = globalThis.crypto?.randomUUID?.()
    ?? `tech-ops-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return { headers: { 'Idempotency-Key': key }, requestId: key };
};

export default function useTechOps(projectId) {
  const client = useApiClient();
  const api = useMemo(() => createTechOpsApi(client), [client]);
  const [state, setState] = useState({ loading: true, busy: null, preparation: null, snapshot: null, run: null, error: null });
  const proposalEvents = useJobEvents(state.preparation?.activeProposalTaskRunId ?? null);
  const refresh = useCallback(async () => {
    setState((value) => ({ ...value, loading: true, error: null }));
    try {
      let preparation;
      try { preparation = await api.preparation(projectId); }
      catch (error) {
        if (![404, 409, 422].includes(error?.status)) throw error;
        preparation = await api.initialize(projectId, commandOptions());
      }
      const [snapshotResult, runsResult] = await Promise.allSettled([api.currentSnapshot(projectId), api.runs(projectId)]);
      const snapshot = snapshotResult.status === 'fulfilled' ? snapshotResult.value : null;
      const runs = runsResult.status === 'fulfilled' ? runsResult.value.runs ?? [] : [];
      setState({ loading: false, busy: null, preparation, snapshot,
        run: runs.find((item) => item.module === 'TECH_OPS') ?? null, error: null });
    } catch (error) { setState((value) => ({ ...value, loading: false, busy: null, error })); }
  }, [api, projectId]);
  useEffect(() => { const timer = setTimeout(() => void refresh(), 0); return () => clearTimeout(timer); }, [refresh]);
  useEffect(() => {
    if (!proposalEvents.terminal) return undefined;
    const timer = setTimeout(() => void refresh(), 0);
    return () => clearTimeout(timer);
  }, [proposalEvents.terminal, refresh]);

  const act = async (busy, action) => {
    setState((value) => ({ ...value, busy, error: null }));
    try { const result = await action(); await refresh(); return result; }
    catch (error) { setState((value) => ({ ...value, busy: null, error })); throw error; }
  };
  return {
    ...state, proposalEvents, refresh,
    saveFacts: (values) => act('facts', () => api.patchFacts(projectId, values)),
    decide: (fieldKey, body) => act(fieldKey, () => api.decide(projectId, fieldKey, body, commandOptions())),
    retryProposals: () => act('proposal-retry', () => api.retryProposals(projectId, commandOptions())),
    uploadEvidence: (file, evidenceType, description) => act('evidence', async () => {
      const artifact = await api.uploadEvidenceArtifact(projectId, file);
      return api.addEvidence(projectId, { evidenceType, artifactId: artifact.artifactId, description });
    }),
    removeEvidence: (id) => act(`evidence-${id}`, () => api.removeEvidence(projectId, id)),
    finalize: () => act('finalize', () => api.finalize(projectId)),
    handoff: () => act('handoff', () => api.handoff(projectId, state.snapshot?.snapshotId)),
  };
}
