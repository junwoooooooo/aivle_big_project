import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { usePersonas } from './usePersonas.js';

vi.mock('../../../shared/api/ApiClientProvider.jsx', () => ({
  useApiClient: vi.fn(),
}));

describe('usePersonas', () => {
  beforeEach(() => vi.clearAllMocks());

  it('accepts a NEEDS_VALIDATION feasibility result as a valid persona input', async () => {
    const notFound = Object.assign(new Error('not found'), { status: 404 });
    const client = {
      get: vi.fn()
        .mockResolvedValueOnce({ data: [] })
        .mockRejectedValueOnce(notFound)
        .mockRejectedValueOnce(notFound)
        .mockResolvedValueOnce({
          data: {
            assessmentId: 8,
            structuredPlanId: 3,
            status: 'NEEDS_VALIDATION',
          },
        }),
    };
    useApiClient.mockReturnValue(client);

    const { result } = renderHook(() => usePersonas('10'));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(result.current.feasibility.assessmentId).toBe(8);
  });

  it('retries one initial request failure before exposing an error', async () => {
    const networkError = Object.assign(new Error('temporary network error'), { status: 503 });
    const recommendation = { summary: '추천 결과', items: [], hypotheses: [], validationPlans: [] };
    const client = {
      get: vi.fn()
        .mockRejectedValueOnce(networkError)
        .mockResolvedValueOnce({ data: [] })
        .mockResolvedValueOnce({ data: recommendation }),
    };
    useApiClient.mockReturnValue(client);

    const { result } = renderHook(() => usePersonas('10'));

    expect(result.current.status).toBe('loading');
    await waitFor(() => expect(result.current.status).toBe('result'), { timeout: 2500 });
    expect(client.get).toHaveBeenCalledTimes(3);
    expect(result.current.error).toBeNull();
  });
});
