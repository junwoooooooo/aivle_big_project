import { describe, expect, it, vi } from 'vitest';

import { createAuthApi } from './authApi.js';

describe('auth api', () => {
  it('uses the canonical signup endpoint without auth refresh recursion', async () => {
    const client = { post: vi.fn(async () => ({ data: { user: {}, tokens: {} } })) };
    await createAuthApi(client).signup({
      email: 'new@example.com',
      displayName: 'New',
      password: 'password',
    });
    expect(client.post).toHaveBeenCalledWith(
      '/auth/signup',
      expect.any(Object),
      expect.objectContaining({ authenticate: false, refreshOnUnauthorized: false }),
    );
  });

  it('uses users me as the user information source', async () => {
    const client = { get: vi.fn(async () => ({ data: { id: 1, displayName: 'User' } })) };
    await expect(createAuthApi(client).getMe()).resolves.toMatchObject({ id: 1 });
    expect(client.get).toHaveBeenCalledWith('/users/me');
  });

  it('sends refresh without bearer authentication or automatic retry', async () => {
    const client = { post: vi.fn(async () => ({ data: { accessToken: 'new' } })) };
    await createAuthApi(client).refresh('refresh-value');
    expect(client.post).toHaveBeenCalledWith(
      '/auth/refresh',
      { refreshToken: 'refresh-value' },
      expect.objectContaining({ authenticate: false, refreshOnUnauthorized: false }),
    );
  });
});
