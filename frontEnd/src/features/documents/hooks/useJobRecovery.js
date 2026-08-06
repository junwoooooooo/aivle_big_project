import { useCallback, useEffect, useRef, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createStructuredPlanApi } from '../../structured-plan/api/structuredPlanApi.js';
import { toStructuredPlanViewModel } from '../../structured-plan/model/structuredPlanViewModel.js';
import { createDocumentApi } from '../api/documentApi.js';
import {
  ACTIVE_JOB_STATUSES,
  RESULT_JOB_STATUSES,
  toJobViewModel,
} from '../model/documentViewModel.js';

const ACTIVE_INTERVAL = 2000;
const HIDDEN_INTERVAL = 5000;
const MAX_BACKOFF = 30000;
const MAX_AUTOMATIC_POLLS = 8;

function isNotFound(error) {
  return error?.status === 404;
}

export function useJobRecovery(projectId) {
  const client = useApiClient();
  const mounted = useRef(false);
  const timer = useRef(null);
  const request = useRef(null);
  const failures = useRef(0);
  const pollCount = useRef(0);
  const [state, setState] = useState({
    status: 'loading',
    job: null,
    plan: null,
    error: null,
  });

  const clearPending = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    request.current?.abort();
    request.current = null;
  }, []);

  const loadPlan = useCallback(async (signal) => {
    try {
      const plan = await createStructuredPlanApi(client).getLatest(projectId, { signal });
      if (mounted.current) {
        setState((current) => ({
          ...current,
          status: 'result',
          plan: toStructuredPlanViewModel(plan),
          error: null,
        }));
      }
      return true;
    } catch (error) {
      if (isNotFound(error)) return false;
      throw error;
    }
  }, [client, projectId]);

  const poll = useCallback(async (jobId) => {
    request.current = new AbortController();
    try {
      const job = await createDocumentApi(client).getJob(jobId, {
        signal: request.current.signal,
      });
      if (!mounted.current) return;
      failures.current = 0;
      const view = toJobViewModel(job);
      setState((current) => ({
        ...current,
        status: ACTIVE_JOB_STATUSES.has(job.status) ? 'processing' : 'terminal',
        job: view,
        error: null,
      }));
      if (ACTIVE_JOB_STATUSES.has(job.status)) {
        pollCount.current += 1;
        if (pollCount.current >= MAX_AUTOMATIC_POLLS) {
          setState((current) => ({ ...current, status: 'waiting', job: view }));
          return;
        }
        const delay = document.hidden ? HIDDEN_INTERVAL : ACTIVE_INTERVAL;
        timer.current = setTimeout(() => poll(jobId), delay);
      } else if (RESULT_JOB_STATUSES.has(job.status)) {
        await loadPlan(request.current.signal);
      }
    } catch (error) {
      if (!mounted.current || error.code === 'REQUEST_ABORTED') return;
      if (isNotFound(error) || error.retryable === false) {
        setState((current) => ({
          ...current,
          status: 'error',
          error,
        }));
        return;
      }
      failures.current += 1;
      setState((current) => ({ ...current, error }));
      const delay = Math.min(
        MAX_BACKOFF,
        ACTIVE_INTERVAL * (2 ** Math.min(failures.current, 4)),
      );
      timer.current = setTimeout(() => poll(jobId), delay);
    }
  }, [client, loadPlan]);

  const recover = useCallback(async () => {
    clearPending();
    pollCount.current = 0;
    setState({ status: 'loading', job: null, plan: null, error: null });
    request.current = new AbortController();
    try {
      const job = await createDocumentApi(client).getLatestJob(projectId, {
        signal: request.current.signal,
      });
      if (!mounted.current) return;
      const view = toJobViewModel(job);
      if (ACTIVE_JOB_STATUSES.has(job.status)) {
        setState({ status: 'processing', job: view, plan: null, error: null });
        timer.current = setTimeout(() => poll(job.jobId), 0);
      } else if (RESULT_JOB_STATUSES.has(job.status)) {
        setState({ status: 'terminal', job: view, plan: null, error: null });
        const found = await loadPlan(request.current.signal);
        if (!found && mounted.current) {
          setState({ status: 'error', job: view, plan: null, error: new Error('결과를 찾을 수 없습니다.') });
        }
      } else {
        setState({ status: 'terminal', job: view, plan: null, error: null });
      }
    } catch (error) {
      if (!mounted.current || error.code === 'REQUEST_ABORTED') return;
      if (isNotFound(error)) {
        try {
          const found = await loadPlan(request.current.signal);
          if (!found && mounted.current) {
            setState({ status: 'empty', job: null, plan: null, error: null });
          }
        } catch (planError) {
          if (mounted.current && planError.code !== 'REQUEST_ABORTED') {
            setState({ status: 'error', job: null, plan: null, error: planError });
          }
        }
      } else {
        setState({ status: 'error', job: null, plan: null, error });
      }
    }
  }, [clearPending, client, loadPlan, poll, projectId]);

  useEffect(() => {
    mounted.current = true;
    recover();
    const onVisibility = () => {
      if (!document.hidden) recover();
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      mounted.current = false;
      document.removeEventListener('visibilitychange', onVisibility);
      clearPending();
    };
  }, [clearPending, recover]);

  return { ...state, retry: recover };
}
