import { describe, expect, it, vi } from 'vitest';
import { createLegalReviewApi } from './legalReviewApi.js';

describe('legalReviewApi', () => {
  it('starts a review from the project-scoped endpoint', async () => {
    const client = { post: vi.fn().mockResolvedValue({ data: { jobId: 9 } }) };
    await expect(createLegalReviewApi(client).start('a/b')).resolves.toEqual({ jobId: 9 });
    expect(client.post).toHaveBeenCalledWith('/projects/a%2Fb/legal-reviews', undefined, undefined);
  });

  it('loads the latest owner-visible review', async () => {
    const client = { get: vi.fn().mockResolvedValue({ data: { legalReviewId: 3 } }) };
    await createLegalReviewApi(client).latest('12');
    expect(client.get).toHaveBeenCalledWith('/projects/12/legal-reviews/latest', undefined);
  });

  it('recovers only the LEGAL_REVIEW job type', async () => {
    const client = { get: vi.fn().mockResolvedValue({ data: { jobId: 4 } }) };
    await createLegalReviewApi(client).latestJob('12');
    expect(client.get).toHaveBeenCalledWith(
      '/projects/12/jobs/latest?jobType=LEGAL_REVIEW',
      undefined,
    );
  });
});
