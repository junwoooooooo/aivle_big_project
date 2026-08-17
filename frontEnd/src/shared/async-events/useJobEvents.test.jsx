import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useApiClient } from '../api/ApiClientProvider.jsx';
import { useJobEvents } from './useJobEvents.js';

vi.mock('../api/ApiClientProvider.jsx', () => ({ useApiClient: vi.fn() }));

describe('useJobEvents SSE-only transport', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('reconnects with the last cursor and never falls back to REST polling', async () => {
    const encoder = new TextEncoder();
    const client = {
      stream: vi.fn()
        .mockResolvedValueOnce(streamResponse(encoder, [
          'id: 1\ndata: {"jobId":"job-1","sequence":1,"status":"RUNNING"}\n\n',
        ]))
        .mockImplementationOnce(pendingUntilAbort),
      get: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result, unmount } = renderHook(() => useJobEvents('job-1', { reconnectDelayMs: 100 }));

    await flush();
    await act(async () => { vi.advanceTimersByTime(100); await flush(); });

    expect(client.stream).toHaveBeenCalledTimes(2);
    expect(client.stream.mock.calls[1][1].headers['Last-Event-ID']).toBe('1');
    expect(client.get).not.toHaveBeenCalled();
    expect(result.current.events).toHaveLength(1);
    expect(result.current.transport).toBe('SSE');
    unmount();
  });

  it('uses capped exponential SSE reconnect delays without REST requests', async () => {
    const client = { stream: vi.fn(async () => { throw new Error('unavailable'); }), get: vi.fn() };
    useApiClient.mockReturnValue(client);
    const { result, unmount } = renderHook(() => useJobEvents('job-backoff', {
      reconnectDelayMs: 100, maxReconnectDelayMs: 200,
    }));

    await flush();
    await act(async () => { vi.advanceTimersByTime(100); await flush(); });
    await act(async () => { vi.advanceTimersByTime(200); await flush(); });

    expect(client.stream).toHaveBeenCalledTimes(3);
    expect(client.get).not.toHaveBeenCalled();
    expect(result.current.connectionState).toBe('connecting');
    expect(result.current.transport).toBe('SSE');
    unmount();
  });

  it.each([401, 403])('stops after HTTP %s', async (status) => {
    const error = Object.assign(new Error('authentication failed'), { status });
    const client = { stream: vi.fn(async () => { throw error; }), get: vi.fn() };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useJobEvents(`job-auth-${status}`));
    await flush();
    expect(client.stream).toHaveBeenCalledOnce();
    expect(client.get).not.toHaveBeenCalled();
    expect(result.current.connectionState).toBe('error');
  });

  it.each([
    Object.assign(new Error('missing'), { status: 404 }),
    Object.assign(new Error('missing'), { code: 'JOB_NOT_FOUND' }),
  ])('quietly retries a cursor-zero registration race before treating the job as missing', async (error) => {
    const client = { stream: vi.fn(async () => { throw error; }), get: vi.fn() };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useJobEvents('missing-job', { reconnectDelayMs: 100 }));

    await flush();
    await act(async () => { vi.advanceTimersByTime(100); await flush(); });
    await act(async () => { vi.advanceTimersByTime(100); await flush(); });

    expect(client.stream).toHaveBeenCalledTimes(3);
    expect(client.get).not.toHaveBeenCalled();
    expect(result.current.connectionState).toBe('error');
  });

  it('recovers when the first event request races task registration', async () => {
    const missing = Object.assign(new Error('missing'), { status: 404 });
    const client = {
      stream: vi.fn().mockRejectedValueOnce(missing).mockImplementationOnce(pendingUntilAbort),
      get: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result, unmount } = renderHook(() => useJobEvents('new-job', { reconnectDelayMs: 100 }));
    await flush();
    expect(result.current.connectionState).toBe('connecting');
    await act(async () => { vi.advanceTimersByTime(100); await flush(); });
    expect(client.stream).toHaveBeenCalledTimes(2);
    expect(result.current.connectionState).not.toBe('error');
    unmount();
  });

  it('stops reconnecting when a terminal event arrives', async () => {
    const encoder = new TextEncoder();
    const client = {
      stream: vi.fn(async () => streamResponse(encoder, [
        'id: 1\ndata: {"jobId":"job-terminal","sequence":1,"status":"COMPLETED"}\n\n',
      ])),
      get: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useJobEvents('job-terminal'));
    await flush();
    expect(result.current.terminal).toBe(true);
    expect(result.current.connectionState).toBe('terminal');
    expect(client.stream).toHaveBeenCalledOnce();
    expect(client.get).not.toHaveBeenCalled();
  });
});

function pendingUntilAbort(path, options) {
  return new Promise((resolve, reject) => options.signal.addEventListener(
    'abort', () => reject(new DOMException('aborted', 'AbortError')), { once: true },
  ));
}

function streamResponse(encoder, frames) {
  return new Response(new ReadableStream({
    start(controller) { frames.forEach((frame) => controller.enqueue(encoder.encode(frame))); controller.close(); },
  }), { headers: { 'Content-Type': 'text/event-stream' } });
}

async function flush() {
  await act(async () => { await Promise.resolve(); await Promise.resolve(); await Promise.resolve(); });
}
