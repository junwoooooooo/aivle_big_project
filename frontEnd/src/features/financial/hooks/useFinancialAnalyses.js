import { useCallback, useEffect, useMemo, useState } from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createFinancialApi } from '../api/financialApi.js';

export default function useFinancialAnalyses(projectId) {
  const client = useApiClient();
  const api = useMemo(() => createFinancialApi(client), [client]);
  const [state, setState] = useState({ items: [], source: null, loading: true, error: null });
  const refresh = useCallback(async () => {
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      const [items, source] = await Promise.all([api.list(projectId), api.source(projectId)]);
      setState({ items, source, loading: false, error: null });
    } catch (error) { setState((current) => ({ ...current, loading: false, error })); }
  }, [api, projectId]);
  useEffect(() => { void refresh(); }, [refresh]);
  return { ...state, refresh, api };
}
