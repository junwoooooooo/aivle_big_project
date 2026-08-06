import { describe, expect, it, vi } from 'vitest';

import { AUTH_STATUS, createAuthSession, createMemoryTokenProvider } from './authSession.js';

function createFixture() {
  const tokenProvider = createMemoryTokenProvider();
  const authApi = {
    signup: vi.fn(),
    login: vi.fn(),
    refresh: vi.fn(),
    logout: vi.fn(),
    getMe: vi.fn(),
  };
  return {
    authApi,
    tokenProvider,
    session: createAuthSession({ authApi, tokenProvider }),
  };
}

describe('auth session', () => {
  it('stores an access and refresh token pair without logging it', () => {
    const provider = createMemoryTokenProvider();
    provider.setTokenPair({ accessToken: 'secret', refreshToken: 'refresh' });
    expect(provider.getAccessToken()).toBe('secret');
    expect(provider.getRefreshToken()).toBe('refresh');
    provider.clearSession();
    expect(provider.getAccessToken()).toBeNull();
  });

  it('logs in and stores the returned token pair', async () => {
    const { authApi, tokenProvider, session } = createFixture();
    authApi.login.mockResolvedValue({
      user: { id: 1, email: 'user@example.com' },
      tokens: { accessToken: 'access', refreshToken: 'refresh' },
    });
    await expect(session.login({ email: 'user@example.com', password: 'password' }))
      .resolves.toMatchObject({ id: 1 });
    expect(tokenProvider.getAccessToken()).toBe('access');
  });

  it('signs up without storing tokens or authenticating the new account', async () => {
    const { authApi, tokenProvider, session } = createFixture();
    authApi.signup.mockResolvedValue({
      user: { id: 2, email: 'new@example.com' },
      signupCompleted: true,
    });
    await session.signup({ email: 'new@example.com', password: 'password', displayName: 'New' });
    expect(tokenProvider.getRefreshToken()).toBeNull();
  });

  it('bootstraps by rotating refresh then loading users me', async () => {
    const { authApi, tokenProvider, session } = createFixture();
    tokenProvider.setTokenPair({ accessToken: null, refreshToken: 'stored-refresh' });
    authApi.refresh.mockResolvedValue({
      accessToken: 'rotated-access',
      refreshToken: 'rotated-refresh',
    });
    authApi.getMe.mockResolvedValue({ id: 1, displayName: '사용자' });
    await expect(session.bootstrap()).resolves.toEqual({
      status: AUTH_STATUS.AUTHENTICATED,
      user: { id: 1, displayName: '사용자' },
    });
    expect(tokenProvider.getRefreshToken()).toBe('rotated-refresh');
  });

  it('clears the session when refresh fails', async () => {
    const { authApi, tokenProvider, session } = createFixture();
    tokenProvider.setTokenPair({ accessToken: 'old', refreshToken: 'old-refresh' });
    authApi.refresh.mockRejectedValue(new Error('expired'));
    await expect(session.refreshAccessToken()).resolves.toBe(false);
    expect(tokenProvider.getAccessToken()).toBeNull();
    expect(tokenProvider.getRefreshToken()).toBeNull();
  });

  it('uses one refresh rotation for concurrent bootstrap calls', async () => {
    const { authApi, tokenProvider, session } = createFixture();
    tokenProvider.setTokenPair({ accessToken: null, refreshToken: 'one-time-refresh' });
    authApi.refresh.mockResolvedValue({
      accessToken: 'rotated-access',
      refreshToken: 'rotated-refresh',
    });
    authApi.getMe.mockResolvedValue({ id: 1 });
    const [first, second] = await Promise.all([
      session.bootstrap(),
      session.bootstrap(),
    ]);
    expect(first.status).toBe(AUTH_STATUS.AUTHENTICATED);
    expect(second.status).toBe(AUTH_STATUS.AUTHENTICATED);
    expect(authApi.refresh).toHaveBeenCalledOnce();
  });

  it('calls logout then clears local session', async () => {
    const { authApi, tokenProvider, session } = createFixture();
    tokenProvider.setTokenPair({ accessToken: 'access', refreshToken: 'refresh' });
    await session.logout();
    expect(authApi.logout).toHaveBeenCalledWith('refresh');
    expect(tokenProvider.getAccessToken()).toBeNull();
  });
});
