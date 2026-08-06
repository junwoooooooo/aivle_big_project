import { describe, expect, it, vi } from 'vitest';
import { createFinancialApi } from './financialApi.js';

describe('financialApi', () => {
  it('uses the singular source endpoint and all seven collection/detail mutations', async () => {
    const client = {
      get: vi.fn().mockResolvedValue({ data: {} }),
      post: vi.fn().mockResolvedValue({ data: {} }),
      patch: vi.fn().mockResolvedValue({ data: {} }),
      delete: vi.fn().mockResolvedValue({ data: {} }),
    };
    const api = createFinancialApi(client);
    await api.source(10); await api.list(10); await api.detail(10, 20);
    await api.create(10, {}); await api.update(10, 20, {});
    await api.run(10, 20); await api.duplicate(10, 20); await api.remove(10, 20);
    expect(client.get).toHaveBeenCalledWith('/projects/10/financial-analysis/source', undefined);
    expect(client.get).toHaveBeenCalledWith('/projects/10/financial-analyses', undefined);
    expect(client.get).toHaveBeenCalledWith('/projects/10/financial-analyses/20', undefined);
    expect(client.patch).toHaveBeenCalledWith('/projects/10/financial-analyses/20', {}, undefined);
    expect(client.delete).toHaveBeenCalledWith('/projects/10/financial-analyses/20', undefined);
  });
});
