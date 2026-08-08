const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}/marketing-source-snapshots`;

export const createMarketingSourceApi = (client) => Object.freeze({
  current: async (projectId, options) => (await client.get(`${base(projectId)}/current`, options)).data,
  finalize: async (projectId, options) => (await client.post(`${base(projectId)}/finalize`, {}, options)).data,
});
