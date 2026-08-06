const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}`;

export function createConceptSelectionApi(client) {
  return Object.freeze({
    currentRun: (projectId, options) => client.get(`${base(projectId)}/concept-factory-runs/current`, options),
    concepts: (projectId, options) => client.get(`${base(projectId)}/concepts`, options),
  });
}
