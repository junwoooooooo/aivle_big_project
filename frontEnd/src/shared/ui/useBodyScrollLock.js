import { useEffect } from 'react';

let lockCount = 0;
let previousOverflow = '';

export function useBodyScrollLock(active) {
  useEffect(() => {
    if (!active) return undefined;
    if (lockCount === 0) previousOverflow = document.body.style.overflow;
    lockCount += 1;
    document.body.style.overflow = 'hidden';

    return () => {
      lockCount = Math.max(0, lockCount - 1);
      if (lockCount === 0) {
        document.body.style.overflow = previousOverflow;
        previousOverflow = '';
      }
    };
  }, [active]);
}
