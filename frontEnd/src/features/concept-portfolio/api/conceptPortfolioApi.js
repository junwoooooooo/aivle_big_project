const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}`;
const selectionBase = (projectId) => `${base(projectId)}/concept-portfolio-selections`;

export function createConceptPortfolioApi(client) {
  return Object.freeze({
    ideaBrief: (projectId, options) => client.get(`${base(projectId)}/idea-brief`, options),
    currentRun: (projectId, options) => client.get(`${base(projectId)}/concept-portfolio-runs/current`, options),
    createRun: (projectId, body, options) => client.post(`${base(projectId)}/concept-portfolio-runs`, body, options),
    concepts: (projectId, runId, options) => client.get(`${base(projectId)}/concept-portfolio-runs/${encodeURIComponent(runId)}/concepts`, options),
    inputRequests: (projectId, runId, options) => client.get(`${base(projectId)}/concept-portfolio-runs/${encodeURIComponent(runId)}/input-requests`, options),
    respond: (projectId, runId, requestId, body, options) => client.post(`${base(projectId)}/concept-portfolio-runs/${encodeURIComponent(runId)}/input-requests/${encodeURIComponent(requestId)}/responses`, body, options),
    retryContinuation: (projectId, runId, requestId, body, options) => client.post(`${base(projectId)}/concept-portfolio-runs/${encodeURIComponent(runId)}/input-requests/${encodeURIComponent(requestId)}/retry`, body, options),
    currentSelection: (projectId, options) => client.get(`${selectionBase(projectId)}/current`, options),
    select: (projectId, body, options) => client.post(selectionBase(projectId), body, options),
    hypotheses: (projectId, selectionId, options) => client.get(`${selectionBase(projectId)}/${selectionId}/hypotheses`, options),
    confirm: (projectId, selectionId, body, options) => client.post(`${selectionBase(projectId)}/${selectionId}/hypotheses/confirm`, body, options),
    alternative: (projectId, selectionId, type, body, options) => client.post(`${selectionBase(projectId)}/${selectionId}/hypotheses/${encodeURIComponent(type)}/alternative`, body, options),
    retryDelta: (projectId, selectionId, body, options) => client.post(`${selectionBase(projectId)}/${selectionId}/delta-legal/retry`, body, options),
    finalizeReport: (projectId, selectionId, options) => client.post(`${selectionBase(projectId)}/${selectionId}/legal-regulatory-report/finalize`, {}, options),
    report: (projectId, selectionId, options) => client.get(`${selectionBase(projectId)}/${selectionId}/legal-regulatory-report/current`, options),
    finalizeMarketSeed: (projectId, selectionId, body, options) => client.post(`${selectionBase(projectId)}/${selectionId}/market-seed/finalize`, body, options),
    marketSeed: (projectId, selectionId, options) => client.get(`${selectionBase(projectId)}/${selectionId}/market-seed/current`, options),
  });
}
