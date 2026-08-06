import { describe, expect, it, vi } from 'vitest';
import { createPersonaApi } from './personaApi.js';

describe('personaApi', () => {
  it('uses canonical persona contracts and encoded project IDs', async () => {
    const client = {
      get: vi.fn(async () => ({ data: [] })),
      post: vi.fn(async () => ({ data: { jobId: 3 } })),
    };
    const api = createPersonaApi(client);
    await api.catalog();
    await api.start('a/b');
    await api.latest('a/b');
    expect(client.get.mock.calls.map(([path]) => path)).toEqual([
      '/personas/catalog',
      '/projects/a%2Fb/persona-recommendations/latest',
    ]);
    expect(client.post).toHaveBeenCalledWith(
      '/projects/a%2Fb/persona-recommendations', undefined, undefined,
    );
  });

  it('recovers only PERSONA_RECOMMENDATION jobs', async () => {
    const client = { get: vi.fn(async () => ({ data: {} })) };
    await createPersonaApi(client).latestJob('7');
    expect(client.get).toHaveBeenCalledWith(
      '/projects/7/jobs/latest?jobType=PERSONA_RECOMMENDATION', undefined,
    );
  });
});
