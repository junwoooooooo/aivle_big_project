import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import useMarketingGeneration from './useMarketingGeneration.js';

describe('useMarketingGeneration', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('starts, polls, and reloads versions after success', async () => {
    vi.useFakeTimers();
    const api = {
      generate: vi.fn().mockResolvedValue({
        jobId: 9,
        status: 'QUEUED',
      }),
      job: vi.fn()
        .mockResolvedValueOnce({ jobId: 9, status: 'RUNNING' })
        .mockResolvedValueOnce({ jobId: 9, status: 'SUCCEEDED' }),
    };
    const onSucceeded = vi.fn().mockResolvedValue(undefined);
    const { result } = renderHook(() => useMarketingGeneration({
      api,
      projectId: '7',
      contentId: '3',
      onSucceeded,
    }));

    await act(async () => {
      await result.current.generate(
        new File(['image'], 'source.png', { type: 'image/png' }),
        11,
      );
    });
    await act(async () => {
      await vi.runOnlyPendingTimersAsync();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });

    expect(api.generate).toHaveBeenCalledTimes(1);
    expect(api.job).toHaveBeenCalledTimes(2);
    expect(onSucceeded).toHaveBeenCalledTimes(1);
    expect(result.current.status).toBe('SUCCEEDED');
  });

  it('prevents duplicate starts while a job is active', async () => {
    vi.useFakeTimers();
    const api = {
      generate: vi.fn().mockResolvedValue({
        jobId: 9,
        status: 'QUEUED',
      }),
      job: vi.fn(),
    };
    const { result } = renderHook(() => useMarketingGeneration({
      api,
      projectId: '7',
      contentId: '3',
    }));
    const file = new File(['image'], 'source.png', { type: 'image/png' });
    await act(async () => {
      await result.current.generate(file, 11);
    });
    let second;
    await act(async () => {
      second = await result.current.generate(file, 11);
    });

    expect(second).toBe(false);
    expect(api.generate).toHaveBeenCalledTimes(1);
  });

  it('exposes a safe failed state', async () => {
    const error = { code: 'AI_SERVER_INTERNAL_ERROR' };
    const api = {
      generate: vi.fn().mockRejectedValue(error),
      job: vi.fn(),
    };
    const { result } = renderHook(() => useMarketingGeneration({
      api,
      projectId: '7',
      contentId: '3',
    }));
    await act(async () => {
      await result.current.generate(
        new File(['image'], 'source.png', { type: 'image/png' }),
        11,
      );
    });

    expect(result.current.status).toBe('FAILED');
    expect(result.current.error).toBe(error);
  });
});
