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
});
