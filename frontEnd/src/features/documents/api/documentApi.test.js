import { describe, expect, it, vi } from 'vitest';

import { createDocumentApi } from './documentApi.js';

describe('document api', () => {
  it('uploads multipart with a caller-owned idempotency key', async () => {
    const client = {
      upload: vi.fn(async () => ({ data: { jobId: 7 } })),
    };
    const file = new File(['docx'], 'plan.docx');

    await createDocumentApi(client).upload('3', file, 'stable-key');

    const [path, body, options] = client.upload.mock.calls[0];
    expect(path).toBe('/projects/3/documents');
    expect(body).toBeInstanceOf(FormData);
    expect(body.get('file')).toBe(file);
    expect(body.get('documentType')).toBe('BUSINESS_PLAN');
    expect(options.headers['Idempotency-Key']).toBe('stable-key');
  });

  it('uses the owner-scoped recovery and resource endpoints', async () => {
    const client = { get: vi.fn(async () => ({ data: {} })) };
    const api = createDocumentApi(client);
    await api.getLatestJob('a/b');
    await api.getJob(9);
    await api.getVersion(2, 4);

    expect(client.get).toHaveBeenNthCalledWith(
      1,
      '/projects/a%2Fb/jobs/latest?jobType=DOCUMENT_PARSE',
      undefined,
    );
    expect(client.get).toHaveBeenNthCalledWith(2, '/jobs/9', undefined);
    expect(client.get).toHaveBeenNthCalledWith(
      3,
      '/documents/2/versions/4',
      undefined,
    );
  });
});
