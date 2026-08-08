const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}`;

export function createConceptSelectionApi(client) {
  return Object.freeze({
    currentRun: (projectId, options) => client.get(`${base(projectId)}/concept-factory-runs/current`, options),
    concepts: (projectId, options) => client.get(`${base(projectId)}/concepts`, options),
    currentSelection: (projectId, options) => client.get(`${base(projectId)}/concept-selections/current`, options),
    select: (projectId, conceptId, selectionReason, options) => client.post(`${base(projectId)}/concept-selections`, { conceptId, selectionReason }, options),
    decideHypothesis: (projectId, hypothesisType, body, options) => client.post(
      `${base(projectId)}/concept-selections/current/hypotheses/${encodeURIComponent(hypothesisType)}/actions`, body, options),
    currentMarketSeed: (projectId, options) => client.get(`${base(projectId)}/market-analysis-seed-snapshots/current`, options),
    finalizeMarketSeed: (projectId, options) => client.post(`${base(projectId)}/market-analysis-seed-snapshots/finalize`, {}, options),
  });
}
