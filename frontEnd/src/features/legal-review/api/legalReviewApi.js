export function createLegalReviewApi(client) {
  const projectPath = (projectId) =>
    `/projects/${encodeURIComponent(projectId)}/legal-reviews`;
  return {
    async start(projectId, options) {
      const response = await client.post(projectPath(projectId), undefined, options);
      return response.data;
    },
    async latest(projectId, options) {
      const response = await client.get(`${projectPath(projectId)}/latest`, options);
      return response.data;
    },
    async latestJob(projectId, options) {
      const response = await client.get(
        `/projects/${encodeURIComponent(projectId)}/jobs/latest?jobType=LEGAL_REVIEW`,
        options,
      );
      return response.data;
    },
    async job(jobId, options) {
      const response = await client.get(`/jobs/${encodeURIComponent(jobId)}`, options);
      return response.data;
    },
    async latestPlan(projectId, options) {
      const response = await client.get(
        `/projects/${encodeURIComponent(projectId)}/structured-plans/latest`,
        options,
      );
      return response.data;
    },
  };
}
