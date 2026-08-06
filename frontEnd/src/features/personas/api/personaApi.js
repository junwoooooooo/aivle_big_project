export function createPersonaApi(client) {
  const root = (projectId) =>
    `/projects/${encodeURIComponent(projectId)}/persona-recommendations`;
  return {
    async catalog(options) {
      return (await client.get('/personas/catalog', options)).data;
    },
    async start(projectId, options) {
      return (await client.post(root(projectId), undefined, options)).data;
    },
    async latest(projectId, options) {
      return (await client.get(`${root(projectId)}/latest`, options)).data;
    },
    async latestJob(projectId, options) {
      return (await client.get(
        `/projects/${encodeURIComponent(projectId)}/jobs/latest?jobType=PERSONA_RECOMMENDATION`,
        options,
      )).data;
    },
    async job(jobId, options) {
      return (await client.get(`/jobs/${encodeURIComponent(jobId)}`, options)).data;
    },
    async latestFeasibility(projectId, options) {
      return (await client.get(
        `/projects/${encodeURIComponent(projectId)}/feasibility-assessments/latest`,
        options,
      )).data;
    },
    async available(projectId, options) {
      return (await client.get(
        `/projects/${encodeURIComponent(projectId)}/personas/available`,
        options,
      )).data;
    },
    async select(projectId, personaId, options) {
      return (await client.put(
        `/projects/${encodeURIComponent(projectId)}/personas/selection`,
        { personaId },
        options,
      )).data;
    },
  };
}
