import { renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApiClientProvider } from '../../../shared/api/ApiClientProvider.jsx';
import useMarketIntegration from './useMarketIntegration.js';

describe('Market live invalidation', () => {
  it('re-reads selection, V2 seed, runs and result after liveRevision changes', async () => {
    const client = { get: vi.fn((path) => {
      if (path.endsWith('/concept-portfolio-selections/current')) return Promise.resolve({ data: { selectionId: 17 } });
      if (path.endsWith('/market-seed/current')) return Promise.resolve({ data: { snapshotId: 'seed-v2' } });
      if (path.endsWith('/module-runs')) return Promise.resolve({ data: { runs: [] } });
      if (path.endsWith('/market-result')) return Promise.resolve({ data: null });
      return Promise.resolve({ data: null });
    }), post: vi.fn() };
    const wrapper = ({ children }) => <ApiClientProvider client={client}>{children}</ApiClientProvider>;
    const { rerender } = renderHook(({ revision }) => useMarketIntegration('41', revision), { wrapper, initialProps: { revision: 0 } });
    await waitFor(() => expect(client.get.mock.calls.filter(([path]) => path.endsWith('/concept-portfolio-selections/current'))).toHaveLength(1));
    rerender({ revision: 1 });
    await waitFor(() => expect(client.get.mock.calls.filter(([path]) => path.endsWith('/concept-portfolio-selections/current'))).toHaveLength(2));
    for (const suffix of ['/market-seed/current', '/module-runs', '/market-result']) {
      expect(client.get.mock.calls.filter(([path]) => path.endsWith(suffix))).toHaveLength(2);
    }
  });
});
