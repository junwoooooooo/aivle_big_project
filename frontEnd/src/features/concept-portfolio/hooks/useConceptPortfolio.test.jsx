import { renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApiClientProvider } from '../../../shared/api/ApiClientProvider.jsx';
import { startNewConceptPortfolioRun, useConceptPortfolio } from './useConceptPortfolio.js';

describe('useConceptPortfolio live invalidation', () => {
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
});
