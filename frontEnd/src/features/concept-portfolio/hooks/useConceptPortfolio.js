import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { useJobEvents } from '../../../shared/async-events/index.js';
import { createConceptPortfolioApi } from '../api/conceptPortfolioApi.js';
import { normalizePortfolioConcepts } from '../businessProposalModel.js';

const optional = (promise) => promise.then((payload) => payload?.data ?? null)
  .catch((error) => ([404, 409, 422].includes(error?.status) ? null : Promise.reject(error)));
const key = (prefix) => `${prefix}-${globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`}`;

export async function startNewConceptPortfolioRun(api, projectId) {
  const brief = (await api.ideaBrief(projectId))?.data;
  const ideaBriefSnapshotId = brief?.confirmedSnapshotId ?? brief?.snapshotId ?? brief?.id;
  return api.createRun(projectId, {
    ideaBriefSnapshotId, maxConcepts: 5, idempotencyKey: key('portfolio'),
  });
}

export function useConceptPortfolio(projectId, liveRevision = 0, authoritativeModuleStatus = 'UNSPECIFIED') {
  const client = useApiClient();
  const api = useMemo(() => createConceptPortfolioApi(client), [client]);
  const retryDeltaInFlight = useRef(false);
  const [state, setState] = useState({ loading: true, run: null, concepts: [], inputRequests: [], selection: null, hypotheses: [], report: null, marketSeed: null, error: null, busy: false, activeSelectionTaskRunId: null });
  const selectionJobId = state.activeSelectionTaskRunId
    ?? (['HYPOTHESES_PREPARING', 'PENDING_HYPOTHESIS_CONFIRMATION', 'DELTA_LEGAL_PENDING']
      .includes(state.selection?.status) ? state.selection?.activeTaskRunId : null);
  const selectionEvents = useJobEvents(selectionJobId);

  const refresh = useCallback(async () => {
    if (authoritativeModuleStatus === null) {
      setState((value) => ({ ...value, loading: true }));
      return;
    }
    try {
      const run = await optional(api.currentRun(projectId));
      const [concepts, inputRequests, selection] = await Promise.all([
        run ? optional(api.concepts(projectId, run.runId)) : [],
        run ? optional(api.inputRequests(projectId, run.runId)) : [],
        optional(api.currentSelection(projectId)),
      ]);
      const [hypotheses, report, marketSeed] = selection ? await Promise.all([
        optional(api.hypotheses(projectId, selection.selectionId)),
        optional(api.report(projectId, selection.selectionId)),
        optional(api.marketSeed(projectId, selection.selectionId)),
      ]) : [[], null, null];
      const stale = authoritativeModuleStatus === 'STALE';
      setState((value) => ({ ...value, loading: false,
        run: stale && run ? { ...run, productStatus: 'STALE' } : run,
        concepts: stale ? [] : normalizePortfolioConcepts(concepts),
        inputRequests: stale ? [] : inputRequests ?? [], selection: stale ? null : selection,
        hypotheses: stale ? [] : hypotheses ?? [], report: stale ? null : report,
        marketSeed: stale ? null : marketSeed, error: null }));
    } catch (error) {
      setState((value) => ({ ...value, loading: false, error }));
    }
  }, [api, authoritativeModuleStatus, projectId]);

  useEffect(() => { const timer = setTimeout(refresh, 0); return () => clearTimeout(timer); }, [refresh, liveRevision]);
  useEffect(() => {
    if (!selectionEvents.terminal || !selectionJobId) return undefined;
    const timer = setTimeout(() => {
      setState((value) => ({ ...value, activeSelectionTaskRunId: null }));
      void refresh();
    }, 0);
    return () => clearTimeout(timer);
  }, [selectionEvents.terminal, selectionJobId, refresh]);
  const act = useCallback(async (operation) => {
    setState((value) => ({ ...value, busy: true, error: null }));
    try { await operation(); await refresh(); }
    catch (error) { setState((value) => ({ ...value, error })); }
    finally { setState((value) => ({ ...value, busy: false })); }
  }, [refresh]);

  return {
    ...state,
    activeSelectionTaskRunId: selectionJobId,
    selectionEvents,
    confirmationTaskRunId: selectionJobId,
    confirmationEvents: selectionEvents,
    refresh,
    start: () => act(() => startNewConceptPortfolioRun(api, projectId)),
    select: (conceptId) => act(() => api.select(projectId, { runId: state.run.runId, conceptId, selectionReason: '사용자가 선택한 사업안', idempotencyKey: key('selection') })),
    respond: (requestId, confirmedFacts, note) => act(() => api.respond(projectId, state.run.runId, requestId, { confirmedFacts, note, idempotencyKey: key('input') })),
    retryContinuation: (requestId) => act(() => api.retryContinuation(projectId, state.run.runId, requestId, { idempotencyKey: key('continuation-retry') })),
    confirm: async (changes) => {
      setState((value) => ({ ...value, busy: true, error: null }));
      try {
        const response = await api.confirm(projectId, state.selection.selectionId,
          { changes, confirmAll: true, idempotencyKey: key('hypotheses') });
        const action = response?.data ?? response;
        setState((value) => ({ ...value, activeSelectionTaskRunId: action?.taskRunId ?? null }));
        await refresh();
        return action;
      } catch (error) {
        setState((value) => ({ ...value, error }));
        throw error;
      } finally {
        setState((value) => ({ ...value, busy: false }));
      }
    },
    alternative: (type) => act(() => api.alternative(projectId, state.selection.selectionId, type, { idempotencyKey: key('alternative') })),
    retryDelta: async () => {
      if (retryDeltaInFlight.current) return null;
      retryDeltaInFlight.current = true;
      setState((value) => ({ ...value, busy: true, error: null }));
      try {
        const response = await api.retryDelta(projectId, state.selection.selectionId,
          { idempotencyKey: key('delta-retry') });
        const action = response?.data ?? response;
        setState((value) => ({ ...value, activeSelectionTaskRunId: action?.taskRunId ?? null }));
        await refresh();
        return action;
      } catch (error) {
        await refresh();
        setState((value) => ({ ...value, error }));
        throw error;
      } finally {
        retryDeltaInFlight.current = false;
        setState((value) => ({ ...value, busy: false }));
      }
    },
    finalizeReport: () => act(() => api.finalizeReport(projectId, state.selection.selectionId)),
    finalizeMarketSeed: () => act(() => api.finalizeMarketSeed(projectId, state.selection.selectionId, { idempotencyKey: key('market-seed') })),
  };
}
