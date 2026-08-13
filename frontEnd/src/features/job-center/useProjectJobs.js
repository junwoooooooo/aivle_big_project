import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { useJobEvents } from '../../shared/async-events/index.js';
import { createJobCenterApi } from './jobCenterApi.js';

export function useProjectJobs(projectId, { onTerminal, refreshKey = 0 } = {}) {
  const client = useApiClient();
  const api = useMemo(() => createJobCenterApi(client), [client]);
  const [state, setState] = useState({ loading: true, active: [], recent: [], error: null });
  const [history, setHistory] = useState({ items: [], page: -1, hasMore: false, totalElements: 0, loading: false, error: null });
  const [selectedJobId, setSelectedJobId] = useState(null);
  const handledTerminal = useRef(null);
  const manualSelection = useRef(false);

  const refresh = useCallback(async () => {
    try {
      const [active, recent] = await Promise.all([api.active(projectId), api.recent(projectId)]);
      setState({ loading: false, active, recent, error: null });
      setSelectedJobId((current) => {
        const available = [...active, ...recent];
        if (manualSelection.current && current && available.some((job) => job.jobId === current)) return current;
        if (active[0]?.jobId && active[0].jobId !== current) return active[0].jobId;
        if (current && available.some((job) => job.jobId === current)) return current;
        return active[0]?.jobId ?? recent[0]?.jobId ?? null;
      });
    } catch (error) {
      setState((current) => ({ ...current, loading: false, error }));
    }
  }, [api, projectId]);

  const loadHistory = useCallback(async ({ reset = false } = {}) => {
    if (history.loading || (!reset && history.page >= 0 && !history.hasMore)) return;
    const nextPage = reset || history.page < 0 ? 0 : history.page + 1;
    setHistory((current) => ({ ...current, loading: true, error: null }));
    try {
      const result = await api.history(projectId, nextPage, 20);
      setHistory((current) => ({
        items: reset || nextPage === 0 ? result.items : [...current.items, ...result.items],
        page: result.page,
        hasMore: result.hasMore,
        totalElements: result.totalElements,
        loading: false,
        error: null,
      }));
    } catch (error) {
      setHistory((current) => ({ ...current, loading: false, error }));
    }
  }, [api, history.hasMore, history.loading, history.page, projectId]);

  useEffect(() => {
    setState({ loading: true, active: [], recent: [], error: null });
    setSelectedJobId(null);
    setHistory({ items: [], page: -1, hasMore: false, totalElements: 0, loading: false, error: null });
    handledTerminal.current = null;
    manualSelection.current = false;
    const timer = setTimeout(refresh, 0);
    return () => clearTimeout(timer);
  }, [projectId, refresh, refreshKey]);

  const selectedJob = [...state.active, ...state.recent, ...history.items]
    .find((job) => job.jobId === selectedJobId);
  const liveJobId = state.active.some((job) => job.jobId === selectedJobId)
    ? selectedJobId : null;
  const events = useJobEvents(liveJobId);
  useEffect(() => {
    if (!events.terminal || !selectedJobId || handledTerminal.current === selectedJobId) return;
    handledTerminal.current = selectedJobId;
    refresh();
    onTerminal?.();
  }, [events.events, events.terminal, onTerminal, refresh, selectedJobId, state.active, state.recent]);

  const selectJob = useCallback((jobId) => {
    manualSelection.current = true;
    setSelectedJobId(jobId);
  }, []);

  const notice = selectedJob?.presentationStatus === 'RESOLVED_INPUT'
    ? { status: 'RESOLVED_INPUT', jobId: selectedJob.jobId }
    : null;

  return { ...state, history, loadHistory, selectedJobId, selectJob, events, notice, refresh };
}
