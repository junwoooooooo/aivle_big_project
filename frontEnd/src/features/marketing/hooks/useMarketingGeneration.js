import { useCallback, useEffect, useRef, useState } from 'react';

const ACTIVE = new Set(['QUEUED', 'RUNNING']);

function newIdempotencyKey() {
  return globalThis.crypto?.randomUUID?.()
    ?? `marketing-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export default function useMarketingGeneration({ api, projectId, contentId, onSucceeded }) {
  const mounted = useRef(true);
  const timer = useRef(null);
  const pollRef = useRef(null);
  const [state, setState] = useState({
    status: 'IDLE',
    job: null,
    error: null,
  });

  const clear = useCallback(() => {
    if (timer.current) window.clearTimeout(timer.current);
    timer.current = null;
  }, []);

  const poll = useCallback(async (jobId) => {
    try {
      const job = await api.job(jobId);
      if (!mounted.current) return;
      setState({ status: job.status, job, error: null });
      if (ACTIVE.has(job.status)) {
        timer.current = window.setTimeout(
          () => pollRef.current?.(jobId),
          document.hidden ? 5000 : 2000,
        );
      } else if (job.status === 'SUCCEEDED') {
        await onSucceeded?.();
      }
    } catch (error) {
      if (mounted.current && error?.code !== 'REQUEST_ABORTED') {
        setState((current) => ({ ...current, status: 'FAILED', error }));
      }
    }
  }, [api, onSucceeded]);

  useEffect(() => { pollRef.current = poll; }, [poll]);
  useEffect(() => () => {
    mounted.current = false;
    clear();
  }, [clear]);

  const generate = useCallback(async (file, sourceVersionId) => {
    if (!file || ACTIVE.has(state.status)) return false;
    clear();
    setState({ status: 'QUEUED', job: null, error: null });
    try {
      const accepted = await api.generate(projectId, contentId, file, {
        sourceVersionId,
        idempotencyKey: newIdempotencyKey(),
      });
      if (!mounted.current) return false;
      setState({ status: accepted.status, job: accepted, error: null });
      timer.current = window.setTimeout(
        () => pollRef.current?.(accepted.jobId),
        0,
      );
      return true;
    } catch (error) {
      if (mounted.current) {
        setState({ status: 'FAILED', job: null, error });
      }
      return false;
    }
  }, [api, clear, contentId, projectId, state.status]);

  return {
    ...state,
    generate,
    active: ACTIVE.has(state.status),
  };
}
