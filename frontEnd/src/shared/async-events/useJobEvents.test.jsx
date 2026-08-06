import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useApiClient } from '../api/ApiClientProvider.jsx';
import { useJobEvents } from './useJobEvents.js';

vi.mock('../api/ApiClientProvider.jsx', () => ({ useApiClient: vi.fn() }));

describe('useJobEvents', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('reconnects with the last sequence cursor and deduplicates replay', async () => {
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
    const { result, unmount } = renderHook(() => useJobEvents('job-1', {
      reconnectDelayMs: 100,
      maxSseFailures: 2,
    }));

    await flush();
    await act(async () => { vi.advanceTimersByTime(100); await flush(); });

    expect(client.stream).toHaveBeenCalledTimes(2);
    expect(client.stream.mock.calls[1][1].headers['Last-Event-ID']).toBe('1');
    expect(result.current.events).toHaveLength(1);
    expect(result.current.transport).toBe('SSE');
    unmount();
  });

  it('uses bounded exponential reconnect delays before polling fallback', async () => {
    const client = {
      stream: vi.fn(async () => { throw new Error('stream unavailable'); }),
      get: vi.fn().mockImplementation(pendingUntilAbort),
    };
    useApiClient.mockReturnValue(client);
    const { unmount } = renderHook(() => useJobEvents('job-backoff', {
      reconnectDelayMs: 100,
      maxReconnectDelayMs: 200,
      maxSseFailures: 3,
    }));

    await flush();
    expect(client.stream).toHaveBeenCalledTimes(1);
    await act(async () => { vi.advanceTimersByTime(99); await flush(); });
    expect(client.stream).toHaveBeenCalledTimes(1);
    await act(async () => { vi.advanceTimersByTime(1); await flush(); });
    expect(client.stream).toHaveBeenCalledTimes(2);
    await act(async () => { vi.advanceTimersByTime(199); await flush(); });
    expect(client.stream).toHaveBeenCalledTimes(2);
    await act(async () => { vi.advanceTimersByTime(1); await flush(); });
    expect(client.stream).toHaveBeenCalledTimes(3);
    expect(client.get).toHaveBeenCalledTimes(1);
    unmount();
  });

  it.each([401, 403])('does not reconnect or poll after HTTP %s', async (status) => {
    const error = Object.assign(new Error('authentication failed'), { status });
    const client = {
      stream: vi.fn(async () => { throw error; }),
      get: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useJobEvents(`job-auth-${status}`));

    await flush();
    await act(async () => { vi.runAllTimers(); await flush(); });

    expect(client.stream).toHaveBeenCalledOnce();
    expect(client.get).not.toHaveBeenCalled();
    expect(result.current.connectionState).toBe('error');
    expect(result.current.error).toBe(error);
  });

  it('stops SSE reconnect when a terminal event is applied', async () => {
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
    await act(async () => { vi.runAllTimers(); await flush(); });

    expect(result.current.terminal).toBe(true);
    expect(result.current.connectionState).toBe('terminal');
    expect(client.stream).toHaveBeenCalledOnce();
    expect(client.get).not.toHaveBeenCalled();
  });

  it('falls back to polling and stops its timer at a terminal event', async () => {
    const client = {
      stream: vi.fn(async () => { throw new Error('stream unavailable'); }),
      get: vi.fn().mockResolvedValue({
        data: {
          events: [{ jobId: 'job-poll', sequence: 1, status: 'NEEDS_INPUT' }],
          nextSequence: 1,
          latestSequence: 1,
          hasMore: false,
        },
      }),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useJobEvents('job-poll', {
      maxSseFailures: 1,
      pollIntervalMs: 200,
    }));

    await flush();
    expect(result.current.transport).toBe('POLLING');
    expect(result.current.terminal).toBe(true);
    await act(async () => { vi.advanceTimersByTime(1000); await flush(); });

    expect(client.get).toHaveBeenCalledOnce();
    expect(client.get).toHaveBeenCalledWith(
      '/api/v2/jobs/job-poll/events?after=0',
      expect.any(Object),
    );
  });

  it('cleans up the previous stream when jobId changes and on unmount', async () => {
    const signals = [];
    const client = {
      stream: vi.fn((path, options) => {
        signals.push({ path, signal: options.signal });
        return pendingUntilAbort(path, options);
      }),
      get: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { rerender, unmount } = renderHook(
      ({ jobId }) => useJobEvents(jobId),
      { initialProps: { jobId: 'job-old' } },
    );
    await flush();

    rerender({ jobId: 'job-new' });
    await flush();

    expect(signals[0].signal.aborted).toBe(true);
    expect(signals[1].path).toContain('/job-new/events');
    unmount();
    expect(signals[1].signal.aborted).toBe(true);
  });

  it('exposes stop and reconnect controls without losing the cursor', async () => {
    const client = {
      stream: vi.fn().mockImplementation(pendingUntilAbort),
      get: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useJobEvents('job-controls'));
    await flush();

    act(() => result.current.stop());
    expect(result.current.connectionState).toBe('stopped');
    act(() => result.current.reconnect());
    await flush();

    expect(client.stream).toHaveBeenCalledTimes(2);
    expect(client.stream.mock.calls[1][1].headers['Last-Event-ID']).toBe('0');
  });
});

function pendingUntilAbort(path, options) {
  return new Promise((resolve, reject) => {
    options.signal.addEventListener(
      'abort',
      () => reject(new DOMException('aborted', 'AbortError')),
      { once: true },
    );
  });
}

function streamResponse(encoder, frames) {
  return new Response(new ReadableStream({
    start(controller) {
      frames.forEach((frame) => controller.enqueue(encoder.encode(frame)));
      controller.close();
    },
  }), { headers: { 'Content-Type': 'text/event-stream' } });
}

async function flush() {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
  });
}
