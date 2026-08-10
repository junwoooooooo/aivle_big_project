import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useApiClient } from '../api/ApiClientProvider.jsx';
import { consumeAuthenticatedSse } from './authenticatedSseClient.js';
import { createProjectEventsApi } from './projectEventsApi.js';

const RECONNECT_MS = 1500;
const POLL_MS = 5000;
const INVALIDATION_WINDOW_MS = 180;

export function useProjectEvents(projectId) {
  const client = useApiClient();
  const api = useMemo(() => createProjectEventsApi(client), [client]);
  const cursor = useRef(0);
  const pendingCursor = useRef(0);
  const invalidationTimer = useRef(null);
  const [state, setState] = useState({ revision: 0, transport: 'connecting', error: null });
  const invalidate = useCallback((event) => {
    const next = Number(event?.eventId ?? event?.sseId);
    if (!Number.isSafeInteger(next) || next <= cursor.current || next <= pendingCursor.current) return;
    pendingCursor.current = next;
    clearTimeout(invalidationTimer.current);
    invalidationTimer.current = setTimeout(() => {
      if (pendingCursor.current <= cursor.current) return;
      cursor.current = pendingCursor.current;
      setState((value) => ({ ...value, revision: value.revision + 1, error: null }));
    }, INVALIDATION_WINDOW_MS);
  }, []);

  useEffect(() => {
    if (!projectId) return undefined;
    const controller = new AbortController();
    let reconnectTimer;
    let pollTimer;
    const poll = async () => {
      try {
        const page = await api.poll(projectId, cursor.current, { signal: controller.signal });
        for (const event of page.events ?? []) invalidate(event);
        if (Number.isSafeInteger(page.nextEventId)) invalidate({ eventId: page.nextEventId });
        setState((value) => ({ ...value, transport: 'polling', error: null }));
      } catch (error) {
        if (!controller.signal.aborted) setState((value) => ({ ...value, transport: 'offline', error }));
      }
      if (!controller.signal.aborted) pollTimer = setTimeout(poll, POLL_MS);
    };
    const connect = async () => {
      try {
        await consumeAuthenticatedSse({
          client,
          path: `/api/v2/projects/${encodeURIComponent(projectId)}/events`,
          after: cursor.current,
          signal: controller.signal,
          onOpen: () => setState((value) => ({ ...value, transport: 'sse', error: null })),
          onEvent: invalidate,
        });
        if (!controller.signal.aborted) reconnectTimer = setTimeout(connect, RECONNECT_MS);
      } catch (error) {
        if (controller.signal.aborted) return;
        setState((value) => ({ ...value, transport: 'polling', error }));
        poll();
      }
    };
    cursor.current = 0;
    pendingCursor.current = 0;
    setState({ revision: 0, transport: 'connecting', error: null });
    connect();
    return () => {
      controller.abort();
      clearTimeout(reconnectTimer);
      clearTimeout(pollTimer);
      clearTimeout(invalidationTimer.current);
    };
  }, [api, client, invalidate, projectId]);

  return state;
}
