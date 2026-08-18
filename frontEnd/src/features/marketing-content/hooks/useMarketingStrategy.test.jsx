import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import useMarketingStrategy from './useMarketingStrategy.js';

vi.mock('../../../shared/api/ApiClientProvider.jsx', () => ({ useApiClient: vi.fn() }));

describe('useMarketingStrategy', () => {
  beforeEach(() => vi.clearAllMocks());
  it('현재 전략을 읽고 생성 요청 후 current를 다시 확인한다', async () => {
    const ready = { reportId: null, status: 'NOT_STARTED', ready: true, stale: false, result: null };
    const current = { reportId: 'a'.repeat(64), status: 'SUCCEEDED', ready: true, stale: false,
      result: { executiveSummary: '전략 준비됨' } };
    const client = { get: vi.fn().mockResolvedValueOnce({ data: ready }).mockResolvedValueOnce({ data: current }),
      post: vi.fn().mockResolvedValue({ data: { taskRunId: 'job-1' } }), download: vi.fn() };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useMarketingStrategy('7'));
    await waitFor(() => expect(result.current.loading).toBe(false));
    await act(async () => { await result.current.generate(); });
    expect(client.post).toHaveBeenCalledWith('/api/v3/projects/7/marketing-strategy/generate', {},
      expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }) }));
    expect(result.current.current).toBe(true);
  });

  it('재생성 요청 직후 기존 결과를 유지하며 active 상태를 즉시 표시한다', async () => {
    let release;
    const pending = new Promise((resolve) => { release = resolve; });
    const existing = { reportId: 'old', status: 'SUCCEEDED', ready: true, stale: false,
      result: { executiveSummary: '기존 전략' } };
    const client = { get: vi.fn().mockResolvedValue({ data: existing }),
      post: vi.fn(() => pending), download: vi.fn() };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useMarketingStrategy('7'));
    await waitFor(() => expect(result.current.loading).toBe(false));
    act(() => { void result.current.generate(); });
    expect(result.current.active).toBe(true);
    expect(result.current.view.result.executiveSummary).toBe('기존 전략');
    await act(async () => { release({ data: { taskRunId: 'job-2' } }); await pending; });
  });
});
