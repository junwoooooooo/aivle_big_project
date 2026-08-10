import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { useJobEvents } from '../../../shared/async-events/index.js';
import useTechOps from './useTechOps.js';

vi.mock('../../../shared/api/ApiClientProvider.jsx', () => ({ useApiClient: vi.fn() }));
vi.mock('../../../shared/async-events/index.js', () => ({ useJobEvents: vi.fn() }));

describe('useTechOps', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useJobEvents.mockReturnValue({ terminal: false, events: [] });
  });

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

  it('sends a fresh command key for an alternative and follows its pending TaskRun', async () => {
    const preparation = { preparationId: 'prep-1', revision: 2, requiredFacts: {},
      proposalDecisions: {}, evidenceReferences: [], activeProposalTaskRunId: null };
    const queued = { preparation: { ...preparation, activeProposalTaskRunId: 'task-1',
      proposalGenerationStatus: 'QUEUED' }, taskRunId: 'task-1', status: 'QUEUED' };
    const client = {
      get: vi.fn(async (path) => {
        if (path.endsWith('/tech-ops/preparation')) return { data: preparation };
        if (path.endsWith('/module-runs')) return { data: { runs: [] } };
        throw { status: 404 };
      }),
      post: vi.fn(async (path) => path.includes('/proposals/') ? { data: queued } : { data: {} }),
      patch: vi.fn(), delete: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useTechOps('7'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(() => result.current.decide('deliveryOrProductionMethod', {
      action: 'REJECT_AND_REQUEST_ALTERNATIVE', value: null,
    }));

    const options = client.post.mock.calls[0][2];
    expect(options.headers['Idempotency-Key']).toBeTruthy();
  });

  it('refreshes preparation after terminal proposal events', async () => {
    let terminal = false;
    const preparation = { preparationId: 'prep-1', revision: 2, requiredFacts: {}, proposalDecisions: {},
      evidenceReferences: [], activeProposalTaskRunId: 'task-1', proposalGenerationStatus: 'RUNNING' };
    const client = { get: vi.fn(async (path) => {
      if (path.endsWith('/tech-ops/preparation')) return { data: preparation };
      if (path.endsWith('/module-runs')) return { data: { runs: [] } };
      throw { status: 404 };
    }), post: vi.fn(), patch: vi.fn(), delete: vi.fn() };
    useApiClient.mockReturnValue(client);
    useJobEvents.mockImplementation(() => ({ terminal, events: [] }));
    const { result, rerender } = renderHook(() => useTechOps('7'));
    await waitFor(() => expect(result.current.loading).toBe(false));
    const before = client.get.mock.calls.filter(([path]) => path.endsWith('/tech-ops/preparation')).length;

    terminal = true;
    rerender();

    await waitFor(() => expect(client.get.mock.calls.filter(([path]) => path.endsWith('/tech-ops/preparation')).length)
      .toBeGreaterThan(before));
  });

  it('uploads a real file artifact before registering the evidence reference', async () => {
    const preparation = { preparationId: 'prep-1', revision: 2, requiredFacts: {},
      proposalDecisions: {}, evidenceReferences: [] };
    const client = {
      get: vi.fn(async (path) => {
        if (path.endsWith('/tech-ops/preparation')) return { data: preparation };
        if (path.endsWith('/module-runs')) return { data: { runs: [] } };
        throw { status: 404 };
      }),
      upload: vi.fn(async () => ({ data: { artifactId: 'artifact-1', originalFilename: 'quote.pdf' } })),
      post: vi.fn(async (path, body) => path.endsWith('/preparation/evidence')
        ? { data: preparation, body } : { data: {} }),
      patch: vi.fn(), delete: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useTechOps('7'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    const file = new File(['%PDF-1.4'], 'quote.pdf', { type: 'application/pdf' });
    await act(() => result.current.uploadEvidence(file, 'QUOTE', 'supplier quote'));

    expect(client.upload).toHaveBeenCalledWith('/api/v3/projects/7/evidence-artifacts', expect.any(FormData), undefined);
    const evidenceCall = client.post.mock.calls.find(([path]) => path.endsWith('/preparation/evidence'));
    expect(evidenceCall[1]).toEqual({ evidenceType: 'QUOTE', artifactId: 'artifact-1', description: 'supplier quote' });
    expect(evidenceCall[1]).not.toHaveProperty('artifactRef');
  });
});
