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

  const refresh = useCallback(async () => {
    try {
      const [active, recent] = await Promise.all([api.active(projectId), api.recent(projectId)]);
      setState({ loading: false, active, recent, error: null });
      setSelectedJobId((current) => {
        if (current && [...active, ...recent].some((job) => job.jobId === current)) return current;
        return active[0]?.jobId ?? recent[0]?.jobId ?? null;
      });
    } catch (error) {
      setState((current) => ({ ...current, loading: false, error }));
    }
  }, [api, projectId]);

  useEffect(() => {
    setState({ loading: true, active: [], recent: [], error: null });
    setSelectedJobId(null);
    handledTerminal.current = null;
    const timer = setTimeout(refresh, 0);
    return () => clearTimeout(timer);
  }, [projectId, refresh]);

  const events = useJobEvents(selectedJobId);
  useEffect(() => {
    if (!events.terminal || !selectedJobId || handledTerminal.current === selectedJobId) return;
    handledTerminal.current = selectedJobId;
    const latest = events.events.at(-1);
    setNotice({ jobId: selectedJobId, status: latest?.status ?? 'COMPLETED' });
    refresh();
    onTerminal?.();
  }, [events.events, events.terminal, onTerminal, refresh, selectedJobId]);

  return { ...state, selectedJobId, selectJob: setSelectedJobId, events, notice, refresh };
}
