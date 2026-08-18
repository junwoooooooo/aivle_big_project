import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createMarketingStrategyApi } from '../api/marketingStrategyApi.js';
import { useJobEvents } from '../../../shared/async-events/index.js';

const ACTIVE_STATUSES = new Set([
  'QUEUED',
  'READY',
  'RUNNING',
]);

function idempotencyKey() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }

  return `marketing-strategy-${Date.now()}`;
}

export default function useMarketingStrategy(projectId) {
  const client = useApiClient();

  const api = useMemo(
    () => createMarketingStrategyApi(client),
    [client],
  );

  const [state, setState] = useState({
    loading: true,
    generating: false,
    downloading: false,
    view: null,
    error: null,
  });

  const refresh = useCallback(async () => {
    try {
      const view = await api.current(projectId);

      setState((value) => ({
        ...value,
        loading: false,
        generating: ACTIVE_STATUSES.has(
          view?.status,
        ),
        view,
        error: null,
      }));

      return view;
    } catch (error) {
      setState((value) => ({
        ...value,
        loading: false,
        generating: false,
        error,
      }));

      throw error;
    }
  }, [api, projectId]);

  useEffect(() => {
    const timer = setTimeout(() => {
      void refresh().catch(() => {});
    }, 0);

    return () => clearTimeout(timer);
  }, [refresh]);

  useEffect(() => {
    if (!ACTIVE_STATUSES.has(
        state.view?.status)) {
      return undefined;
    }

    const timer = setInterval(() => {
      void refresh().catch(() => {});
    }, 1500);

    return () => clearInterval(timer);
  }, [refresh, state.view?.status]);

  const generate = useCallback(async () => {
    setState((value) => ({
      ...value,
      generating: true,
      error: null,
    }));

    try {
      const action = await api.generate(
        projectId,
        idempotencyKey(),
      );
      setState((value) => ({
        ...value,
        generating: true,
        view: {
          ...(value.view ?? {}),
          reportId: action.reportId ?? value.view?.reportId ?? null,
          taskRunId: action.taskRunId,
          status: action.status ?? 'QUEUED',
          sourceManifestHash: action.sourceManifestHash ?? value.view?.sourceManifestHash,
        },
      }));
      return action;
    } catch (error) {
      setState((value) => ({
        ...value,
        generating: false,
        error,
      }));

      throw error;
    }
  }, [api, projectId]);

  const download = useCallback(async () => {
    const reportId = state.view?.reportId;

    if (!reportId) {
      return;
    }

    setState((value) => ({
      ...value,
      downloading: true,
      error: null,
    }));

    try {
      const response = await api.download(
        projectId,
        reportId,
        {
          timeoutMs: 60000,
        },
      );

      const url = URL.createObjectURL(
        response.blob,
      );

      const anchor =
        document.createElement('a');

      anchor.href = url;
      anchor.download =
        `marketing-strategy-${projectId}.pdf`;

      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);

      setState((value) => ({
        ...value,
        downloading: false,
      }));
    } catch (error) {
      setState((value) => ({
        ...value,
        downloading: false,
        error,
      }));

      throw error;
    }
  }, [
    api,
    projectId,
    state.view?.reportId,
  ]);

  const active = state.generating || ACTIVE_STATUSES.has(
    state.view?.status,
  );
  const jobEvents = useJobEvents(state.view?.taskRunId ?? null);

  const current = Boolean(
    state.view?.result
      && !state.view?.stale,
  );

  return {
    ...state,
    active,
    current,
    ready: Boolean(state.view?.ready),
    refresh,
    generate,
    download,
    jobEvents,
  };
}
