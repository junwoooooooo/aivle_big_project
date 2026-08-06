import { useCallback, useEffect, useReducer, useRef, useState } from 'react';

import { useApiClient } from '../api/ApiClientProvider.jsx';
import { consumeAuthenticatedSse } from './authenticatedSseClient.js';
import { createJobEventsApi } from './jobEventsApi.js';
import {
  initialJobEventsState,
  isTerminalJobEvent,
  jobEventsReducer,
} from './jobEventsReducer.js';

const DEFAULT_RECONNECT_DELAY = 1000;
const DEFAULT_MAX_RECONNECT_DELAY = 8000;
const DEFAULT_POLL_INTERVAL = 2000;

export function useJobEvents(jobId, options = {}) {
  const client = useApiClient();
  const [state, dispatch] = useReducer(jobEventsReducer, initialJobEventsState);
  const [restartToken, setRestartToken] = useState(0);
  const cursor = useRef(0);
  const currentJobId = useRef(null);
  const activeController = useRef(null);
  const terminal = useRef(false);
  const manuallyStopped = useRef(false);
  const {
    reconnectDelayMs = DEFAULT_RECONNECT_DELAY,
    maxReconnectDelayMs = DEFAULT_MAX_RECONNECT_DELAY,
    pollIntervalMs = DEFAULT_POLL_INTERVAL,
    maxSseFailures = 3,
  } = options;

  const stop = useCallback(() => {
    manuallyStopped.current = true;
    activeController.current?.abort();
    dispatch({ type: 'STOPPED' });
  }, []);

  const reconnect = useCallback(() => {
    if (!jobId || terminal.current) return;
    manuallyStopped.current = false;
    activeController.current?.abort();
    setRestartToken((value) => value + 1);
  }, [jobId]);

  useEffect(() => {
    const jobChanged = currentJobId.current !== jobId;
    if (jobChanged) {
      currentJobId.current = jobId;
      cursor.current = 0;
      terminal.current = false;
      manuallyStopped.current = false;
      dispatch({ type: 'RESET' });
    }
    const controller = new AbortController();
    activeController.current = controller;
    if (!jobId || manuallyStopped.current) {
      return () => controller.abort();
    }
    const api = createJobEventsApi(client);

    const append = (events) => {
      const validEvents = (events ?? [])
        .filter((event) => Number.isSafeInteger(event?.sequence) && event.sequence > 0)
        .sort((left, right) => left.sequence - right.sequence);
      for (const event of validEvents) {
        cursor.current = Math.max(cursor.current, event.sequence);
      }
      dispatch({ type: 'APPEND', events: validEvents });
      const latest = validEvents.at(-1);
      if (latest?.sequence === cursor.current && isTerminalJobEvent(latest)) {
        terminal.current = true;
        controller.abort();
        return true;
      }
      return false;
    };

    const run = async () => {
      let sseFailures = 0;
      const failureLimit = Math.max(1, maxSseFailures);
      while (!controller.signal.aborted && sseFailures < failureLimit) {
        dispatch({ type: 'CONNECTING' });
        try {
          await consumeAuthenticatedSse({
            client,
            jobId,
            after: cursor.current,
            signal: controller.signal,
            onOpen: () => dispatch({ type: 'CONNECTED' }),
            onEvent: (event) => append([event]),
          });
          if (controller.signal.aborted || terminal.current) return;
          throw new Error('event stream closed');
        } catch (error) {
          if (controller.signal.aborted || terminal.current) return;
          if (isAuthenticationError(error)) {
            dispatch({ type: 'ERROR', error });
            return;
          }
          sseFailures += 1;
          if (sseFailures < failureLimit) {
            const delay = Math.min(
              reconnectDelayMs * (2 ** (sseFailures - 1)),
              maxReconnectDelayMs,
            );
            await wait(delay, controller.signal);
          }
        }
      }

      if (controller.signal.aborted || terminal.current) return;
      dispatch({ type: 'POLLING' });
      while (!controller.signal.aborted && !terminal.current) {
        try {
          const result = await api.poll(jobId, cursor.current, { signal: controller.signal });
          if (append(result.events)) return;
          dispatch({ type: 'POLLING' });
          if (result.hasMore) continue;
        } catch (error) {
          if (controller.signal.aborted || terminal.current) return;
          if (isAuthenticationError(error)) {
            dispatch({ type: 'ERROR', error });
            return;
          }
          dispatch({ type: 'POLLING', error });
        }
        await wait(pollIntervalMs, controller.signal);
      }
    };

    run().catch((error) => {
      if (!controller.signal.aborted) dispatch({ type: 'ERROR', error });
    });
    return () => {
      controller.abort();
      if (activeController.current === controller) activeController.current = null;
    };
  }, [
    client,
    jobId,
    maxReconnectDelayMs,
    maxSseFailures,
    pollIntervalMs,
    reconnectDelayMs,
    restartToken,
  ]);

  return { ...state, reconnect, stop };
}

function isAuthenticationError(error) {
  return error?.status === 401 || error?.status === 403;
}

function wait(milliseconds, signal) {
  return new Promise((resolve) => {
    if (signal.aborted) {
      resolve();
      return;
    }
    const onAbort = () => {
      clearTimeout(timer);
      resolve();
    };
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', onAbort);
      resolve();
    }, milliseconds);
    signal.addEventListener('abort', onAbort, { once: true });
  });
}
