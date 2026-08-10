import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { useJobEvents } from '../../../shared/async-events/index.js';
import { createConceptFactoryApi } from '../api/conceptFactoryApi.js';

export default function useConceptFactory(projectId) {
  const client = useApiClient();
  const api = useMemo(() => createConceptFactoryApi(client), [client]);
  const [state, setState] = useState({ loading: true, run: null, slots: [], concepts: [], error: null, confirmedSnapshotId: null });
  const [actionPending, setActionPending] = useState(false);
  const actionPendingRef = useRef(false);
  const refreshedSequence = useRef(0);
  const jobEvents = useJobEvents(state.run?.activeJobId ?? null);

  const refresh = useCallback(async () => {
    try {
      const current = await api.current(projectId);
      const run = current.data;
      const [slotPayload, conceptPayload, briefPayload] = await Promise.all([
        api.slots(projectId, run.runId), api.concepts(projectId), api.ideaBrief(projectId),
      ]);
      setState({ loading: false, run, slots: slotPayload.data ?? [], concepts: conceptPayload.data?.concepts ?? [], error: null, confirmedSnapshotId: briefPayload.data?.confirmedSnapshotId ?? null });
    } catch (error) {
      if (error?.status === 404) {
        try {
          const brief = await api.ideaBrief(projectId);
          setState({ loading: false, run: null, slots: [], concepts: [], error: null, confirmedSnapshotId: brief.data?.confirmedSnapshotId });
        } catch (briefError) {
          setState((value) => ({ ...value, loading: false, error: briefError }));
        }
        return;
      }
      setState((value) => ({ ...value, loading: false, error }));
    }
  }, [api, projectId]);

  useEffect(() => { const timer = setTimeout(refresh, 0); return () => clearTimeout(timer); }, [refresh]);
  useEffect(() => {
    if (!jobEvents.lastSequence || jobEvents.lastSequence === refreshedSequence.current) return undefined;
    refreshedSequence.current = jobEvents.lastSequence;
    const latest = jobEvents.events.at(-1);
    if (!latest || (!jobEvents.terminal && !latest.eventType?.startsWith('job.concept.slot.'))) return undefined;
    const timer = setTimeout(refresh, 100);
    return () => clearTimeout(timer);
  }, [jobEvents.events, jobEvents.lastSequence, jobEvents.terminal, refresh]);

  const start = async () => {
    if (!state.confirmedSnapshotId || actionPendingRef.current) return;
    actionPendingRef.current = true;
    setActionPending(true);
    try {
      await api.create(projectId, state.confirmedSnapshotId);
      await refresh();
    } finally { actionPendingRef.current = false; setActionPending(false); }
  };
  const retry = async () => {
    if (!state.run || actionPendingRef.current) return;
    actionPendingRef.current = true;
    setActionPending(true);
    try {
      await api.retry(projectId, state.run.runId, crypto.randomUUID());
      jobEvents.reconnect();
      await refresh();
    } finally { actionPendingRef.current = false; setActionPending(false); }
  };

  const startNew = async () => {
    if (!state.confirmedSnapshotId || actionPendingRef.current) return;
    actionPendingRef.current = true;
    setActionPending(true);
    try {
      await api.create(projectId, state.confirmedSnapshotId);
      await refresh();
    } finally { actionPendingRef.current = false; setActionPending(false); }
  };

  return { ...state, jobEvents, refresh, start, retry, startNew, actionPending };
}
