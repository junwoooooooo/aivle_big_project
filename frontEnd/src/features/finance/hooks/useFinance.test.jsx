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
    expect(useJobEvents.mock.calls.some(([taskId]) => taskId === 'task-1')).toBe(true);
    const before = client.get.mock.calls.filter(([path]) => path.endsWith('/finance/preparation')).length;

    terminal = true;
    rerender();

    await waitFor(() => expect(client.get.mock.calls.filter(([path]) => path.endsWith('/finance/preparation')).length)
      .toBeGreaterThan(before));
  });

  it('그룹 추천은 여러 field TaskRun을 만들고 마지막에 한 번 canonical refresh한다', async () => {
    const preparation = { preparationId: 'prep-1', revision: 1, assistance: {} };
    const client = { get: vi.fn(async (path) => {
      if (path.endsWith('/finance/preparation')) return { data: preparation };
      if (path.endsWith('/module-runs')) return { data: { runs: [] } };
      throw { status: 404 };
    }), post: vi.fn(async () => ({ data: { status: 'QUEUED' } })), patch: vi.fn() };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useFinance('7'));
    await waitFor(() => expect(result.current.loading).toBe(false));
    const before = client.get.mock.calls.filter(([path]) => path.endsWith('/finance/preparation')).length;

    await act(() => result.current.generateEstimates(['totalMarketingCost', 'newCustomerCount']));

    expect(client.post.mock.calls.filter(([path]) => path.endsWith('/generate'))).toHaveLength(2);
    expect(client.get.mock.calls.filter(([path]) => path.endsWith('/finance/preparation')).length).toBe(before + 1);
  });

  it('그룹 추천이 부분 실패해도 canonical 상태를 다시 읽고 안전한 오류를 보존한다', async () => {
    const preparation = { preparationId: 'prep-1', revision: 1, assistance: {} };
    const failure = { status: 409, message: '이미 실행 중' };
    const client = { get: vi.fn(async (path) => {
      if (path.endsWith('/finance/preparation')) return { data: preparation };
      if (path.endsWith('/module-runs')) return { data: { runs: [] } };
      throw { status: 404 };
    }), post: vi.fn(async (path) => {
      if (path.includes('newCustomerCount')) throw failure;
      return { data: { status: 'QUEUED' } };
    }), patch: vi.fn() };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useFinance('7'));
    await waitFor(() => expect(result.current.loading).toBe(false));
    const before = client.get.mock.calls.filter(([path]) => path.endsWith('/finance/preparation')).length;

    let caught;
    await act(async () => {
      try { await result.current.generateEstimates(['totalMarketingCost', 'newCustomerCount']); }
      catch (error) { caught = error; }
    });

    expect(caught).toBe(failure);
    expect(client.get.mock.calls.filter(([path]) => path.endsWith('/finance/preparation')).length).toBe(before + 1);
    await waitFor(() => expect(result.current.error).toBe(failure));
  });

  it('분석 TaskRun을 SSE로 추적한다', async () => {
    const preparation = { preparationId: 'prep-1', revision: 1, inputSnapshotId: 'snapshot-1', assistance: {} };
    const client = { get: vi.fn(async (path) => {
      if (path.endsWith('/finance/preparation')) return { data: preparation };
      if (path.endsWith('/input-snapshots/current')) return { data: { snapshotId: 'snapshot-1' } };
      if (path.endsWith('/module-runs')) return { data: { runs: [] } };
      if (path.endsWith('/analysis/current')) return { data: { taskRunId: 'analysis-1', status: 'RUNNING' } };
      throw { status: 404 };
    }), post: vi.fn(), patch: vi.fn() };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useFinance('7'));
    await waitFor(() => expect(result.current.analysis?.taskRunId).toBe('analysis-1'));
    expect(useJobEvents.mock.calls.some(([taskId]) => taskId === 'analysis-1')).toBe(true);
  });

  it('reopen은 current snapshot 공식 API만 호출한다', async () => {
    const preparation = { preparationId: 'prep-1', revision: 1, inputSnapshotId: 'snapshot-1', assistance: {} };
    const client = { get: vi.fn(async (path) => {
      if (path.endsWith('/finance/preparation')) return { data: preparation };
      if (path.endsWith('/input-snapshots/current')) return { data: { snapshotId: 'snapshot-1' } };
      if (path.endsWith('/module-runs')) return { data: { runs: [] } };
      throw { status: 404 };
    }), post: vi.fn(async () => ({ data: {} })), patch: vi.fn() };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useFinance('7'));
    await waitFor(() => expect(result.current.loading).toBe(false));
    await act(() => result.current.reopen());
    expect(client.post.mock.calls.some(([path]) => path.endsWith('/input-snapshots/current/reopen'))).toBe(true);
  });
});
