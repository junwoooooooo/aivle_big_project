const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}/planning`;
export function createPlanningApi(client) { return Object.freeze({
  current: (projectId, options) => client.get(`${base(projectId)}/current`, options),
  proposals: (projectId, options) => client.get(`${base(projectId)}/change-proposals`, options),
  decide: (projectId, proposalId, action, modifiedAfter, options) => client.post(`${base(projectId)}/change-proposals/${encodeURIComponent(proposalId)}/decisions`, { action, ...(modifiedAfter == null ? {} : { modifiedAfter }) }, options),
  finalize: (projectId, options) => client.post(`${base(projectId)}/finalize`, {}, options),
}); }
