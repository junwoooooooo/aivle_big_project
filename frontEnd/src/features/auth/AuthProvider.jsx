/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useEffect, useMemo, useState } from 'react';

import { AUTH_STATUS } from './authSession.js';
import { resolveUserPermissions } from '../admin/model/adminPermissions.js';

const AuthContext = createContext(null);

export function AuthProvider({
  children,
  session,
  initialSnapshot = { status: AUTH_STATUS.UNKNOWN, user: null },
}) {
  const [snapshot, setSnapshot] = useState(() => (
    initialSnapshot.status === AUTH_STATUS.UNKNOWN && !session
      ? { status: AUTH_STATUS.UNAUTHENTICATED, user: null }
      : initialSnapshot
  ));

  useEffect(() => {
    if (initialSnapshot.status !== AUTH_STATUS.UNKNOWN) return undefined;
    if (!session) return undefined;
    let active = true;
    session.bootstrap()
      .then((next) => {
        if (active) setSnapshot(next);
      })
      .catch(() => {
        if (active) setSnapshot({ status: AUTH_STATUS.UNAUTHENTICATED, user: null });
      });
    return () => { active = false; };
  }, [initialSnapshot.status, session]);

  useEffect(() => session?.subscribe?.(() => {
    setSnapshot({ status: AUTH_STATUS.UNAUTHENTICATED, user: null });
  }), [session]);

  const value = useMemo(() => ({
    ...snapshot,
    isAuthenticated: snapshot.status === AUTH_STATUS.AUTHENTICATED,
    isAdmin: snapshot.user?.role === 'ADMIN',
    hasRole(role) { return snapshot.user?.role === role; },
    can(permission) { return resolveUserPermissions(snapshot.user).includes(permission); },
    async login(credentials) {
      const user = await session.login(credentials);
      setSnapshot({ status: AUTH_STATUS.AUTHENTICATED, user });
      return user;
    },
    async signup(input) {
      const user = await session.signup(input);
      return user;
    },
    updateUser(user) {
      setSnapshot((current) => ({ ...current, user }));
    },
    async refresh() {
      setSnapshot((current) => ({ ...current, status: AUTH_STATUS.REFRESHING }));
      const refreshed = await session.refreshAccessToken();
      setSnapshot(refreshed
        ? { status: AUTH_STATUS.AUTHENTICATED, user: snapshot.user }
        : { status: AUTH_STATUS.UNAUTHENTICATED, user: null });
      return refreshed;
    },
    async logout() {
      try {
        await session.logout();
      } finally {
        setSnapshot({ status: AUTH_STATUS.UNAUTHENTICATED, user: null });
      }
    },
  }), [session, snapshot]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth는 AuthProvider 안에서 사용해야 합니다.');
  return context;
}
