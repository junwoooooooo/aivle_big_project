const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}/planning/current`;
export const createFinalizedPlanningApi = (client) => Object.freeze({
  current: async (projectId, options) => (await client.get(base(projectId), options)).data,
});
