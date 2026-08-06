import { useCallback, useEffect, useRef, useState } from 'react';

const ACTIVE = new Set(['QUEUED', 'RUNNING']);
export const newIdempotencyKey = () => globalThis.crypto?.randomUUID?.() ?? `marketing-${Date.now()}-${Math.random().toString(16).slice(2)}`;

export default function useMarketingGeneration({ api, projectId, onUpdate, pollMs = 1500 }) {
  const timer = useRef(null);
  const mounted = useRef(true);
  const [state, setState] = useState({ active: false, status: 'IDLE', error: null });

  useEffect(() => () => { mounted.current = false; if (timer.current) clearInterval(timer.current); }, []);

  const poll = useCallback(async (contentId) => {
    try {
      const detail = await api.detail(projectId, contentId);
      if (!mounted.current) return detail;
      const status = detail.content.status;
      setState({ active: ACTIVE.has(status), status, error: null }); onUpdate?.(detail);
      return detail;
    } catch (error) {
      if (mounted.current) setState({ active: false, status: 'FAILED', error });
      return null;
    }
  }, [api, onUpdate, projectId]);

  const begin = useCallback(async (action) => {
    if (timer.current) clearInterval(timer.current);
    setState({ active: true, status: 'QUEUED', error: null });
    try {
      const detail = await action(newIdempotencyKey());
      if (mounted.current) { onUpdate?.(detail); setState({ active: true, status: detail.content.status, error: null }); }
      timer.current = setInterval(async () => {
        const refreshed = await poll(detail.content.contentId);
        if (refreshed && !ACTIVE.has(refreshed.content.status)) { clearInterval(timer.current); timer.current = null; }
      }, pollMs);
      return detail;
    } catch (error) {
      if (mounted.current) setState({ active: false, status: 'FAILED', error });
      throw error;
    }
  }, [onUpdate, poll, pollMs]);

  return { ...state, create: (request) => begin((key) => api.create(projectId, request, key)),
    regenerate: (contentId) => begin((key) => api.regenerate(projectId, contentId, key)), poll };
}
