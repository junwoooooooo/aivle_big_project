import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { useJobEvents } from '../../../shared/async-events/index.js';
import useFinance from './useFinance.js';

vi.mock('../../../shared/api/ApiClientProvider.jsx', () => ({ useApiClient: vi.fn() }));
vi.mock('../../../shared/async-events/index.js', () => ({ useJobEvents: vi.fn() }));

describe('useFinance', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useJobEvents.mockReturnValue({ terminal: false, events: [] });
  });

  it('queues a lazy field estimate with a fresh command key', async () => {
    const preparation = { preparationId: 'prep-1', revision: 1,
      assistance: { totalMarketingCost: { estimateStatus: 'NONE', activeTaskRunId: null } } };
    const client = {
      get: vi.fn(async (path) => {
        if (path.endsWith('/finance/preparation')) return { data: preparation };
        if (path.endsWith('/module-runs')) return { data: { runs: [] } };
        throw { status: 404 };
      }),
      post: vi.fn(async (path) => path.endsWith('/totalMarketingCost/generate')
        ? { data: { taskRunId: 'task-1', status: 'QUEUED' } } : { data: {} }),
      patch: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useFinance('7'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(() => result.current.generateEstimate('totalMarketingCost'));

    const call = client.post.mock.calls.find(([path]) => path.endsWith('/totalMarketingCost/generate'));
    expect(call[2].headers['Idempotency-Key']).toBeTruthy();
  });

  it('follows the active estimate and refreshes after a terminal event', async () => {
    let terminal = false;
    const preparation = { preparationId: 'prep-1', revision: 1,
      assistance: { totalMarketingCost: { estimateStatus: 'RUNNING', activeTaskRunId: 'task-1' } } };
    const client = { get: vi.fn(async (path) => {
      if (path.endsWith('/finance/preparation')) return { data: preparation };
      if (path.endsWith('/module-runs')) return { data: { runs: [] } };
      throw { status: 404 };
    }), post: vi.fn(), patch: vi.fn() };
    useApiClient.mockReturnValue(client);
    useJobEvents.mockImplementation(() => ({ terminal, events: [] }));
    const { result, rerender } = renderHook(() => useFinance('7'));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(useJobEvents).toHaveBeenLastCalledWith('task-1');
    const before = client.get.mock.calls.filter(([path]) => path.endsWith('/finance/preparation')).length;

    terminal = true;
    rerender();

    await waitFor(() => expect(client.get.mock.calls.filter(([path]) => path.endsWith('/finance/preparation')).length)
      .toBeGreaterThan(before));
  });

  it('imports, finalizes, and runs analysis from one action', async () => {
    const preparation = { preparationId: 'prep-1', revision: 1, inputSnapshotId: null, assistance: {} };
    const client = {
      get: vi.fn(async (path) => {
        if (path.endsWith('/finance/preparation')) return { data: preparation };
        if (path.endsWith('/module-runs')) return { data: { runs: [] } };
        throw { status: 404 };
      }),
      upload: vi.fn(async () => ({ data: preparation })),
      post: vi.fn(async (path) => path.endsWith('/analysis') ? { data: { cashFlowChart: [] } } : { data: {} }),
      patch: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useFinance('7'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => { await result.current.importAndAnalyze(new File(['docx'], 'finance.docx')); });

    expect(client.upload).toHaveBeenCalledWith('/api/v3/projects/7/finance/preparation/import', expect.any(FormData), { timeoutMs: 60000 });
    expect(client.post.mock.calls.map(([path]) => path)).toEqual([
      '/api/v3/projects/7/finance/input-snapshots/finalize',
      '/api/v3/projects/7/finance/analysis',
    ]);
    expect(result.current.analysis).toEqual({ cashFlowChart: [] });
  });

  it('downloads the generated report after the one-click analysis workflow', async () => {
    const preparation = { preparationId: 'prep-1', revision: 1, inputSnapshotId: null, assistance: {} };
    const report = new Blob(['report']);
    const client = {
      get: vi.fn(async (path) => {
        if (path.endsWith('/finance/preparation')) return { data: preparation };
        if (path.endsWith('/finance/analysis/document')) return report;
        if (path.endsWith('/module-runs')) return { data: { runs: [] } };
        throw { status: 404 };
      }), upload: vi.fn(async () => ({ data: preparation })),
      post: vi.fn(async (path) => path.endsWith('/analysis') ? { data: { cashFlowChart: [] } } : { data: {} }), patch: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useFinance('7'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    let output;
    await act(async () => { output = await result.current.importAnalyzeAndDownload(new File(['docx'], 'finance.docx')); });

    expect(output.document).toBe(report);
    expect(client.get).toHaveBeenCalledWith('/api/v3/projects/7/finance/analysis/document', { timeoutMs: 60000, responseType: 'blob' });
  });

  it('reopens an existing snapshot before importing a replacement document', async () => {
    const preparation = { preparationId: 'prep-1', revision: 2, inputSnapshotId: 'snapshot-1', assistance: {} };
    const report = new Blob(['report']);
    const client = {
      get: vi.fn(async (path) => {
        if (path.endsWith('/finance/preparation')) return { data: preparation };
        if (path.endsWith('/finance/input-snapshots/current')) return { data: { snapshotId: 'snapshot-1' } };
        if (path.endsWith('/finance/analysis/current')) return { data: null };
        if (path.endsWith('/finance/analysis/document')) return report;
        if (path.endsWith('/module-runs')) return { data: { runs: [] } };
        throw { status: 404 };
      }),
      upload: vi.fn(async () => ({ data: preparation })),
      post: vi.fn(async (path) => path.endsWith('/analysis') ? { data: { cashFlowChart: [] } } : { data: {} }),
      patch: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useFinance('7'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => { await result.current.importAnalyzeAndDownload(new File(['docx'], 'finance.docx')); });

    expect(client.post.mock.calls.map(([path]) => path)).toEqual([
      '/api/v3/projects/7/finance/input-snapshots/current/reopen',
      '/api/v3/projects/7/finance/input-snapshots/finalize',
      '/api/v3/projects/7/finance/analysis',
    ]);
  });
});
