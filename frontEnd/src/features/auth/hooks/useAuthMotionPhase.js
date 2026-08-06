import { useEffect, useState } from 'react';

export const AUTH_SETTLE_DELAY_MS = 280;
export const AUTH_ENTRY_FALLBACK_MS = 1100;

export default function useAuthMotionPhase({ reducedMotion, transitioning }) {
  const [phase, setPhase] = useState(() => transitioning && !reducedMotion ? 'entering' : 'ready');

  useEffect(() => {
    if (!transitioning || reducedMotion) {
      const timer = window.setTimeout(() => setPhase('ready'), 0);
      return () => window.clearTimeout(timer);
    }

    const entering = window.setTimeout(() => setPhase('entering'), 0);
    const fallback = window.setTimeout(() => setPhase('settling'), AUTH_ENTRY_FALLBACK_MS);
    return () => { window.clearTimeout(entering); window.clearTimeout(fallback); };
  }, [reducedMotion, transitioning]);

  useEffect(() => {
    if (phase !== 'settling') return undefined;
    const timer = window.setTimeout(() => setPhase('ready'), AUTH_SETTLE_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [phase]);

  const completeEntry = () => setPhase((current) => current === 'entering' ? 'settling' : current);
  return { completeEntry, phase };
}
