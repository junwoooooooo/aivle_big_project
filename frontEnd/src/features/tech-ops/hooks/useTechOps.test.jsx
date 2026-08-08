import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import useTechOps from './useTechOps.js';

vi.mock('../../../shared/api/ApiClientProvider.jsx', () => ({ useApiClient: vi.fn() }));

describe('useTechOps', () => {
  beforeEach(() => vi.clearAllMocks());

  it('loads the current preparation, immutable snapshot and TechOps run independently', async () => {
    const preparation = { preparationId: 'prep-1', revision: 2, requiredFacts: {}, proposalDecisions: {}, evidenceReferences: [] };
    const snapshot = { snapshotId: 'tech-snapshot-1', snapshotHash: `sha256:${'a'.repeat(64)}` };
    const run = { runId: 'run-1', module: 'TECH_OPS', status: 'NOT_CONNECTED' };
    const client = { get: vi.fn(async (path) => {
      if (path.endsWith('/tech-ops/preparation')) return { data: preparation };
      if (path.endsWith('/tech-ops/input-snapshots/current')) return { data: snapshot };
      if (path.endsWith('/module-runs')) return { data: { runs: [run] } };
      throw new Error(`unexpected path ${path}`);
    }), post: vi.fn(), patch: vi.fn(), delete: vi.fn() };
    useApiClient.mockReturnValue(client);

    const { result } = renderHook(() => useTechOps('7'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.preparation).toEqual(preparation);
    expect(result.current.snapshot).toEqual(snapshot);
    expect(result.current.run).toEqual(run);
  });
});
