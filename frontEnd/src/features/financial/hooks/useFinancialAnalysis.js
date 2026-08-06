import { useCallback, useEffect, useMemo, useState } from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createFinancialApi } from '../api/financialApi.js';

export default function useFinancialAnalysis(projectId, analysisId) {
  const client = useApiClient(); const api = useMemo(() => createFinancialApi(client), [client]);
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const refresh = useCallback(async () => {
    if (!analysisId) return;
    setState((current) => ({ ...current, loading: true, error: null }));
    try { setState({ data: await api.detail(projectId, analysisId), loading: false, error: null }); }
    catch (error) { setState((current) => ({ ...current, loading: false, error })); }
  }, [api, projectId, analysisId]);
  useEffect(() => { void refresh(); }, [refresh]);
  return { ...state, refresh, api };
}
