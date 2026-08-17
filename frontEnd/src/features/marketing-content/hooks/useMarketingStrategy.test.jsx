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
});
