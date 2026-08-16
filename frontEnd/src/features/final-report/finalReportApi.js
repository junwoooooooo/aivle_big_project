export function createFinalReportApi(client) {
  const root = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}/final-report`;
  return Object.freeze({
    current: async (projectId, options) => (await client.get(root(projectId), options)).data,
    generate: async (projectId, idempotencyKey, options = {}) => (await client.post(
      `${root(projectId)}/generate`, {},
      { ...options, headers: { ...options.headers, 'Idempotency-Key': idempotencyKey } },
    )).data,
  });
}
