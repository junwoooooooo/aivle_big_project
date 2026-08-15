const root = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}/marketing-contents`;
const unwrap = (response) => response.data;

export function createMarketingContentApi(client) {
  return Object.freeze({
    list: async (projectId, options) => unwrap(await client.get(root(projectId), options)),
    detail: async (projectId, contentId, options) => unwrap(await client.get(`${root(projectId)}/${encodeURIComponent(contentId)}`, options)),
    create: async (projectId, request, idempotencyKey, options = {}) => unwrap(await client.post(root(projectId), request, {
      ...options, headers: { ...options.headers, 'Idempotency-Key': idempotencyKey, 'X-Correlation-Id': idempotencyKey },
    })),
    update: async (projectId, contentId, request, options) => unwrap(await client.patch(`${root(projectId)}/${encodeURIComponent(contentId)}`, request, options)),
    regenerate: async (projectId, contentId, idempotencyKey, options = {}) => unwrap(await client.post(`${root(projectId)}/${encodeURIComponent(contentId)}/regenerate`, {}, {
      ...options, headers: { ...options.headers, 'Idempotency-Key': idempotencyKey, 'X-Correlation-Id': idempotencyKey },
    })),
    finalize: async (projectId, contentId, options) => unwrap(await client.post(`${root(projectId)}/${encodeURIComponent(contentId)}/finalize`, {}, options)),
    uploadReference: async (projectId, file, options) => {
      const form = new FormData(); form.append('file', file);
      return unwrap(await client.upload(`/api/v3/projects/${encodeURIComponent(projectId)}/evidence-artifacts`, form, options));
    },
  });
}
