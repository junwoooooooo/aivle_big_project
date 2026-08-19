import { useCallback, useEffect, useMemo, useState } from 'react';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { createConceptPortfolioApi } from '../concept-portfolio/api/conceptPortfolioApi.js';

const optional = (promise) => promise.catch((error) => (
  [404, 409, 422].includes(error?.status) ? null : Promise.reject(error)
));

/** MAIN presentation state backed exclusively by the FULL v3 refinement commands. */
export function useConceptRevision(client, marketApi, projectId, enabled) {
  const portfolio = useMemo(() => createConceptPortfolioApi(client), [client]);
  const [state, setState] = useState({ loading: true, selectionId: null, refinement: null, concept: null, error: null });
  const [finalizing, setFinalizing] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [deciding, setDeciding] = useState(false);

  const load = useCallback(async () => {
    const [presentation, selectionResponse] = await Promise.all([
      optional(marketApi.currentRefinementPresentation()), optional(portfolio.currentSelection(projectId)),
    ]);
    const selection = selectionResponse?.data ?? selectionResponse;
    const selectionId = selection?.selectionId ?? null;
    const seedResponse = selectionId ? await optional(portfolio.marketSeed(projectId, selectionId)) : null;
    const seed = seedResponse?.data ?? seedResponse;
    return {
      loading: false,
      selectionId,
      refinement: presentation,
      concept: presentation?.concept?.selectedConcept ? presentation.concept : seed?.snapshot ?? null,
      error: null,
    };
  }, [marketApi, portfolio, projectId]);

  const refresh = useCallback(async () => setState(await load()), [load]);
  useEffect(() => {
    if (!enabled) return undefined;
    let alive = true;
    load().then((next) => { if (alive) setState(next); })
      .catch((failure) => { if (alive) setState((value) => ({ ...value, loading: false, error: getUserErrorMessage(failure) })); });
    return () => { alive = false; };
  }, [enabled, load]);

  useEffect(() => {
    if (!enabled || state.refinement?.outcome !== 'RUNNING') return undefined;
    const timer = setInterval(() => { load().then(setState).catch(() => {}); }, 5000);
    return () => clearInterval(timer);
  }, [enabled, load, state.refinement?.outcome]);

  const finalize = useCallback(async () => {
    const command = state.refinement?.command;
    if (!command?.round) return;
    setFinalizing(true);
    try {
      await marketApi.finalizeRefinement({ expectedRound: command.round, expectedDecisionHash: command.decisionHash });
      await refresh();
    } catch (failure) { setState((value) => ({ ...value, error: getUserErrorMessage(failure) })); }
    finally { setFinalizing(false); }
  }, [marketApi, refresh, state.refinement]);

  const retry = useCallback(async () => {
    const command = state.refinement?.command;
    setRetrying(true);
    try {
      if (state.refinement?.outcome === 'FAILED') await marketApi.retryRefinement();
      else await marketApi.nextRefinement({ expectedRound: command.round,
        expectedProposalSetHash: command.proposalSetHash, expectedDecisionHash: command.decisionHash });
      await refresh();
    } catch (failure) { setState((value) => ({ ...value, error: getUserErrorMessage(failure) })); }
    finally { setRetrying(false); }
  }, [marketApi, refresh, state.refinement]);

  const decide = useCallback(async (round, fieldKeys) => {
    const view = state.refinement;
    const command = view?.command;
    if (!command || command.round !== round) return;
    setDeciding(true);
    try {
      if (fieldKeys.length === 0 && command.nextAvailable) {
        await marketApi.nextRefinement({ expectedRound: round,
          expectedProposalSetHash: command.proposalSetHash, expectedDecisionHash: command.decisionHash });
      } else {
        const selected = (view.changes ?? [])
          .filter((change) => change.round === round && fieldKeys.includes(change.field))
          .map((change) => change.proposalKey);
        const decision = await marketApi.decideRefinement({ expectedRound: round,
          proposalSetHash: command.proposalSetHash, selectedProposalKeys: selected,
          keepCurrent: selected.length === 0 });
        const decisionHash = decision?.decision?.decisionHash;
        if (selected.length > 0) {
          await marketApi.applyRefinement({ expectedRound: round, expectedDecisionHash: decisionHash });
        } else {
          await marketApi.finalizeRefinement({ expectedRound: round, expectedDecisionHash: decisionHash });
        }
      }
      await refresh();
    } catch (failure) { setState((value) => ({ ...value, error: getUserErrorMessage(failure) })); }
    finally { setDeciding(false); }
  }, [marketApi, refresh, state.refinement]);

  return { ...state, finalizing, finalize, retrying, retry, deciding, decide };
}
