import { describe, expect, it, vi } from 'vitest';

import { createStructuredPlanApi } from './structuredPlanApi.js';

describe('structured plan api', () => {
  it('loads the latest project plan', async () => {
    const client = { get: vi.fn(async () => ({ data: { planId: 3 } })) };
    const result = await createStructuredPlanApi(client).getLatest('project 1');

    expect(client.get).toHaveBeenCalledWith(
      '/projects/project%201/structured-plans/latest',
      undefined,
    );
    expect(result.planId).toBe(3);
  });

  it('patches one missing field with the caller supplied lock version', async () => {
    const client = { patch: vi.fn(async () => ({ data: { fieldId: 4, version: 2 } })) };
    const payload = { status: 'FILLED', value: '보완값', version: 1 };
    const result = await createStructuredPlanApi(client)
      .updateMissingField(1, 2, 4, payload);

    expect(client.patch).toHaveBeenCalledWith(
      '/projects/1/structured-plans/2/missing-fields/4',
      payload,
      undefined,
    );
    expect(result.version).toBe(2);
  });

  it('confirms with the plan lock version rather than versionNumber', async () => {
    const client = { post: vi.fn(async () => ({ data: { status: 'CONFIRMED' } })) };
    const result = await createStructuredPlanApi(client).confirm(1, 2, { version: 7 });

    expect(client.post).toHaveBeenCalledWith(
      '/projects/1/structured-plans/2/confirm',
      { version: 7 },
      undefined,
    );
    expect(result.status).toBe('CONFIRMED');
  });
});
