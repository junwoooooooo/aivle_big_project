import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import useMarketingGeneration from './useMarketingGeneration.js';

describe('useMarketingGeneration', () => {
  afterEach(() => vi.useRealTimers());
  it('restores a queued task through detail polling until completion', async () => {
    vi.useFakeTimers(); const onUpdate = vi.fn();
    const api = {
      create: vi.fn().mockResolvedValue({ content: { contentId: 'content-1', status: 'QUEUED' } }),
      detail: vi.fn().mockResolvedValue({ content: { contentId: 'content-1', status: 'COMPLETED' }, revisions: [] }),
      regenerate: vi.fn(),
    };
    const { result } = renderHook(() => useMarketingGeneration({ api, projectId: '7', onUpdate, pollMs: 20 }));
    await act(async () => { await result.current.create({ contract: 'marketing-content-request-v1' }); });
    expect(result.current.status).toBe('QUEUED');
    await act(async () => { await vi.advanceTimersByTimeAsync(20); });
    expect(api.detail).toHaveBeenCalledWith('7', 'content-1');
    expect(result.current.status).toBe('COMPLETED');
    expect(result.current.active).toBe(false);
  });
});
