/* eslint-disable react-refresh/only-export-components */
import { createContext } from 'react';

export const AuthMotionContext = createContext({ motionReady: true, phase: 'ready' });

export function AuthMotionProvider({ children, value }) {
  return <AuthMotionContext.Provider value={value}>{children}</AuthMotionContext.Provider>;
}
