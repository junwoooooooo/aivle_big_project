import { useCallback, useEffect, useRef, useState } from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createPersonaApi } from '../api/personaApi.js';
import { useServicePolicy } from '../../service-policy/useServicePolicy.js';
import { isServicePolicyError } from '../../service-policy/servicePolicyRestrictions.js';

const ACTIVE = new Set(['QUEUED', 'RUNNING']);
const SUCCESS = new Set(['SUCCEEDED', 'PARTIAL']);
const FEASIBILITY_READY = new Set(['COMPLETED', 'NEEDS_VALIDATION']);
const INITIAL_RETRY_DELAY = 900;

export function usePersonas(projectId) {
  const client = useApiClient();
  const { refresh: refreshPolicy } = useServicePolicy();
  const mounted = useRef(false);
  const timer = useRef(null);
  const aborter = useRef(null);
  const recoverRef = useRef(null);
  const pollRef = useRef(null);
  const [state, setState] = useState({
    status: 'loading', catalog: [], recommendation: null, feasibility: null,
    job: null, error: null, refreshError: null, refreshing: false,
  });

  const clear = useCallback(() => {
    if (timer.current) window.clearTimeout(timer.current);
    timer.current = null;
    aborter.current?.abort();
  }, []);

  const loadResult = useCallback(async (signal, catalog) => {
    const recommendation = await createPersonaApi(client).latest(projectId, { signal });
    if (mounted.current) setState((current) => ({ ...current, status: 'result', recommendation, catalog: catalog ?? current.catalog, error: null, refreshError: null, refreshing: false }));
  }, [client, projectId]);

  const preserveOrFail = useCallback((error) => {
    if (!mounted.current || error.code === 'REQUEST_ABORTED') return;
    setState((current) => {
      const hasPreviousData = current.catalog.length > 0 || current.recommendation != null || current.feasibility != null;
      return hasPreviousData
        ? { ...current, error: null, refreshError: error, refreshing: false }
        : { ...current, status: 'error', error, refreshError: null, refreshing: false };
    });
  }, []);

  const poll = useCallback(async (jobId) => {
    aborter.current = new AbortController();
    try {
      const job = await createPersonaApi(client).job(jobId, { signal: aborter.current.signal });
      if (!mounted.current) return;
      if (ACTIVE.has(job.status)) {
        setState((current) => ({ ...current, status: 'processing', job, error: null, refreshError: null, refreshing: false }));
        timer.current = window.setTimeout(() => pollRef.current?.(jobId), document.hidden ? 5000 : 2000);
      } else if (SUCCESS.has(job.status)) {
        await loadResult(aborter.current.signal);
      } else {
        setState((current) => ({ ...current, status: 'failed', job, error: null, refreshError: null, refreshing: false }));
      }
    } catch (error) {
      preserveOrFail(error);
    }
  }, [client, loadResult, preserveOrFail]);
  useEffect(() => { pollRef.current = poll; }, [poll]);

  const recover = useCallback(async (attempt = 0) => {
    clear();
    setState((current) => {
      const hasPreviousData = current.catalog.length > 0 || current.recommendation != null || current.feasibility != null;
      return hasPreviousData ? { ...current, refreshing: true, refreshError: null, error: null } : { ...current, status: 'loading', error: null, refreshError: null, refreshing: true };
    });
    aborter.current = new AbortController();
    const api = createPersonaApi(client);
    let catalog = [];
    try {
      catalog = await api.catalog({ signal: aborter.current.signal });
      await loadResult(aborter.current.signal, catalog);
      return;
    } catch (error) {
      if (error.status !== 404) {
        if (attempt === 0 && error.code !== 'REQUEST_ABORTED') {
          timer.current = window.setTimeout(() => recoverRef.current?.(1), INITIAL_RETRY_DELAY);
          return;
        }
        preserveOrFail(error);
        return;
      }
    }
    try {
      const job = await api.latestJob(projectId, { signal: aborter.current.signal });
      if (ACTIVE.has(job.status)) {
        if (mounted.current) {
          setState((current) => ({ ...current, status: 'processing', catalog, job, error: null, refreshError: null, refreshing: false }));
          timer.current = window.setTimeout(() => poll(job.jobId), 0);
        }
        return;
      }
      if (SUCCESS.has(job.status)) {
        await loadResult(aborter.current.signal, catalog);
        return;
      }
    } catch (error) {
      if (error.status !== 404) {
        if (attempt === 0 && error.code !== 'REQUEST_ABORTED') {
          timer.current = window.setTimeout(() => recoverRef.current?.(1), INITIAL_RETRY_DELAY);
          return;
        }
        preserveOrFail(error);
        return;
      }
    }
    try {
      const feasibility = await api.latestFeasibility(projectId, { signal: aborter.current.signal });
      if (mounted.current) setState({ status: FEASIBILITY_READY.has(feasibility.status) ? 'ready' : 'not-ready', catalog, recommendation: null, feasibility, job: null, error: null, refreshError: null, refreshing: false });
    } catch (error) {
      if (!mounted.current) return;
      if (error.status === 404) setState({ status: 'not-ready', catalog, recommendation: null, feasibility: null, job: null, error: null, refreshError: null, refreshing: false });
      else if (attempt === 0 && error.code !== 'REQUEST_ABORTED') timer.current = window.setTimeout(() => recoverRef.current?.(1), INITIAL_RETRY_DELAY);
      else preserveOrFail(error);
    }
  }, [clear, client, loadResult, poll, preserveOrFail, projectId]);
  useEffect(() => { recoverRef.current = recover; }, [recover]);

  const start = useCallback(async () => {
    clear();
    setState((current) => ({ ...current, status: 'starting', error: null, refreshError: null, refreshing: false }));
    try {
      const accepted = await createPersonaApi(client).start(projectId);
      if (!mounted.current) return;
      setState((current) => ({ ...current, status: 'processing', job: { jobId: accepted.jobId, status: accepted.status, progress: 0 }, refreshError: null }));
      timer.current = window.setTimeout(() => poll(accepted.jobId), 0);
    } catch (error) {
      if (isServicePolicyError(error)) {
        void refreshPolicy().catch(() => undefined);
      }
      preserveOrFail(error);
    }
  }, [clear, client, poll, preserveOrFail, projectId, refreshPolicy]);

  useEffect(() => {
    mounted.current = true;
    const kickoff = window.setTimeout(() => recover(), 0);
    const visible = () => { if (!document.hidden) recoverRef.current?.(); };
    document.addEventListener('visibilitychange', visible);
    return () => { mounted.current = false; window.clearTimeout(kickoff); document.removeEventListener('visibilitychange', visible); clear(); };
  }, [clear, recover]);

  return { ...state, start, retry: () => recover(1) };
}
