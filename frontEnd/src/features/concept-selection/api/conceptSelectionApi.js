const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}`;

export function createConceptSelectionApi(client) {
  return Object.freeze({
    currentRun: (projectId, options) => client.get(`${base(projectId)}/concept-factory-runs/current`, options),
    concepts: (projectId, options) => client.get(`${base(projectId)}/concepts`, options),
    currentSelection: (projectId, options) => client.get(`${base(projectId)}/concept-selections/current`, options),
    select: (projectId, conceptId, selectionReason, options) => client.post(`${base(projectId)}/concept-selections`, { conceptId, selectionReason }, options),
  });
}
