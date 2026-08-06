import { describe, expect, it, vi } from 'vitest';
import { createFeasibilityApi } from './feasibilityApi.js';

describe('feasibilityApi', () => {
  it('uses the project-scoped assessment contract', async () => {
    const client = {
      post: vi.fn(async () => ({ data: { jobId: 9 } })),
      get: vi.fn(async () => ({ data: { assessmentId: 4 } })),
    };
    const api = createFeasibilityApi(client);
    await api.start('12');
    await api.latest('12');
    expect(client.post).toHaveBeenCalledWith(
      '/projects/12/feasibility-assessments', undefined, undefined,
    );
    expect(client.get).toHaveBeenCalledWith(
      '/projects/12/feasibility-assessments/latest', undefined,
    );
  });

  it('recovers only FEASIBILITY_ANALYSIS jobs', async () => {
    const client = { get: vi.fn(async () => ({ data: { jobId: 3 } })) };
    await createFeasibilityApi(client).latestJob('7');
    expect(client.get).toHaveBeenCalledWith(
      '/projects/7/jobs/latest?jobType=FEASIBILITY_ANALYSIS', undefined,
    );
  });

  it('loads plan and legal inputs from APIs instead of navigation state', async () => {
    const client = { get: vi.fn(async () => ({ data: {} })) };
    const api = createFeasibilityApi(client);
    await api.latestPlan('8');
    await api.latestLegalReview('8');
    expect(client.get.mock.calls.map(([path]) => path)).toEqual([
      '/projects/8/structured-plans/latest',
      '/projects/8/legal-reviews/latest',
    ]);
  });
});
