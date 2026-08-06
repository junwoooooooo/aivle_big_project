import { useEffect, useState } from 'react';

const storageKey = 'authLoginRetryAt';

function secondsRemaining() {
  const retryAt = Number.parseInt(window.sessionStorage.getItem(storageKey), 10);
  return Number.isFinite(retryAt) ? Math.max(0, Math.ceil((retryAt - Date.now()) / 1000)) : 0;
}

export default function useLoginRetryCountdown() {
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  useEffect(() => {
    const update = () => {
      const next = secondsRemaining();
      if (!next) window.sessionStorage.removeItem(storageKey);
      setRemainingSeconds(next);
    };
    const initial = window.setTimeout(update, 0);
    const interval = window.setInterval(update, 1000);
    return () => { window.clearTimeout(initial); window.clearInterval(interval); };
  }, []);
  function startRetryCountdown(seconds) {
    if (!Number.isFinite(seconds) || seconds <= 0) return;
    window.sessionStorage.setItem(storageKey, String(Date.now() + (seconds * 1000)));
    setRemainingSeconds(seconds);
  }
  function clearRetryCountdown() { window.sessionStorage.removeItem(storageKey); setRemainingSeconds(0); }
  return { clearRetryCountdown, isLimited: remainingSeconds > 0, remainingSeconds, startRetryCountdown };
}
