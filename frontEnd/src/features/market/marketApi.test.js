import { describe, expect, it, vi } from 'vitest';
import { createMarketApi } from './marketApi.js';

describe('market recollect API', () => {
  it('binds the current Market version and all donor recollect options', async () => {
    const client = { post: vi.fn().mockResolvedValue({ data: { taskRunId: 'task-b' } }) };
    const api = createMarketApi(client, '41');

    await api.recollectMarketResearch(73, {
      asOf: '2026-08-13', slots: 'S1,S5', from: 'extract', slotsFrom: 'current',
    });

    expect(client.post).toHaveBeenCalledWith('/api/v3/projects/41/market-research/recollect', {
      sourceMarketResearchVersionId: 73,
      asOf: '2026-08-13', slots: 'S1,S5', from: 'extract', slotsFrom: 'current',
    }, expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }) }));
  });
});
