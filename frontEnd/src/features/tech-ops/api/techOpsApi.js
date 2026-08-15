const root = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}`;
const tech = (projectId) => `${root(projectId)}/tech-ops`;

export const createTechOpsApi = (client) => Object.freeze({
  preparation: async (projectId, options) => (await client.get(`${tech(projectId)}/preparation`, options)).data,
  initialize: async (projectId, options) => (await client.post(`${tech(projectId)}/preparation/initialize`, {}, options)).data,
  patchFacts: async (projectId, values, options) => (await client.patch(`${tech(projectId)}/preparation`, { values }, options)).data,
  decide: async (projectId, fieldKey, body, options) => (await client.post(`${tech(projectId)}/preparation/proposals/${encodeURIComponent(fieldKey)}/decision`, body, options)).data,
  retryProposals: async (projectId, options) => (await client.post(`${tech(projectId)}/preparation/proposals/retry`, {}, options)).data,
  uploadEvidenceArtifact: async (projectId, file, options) => {
    const form = new FormData(); form.append('file', file);
    return (await client.upload(`${root(projectId)}/evidence-artifacts`, form, options)).data;
  },
  addEvidence: async (projectId, body, options) => (await client.post(`${tech(projectId)}/preparation/evidence`, body, options)).data,
  removeEvidence: async (projectId, evidenceId, options) => (await client.delete(`${tech(projectId)}/preparation/evidence/${encodeURIComponent(evidenceId)}`, options)).data,
  finalize: async (projectId, options) => (await client.post(`${tech(projectId)}/input-snapshots/finalize`, {}, options)).data,
  currentSnapshot: async (projectId, options) => (await client.get(`${tech(projectId)}/input-snapshots/current`, options)).data,
  startAdvisory: async (projectId, options) => (await client.post(`${tech(projectId)}/advisory-runs`, {}, options)).data,
  currentAdvisory: async (projectId, options) => (await client.get(`${tech(projectId)}/advisory/current`, options)).data,
  handoff: async (projectId, snapshotId, options) => (await client.post(`${root(projectId)}/module-handoffs`,
    { module: 'TECH_OPS', inputSnapshotId: snapshotId, requestedOperation: 'START_TECH_OPS_ANALYSIS' }, options)).data,
  runs: async (projectId, options) => (await client.get(`${root(projectId)}/module-runs`, options)).data,
});
