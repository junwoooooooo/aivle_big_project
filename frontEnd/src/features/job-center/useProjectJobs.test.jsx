import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { useJobEvents } from '../../shared/async-events/index.js';
import { useProjectJobs } from './useProjectJobs.js';

vi.mock('../../shared/api/ApiClientProvider.jsx', () => ({ useApiClient: vi.fn() }));
vi.mock('../../shared/async-events/index.js', () => ({ useJobEvents: vi.fn() }));

describe('useProjectJobs', () => {
  beforeEach(() => vi.clearAllMocks());

  it('restores server jobs on refresh and connects only the selected job', async () => {
    const client = { get: vi.fn()
      .mockResolvedValueOnce({ data: [{ jobId: 'job-1', status: 'RUNNING' }] })
      .mockResolvedValueOnce({ data: [{ jobId: 'job-old', status: 'SUCCEEDED' }] }) };
    useApiClient.mockReturnValue(client);
    useJobEvents.mockReturnValue({ terminal: false, events: [], reconnect: vi.fn() });

    const { result } = renderHook(() => useProjectJobs('41'));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.selectedJobId).toBe('job-1');
    expect(useJobEvents).toHaveBeenLastCalledWith('job-1');
    expect(client.get).toHaveBeenNthCalledWith(1, '/api/v3/projects/41/active-jobs', {});
  });

  it('re-queries server truth and module status after a terminal event', async () => {
    let terminal = false;
    const onTerminal = vi.fn();
    const client = { get: vi.fn().mockResolvedValue({ data: [{ jobId: 'job-1', status: 'RUNNING' }] }) };
    useApiClient.mockReturnValue(client);
    useJobEvents.mockImplementation(() => ({
      terminal, events: terminal ? [{ sequence: 2, status: 'COMPLETED' }] : [], reconnect: vi.fn(),
    }));
    const { result, rerender } = renderHook(() => useProjectJobs('41', { onTerminal }));
    await waitFor(() => expect(result.current.selectedJobId).toBe('job-1'));
    terminal = true;
    rerender();
    await waitFor(() => expect(onTerminal).toHaveBeenCalledTimes(1));
    expect(client.get).toHaveBeenCalledTimes(4);
  });

  it('replaces a stale needs-input notice after refresh resolves its actionability', async () => {
    let terminal = false;
    const current = { jobId: 'job-1', status: 'NEEDS_INPUT', rawStatus: 'NEEDS_INPUT',
      actionable: true, presentationStatus: 'NEEDS_INPUT' };
    const resolved = { ...current, actionable: false, presentationStatus: 'RESOLVED_INPUT' };
    const client = { get: vi.fn()
      .mockResolvedValueOnce({ data: [current] })
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: [resolved] }) };
    useApiClient.mockReturnValue(client);
    useJobEvents.mockImplementation(() => ({
      terminal, events: terminal ? [{ sequence: 2, status: 'NEEDS_INPUT' }] : [], reconnect: vi.fn(),
    }));
    const { result, rerender } = renderHook(() => useProjectJobs('41'));
    await waitFor(() => expect(result.current.selectedJobId).toBe('job-1'));

    terminal = true;
    rerender();

    await waitFor(() => expect(result.current.notice?.status).toBe('RESOLVED_INPUT'));
  });

  it('auto-selects a new active job but preserves an explicit historical selection', async () => {
    const oldActive = { jobId: 'active-old', status: 'RUNNING' };
    const newActive = { jobId: 'active-new', status: 'RUNNING' };
    const historical = { jobId: 'history-1', status: 'FAILED' };
    const client = { get: vi.fn()
      .mockResolvedValueOnce({ data: [oldActive] })
      .mockResolvedValueOnce({ data: [historical] })
      .mockResolvedValueOnce({ data: [newActive, oldActive] })
      .mockResolvedValueOnce({ data: [historical] })
      .mockResolvedValueOnce({ data: [newActive, oldActive] })
      .mockResolvedValueOnce({ data: [historical] }) };
    useApiClient.mockReturnValue(client);
    useJobEvents.mockReturnValue({ terminal: false, events: [], reconnect: vi.fn() });
    const { result } = renderHook(() => useProjectJobs('41'));
    await waitFor(() => expect(result.current.selectedJobId).toBe('active-old'));

    await act(async () => result.current.refresh());
    expect(result.current.selectedJobId).toBe('active-new');

    act(() => result.current.selectJob('history-1'));
    await act(async () => result.current.refresh());
    expect(result.current.selectedJobId).toBe('history-1');
  });

  it('loads all persisted events for a terminal job without opening SSE', async () => {
    const terminalJob = { jobId: 'job-done', status: 'COMPLETED' };
    const events = Array.from({ length: 5 }, (_, index) => ({ sequence: index + 1, status: index === 4 ? 'COMPLETED' : 'RUNNING' }));
    const client = { get: vi.fn(async (path) => {
      if (path.includes('/active-jobs')) return { data: [] };
      if (path.includes('/recent-jobs')) return { data: [terminalJob] };
      if (path.includes('after=0')) return { data: { events: events.slice(0, 3), nextSequence: 3, hasMore: true } };
      if (path.includes('after=3')) return { data: { events: events.slice(3), nextSequence: 5, hasMore: false } };
      throw new Error(`unexpected path: ${path}`);
    }) };
    useApiClient.mockReturnValue(client);
    useJobEvents.mockReturnValue({ terminal: false, events: [], reconnect: vi.fn() });

    const { result } = renderHook(() => useProjectJobs('41'));

    await waitFor(() => expect(result.current.events.events).toHaveLength(5));
    expect(result.current.events.transport).toBe('REST');
    expect(result.current.events.loading).toBe(false);
    expect(useJobEvents).toHaveBeenLastCalledWith(null);
    expect(client.get).toHaveBeenCalledWith('/api/v2/jobs/job-done/events?after=3', expect.objectContaining({ signal: expect.any(AbortSignal) }));
  });

  it('loads a failed job history from persisted events', async () => {
    const failedJob = { jobId: 'job-failed', status: 'FAILED' };
    const client = { get: vi.fn(async (path) => {
      if (path.includes('/active-jobs')) return { data: [] };
      if (path.includes('/recent-jobs')) return { data: [failedJob] };
      return { data: { events: [{ sequence: 1, status: 'RUNNING' }, { sequence: 2, status: 'FAILED' }], nextSequence: 2, hasMore: false } };
    }) };
    useApiClient.mockReturnValue(client);
    useJobEvents.mockReturnValue({ terminal: false, events: [], reconnect: vi.fn() });

    const { result } = renderHook(() => useProjectJobs('41'));

    await waitFor(() => expect(result.current.events.events).toHaveLength(2));
    expect(result.current.events.events.at(-1).status).toBe('FAILED');
    expect(result.current.events.transport).toBe('REST');
  });

  it('keeps an actually empty terminal event history empty after REST completes', async () => {
    const terminalJob = { jobId: 'job-empty', status: 'CANCELLED' };
    const client = { get: vi.fn(async (path) => path.includes('/active-jobs')
      ? { data: [] }
      : path.includes('/recent-jobs')
        ? { data: [terminalJob] }
        : { data: { events: [], nextSequence: 0, hasMore: false } }) };
    useApiClient.mockReturnValue(client);
    useJobEvents.mockReturnValue({ terminal: false, events: [], reconnect: vi.fn() });

    const { result } = renderHook(() => useProjectJobs('41'));

    await waitFor(() => expect(client.get).toHaveBeenCalledWith('/api/v2/jobs/job-empty/events?after=0', expect.objectContaining({ signal: expect.any(AbortSignal) })));
    await waitFor(() => expect(result.current.events.loading).toBe(false));
    expect(result.current.events.transport).toBe('REST');
    expect(result.current.events.events).toEqual([]);
  });

  it('does not let a previous historical request overwrite a newly selected job', async () => {
    let releaseOld;
    const oldEvents = new Promise((resolve) => { releaseOld = resolve; });
    const jobs = [{ jobId: 'job-old', status: 'FAILED' }, { jobId: 'job-new', status: 'COMPLETED' }];
    const client = { get: vi.fn(async (path) => {
      if (path.includes('/active-jobs')) return { data: [] };
      if (path.includes('/recent-jobs')) return { data: jobs };
      if (path.includes('/job-old/')) return oldEvents;
      if (path.includes('/job-new/')) return { data: { events: [{ sequence: 8, status: 'COMPLETED' }], nextSequence: 8, hasMore: false } };
      throw new Error(`unexpected path: ${path}`);
    }) };
    useApiClient.mockReturnValue(client);
    useJobEvents.mockReturnValue({ terminal: false, events: [], reconnect: vi.fn() });
    const { result } = renderHook(() => useProjectJobs('41'));
    await waitFor(() => expect(result.current.selectedJobId).toBe('job-old'));

    act(() => result.current.selectJob('job-new'));
    await waitFor(() => expect(result.current.events.events[0]?.sequence).toBe(8));
    releaseOld({ data: { events: [{ sequence: 1, status: 'FAILED' }], nextSequence: 1, hasMore: false } });
    await act(async () => Promise.resolve());

    expect(result.current.selectedJobId).toBe('job-new');
    expect(result.current.events.events[0]?.sequence).toBe(8);
  });
});
