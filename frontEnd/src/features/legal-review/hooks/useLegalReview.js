import { useCallback, useEffect, useRef, useState } from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createLegalReviewApi } from '../api/legalReviewApi.js';
import { useServicePolicy } from '../../service-policy/useServicePolicy.js';
import { isServicePolicyError } from '../../service-policy/servicePolicyRestrictions.js';

const ACTIVE = new Set(['QUEUED', 'RUNNING']);
const SUCCESS = new Set(['SUCCEEDED', 'PARTIAL']);
const POLL_MS = 2000;

export function useLegalReview(projectId) {
  const client = useApiClient();
  const { refresh: refreshPolicy } = useServicePolicy();
  const mounted = useRef(false);
  const timer = useRef(null);
  const aborter = useRef(null);
  const [state, setState] = useState({
    status: 'loading', review: null, job: null, plan: null, error: null,
  });

  const clear = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    aborter.current?.abort();
    aborter.current = null;
  }, []);

  const loadResult = useCallback(async (signal) => {
    const review = await createLegalReviewApi(client).latest(projectId, { signal });
    if (mounted.current) {
      setState((current) => ({
        ...current, status: 'result', review, error: null,
      }));
    }
  }, [client, projectId]);

  const poll = useCallback(async (jobId) => {
    aborter.current = new AbortController();
    try {
      const job = await createLegalReviewApi(client).job(jobId, {
        signal: aborter.current.signal,
      });
      if (!mounted.current) return;
      setState((current) => ({
        ...current, status: ACTIVE.has(job.status) ? 'processing' : 'terminal',
        job, error: null,
      }));
      if (ACTIVE.has(job.status)) {
        timer.current = setTimeout(() => poll(jobId), document.hidden ? 5000 : POLL_MS);
      } else if (SUCCESS.has(job.status)) {
        await loadResult(aborter.current.signal);
      }
    } catch (error) {
      if (!mounted.current || error.code === 'REQUEST_ABORTED') return;
      setState((current) => ({ ...current, status: 'error', error }));
    }
  }, [client, loadResult]);

  const recover = useCallback(async () => {
    clear();
    setState({ status: 'loading', review: null, job: null, plan: null, error: null });
    aborter.current = new AbortController();
    const api = createLegalReviewApi(client);
    try {
      await loadResult(aborter.current.signal);
      return;
    } catch (error) {
      if (error.status !== 404) {
        if (mounted.current) setState((current) => ({ ...current, status: 'error', error }));
        return;
      }
    }
    try {
      const job = await api.latestJob(projectId, { signal: aborter.current.signal });
      if (!mounted.current) return;
      if (ACTIVE.has(job.status)) {
        setState({ status: 'processing', review: null, job, plan: null, error: null });
        timer.current = setTimeout(() => poll(job.jobId), 0);
      } else if (SUCCESS.has(job.status)) {
        await loadResult(aborter.current.signal);
      } else {
        setState({ status: 'failed', review: null, job, plan: null, error: null });
      }
      return;
    } catch (error) {
      if (error.status !== 404) {
        if (mounted.current) setState({ status: 'error', review: null, job: null, plan: null, error });
        return;
      }
    }
    try {
      const plan = await api.latestPlan(projectId, { signal: aborter.current.signal });
      if (mounted.current) {
        setState({
          status: plan.status === 'CONFIRMED' && plan.completionRate === 100
            ? 'ready' : 'plan-not-confirmed',
          review: null, job: null, plan, error: null,
        });
      }
    } catch (error) {
      if (mounted.current) {
        setState({
          status: error.status === 404 ? 'plan-not-confirmed' : 'error',
          review: null, job: null, plan: null,
          error: error.status === 404 ? null : error,
        });
      }
    }
  }, [clear, client, loadResult, poll, projectId]);

  const start = useCallback(async () => {
    clear();
    setState((current) => ({ ...current, status: 'starting', error: null }));
    try {
      const accepted = await createLegalReviewApi(client).start(projectId);
      if (!mounted.current) return;
      setState((current) => ({
        ...current, status: 'processing',
        job: { jobId: accepted.jobId, status: accepted.status, progress: 0 },
      }));
      timer.current = setTimeout(() => poll(accepted.jobId), 0);
    } catch (error) {
      if (isServicePolicyError(error)) {
        void refreshPolicy().catch(() => undefined);
      }
      if (mounted.current) setState((current) => ({ ...current, status: 'error', error }));
    }
  }, [clear, client, poll, projectId, refreshPolicy]);

  useEffect(() => {
    mounted.current = true;
    recover();
    const onVisible = () => { if (!document.hidden) recover(); };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      mounted.current = false;
      document.removeEventListener('visibilitychange', onVisible);
      clear();
    };
  }, [clear, recover]);

  return { ...state, start, retry: recover };
}
