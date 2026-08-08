const ideaBriefBase = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}/idea-brief`;

export function createIdeaBriefApiAdapter(client) {
  return Object.freeze({
    get: (projectId, options) => client.get(ideaBriefBase(projectId), options),
    derive: (projectId, payload, options) => client.post(`${ideaBriefBase(projectId)}/derive`, payload, options),
    patchFields: (projectId, payload, options) => client.patch(`${ideaBriefBase(projectId)}/fields`, payload, options),
    patchInterpretation: (projectId, payload, options) => client.patch(`${ideaBriefBase(projectId)}/interpretation`, payload, options),
    reviewCommitments: (projectId, payload, options) => client.patch(`${ideaBriefBase(projectId)}/commitments`, payload, options),
    answerQuestions: (projectId, payload, options) => client.post(`${ideaBriefBase(projectId)}/answers`, payload, options),
    reanalyze: (projectId, options) => client.post(`${ideaBriefBase(projectId)}/reanalyze`, {}, options),
    confirm: (projectId, payload, options) => client.post(`${ideaBriefBase(projectId)}/confirm`, payload, options),
    confirmInterpretation: (projectId, payload, options) => client.post(`${ideaBriefBase(projectId)}/confirm-interpretation`, payload, options),
  });
}

export function ideaCommandOptions(prefix) {
  const suffix = globalThis.crypto?.randomUUID?.()
    ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return { headers: { 'Idempotency-Key': `${prefix}:${suffix}` } };
}
