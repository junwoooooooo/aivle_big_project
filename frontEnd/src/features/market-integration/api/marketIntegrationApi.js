const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}`;

export function createMarketIntegrationApi(client) {
  return Object.freeze({
    currentSelection: (projectId, options) => client.get(`${base(projectId)}/concept-selections/current`, options),
    runs: (projectId, options) => client.get(`${base(projectId)}/module-runs`, options),
    run: (projectId, runId, options) => client.get(`${base(projectId)}/module-runs/${encodeURIComponent(runId)}`, options),
    result: (projectId, options) => client.get(`${base(projectId)}/market-result`, options),
    decide: (projectId, proposalId, action, modifiedAfter, options) => client.post(
      `${base(projectId)}/planning-change-proposals/${encodeURIComponent(proposalId)}/decision`,
      { action, ...(modifiedAfter == null ? {} : { modifiedAfter }) }, options,
    ),
    prepare: (projectId, inputSnapshotId, options) => client.post(`${base(projectId)}/module-handoffs`, {
      module: 'MARKET_ANALYSIS', inputSnapshotId, requestedOperation: 'START_MARKET_ANALYSIS',
    }, options),
  });
}
