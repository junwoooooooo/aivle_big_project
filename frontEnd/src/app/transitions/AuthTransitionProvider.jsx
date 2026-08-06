/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

const AuthTransitionContext = createContext(null);

export function AuthTransitionProvider({ children }) {
  const navigate = useNavigate();
  const [transition, setTransition] = useState(null);
  const runningRef = useRef(false);
  const start = useCallback(async ({ destination, message, onCovered }) => {
    if (runningRef.current) return false;
    runningRef.current = true;
    setTransition({ phase: 'covering', message });
    await new Promise((resolve) => window.setTimeout(resolve, 560));
    await onCovered?.();
    navigate(destination, { replace: true, state: null });
    setTransition({ phase: 'revealing', message });
    await new Promise((resolve) => window.setTimeout(resolve, 720));
    setTransition(null);
    runningRef.current = false;
    return true;
  }, [navigate]);
  return <AuthTransitionContext.Provider value={{ start, isTransitioning: Boolean(transition) }}>{children}<AuthTransitionHost transition={transition} /></AuthTransitionContext.Provider>;
}

function AuthTransitionHost({ transition }) {
  if (!transition) return null;
  return <div className={`auth-transition-host auth-transition-host--${transition.phase}`} role="status" aria-live="polite" aria-busy="true"><div><strong>Venture Verify</strong><span>{transition.message}</span><i /></div></div>;
}

export function useAuthTransition() {
  const context = useContext(AuthTransitionContext);
  if (!context) throw new Error('useAuthTransition must be used inside AuthTransitionProvider');
  return context;
}
