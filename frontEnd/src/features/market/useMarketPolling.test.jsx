import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import useMarketLiveState, { MARKET_EXECUTION_GUIDANCE_LIMIT_MS } from './useMarketPolling.js';

describe('useMarketLiveState SSE refresh seam', () => {
  it('does not warn before the 20-minute Market worker deadline', () => {
    expect(MARKET_EXECUTION_GUIDANCE_LIMIT_MS).toBe(22 * 60 * 1000);
    expect(MARKET_EXECUTION_GUIDANCE_LIMIT_MS).toBeGreaterThanOrEqual(20 * 60 * 1000);
  });
  it('reloads canonical current state when the project live revision changes', async () => {
    const load = vi.fn().mockResolvedValue({ run: null, version: null });
    const start = vi.fn();
    const { rerender } = renderHook(
      ({ revision }) => useMarketLiveState(load, start, revision),
      { initialProps: { revision: 0 } },
    );

    await waitFor(() => expect(load).toHaveBeenCalledTimes(1));
    rerender({ revision: 1 });
    await waitFor(() => expect(load).toHaveBeenCalledTimes(2));
  });

  it('runs a recollect action through the same TaskRun live-state seam', async () => {
    const load = vi.fn().mockResolvedValue({ run: null, version: { id: 73 } });
    const start = vi.fn();
    const recollect = vi.fn().mockResolvedValue({ taskRunId: 'task-b', taskState: 'QUEUED' });
    const { result } = renderHook(() => useMarketLiveState(load, start, 0));
    await waitFor(() => expect(load).toHaveBeenCalledOnce());

    await act(async () => result.current.triggerAction(recollect));

    expect(recollect).toHaveBeenCalledOnce();
    expect(result.current.run.taskRunId).toBe('task-b');
  });
});
