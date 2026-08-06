import { useEffect, useState } from 'react';

export default function useTypingLoop(text, { paused = false, reducedMotion = false } = {}) {
  const [length, setLength] = useState(reducedMotion ? text.length : 0);
  useEffect(() => {
    if (reducedMotion) return undefined;
    if (paused) return undefined;
    let index = 0;
    const reset = window.setTimeout(() => setLength(0), 0);
    const timer = window.setInterval(() => {
      index += 1;
      setLength(index);
      if (index >= text.length) window.clearInterval(timer);
    }, 52);
    return () => { window.clearTimeout(reset); window.clearInterval(timer); };
  }, [paused, reducedMotion, text]);
  return reducedMotion ? text : text.slice(0, length);
}
