const root = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}`;
const finance = (projectId) => `${root(projectId)}/finance`;

export const createFinanceApi = (client) => Object.freeze({
  preparation: async (projectId, options) => (await client.get(`${finance(projectId)}/preparation`, options)).data,
  initialize: async (projectId, options) => (await client.post(`${finance(projectId)}/preparation/initialize`, {}, options)).data,
  patchFields: async (projectId, values, options) => (await client.patch(`${finance(projectId)}/preparation`, { values }, options)).data,
  generateEstimate: async (projectId, fieldKey, options) => (await client.post(`${finance(projectId)}/preparation/assistance/${encodeURIComponent(fieldKey)}/generate`, {}, options)).data,
  decideEstimate: async (projectId, fieldKey, payload, options) => (await client.post(`${finance(projectId)}/preparation/assistance/${encodeURIComponent(fieldKey)}/decision`, payload, options)).data,
  finalize: async (projectId, options) => (await client.post(`${finance(projectId)}/input-snapshots/finalize`, {}, options)).data,
  reopen: async (projectId, options) => (await client.post(`${finance(projectId)}/input-snapshots/current/reopen`, {}, options)).data,
  currentSnapshot: async (projectId, options) => (await client.get(`${finance(projectId)}/input-snapshots/current`, options)).data,
  analyze: async (projectId, options) => (await client.post(`${finance(projectId)}/analysis`, {}, options)).data,
  demo: async (projectId, options) => (await client.post(`${finance(projectId)}/demo`, {}, options)).data,
  handoff: async (projectId, snapshotId, options) => (await client.post(`${root(projectId)}/module-handoffs`,
    { module: 'FINANCIAL_ANALYSIS', inputSnapshotId: snapshotId, requestedOperation: 'START_FINANCIAL_ANALYSIS' }, options)).data,
  runs: async (projectId, options) => (await client.get(`${root(projectId)}/module-runs`, options)).data,
});
