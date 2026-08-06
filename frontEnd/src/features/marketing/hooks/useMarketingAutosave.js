import { useCallback, useEffect, useRef, useState } from 'react';

export default function useMarketingAutosave({ value, dirty, onSave, delay = 1000 }) {
  const valueRef = useRef(value);
  const saveRef = useRef(onSave);
  const inFlight = useRef(false);
  const followUpTimer = useRef(null);
  const [status, setStatus] = useState('saved');

  useEffect(() => { valueRef.current = value; }, [value]);
  useEffect(() => { saveRef.current = onSave; }, [onSave]);

  const save = useCallback(async () => {
    if (inFlight.current || !dirty) return null;
    inFlight.current = true;
    const submitted = valueRef.current;
    let changedDuringSave = false;
    setStatus('saving');
    try {
      const result = await saveRef.current(submitted);
      changedDuringSave = valueRef.current !== submitted;
      setStatus('saved');
      return result;
    } catch {
      setStatus('error');
      return null;
    } finally {
      inFlight.current = false;
      if (changedDuringSave) {
        setStatus('pending');
        window.clearTimeout(followUpTimer.current);
        followUpTimer.current = window.setTimeout(() => void save(), delay);
      }
    }
  }, [delay, dirty]);

  useEffect(() => {
    if (!dirty) return undefined;
    const timer = window.setTimeout(() => void save(), delay);
    return () => window.clearTimeout(timer);
  }, [delay, dirty, save, value]);

  useEffect(() => () => window.clearTimeout(followUpTimer.current), []);

  return { status, save };
}
