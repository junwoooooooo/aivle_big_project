import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiClientProvider } from '../../../shared/api/ApiClientProvider.jsx';
import { useJobEvents } from '../../../shared/async-events/index.js';
import { startNewConceptPortfolioRun, useConceptPortfolio } from './useConceptPortfolio.js';

vi.mock('../../../shared/async-events/index.js', () => ({ useJobEvents: vi.fn() }));

describe('useConceptPortfolio live invalidation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useJobEvents.mockReturnValue({ terminal: false, events: [] });
  });
  it('re-reads canonical REST state after project event revision changes', async () => {
    const client = { get: vi.fn((path) => {
      if (path.endsWith('/current') && path.includes('concept-portfolio-runs')) return Promise.resolve({ data: { runId: 'run-1' } });
      if (path.endsWith('/concepts')) return Promise.resolve({ data: [{ conceptId: 'c1', candidateId: 'candidate', selectable: true }] });
      if (path.endsWith('/input-requests')) return Promise.resolve({ data: [] });
      return Promise.resolve({ data: null });
    }), post: vi.fn() };
    const wrapper = ({ children }) => <ApiClientProvider client={client}>{children}</ApiClientProvider>;
    const { result, rerender } = renderHook(({ revision }) => useConceptPortfolio('41', revision), { wrapper, initialProps: { revision: 0 } });
    await waitFor(() => expect(result.current.loading).toBe(false));
    const firstReads = client.get.mock.calls.filter(([path]) => path.includes('concept-portfolio-runs/current')).length;
    rerender({ revision: 1 });
    await waitFor(() => expect(client.get.mock.calls.filter(([path]) => path.includes('concept-portfolio-runs/current')).length).toBeGreaterThan(firstReads));
  });

  it('uses a fresh idempotency key for each terminal Portfolio retry', async () => {
    const api = { ideaBrief: vi.fn().mockResolvedValue({ data: { confirmedSnapshotId: 'brief-1' } }),
      createRun: vi.fn().mockResolvedValue({ data: { runId: 'run' } }) };
    await startNewConceptPortfolioRun(api, '41');
    await startNewConceptPortfolioRun(api, '41');
    const first = api.createRun.mock.calls[0][1];
    const second = api.createRun.mock.calls[1][1];
    expect(first).toMatchObject({ ideaBriefSnapshotId: 'brief-1', maxConcepts: 5 });
    expect(second.idempotencyKey).not.toBe(first.idempotencyKey);
  });

  it('tracks retryDelta task events, refreshes on terminal, and clears the stale task id', async () => {
    let terminal = false;
    let selection = { selectionId: 17, status: 'DELTA_LEGAL_FAILED', activeTaskRunId: null };
    const observed = [];
    useJobEvents.mockImplementation((taskRunId) => {
      observed.push(taskRunId);
      return { terminal, events: [] };
    });
    const client = portfolioClient(() => selection);
    client.post.mockImplementation((path) => {
      if (path.endsWith('/delta-legal/retry')) {
        selection = { selectionId: 17, status: 'DELTA_LEGAL_PENDING', activeTaskRunId: 'delta-task-2' };
        return Promise.resolve({ data: { taskRunId: 'delta-task-2', action: 'DELTA_LEGAL' } });
      }
      return Promise.resolve({ data: null });
    });
    const wrapper = ({ children }) => <ApiClientProvider client={client}>{children}</ApiClientProvider>;
    const { result, rerender } = renderHook(() => useConceptPortfolio('41'), { wrapper });
    await waitFor(() => expect(result.current.selection?.status).toBe('DELTA_LEGAL_FAILED'));

    await act(async () => result.current.retryDelta());

    expect(result.current.activeSelectionTaskRunId).toBe('delta-task-2');
    await waitFor(() => expect(observed).toContain('delta-task-2'));
    selection = { selectionId: 17, status: 'READY_FOR_LEGAL_REPORT', activeTaskRunId: null };
    terminal = true;
    rerender();
    await waitFor(() => expect(result.current.selection?.status).toBe('READY_FOR_LEGAL_REPORT'));
    expect(result.current.activeSelectionTaskRunId).toBeNull();
  });

  it('prevents duplicate delta retries and exposes a retry failure after refreshing state', async () => {
    let rejectRequest;
    const pending = new Promise((_, reject) => { rejectRequest = reject; });
    const selection = { selectionId: 17, status: 'DELTA_LEGAL_FAILED', activeTaskRunId: null };
    const client = portfolioClient(() => selection);
    client.post.mockReturnValue(pending);
    const wrapper = ({ children }) => <ApiClientProvider client={client}>{children}</ApiClientProvider>;
    const { result } = renderHook(() => useConceptPortfolio('41'), { wrapper });
    await waitFor(() => expect(result.current.selection).not.toBeNull());

    let first;
    act(() => { first = result.current.retryDelta(); });
    const second = await result.current.retryDelta();
    expect(second).toBeNull();
    expect(client.post).toHaveBeenCalledTimes(1);
    await act(async () => {
      rejectRequest(new Error('retry failed'));
      await expect(first).rejects.toThrow('retry failed');
    });
    expect(result.current.error?.message).toBe('retry failed');
    expect(result.current.busy).toBe(false);
    expect(result.current.selection.status).toBe('DELTA_LEGAL_FAILED');
  });

  it('gates stale current results and starts the replacement run from the latest confirmed snapshot', async () => {
    const selection = { selectionId: 17, status: 'READY_FOR_MARKET' };
    const client = portfolioClient(() => selection);
    client.get.mockImplementation((path) => {
      if (path.endsWith('/idea-brief')) return Promise.resolve({ data: { confirmedSnapshotId: 'idea-v2' } });
      return portfolioGet(path, selection);
    });
    client.post.mockResolvedValue({ data: { runId: 'concept-v2' } });
    const wrapper = ({ children }) => <ApiClientProvider client={client}>{children}</ApiClientProvider>;
    const { result } = renderHook(() => useConceptPortfolio('41', 0, 'STALE'), { wrapper });
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.run.productStatus).toBe('STALE');
    expect(result.current.concepts).toEqual([]);
    expect(result.current.selection).toBeNull();
    await act(async () => result.current.start());
    expect(client.post).toHaveBeenCalledWith(expect.stringContaining('/concept-portfolio-runs'),
      expect.objectContaining({ ideaBriefSnapshotId: 'idea-v2' }), undefined);
  });
});

function portfolioGet(path, selection) {
  if (path.endsWith('/concept-portfolio-runs/current')) return Promise.resolve({ data: { runId: 'concept-v1' } });
  if (path.endsWith('/concepts')) return Promise.resolve({ data: [{ conceptId: 'old-concept', selectable: true }] });
  if (path.endsWith('/input-requests')) return Promise.resolve({ data: [] });
  if (path.endsWith('/concept-portfolio-selections/current')) return Promise.resolve({ data: selection });
  if (path.endsWith('/hypotheses')) return Promise.resolve({ data: [] });
  return Promise.resolve({ data: null });
}

function portfolioClient(selection) {
  return { get: vi.fn((path) => portfolioGet(path, selection())), post: vi.fn() };
}
