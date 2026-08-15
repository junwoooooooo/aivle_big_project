import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { useJobEvents } from '../../../shared/async-events/index.js';
import { createMarketingVisualApi } from '../api/marketingVisualApi.js';
import { VISUAL_ACTIVE, visualRequest } from '../model/marketingVisualModel.js';
import { newIdempotencyKey } from './useMarketingGeneration.js';

export default function useMarketingVisual(projectId, contentId) {
  const client = useApiClient();
  const api = useMemo(() => createMarketingVisualApi(client), [client]);
  const [state, setState] = useState({ run: null, error: null, previewUrl: null, busy: false });
  const activeJobId = VISUAL_ACTIVE.has(state.run?.state) ? state.run.taskRunId : null;
  const events = useJobEvents(activeJobId);
  const terminalHandled = useRef(null);

  const apply = useCallback((run) => { setState((value) => ({ ...value, run, error: null, busy: VISUAL_ACTIVE.has(run?.state) })); return run; }, []);
  const refresh = useCallback(async (taskRunId = state.run?.taskRunId) => {
    if (!taskRunId) return null;
    try { return apply(await api.get(projectId, taskRunId)); }
    catch (error) { setState((value) => ({ ...value, error, busy: false })); return null; }
  }, [api, apply, projectId, state.run?.taskRunId]);

  useEffect(() => {
    if (!contentId) { setState({ run: null, error: null, previewUrl: null, busy: false }); return undefined; }
    const controller = new AbortController();
    api.current(projectId, contentId, { signal: controller.signal }).then(apply).catch((error) => {
      if (error?.status !== 404 && error?.code !== 'RESOURCE_NOT_FOUND') setState((value) => ({ ...value, error }));
    });
    return () => controller.abort();
  }, [api, apply, contentId, projectId]);

  useEffect(() => {
    if (!events.terminal || !activeJobId || terminalHandled.current === activeJobId) return;
    terminalHandled.current = activeJobId; void refresh(activeJobId);
  }, [activeJobId, events.terminal, refresh]);

  useEffect(() => {
    let revoked = false; let url = null;
    const path = state.run?.result?.artifact?.downloadPath;
    if (!path) { setState((value) => value.previewUrl ? { ...value, previewUrl: null } : value); return undefined; }
    api.download(path).then(({ blob }) => {
      if (revoked) return; url = URL.createObjectURL(blob);
      setState((value) => ({ ...value, previewUrl: url }));
    }).catch((error) => setState((value) => ({ ...value, error })));
    return () => { revoked = true; if (url) URL.revokeObjectURL(url); };
  }, [api, state.run?.result?.artifact?.downloadPath]);

  const create = async ({ form, file, revisionId }) => {
    setState((value) => ({ ...value, busy: true, error: null }));
    try {
      const artifact = await api.uploadSource(projectId, file);
      return apply(await api.create(projectId, visualRequest(form, contentId, revisionId, artifact.artifactId), newIdempotencyKey()));
    } catch (error) { setState((value) => ({ ...value, busy: false, error })); throw error; }
  };
  const retry = async () => apply(await api.retry(projectId, state.run.taskRunId, newIdempotencyKey()));
  const cancel = async () => apply(await api.cancel(projectId, state.run.taskRunId));
  const download = async () => {
    const artifact = state.run?.result?.artifact; if (!artifact) return;
    const { blob } = await api.download(artifact.downloadPath); const url = URL.createObjectURL(blob);
    const link = document.createElement('a'); link.href = url; link.download = artifact.filename; link.click(); URL.revokeObjectURL(url);
  };
  return { ...state, activeJobId, events, create, retry, cancel, refresh, download };
}
