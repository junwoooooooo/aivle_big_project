export function createFeasibilityApi(client) {
  const path = (projectId) =>
    `/projects/${encodeURIComponent(projectId)}/feasibility-assessments`;
  return {
    async start(projectId, options) {
      return (await client.post(path(projectId), undefined, options)).data;
    },
    async latest(projectId, options) {
      return (await client.get(`${path(projectId)}/latest`, options)).data;
    },
    async latestJob(projectId, options) {
      return (await client.get(
        `/projects/${encodeURIComponent(projectId)}/jobs/latest?jobType=FEASIBILITY_ANALYSIS`,
        options,
      )).data;
    },
    async job(jobId, options) {
      return (await client.get(`/jobs/${encodeURIComponent(jobId)}`, options)).data;
    },
    async latestPlan(projectId, options) {
      return (await client.get(
        `/projects/${encodeURIComponent(projectId)}/structured-plans/latest`,
        options,
      )).data;
    },
    async latestLegalReview(projectId, options) {
      return (await client.get(
        `/projects/${encodeURIComponent(projectId)}/legal-reviews/latest`,
        options,
      )).data;
    },
  };
}
