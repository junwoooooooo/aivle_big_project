import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiClientProvider } from '../../shared/api/ApiClientProvider.jsx';
import { useConceptWorkboard } from './useConceptWorkboard.js';

const jobState = {
  events: [],
  transport: 'SSE',
  connectionState: 'CONNECTED',
  stop: vi.fn(),
  reconnect: vi.fn(),
};

vi.mock('../../shared/async-events/index.js', () => ({
  useJobEvents: () => jobState,
}));

function wrapper(client) {
  return function TestWrapper({ children }) {
    return <ApiClientProvider client={client}>{children}</ApiClientProvider>;
  };
}

function batch(status = 'GENERATING') {
  return {
    batchId: 41,
    jobId: 'job-41',
    status,
    confirmedBriefVersionId: 11,
    briefHash: 'sha256:brief',
    regulatoryBoundaryVersionId: 21,
    boundaryHash: 'sha256:boundary',
    stale: false,
  };
}

describe('useConceptWorkboard', () => {
  beforeEach(() => {
    jobState.events = [];
    vi.clearAllMocks();
  });

  it('restores the current batch and slots from authoritative queries', async () => {
    const current = batch();
    const client = {
      get: vi.fn(async (path) => {
        if (path.endsWith('/current')) return { data: { batch: current } };
        if (path.endsWith('/slots')) return { data: [
          { slotId: 3, slotIndex: 2 }, { slotId: 1, slotIndex: 0 }, { slotId: 2, slotIndex: 1 },
        ] };
        if (path.endsWith('/41')) return { data: { batch: current } };
        throw new Error(path);
      }),
    };

    const { result } = renderHook(() => useConceptWorkboard('7'), { wrapper: wrapper(client) });

    await waitFor(() => expect(result.current.batch?.batchId).toBe(41));
    expect(result.current.slots.map((slot) => slot.slotIndex)).toEqual([0, 1, 2]);
    expect(client.get).toHaveBeenCalledWith('/api/v2/projects/7/concept-explorations/current');
    expect(client.get).not.toHaveBeenCalledWith('/api/v2/projects/7/concepts?contract=concept-core-v1');
  });

  it('loads public concepts only after the restored batch is completed', async () => {
    const completed = batch('COMPLETED');
    const client = {
      get: vi.fn(async (path) => {
        if (path.endsWith('/current')) return { data: { batch: completed } };
        if (path.endsWith('/slots')) return { data: [] };
        if (path.endsWith('/41')) return { data: { batch: completed } };
        if (path.includes('/concepts?contract=')) return { data: [{ conceptId: 1 }, { conceptId: 2 }, { conceptId: 3 }] };
        throw new Error(path);
      }),
    };

    const { result } = renderHook(() => useConceptWorkboard('7'), { wrapper: wrapper(client) });

    await waitFor(() => expect(result.current.concepts).toHaveLength(3));
    expect(client.get).toHaveBeenCalledWith('/api/v2/projects/7/concepts?contract=concept-core-v1');
  });
});
