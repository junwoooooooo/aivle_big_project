import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useJobEvents } from '../../../shared/async-events/index.js';
import useMarketingGeneration from './useMarketingGeneration.js';

vi.mock('../../../shared/async-events/index.js', () => ({ useJobEvents: vi.fn() }));

const detail = (status, contentId = 'content-1', activeJobId = null) => ({
  content: { contentId, status, activeJobId },
  revisions: [],
});

const deferred = () => {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
};

describe('useMarketingGeneration canonical refresh', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useJobEvents.mockReturnValue({ terminal: false, events: [], transport: 'SSE' });
  });

  afterEach(() => vi.useRealTimers());

  it('restores a running detail and connects event replay with activeJobId', () => {
    const api = { detail: vi.fn(), create: vi.fn(), regenerate: vi.fn() };
    const { result } = renderHook(() => useMarketingGeneration({ api, projectId: '7' }));
    act(() => result.current.restore(detail('RUNNING', 'content-1', 'job-1')));
    expect(result.current.active).toBe(true);
    expect(useJobEvents).toHaveBeenLastCalledWith('job-1');
  });

  it('re-queries canonical detail after an SSE terminal event', async () => {
    let terminal = false;
    useJobEvents.mockImplementation(() => ({ terminal, events: [] }));
    const completed = detail('COMPLETED');
    const api = { detail: vi.fn().mockResolvedValue(completed), create: vi.fn(), regenerate: vi.fn() };
    const onUpdate = vi.fn();
    const { result, rerender } = renderHook(() => useMarketingGeneration({
      api, projectId: '7', onUpdate, pollIntervalMs: 60_000,
    }));
    act(() => result.current.restore(detail('RUNNING', 'content-1', 'job-1')));
    terminal = true;
    rerender();

    await waitFor(() => expect(result.current.status).toBe('COMPLETED'));
    expect(api.detail).toHaveBeenCalledWith('7', 'content-1');
    expect(onUpdate).toHaveBeenLastCalledWith(completed, {});
  });

  it('detects QUEUED to RUNNING to COMPLETED when the SSE terminal event is missed', async () => {
    vi.useFakeTimers();
    const api = {
      detail: vi.fn()
        .mockResolvedValueOnce(detail('RUNNING', 'content-1', 'job-1'))
        .mockResolvedValueOnce(detail('COMPLETED')),
      create: vi.fn(),
      regenerate: vi.fn(),
    };
    const { result } = renderHook(() => useMarketingGeneration({
      api, projectId: '7', pollIntervalMs: 25,
    }));
    act(() => result.current.restore(detail('QUEUED', 'content-1', 'job-1')));

    await act(async () => { await vi.advanceTimersByTimeAsync(25); });
    expect(result.current.status).toBe('RUNNING');
    await act(async () => { await vi.advanceTimersByTimeAsync(25); });
    expect(result.current.status).toBe('COMPLETED');
    expect(api.detail).toHaveBeenCalledTimes(2);
  });

  it('stops polling after canonical FAILED detail', async () => {
    vi.useFakeTimers();
    const api = {
      detail: vi.fn().mockResolvedValue(detail('FAILED')),
      create: vi.fn(),
      regenerate: vi.fn(),
    };
    const { result } = renderHook(() => useMarketingGeneration({
      api, projectId: '7', pollIntervalMs: 25,
    }));
    act(() => result.current.restore(detail('RUNNING', 'content-1', 'job-1')));

    await act(async () => { await vi.advanceTimersByTimeAsync(25); });
    expect(result.current.status).toBe('FAILED');
    await act(async () => { await vi.advanceTimersByTimeAsync(100); });
    expect(api.detail).toHaveBeenCalledTimes(1);
  });

  it('does not let an old content poll overwrite a newly restored content', async () => {
    vi.useFakeTimers();
    const oldRequest = deferred();
    const api = {
      detail: vi.fn().mockReturnValue(oldRequest.promise),
      create: vi.fn(),
      regenerate: vi.fn(),
    };
    const { result } = renderHook(() => useMarketingGeneration({
      api, projectId: '7', pollIntervalMs: 25,
    }));
    act(() => result.current.restore(detail('RUNNING', 'old-content', 'old-job')));
    await act(async () => { await vi.advanceTimersByTimeAsync(25); });
    act(() => result.current.restore(detail('COMPLETED', 'new-content')));
    await act(async () => { oldRequest.resolve(detail('COMPLETED', 'old-content')); });

    expect(result.current.contentId).toBe('new-content');
    expect(result.current.status).toBe('COMPLETED');
  });

  it('cleans up the canonical polling timer on unmount', async () => {
    vi.useFakeTimers();
    const api = { detail: vi.fn(), create: vi.fn(), regenerate: vi.fn() };
    const { result, unmount } = renderHook(() => useMarketingGeneration({
      api, projectId: '7', pollIntervalMs: 25,
    }));
    act(() => result.current.restore(detail('RUNNING', 'content-1', 'job-1')));
    unmount();
    await vi.advanceTimersByTimeAsync(100);
    expect(api.detail).not.toHaveBeenCalled();
  });
});
