const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}`;

export function createConceptFactoryApi(client) {
  return Object.freeze({
    current: (projectId, options) => client.get(`${base(projectId)}/concept-factory-runs/current`, options),
    slots: (projectId, runId, options) => client.get(`${base(projectId)}/concept-factory-runs/${encodeURIComponent(runId)}/slots`, options),
    concepts: (projectId, options) => client.get(`${base(projectId)}/concepts`, options),
    create: (projectId, snapshotId, options) => client.post(`${base(projectId)}/concept-factory-runs`, { ideaBriefSnapshotId: snapshotId }, options),
    retry: (projectId, runId, idempotencyKey, options) => client.post(`${base(projectId)}/concept-factory-runs/${encodeURIComponent(runId)}/retry`, { idempotencyKey }, options),
    ideaBrief: (projectId, options) => client.get(`${base(projectId)}/idea-brief`, options),
  });
}
