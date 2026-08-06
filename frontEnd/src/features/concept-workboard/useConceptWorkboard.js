import { useCallback, useEffect, useMemo, useState } from 'react';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { useJobEvents } from '../../shared/async-events/index.js';
import { createConceptWorkboardApi } from './conceptWorkboardApi.js';
import { sortSlots } from './conceptWorkboardModel.js';

const IMPORTANT_EVENTS = new Set([
  'job.concept.slot.generated', 'job.concept.slot.schema_invalid', 'job.concept.slot.retrying',
  'job.concept.slot.repairing', 'job.concept.slot.validating_origin',
  'job.concept.slot.validating_boundary', 'job.concept.slot.redesigning',
  'job.concept.slot.replacing', 'job.concept.slot.eligible', 'job.concept.slot.rejected',
  'job.concept.batch.needs_input', 'job.concept.batch.completed', 'job.concept.batch.failed',
  'job.concept.batch.recovered',
]);

export function useConceptWorkboard(projectId, enabled = true) {
  const client = useApiClient();
  const api = useMemo(() => createConceptWorkboardApi(client, projectId), [client, projectId]);
  const [state, setState] = useState({ batch: null, slots: [], concepts: [] });
  const [network, setNetwork] = useState('IDLE');
  const [error, setError] = useState('');
  const job = useJobEvents(enabled ? state.batch?.jobId : null);

  const load = useCallback(async () => {
    if (!enabled) return;
    setNetwork('LOADING');
    try {
      const current = await api.current();
      if (!current?.batch?.batchId) {
        setState({ batch: null, slots: [], concepts: [] });
        setNetwork('IDLE');
        return;
      }
      const batchId = current.batch.batchId;
      const [detail, slots] = await Promise.all([api.batch(batchId), api.slots(batchId)]);
      const batch = detail?.batch || current.batch;
      const concepts = batch.status === 'COMPLETED' ? await api.concepts() : [];
      setState({ batch, slots: sortSlots(slots || detail?.slots || current.slots), concepts });
      setError('');
      setNetwork(job.transport === 'POLLING' ? 'POLLING' : 'STREAMING');
    } catch (failure) {
      setNetwork('ERROR');
      setError(getUserErrorMessage(failure));
    }
  }, [api, enabled, job.transport]);

  useEffect(() => {
    // Loading persisted state synchronizes this feature with the current project route.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);
  const lastEvent = job.events.at(-1);
  const lastSequence = lastEvent?.sequence;
  const lastMessageKey = lastEvent?.messageKey;
  useEffect(() => {
    if (!lastSequence || !IMPORTANT_EVENTS.has(lastMessageKey)) return;
    // Durable events signal that authoritative query state should be reloaded.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [lastSequence, lastMessageKey, load]);

  const start = useCallback(async (briefId, boundaryId) => {
    setNetwork('LOADING'); setError('');
    try {
      const batch = await api.start(briefId, boundaryId);
      setState({ batch, slots: [], concepts: [] });
      setNetwork('STREAMING');
      return batch;
    } catch (failure) {
      setNetwork('ERROR'); setError(getUserErrorMessage(failure)); return null;
    }
  }, [api]);

  const retry = useCallback(async () => {
    if (!state.batch?.batchId || !state.batch.retryable) return;
    setNetwork('LOADING'); setError('');
    try {
      const batch = await api.retry(state.batch.batchId);
      setState((current) => ({ ...current, batch, concepts: [] }));
      setNetwork('STREAMING');
    } catch (failure) { setNetwork('ERROR'); setError(getUserErrorMessage(failure)); }
  }, [api, state.batch]);

  return { ...state, network, error, job, load, start, retry, hasBatch: Boolean(state.batch?.batchId) };
}
