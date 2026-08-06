export const AUTH_STATUS = Object.freeze({
  UNKNOWN: 'unknown',
  AUTHENTICATED: 'authenticated',
  UNAUTHENTICATED: 'unauthenticated',
  REFRESHING: 'refreshing',
});

export function createMemoryTokenProvider() {
  let tokenPair = null;
  return {
    getAccessToken: () => tokenPair?.accessToken ?? null,
    getRefreshToken: () => tokenPair?.refreshToken ?? null,
    setTokenPair: (nextTokenPair) => { tokenPair = nextTokenPair; },
    clearSession: () => { tokenPair = null; },
  };
}

export function createAuthSession({ authApi, tokenProvider }) {
  const listeners = new Set();
  let refreshPromise = null;

  function clearSessionAndNotify() {
    tokenProvider.clearSession();
    listeners.forEach((listener) => listener());
  }

  async function performRefresh() {
    const refreshToken = tokenProvider.getRefreshToken();
    if (!refreshToken) return false;
    try {
      const tokenPair = await authApi.refresh(refreshToken);
      tokenProvider.setTokenPair(tokenPair);
      return true;
    } catch {
      clearSessionAndNotify();
      return false;
    }
  }

  function refreshAccessToken() {
    if (!refreshPromise) {
      refreshPromise = performRefresh().finally(() => {
        refreshPromise = null;
      });
    }
    return refreshPromise;
  }

  return {
    tokenProvider,
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    async bootstrap() {
      try {
        if (!tokenProvider.getAccessToken()) {
          const refreshed = await refreshAccessToken();
          if (!refreshed) {
            return { status: AUTH_STATUS.UNAUTHENTICATED, user: null };
          }
        }
        const user = await authApi.getMe();
        return { status: AUTH_STATUS.AUTHENTICATED, user };
      } catch {
        clearSessionAndNotify();
        return { status: AUTH_STATUS.UNAUTHENTICATED, user: null };
      }
    },
    async login(credentials) {
      const result = await authApi.login(credentials);
      tokenProvider.setTokenPair(result.tokens);
      return result.user;
    },
    async signup(input) {
      const result = await authApi.signup(input);
      return result.user;
    },
    refreshAccessToken,
    async logout() {
      const refreshToken = tokenProvider.getRefreshToken();
      try {
        if (refreshToken) await authApi.logout(refreshToken);
      } finally {
        clearSessionAndNotify();
      }
    },
  };
}
