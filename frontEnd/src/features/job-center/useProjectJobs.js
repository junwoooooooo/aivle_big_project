import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { useJobEvents } from '../../shared/async-events/index.js';
import { createJobCenterApi } from './jobCenterApi.js';

export function useProjectJobs(projectId, { onTerminal } = {}) {
  const client = useApiClient();
  const api = useMemo(() => createJobCenterApi(client), [client]);
  const [state, setState] = useState({ loading: true, active: [], recent: [], error: null });
  const [selectedJobId, setSelectedJobId] = useState(null);
  const [notice, setNotice] = useState(null);
  const handledTerminal = useRef(null);
  const manualSelection = useRef(false);

  const refresh = useCallback(async () => {
    try {
      const [active, recent] = await Promise.all([api.active(projectId), api.recent(projectId)]);
      setState({ loading: false, active, recent, error: null });
      setNotice((current) => {
        if (!current) return null;
        const job = [...active, ...recent].find((value) => value.jobId === current.jobId);
        if (!job) return null;
        const newerActive = active.some((value) => value.jobId !== job.jobId
          && value.subjectType === job.subjectType && value.subjectId === job.subjectId
          && value.latestForSubject);
        if (newerActive) return null;
        if (job.rawStatus === 'NEEDS_INPUT' && job.actionable === false) {
          return { ...current, jobId: job.jobId, status: 'RESOLVED_INPUT', taskType: job.taskType };
        }
        return current;
      });
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

  useEffect(() => {
    setState({ loading: true, active: [], recent: [], error: null });
    setSelectedJobId(null);
    setNotice(null);
    handledTerminal.current = null;
    manualSelection.current = false;
    const timer = setTimeout(refresh, 0);
    return () => clearTimeout(timer);
  }, [projectId, refresh]);

  const events = useJobEvents(selectedJobId);
  useEffect(() => {
    if (!events.terminal || !selectedJobId || handledTerminal.current === selectedJobId) return;
    handledTerminal.current = selectedJobId;
    const latest = events.events.at(-1);
    const selected = [...state.active, ...state.recent].find((job) => job.jobId === selectedJobId);
    setNotice({ jobId: selectedJobId, status: latest?.status ?? 'COMPLETED', taskType: selected?.taskType });
    refresh();
    onTerminal?.();
  }, [events.events, events.terminal, onTerminal, refresh, selectedJobId, state.active, state.recent]);

  const selectJob = useCallback((jobId) => {
    manualSelection.current = true;
    setSelectedJobId(jobId);
  }, []);

  return { ...state, selectedJobId, selectJob, events, notice, refresh };
}
