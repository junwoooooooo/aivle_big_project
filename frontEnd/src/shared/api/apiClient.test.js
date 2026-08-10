import { describe, expect, it, vi } from 'vitest';

import { createApiClient } from './apiClient.js';
import { getUserErrorMessage } from './apiError.js';

describe('api client', () => {
  it('adds a bearer token from the provider', async () => {
    const fetchImpl = vi.fn(async () => new Response(null, { status: 204 }));
    const client = createApiClient({
      baseUrl: '/api/v1',
      fetchImpl,
      tokenProvider: { getAccessToken: () => 'access-token' },
    });
    await client.get('/projects');
    const [, options] = fetchImpl.mock.calls[0];
    expect(options.headers.get('Authorization')).toBe('Bearer access-token');
  });

  it('opens an authenticated SSE stream without putting the token in the URL', async () => {
    const fetchImpl = vi.fn(async () => new Response(new ReadableStream(), {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
    }));
    const client = createApiClient({
      baseUrl: '/api/v1',
      fetchImpl,
      tokenProvider: { getAccessToken: () => 'stream-access-token' },
    });

    await client.stream('/api/v2/jobs/job-1/events', {
      headers: { 'Last-Event-ID': '7' },
    });

    const [url, options] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/v2/jobs/job-1/events');
    expect(url).not.toContain('stream-access-token');
    expect(options.headers.get('Authorization')).toBe('Bearer stream-access-token');
    expect(options.headers.get('Last-Event-ID')).toBe('7');
  });

  it('returns the successful API response envelope', async () => {
    const payload = { success: true, data: { id: 1 }, meta: { requestId: 'req-1' } };
    const client = createApiClient({
      fetchImpl: vi.fn(async () => new Response(JSON.stringify(payload), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })),
    });
    await expect(client.get('/projects/1')).resolves.toEqual(payload);
  });

  it('normalizes the backend error envelope and field errors', async () => {
    const fetchImpl = vi.fn(async () => new Response(JSON.stringify({
      success: false,
      error: {
        code: 'VALIDATION_FAILED',
        message: '입력값을 확인해 주세요.',
        fieldErrors: [{ field: 'title', message: '필수입니다.' }],
        retryable: false,
      },
      meta: { requestId: 'req-test' },
    }), {
      status: 422,
      headers: { 'Content-Type': 'application/json' },
    }));
    const client = createApiClient({ baseUrl: 'https://api.example/api/v1', fetchImpl });
    await expect(client.post('/projects', { title: '' })).rejects.toMatchObject({
      status: 422,
      code: 'VALIDATION_FAILED',
      requestId: 'req-test',
      fieldErrors: [{ field: 'title', message: '필수입니다.' }],
    });
  });

  it('calls the unauthorized hook', async () => {
    const onUnauthorized = vi.fn();
    const client = createApiClient({
      fetchImpl: vi.fn(async () => new Response(null, { status: 401 })),
      onUnauthorized,
    });
    await expect(client.get('/projects')).rejects.toMatchObject({ status: 401 });
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('supports multipart without setting Content-Type', async () => {
    const fetchImpl = vi.fn(async () => new Response(null, { status: 204 }));
    const client = createApiClient({ baseUrl: '/api/v1', fetchImpl });
    const formData = new FormData();
    formData.append('file', new Blob(['test']));
    await client.upload('/projects/1/documents', formData);
    const [, options] = fetchImpl.mock.calls[0];
    expect(options.headers.has('Content-Type')).toBe(false);
  });

  it('forwards an external abort signal', async () => {
    const fetchImpl = vi.fn((url, options) => new Promise((resolve, reject) => {
      options.signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
    }));
    const client = createApiClient({ fetchImpl });
    const controller = new AbortController();
    const request = client.get('/projects', { signal: controller.signal });
    controller.abort();
    await expect(request).rejects.toMatchObject({ code: 'REQUEST_ABORTED' });
  });

  it('normalizes a network error', async () => {
    const client = createApiClient({
      fetchImpl: vi.fn(async () => { throw new TypeError('offline'); }),
    });
    await expect(client.get('/projects')).rejects.toMatchObject({
      code: 'NETWORK_ERROR',
      retryable: true,
    });
  });

  it('refreshes once and retries a 401 request', async () => {
    let token = 'old';
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    const refreshSession = vi.fn(async () => {
      token = 'new';
      return true;
    });
    const client = createApiClient({
      fetchImpl,
      tokenProvider: { getAccessToken: () => token },
      refreshSession,
    });
    await client.get('/projects');
    expect(refreshSession).toHaveBeenCalledOnce();
    expect(fetchImpl.mock.calls[1][1].headers.get('Authorization')).toBe('Bearer new');
  });

  it('deduplicates concurrent refresh requests', async () => {
    const refreshSession = vi.fn(async () => true);
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValue(new Response(null, { status: 204 }));
    const client = createApiClient({ fetchImpl, refreshSession });
    await Promise.all([client.get('/one'), client.get('/two')]);
    expect(refreshSession).toHaveBeenCalledOnce();
  });

  it('handles 204 responses as null', async () => {
    const client = createApiClient({
      fetchImpl: vi.fn(async () => new Response(null, { status: 204 })),
    });
    await expect(client.delete('/projects/1')).resolves.toBeNull();
  });

  it('maps technical error codes to user messages', () => {
    expect(getUserErrorMessage({ code: 'CONFLICT' })).toMatch(/최신 내용/);
    expect(getUserErrorMessage({ code: 'UNKNOWN_BACKEND_CODE' })).not.toMatch(/UNKNOWN/);
  });
});
