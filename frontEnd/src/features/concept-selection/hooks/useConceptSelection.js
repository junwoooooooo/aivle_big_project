import { useCallback, useEffect, useMemo, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
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
  const [state, setState] = useState({ loading: true, run: null, concepts: [], error: null });
  const [draft, setDraft] = useState(() => readDraft(projectId));

  const refresh = useCallback(async () => {
    try {
      const runPayload = await api.currentRun(projectId);
      const run = runPayload.data;
      if (run?.status !== 'COMPLETED') {
        setState({ loading: false, run, concepts: [], error: null });
        return;
      }
      const conceptPayload = await api.concepts(projectId);
      setState({ loading: false, run, concepts: conceptPayload.data?.concepts ?? [], error: null });
    } catch (error) {
      if (error?.status === 404) {
        setState({ loading: false, run: null, concepts: [], error: null });
        return;
      }
      setState((value) => ({ ...value, loading: false, error }));
    }
  }, [api, projectId]);

  useEffect(() => { const timer = setTimeout(refresh, 0); return () => clearTimeout(timer); }, [refresh]);

  const saveDraft = useCallback((comparedConceptIds, preferredConceptId) => {
    const next = createLocalSelectionDraft(projectId, comparedConceptIds, preferredConceptId);
    sessionStorage.setItem(storageKey(projectId), JSON.stringify(next));
    setDraft(next);
    return next;
  }, [projectId]);

  return { ...state, draft, refresh, saveDraft };
}
