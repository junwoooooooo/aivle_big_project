import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import useMarketingGeneration from './useMarketingGeneration.js';
import useMarketingContent from './useMarketingContent.js';

vi.mock('../../../shared/api/ApiClientProvider.jsx', () => ({ useApiClient: vi.fn() }));
vi.mock('./useMarketingGeneration.js', () => ({ default: vi.fn() }));

describe('useMarketingContent refresh recovery', () => {
  beforeEach(() => vi.clearAllMocks());

  it('queries running content detail and restores its active job on page refresh', async () => {
    const restore = vi.fn();
    useMarketingGeneration.mockReturnValue({
      active: false, status: 'IDLE', activeJobId: null, contentId: null, error: null,
      jobEvents: { events: [] }, restore, create: vi.fn(), regenerate: vi.fn(),
    });
    const detail = { content: { contentId: 'content-1', status: 'RUNNING', activeJobId: 'job-1' }, revisions: [] };
    const client = {
      get: vi.fn(async (path) => {
        if (path.endsWith('/marketing-contents')) return { data: { contents: [detail.content] } };
        if (path.endsWith('/marketing-source-snapshots/current')) return { data: { snapshotId: 'source-1', snapshot: {} } };
        if (path.endsWith('/marketing-contents/content-1')) return { data: detail };
        throw new Error(`unexpected path ${path}`);
      }),
      post: vi.fn(), patch: vi.fn(),
    };
    useApiClient.mockReturnValue(client);

    const { result } = renderHook(() => useMarketingContent('7'));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(client.get).toHaveBeenCalledWith('/api/v3/projects/7/marketing-contents/content-1', undefined);
    expect(restore).toHaveBeenCalledWith(detail);
    expect(result.current.selected).toEqual(detail);
  });
});
