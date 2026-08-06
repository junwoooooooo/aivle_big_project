import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import {
  createConservativeServicePolicy,
  createServicePolicyApi,
} from './api/servicePolicyApi.js';
import ServicePolicyContext from './servicePolicyContext.js';

function initialState() {
  return {
    loading: true,
    policy: createConservativeServicePolicy(),
    error: null,
  };
}

export default function ServicePolicyProvider({ children }) {
  const client = useApiClient();
  const api = useMemo(() => createServicePolicyApi(client), [client]);
  const [state, setState] = useState(initialState);
  const lastLoadedAt = useRef(0);

  const refresh = useCallback(async () => {
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      const policy = await api.getServicePolicy();
      lastLoadedAt.current = Date.now();
      setState({ loading: false, policy, error: null });
      return policy;
    } catch (error) {
      setState((current) => ({
        loading: false,
        policy: current.policy ?? createConservativeServicePolicy(),
        error,
      }));
      throw error;
    }
  }, [api]);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    api.getServicePolicy({ signal: controller.signal })
      .then((policy) => {
        if (active) {
          lastLoadedAt.current = Date.now();
          setState({ loading: false, policy, error: null });
        }
      })
      .catch((error) => {
        if (active && error?.code !== 'REQUEST_ABORTED') {
          setState((current) => ({
            loading: false,
            policy: current.policy ?? createConservativeServicePolicy(),
            error,
          }));
        }
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [api]);

  useEffect(() => {
    const refreshAfterReturning = () => {
      if (Date.now() - lastLoadedAt.current < 30_000) return;
      void refresh().catch(() => undefined);
    };
    window.addEventListener('focus', refreshAfterReturning);
    return () => window.removeEventListener('focus', refreshAfterReturning);
  }, [refresh]);

  const value = useMemo(() => ({
    loading: state.loading,
    policy: state.policy,
    error: state.error,
    refresh,
  }), [refresh, state.error, state.loading, state.policy]);

  return (
    <ServicePolicyContext.Provider value={value}>
      {children}
    </ServicePolicyContext.Provider>
  );
}
