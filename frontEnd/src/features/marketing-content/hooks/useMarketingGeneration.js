import { useCallback, useEffect, useRef, useState } from 'react';

import { useJobEvents } from '../../../shared/async-events/index.js';

const ACTIVE = new Set(['QUEUED', 'RUNNING']);
export const newIdempotencyKey = () => globalThis.crypto?.randomUUID?.() ?? `marketing-${Date.now()}-${Math.random().toString(16).slice(2)}`;

export default function useMarketingGeneration({ api, projectId, onUpdate }) {
  const [state, setState] = useState({ active: false, status: 'IDLE', activeJobId: null, contentId: null, error: null });
  const handledTerminal = useRef(null);
  const jobEvents = useJobEvents(state.activeJobId);

  const applyDetail = useCallback((detail) => {
    if (!detail?.content) return detail;
    const { content } = detail;
    const active = ACTIVE.has(content.status);
    setState({ active, status: content.status, activeJobId: active ? content.activeJobId : null,
      contentId: content.contentId, error: null });
    if (active && handledTerminal.current !== content.activeJobId) handledTerminal.current = null;
    onUpdate?.(detail);
    return detail;
  }, [onUpdate]);

  const restore = useCallback((detail) => applyDetail(detail), [applyDetail]);

  const refreshDetail = useCallback(async (contentId = state.contentId) => {
    if (!contentId) return null;
    try {
      return applyDetail(await api.detail(projectId, contentId));
    } catch (error) {
      setState((value) => ({ ...value, active: false, status: 'FAILED', activeJobId: null, error }));
      return null;
    }
  }, [api, applyDetail, projectId, state.contentId]);

  useEffect(() => {
    if (!jobEvents.terminal || !state.activeJobId || handledTerminal.current === state.activeJobId) return;
    handledTerminal.current = state.activeJobId;
    void refreshDetail(state.contentId);
  }, [jobEvents.terminal, refreshDetail, state.activeJobId, state.contentId]);

  const begin = useCallback(async (action) => {
    setState((value) => ({ ...value, active: true, status: 'QUEUED', error: null }));
    try {
      return applyDetail(await action(newIdempotencyKey()));
    } catch (error) {
      setState((value) => ({ ...value, active: false, status: 'FAILED', activeJobId: null, error }));
      throw error;
    }
  }, [applyDetail]);

  return {
    ...state,
    jobEvents,
    restore,
    refreshDetail,
    create: (request) => begin((key) => api.create(projectId, request, key)),
    regenerate: (contentId) => begin((key) => api.regenerate(projectId, contentId, key)),
  };
}
