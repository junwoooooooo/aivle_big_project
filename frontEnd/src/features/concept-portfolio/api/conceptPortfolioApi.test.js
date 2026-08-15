import { describe, expect, it, vi } from 'vitest';
import { createConceptPortfolioApi } from './conceptPortfolioApi.js';

describe('Concept Portfolio retry API', () => {
  it('uses existing continuation and Delta retry resources', async () => {
    const client = { post: vi.fn().mockResolvedValue({ data: {} }) };
    const api = createConceptPortfolioApi(client);
    await api.retryContinuation('41', 'run-1', 'input-1', { idempotencyKey: 'retry-1' });
    await api.retryDelta('41', 17, { idempotencyKey: 'retry-2' });
    expect(client.post).toHaveBeenNthCalledWith(1,
      '/api/v3/projects/41/concept-portfolio-runs/run-1/input-requests/input-1/retry',
      { idempotencyKey: 'retry-1' }, undefined);
    expect(client.post).toHaveBeenNthCalledWith(2,
      '/api/v3/projects/41/concept-portfolio-selections/17/delta-legal/retry',
      { idempotencyKey: 'retry-2' }, undefined);
  });
});
