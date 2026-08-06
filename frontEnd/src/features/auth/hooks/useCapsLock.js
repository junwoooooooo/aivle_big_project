import { useCallback, useState } from 'react';

export default function useCapsLock() {
  const [isCapsLockOn, setCapsLockOn] = useState(false);
  const readCapsLockState = useCallback((event) => {
    const modifierState = event?.getModifierState?.('CapsLock');
    setCapsLockOn(Boolean(modifierState) || (!event?.isTrusted && event?.key === 'CapsLock'));
  }, []);

  const handleBlur = useCallback(() => setCapsLockOn(false), []);

  return {
    isCapsLockOn,
    updateCapsLock: readCapsLockState,
    handleKeyDown: readCapsLockState,
    handleKeyUp: readCapsLockState,
    handleFocus: readCapsLockState,
    handleBlur,
  };
}
