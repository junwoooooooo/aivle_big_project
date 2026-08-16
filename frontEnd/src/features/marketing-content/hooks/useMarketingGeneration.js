import { useCallback, useEffect, useRef, useState } from 'react';

import { useJobEvents } from '../../../shared/async-events/index.js';

const ACTIVE = new Set(['QUEUED', 'RUNNING']);
export const MARKETING_DETAIL_POLL_INTERVAL_MS = 1500;
export const newIdempotencyKey = () => globalThis.crypto?.randomUUID?.()
  ?? `marketing-${Date.now()}-${Math.random().toString(16).slice(2)}`;

export default function useMarketingGeneration({
  api,
  projectId,
  onUpdate,
  pollIntervalMs = MARKETING_DETAIL_POLL_INTERVAL_MS,
}) {
  const [state, setState] = useState({
    active: false,
    status: 'IDLE',
    activeJobId: null,
    contentId: null,
    error: null,
  });
  const handledTerminal = useRef(null);
  const requestEpoch = useRef(0);
  const mounted = useRef(true);
  const jobEvents = useJobEvents(state.activeJobId);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      requestEpoch.current += 1;
    };
  }, []);

  const applyDetail = useCallback((detail, options = {}) => {
    if (!detail?.content || !mounted.current) return detail;
    const { content } = detail;
    const active = ACTIVE.has(content.status);
    setState({
      active,
      status: content.status,
      activeJobId: active ? content.activeJobId : null,
      contentId: content.contentId,
      error: null,
    });
    if (active && handledTerminal.current !== content.activeJobId) {
      handledTerminal.current = null;
    }
    onUpdate?.(detail, options);
    return detail;
  }, [onUpdate]);

  const restore = useCallback((detail) => {
    requestEpoch.current += 1;
    return applyDetail(detail);
  }, [applyDetail]);

  const refreshDetail = useCallback(async (contentId) => {
    if (!contentId) return null;
    const epoch = requestEpoch.current;
    try {
      const detail = await api.detail(projectId, contentId);
      if (!mounted.current || epoch !== requestEpoch.current) return null;
      return applyDetail(detail);
    } catch (error) {
      if (mounted.current && epoch === requestEpoch.current) {
        setState((value) => ({
          ...value,
          active: false,
          status: 'FAILED',
          activeJobId: null,
          error,
        }));
      }
      return null;
    }
  }, [api, applyDetail, projectId]);

  useEffect(() => {
    if (!jobEvents.terminal || !state.activeJobId
      || handledTerminal.current === state.activeJobId) return;
    handledTerminal.current = state.activeJobId;
    void refreshDetail(state.contentId);
  }, [jobEvents.terminal, refreshDetail, state.activeJobId, state.contentId]);

  useEffect(() => {
    if (!state.active || !state.contentId) return undefined;
    let cancelled = false;
    let timer = null;
    const contentId = state.contentId;
    const epoch = requestEpoch.current;

    const schedule = () => {
      timer = setTimeout(async () => {
        if (cancelled || epoch !== requestEpoch.current) return;
        await refreshDetail(contentId);
        if (!cancelled && epoch === requestEpoch.current) schedule();
      }, pollIntervalMs);
    };

    schedule();
    return () => {
      cancelled = true;
      if (timer !== null) clearTimeout(timer);
    };
  }, [pollIntervalMs, refreshDetail, state.active, state.contentId]);

  const begin = useCallback(async (action) => {
    const epoch = requestEpoch.current + 1;
    requestEpoch.current = epoch;
    setState((value) => ({
      ...value,
      active: true,
      status: 'QUEUED',
      error: null,
    }));
    try {
      const detail = await action(newIdempotencyKey());
      if (!mounted.current || epoch !== requestEpoch.current) return detail;
      return applyDetail(detail, { forceSelect: true });
    } catch (error) {
      try {
        const recovered = await api.current(projectId);
        if (recovered?.content && mounted.current && epoch === requestEpoch.current) {
          return applyDetail(recovered, { forceSelect: true });
        }
      } catch {
        // The original mutation is never resent; fall through to a safe failed state.
      }
      if (mounted.current && epoch === requestEpoch.current) {
        setState((value) => ({
          ...value,
          active: false,
          status: 'FAILED',
          activeJobId: null,
          error,
        }));
      }
      throw error;
    }
  }, [api, applyDetail, projectId]);

  return {
    ...state,
    jobEvents,
    restore,
    refreshDetail,
    create: (request) => begin((key) => api.create(projectId, request, key)),
    regenerate: (contentId) => begin((key) => api.regenerate(projectId, contentId, key)),
    retry: (contentId) => begin((key) => api.retry(projectId, contentId, key)),
  };
}
