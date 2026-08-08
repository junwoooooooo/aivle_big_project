import { useCallback, useEffect, useMemo, useState } from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createBusinessModelApi } from '../api/businessModelApi.js';

export default function useBusinessModel(projectId) {
  const client = useApiClient();
  const api = useMemo(() => createBusinessModelApi(client), [client]);
  const [state, setState] = useState({ loading: true, marketSeed: null, runs: [], busy: false, error: null });

  const refresh = useCallback(async () => {
    try {
      const [seedResponse, runResponse] = await Promise.all([
        api.currentMarketSeed(projectId), api.runs(projectId),
      ]);
      setState((current) => ({
        ...current,
        loading: false,
        marketSeed: seedResponse.data,
        runs: runResponse.data?.runs ?? [],
        error: null,
      }));
    } catch (error) {
      setState((current) => ({ ...current, loading: false, error }));
    }
  }, [api, projectId]);

  useEffect(() => {
    const timer = setTimeout(refresh, 0);
    return () => clearTimeout(timer);
  }, [refresh]);

  const prepare = async () => {
    if (!state.marketSeed?.snapshotId) return;
    setState((current) => ({ ...current, busy: true, error: null }));
    try {
      await api.prepare(projectId, state.marketSeed.snapshotId);
      await refresh();
      setState((current) => ({ ...current, busy: false }));
    } catch (error) {
      setState((current) => ({ ...current, busy: false, error }));
    }
  };

  return { ...state, refresh, prepare };
}
