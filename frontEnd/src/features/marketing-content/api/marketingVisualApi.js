const root = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}/marketing-visual-runs`;
const artifactRoot = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}/evidence-artifacts`;
const unwrap = (response) => response.data;

export function createMarketingVisualApi(client) {
  return Object.freeze({
    uploadSource: async (projectId, file, options) => {
      const form = new FormData(); form.append('file', file);
      return unwrap(await client.upload(artifactRoot(projectId), form, options));
    },
    create: async (projectId, request, key, options = {}) => unwrap(await client.post(root(projectId), request, {
      ...options, headers: { ...options.headers, 'Idempotency-Key': key, 'X-Correlation-Id': key },
    })),
    get: async (projectId, taskRunId, options) => unwrap(await client.get(`${root(projectId)}/${encodeURIComponent(taskRunId)}`, options)),
    current: async (projectId, contentId, options) => unwrap(await client.get(`${root(projectId)}/current?marketingContentId=${encodeURIComponent(contentId)}`, options)),
    retry: async (projectId, taskRunId, key, options = {}) => unwrap(await client.post(`${root(projectId)}/${encodeURIComponent(taskRunId)}/retry`, {}, {
      ...options, headers: { ...options.headers, 'Idempotency-Key': key, 'X-Correlation-Id': key },
    })),
    cancel: async (projectId, taskRunId, options) => unwrap(await client.post(`${root(projectId)}/${encodeURIComponent(taskRunId)}/cancel`, {}, options)),
    download: (path, options) => client.download(path, options),
  });
}
