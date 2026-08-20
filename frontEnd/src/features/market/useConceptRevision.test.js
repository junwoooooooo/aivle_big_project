import { describe, expect, it, vi } from 'vitest';
import { waitForRefinementFinalized } from './useConceptRevision.js';

describe('waitForRefinementFinalized', () => {
  it('waits until BUILD_HANDOFF is visible as finalized', async () => {
    const load = vi.fn()
      .mockResolvedValueOnce({
        refinement: { finalized: false },
      })
      .mockResolvedValueOnce({
        refinement: { finalized: false },
      })
      .mockResolvedValueOnce({
        refinement: { finalized: true },
      });

    const sleepFn = vi.fn().mockResolvedValue();

    const result = await waitForRefinementFinalized(load, {
      attempts: 5,
      intervalMs: 0,
      sleepFn,
    });

    expect(result.refinement.finalized).toBe(true);
    expect(load).toHaveBeenCalledTimes(3);
    expect(sleepFn).toHaveBeenCalledTimes(2);
  });

  it('returns the latest state when polling budget is exhausted', async () => {
    const load = vi.fn().mockResolvedValue({
      refinement: { finalized: false },
    });

    const sleepFn = vi.fn().mockResolvedValue();

    const result = await waitForRefinementFinalized(load, {
      attempts: 2,
      intervalMs: 0,
      sleepFn,
    });

    expect(result.refinement.finalized).toBe(false);
    expect(load).toHaveBeenCalledTimes(3);
  });
});