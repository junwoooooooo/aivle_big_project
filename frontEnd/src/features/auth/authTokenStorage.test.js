import { describe, expect, it } from 'vitest';

import { authTokenStorage, createAuthTokenProvider } from './authTokenStorage.js';

describe('browser token storage policy', () => {
  it('stores only the refresh token in sessionStorage', () => {
    const provider = createAuthTokenProvider();
    provider.setTokenPair({ accessToken: 'memory-access', refreshToken: 'tab-refresh' });
    expect(provider.getAccessToken()).toBe('memory-access');
    expect(authTokenStorage.getRefreshToken()).toBe('tab-refresh');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.getItem('memory-access')).toBeNull();
  });

  it('clears both memory access and session refresh values', () => {
    const provider = createAuthTokenProvider();
    provider.setTokenPair({ accessToken: 'access', refreshToken: 'refresh' });
    provider.clearSession();
    expect(provider.getAccessToken()).toBeNull();
    expect(authTokenStorage.getRefreshToken()).toBeNull();
  });
});
