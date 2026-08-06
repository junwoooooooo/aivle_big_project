const REFRESH_TOKEN_KEY = 'business-validation-refresh-token';

// Same-tab refresh recovery only; the backend does not yet issue an HttpOnly refresh cookie.

function getSessionStorage() {
  return typeof window === 'undefined' ? null : window.sessionStorage;
}

export const authTokenStorage = {
  getRefreshToken() {
    return getSessionStorage()?.getItem(REFRESH_TOKEN_KEY) ?? null;
  },
  setRefreshToken(refreshToken) {
    if (!refreshToken) {
      this.clearRefreshToken();
      return;
    }
    getSessionStorage()?.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },
  clearRefreshToken() {
    getSessionStorage()?.removeItem(REFRESH_TOKEN_KEY);
  },
};

export function createAuthTokenProvider({ storage = authTokenStorage } = {}) {
  let accessToken = null;

  return {
    getAccessToken: () => accessToken,
    getRefreshToken: () => storage.getRefreshToken(),
    setTokenPair(tokenPair) {
      accessToken = tokenPair.accessToken;
      storage.setRefreshToken(tokenPair.refreshToken);
    },
    clearSession() {
      accessToken = null;
      storage.clearRefreshToken();
    },
  };
}
