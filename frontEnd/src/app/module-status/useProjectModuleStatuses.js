import { useCallback, useEffect, useMemo, useState } from 'react';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { createProjectModuleApi } from './projectModuleApi.js';
import { normalizeProjectModuleStatuses } from './projectModuleModel.js';

export function useProjectModuleStatuses(projectId) {
  const client = useApiClient();
  const api = useMemo(() => createProjectModuleApi(client), [client]);
  const [refreshKey, setRefreshKey] = useState(0);
  const [state, setState] = useState({ projectId, status: 'loading', modules: {}, error: null });
  const retry = useCallback(() => {
    setState({ projectId, status: 'loading', modules: {}, error: null });
    setRefreshKey((value) => value + 1);
  }, [projectId]);

  useEffect(() => {
    const controller = new AbortController();
    api.findAll(projectId, { signal: controller.signal })
      .then((items) => {
        if (!controller.signal.aborted) {
          setState({ projectId, status: 'success', modules: normalizeProjectModuleStatuses(items), error: null });
        }
      })
      .catch((error) => {
        if (!controller.signal.aborted) setState({ projectId, status: 'error', modules: {}, error });
      });
    return () => controller.abort();
  }, [api, projectId, refreshKey]);

  if (state.projectId !== projectId) {
    return { status: 'loading', modules: {}, error: null, retry };
  }
  return { ...state, retry };
}
