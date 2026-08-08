import { useCallback, useEffect, useMemo, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { useJobEvents } from '../../../shared/async-events/index.js';
import { createConceptSelectionApi } from '../api/conceptSelectionApi.js';
import { createLocalSelectionDraft } from '../model/conceptComparisonModel.js';

const storageKey = (projectId) => `concept-selection-draft:${projectId}`;

function readDraft(projectId) {
  try {
    return JSON.parse(sessionStorage.getItem(storageKey(projectId))) ?? null;
  } catch {
    return null;
  }
}

export default function useConceptSelection(projectId) {
  const client = useApiClient();
  const api = useMemo(() => createConceptSelectionApi(client), [client]);
  const [state, setState] = useState({ loading: true, run: null, concepts: [], currentSelection: null, marketSeed: null, error: null, finalizing: false });
  const [draft, setDraft] = useState(() => readDraft(projectId));
  const actionEvents = useJobEvents(state.currentSelection?.activeActionTaskRunId ?? null);

  const refresh = useCallback(async () => {
    try {
      const runPayload = await api.currentRun(projectId);
      const run = runPayload.data;
      if (run?.status !== 'COMPLETED') {
        setState({ loading: false, run, concepts: [], currentSelection: null, marketSeed: null, error: null, finalizing: false });
        return;
      }
      const [conceptPayload, currentSelection, marketSeed] = await Promise.all([
        api.concepts(projectId),
        api.currentSelection(projectId).then((payload) => payload.data).catch((error) => {
          if (error?.status === 404) return null;
          throw error;
        }),
        api.currentMarketSeed(projectId).then((payload) => payload.data).catch((error) => {
          if ([404, 409, 422].includes(error?.status)) return null;
          throw error;
        }),
      ]);
      setState({ loading: false, run, concepts: conceptPayload.data?.concepts ?? [], currentSelection, marketSeed, error: null, finalizing: false });
    } catch (error) {
      if (error?.status === 404) {
        setState({ loading: false, run: null, concepts: [], currentSelection: null, marketSeed: null, error: null, finalizing: false });
        return;
      }
      setState((value) => ({ ...value, loading: false, error }));
    }
  }, [api, projectId]);

  useEffect(() => { const timer = setTimeout(refresh, 0); return () => clearTimeout(timer); }, [refresh]);
  useEffect(() => {
    if (!actionEvents.terminal) return undefined;
    const timer = setTimeout(refresh, 0);
    return () => clearTimeout(timer);
  }, [actionEvents.terminal, refresh]);

  const saveDraft = useCallback((comparedConceptIds, preferredConceptId) => {
    const next = createLocalSelectionDraft(projectId, comparedConceptIds, preferredConceptId);
    sessionStorage.setItem(storageKey(projectId), JSON.stringify(next));
    setDraft(next);
    return next;
  }, [projectId]);

  const confirmSelection = useCallback(async (conceptId, selectionReason) => {
    const payload = await api.select(projectId, conceptId, selectionReason);
    setState((value) => ({ ...value, currentSelection: payload.data, marketSeed: null }));
    return payload.data;
  }, [api, projectId]);

  const decideHypothesis = useCallback(async (hypothesisType, action, expectedProposalVersion, value) => {
    const commandKey = globalThis.crypto?.randomUUID?.()
      ?? `concept-selection-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    const payload = await api.decideHypothesis(projectId, hypothesisType, {
      action, expectedProposalVersion, ...(value === undefined ? {} : { value }),
    }, { headers: { 'Idempotency-Key': commandKey }, requestId: commandKey });
    setState((stateValue) => {
      const current = stateValue.currentSelection;
      if (!current) return stateValue;
      const hypotheses = (current.hypotheses ?? []).filter((item) => item.hypothesisType !== hypothesisType);
      hypotheses.push(payload.data.hypothesis);
      return { ...stateValue, currentSelection: {
        ...current, hypotheses, decisionComplete: payload.data.decisionComplete,
        activeActionTaskRunId: payload.data.taskRunId ?? null,
        pendingActionType: payload.data.taskRunId ? payload.data.actionType : null,
        pendingHypothesisType: payload.data.taskRunId ? payload.data.hypothesisType : null,
        actionStatus: payload.data.taskRunId ? payload.data.status : 'IDLE',
        safeActionError: null,
      } };
    });
    return payload.data;
  }, [api, projectId]);

  const finalizeMarketSeed = useCallback(async () => {
    setState((value) => ({ ...value, finalizing: true, error: null }));
    try {
      const payload = await api.finalizeMarketSeed(projectId);
      setState((value) => ({ ...value, marketSeed: payload.data, finalizing: false }));
      return payload.data;
    } catch (error) {
      setState((value) => ({ ...value, finalizing: false, error }));
      throw error;
    }
  }, [api, projectId]);

  return { ...state, draft, actionEvents, refresh, saveDraft, confirmSelection, decideHypothesis, finalizeMarketSeed };
}
