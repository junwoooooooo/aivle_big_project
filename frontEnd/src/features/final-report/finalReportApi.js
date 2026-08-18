export function createFinalReportApi(client) {
  const root = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}/final-report`;
  return Object.freeze({
    status: async (projectId, options) => (await client.get(`${root(projectId)}/status`, options)).data,
    current: async (projectId, options) => (await client.get(root(projectId), options)).data,
    generate: async (projectId, idempotencyKey, includedOptionalSources = [], options = {}) => (await client.post(
      `${root(projectId)}/generate`, { includedOptionalSources },
      { ...options, headers: { ...options.headers, 'Idempotency-Key': idempotencyKey } },
    )).data,
    currentReview: async (projectId, options) => (await client.get(`${root(projectId)}/review`, options)).data,
    review: async (projectId, snapshotId, idempotencyKey, options = {}) => (await client.post(
      `${root(projectId)}/${encodeURIComponent(snapshotId)}/review`, {},
      { ...options, headers: { ...options.headers, 'Idempotency-Key': idempotencyKey } },
    )).data,
    download: async (projectId, snapshotId, format, includeReview = false, options = {}) => client.download(
      `${root(projectId)}/${encodeURIComponent(snapshotId)}/${format}${includeReview ? '?includeReview=true' : ''}`,
      options,
    ),
  });
}
