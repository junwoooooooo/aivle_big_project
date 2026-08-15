import { useCallback, useEffect, useRef, useState } from 'react';

import { useApiClient } from '../api/ApiClientProvider.jsx';
import { consumeAuthenticatedSse } from './authenticatedSseClient.js';

const RECONNECT_MS = 1500;
const INVALIDATION_WINDOW_MS = 180;

export function useProjectEvents(projectId) {
  const client = useApiClient();
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
        setState((value) => ({ ...value, transport: 'reconnecting', error }));
        reconnectTimer = setTimeout(connect, RECONNECT_MS);
      }
    };
    cursor.current = 0;
    pendingCursor.current = 0;
    setState({ revision: 0, transport: 'connecting', error: null });
    connect();
    return () => {
      controller.abort();
      clearTimeout(reconnectTimer);
      clearTimeout(invalidationTimer.current);
    };
  }, [client, invalidate, projectId]);

  return state;
}
