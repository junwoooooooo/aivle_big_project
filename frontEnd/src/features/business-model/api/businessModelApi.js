const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}`;

export function createBusinessModelApi(client) {
  return Object.freeze({
    currentMarketSeed: (projectId, options) => client.get(
      `${base(projectId)}/market-analysis-seed-snapshots/current`, options,
    ),
    runs: (projectId, options) => client.get(`${base(projectId)}/module-runs`, options),
    prepare: (projectId, inputSnapshotId, options) => client.post(
      `${base(projectId)}/module-handoffs`,
      { module: 'BUSINESS_MODEL', inputSnapshotId, requestedOperation: 'START_BUSINESS_MODEL_ANALYSIS' },
      options,
    ),
  });
}
