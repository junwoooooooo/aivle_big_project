import { useCallback, useEffect, useMemo, useState } from 'react';

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

export function useConceptPortfolio(projectId, liveRevision = 0) {
  const client = useApiClient();
  const api = useMemo(() => createConceptPortfolioApi(client), [client]);
  const [state, setState] = useState({ loading: true, run: null, concepts: [], inputRequests: [], selection: null, hypotheses: [], report: null, marketSeed: null, error: null, busy: false, confirmationTaskRunId: null });
  const confirmationJobId = state.confirmationTaskRunId
    ?? (state.selection?.status === 'PENDING_HYPOTHESIS_CONFIRMATION'
      ? state.selection?.activeTaskRunId : null);
  const confirmationEvents = useJobEvents(confirmationJobId);

  const refresh = useCallback(async () => {
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
      setState((value) => ({ ...value, loading: false, run, concepts: normalizePortfolioConcepts(concepts), inputRequests: inputRequests ?? [], selection, hypotheses: hypotheses ?? [], report, marketSeed, error: null }));
    } catch (error) {
      setState((value) => ({ ...value, loading: false, error }));
    }
  }, [api, projectId]);

  useEffect(() => { const timer = setTimeout(refresh, 0); return () => clearTimeout(timer); }, [refresh, liveRevision]);
  useEffect(() => {
    if (!confirmationEvents.terminal || !confirmationJobId) return undefined;
    const timer = setTimeout(() => { void refresh(); }, 0);
    return () => clearTimeout(timer);
  }, [confirmationEvents.terminal, confirmationJobId, refresh]);
  const act = useCallback(async (operation) => {
    setState((value) => ({ ...value, busy: true, error: null }));
    try { await operation(); await refresh(); }
    catch (error) { setState((value) => ({ ...value, error })); }
    finally { setState((value) => ({ ...value, busy: false })); }
  }, [refresh]);

  return {
    ...state,
    confirmationTaskRunId: confirmationJobId,
    confirmationEvents,
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
        setState((value) => ({ ...value, confirmationTaskRunId: action?.taskRunId ?? null }));
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
    retryDelta: () => act(() => api.retryDelta(projectId, state.selection.selectionId, { idempotencyKey: key('delta-retry') })),
    finalizeReport: () => act(() => api.finalizeReport(projectId, state.selection.selectionId)),
    finalizeMarketSeed: () => act(() => api.finalizeMarketSeed(projectId, state.selection.selectionId, { idempotencyKey: key('market-seed') })),
  };
}
