const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}`;

export function createMarketIntegrationApi(client) {
  return Object.freeze({
    currentMarketSeed: (projectId, options) => client.get(`${base(projectId)}/market-analysis-seed-snapshots/current`, options),
    runs: (projectId, options) => client.get(`${base(projectId)}/module-runs`, options),
    run: (projectId, runId, options) => client.get(`${base(projectId)}/module-runs/${encodeURIComponent(runId)}`, options),
    result: (projectId, options) => client.get(`${base(projectId)}/market-result`, options),
    prepare: (projectId, inputSnapshotId, options) => client.post(`${base(projectId)}/module-handoffs`, {
      module: 'MARKET_ANALYSIS', inputSnapshotId, requestedOperation: 'START_MARKET_ANALYSIS',
    }, options),
  });
}
