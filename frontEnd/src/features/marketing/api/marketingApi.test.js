import { describe, expect, it, vi } from 'vitest';

import { createMarketingApi } from './marketingApi.js';

describe('marketingApi', () => {
  it('uses the project-scoped content and version routes', async () => {
    const client = {
      get: vi.fn().mockResolvedValue({ data: [] }),
      post: vi.fn().mockResolvedValue({ data: { id: 1 } }),
      patch: vi.fn().mockResolvedValue({ data: { id: 1 } }),
      delete: vi.fn().mockResolvedValue(null),
      upload: vi.fn().mockResolvedValue({ data: { jobId: 9 } }),
    };
    const api = createMarketingApi(client);

    await api.list('7');
    await api.create('7', { title: '시안' });
    await api.update('7', '3', { title: '수정' });
    await api.alternateDraft('7', '3', 2);
    await api.createVersion('7', '3', { headline: '헤드라인' });
    await api.refreshSource('7', '3', {
      panelInterviewId: 21,
      marketResponseId: 31,
      generateDraft: false,
    });
    const image = new File(['image'], 'source.png', { type: 'image/png' });
    await api.generate('7', '3', image, {
      sourceVersionId: 11,
      idempotencyKey: 'marketing-key',
    });
    await api.rerun('7', '3', 9, 'rerun-key');
    await api.job(9);

    expect(client.get).toHaveBeenCalledWith('/projects/7/marketing-contents', undefined);
    expect(client.post).toHaveBeenCalledWith('/projects/7/marketing-contents', { title: '시안' }, undefined);
    expect(client.patch).toHaveBeenCalledWith('/projects/7/marketing-contents/3', { title: '수정' }, undefined);
    expect(client.post).toHaveBeenCalledWith('/projects/7/marketing-contents/3/draft-copy?alternative=2', undefined, undefined);
    expect(client.post).toHaveBeenCalledWith('/projects/7/marketing-contents/3/versions', { headline: '헤드라인' }, undefined);
    expect(client.post).toHaveBeenCalledWith(
      '/projects/7/marketing-contents/3/source-refresh',
      { panelInterviewId: 21, marketResponseId: 31, generateDraft: false },
      undefined,
    );
    expect(client.upload).toHaveBeenCalledWith(
      '/projects/7/marketing-contents/3/generate?sourceVersionId=11',
      expect.any(FormData),
      { headers: { 'Idempotency-Key': 'marketing-key' } },
    );
    expect(client.post).toHaveBeenCalledWith(
      '/projects/7/marketing-contents/3/rerun',
      { originalJobId: 9 },
      { headers: { 'Idempotency-Key': 'rerun-key' } },
    );
    expect(client.get).toHaveBeenCalledWith('/jobs/9', undefined);
  });
});
