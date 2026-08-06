import { describe, expect, it, vi } from 'vitest';

import { createValidationApi } from './validationApi.js';

describe('validationApi', () => {
  it('uses project-scoped panel and market routes', async () => {
    const client = {
      get: vi.fn().mockResolvedValue({ data: [] }),
      post: vi.fn().mockResolvedValue({ data: {} }),
      patch: vi.fn().mockResolvedValue({ data: {} }),
      delete: vi.fn().mockResolvedValue(null),
    };
    const api = createValidationApi(client);

    await api.interviews('7');
    await api.runInterview('7', '11');
    await api.marketResponses('7');
    await api.runMarketResponse('7', '13');

    expect(client.get).toHaveBeenCalledWith('/projects/7/panel-interviews', undefined);
    expect(client.post).toHaveBeenCalledWith('/projects/7/panel-interviews/11/run', undefined, undefined);
    expect(client.get).toHaveBeenCalledWith('/projects/7/market-responses', undefined);
    expect(client.post).toHaveBeenCalledWith('/projects/7/market-responses/13/run', undefined, undefined);
  });
});
