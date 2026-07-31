export function createLegalReviewApi(client) {
  const base = (projectId) => `/projects/${encodeURIComponent(projectId)}`;
  const projectPath = (projectId) => `${base(projectId)}/legal-reviews`;
  return {
    /** mode: 'FULL' | 'INCREMENTAL' | undefined(서버 기본값) */
    async start(projectId, mode, options) {
      const body = mode ? { mode } : undefined;
      const response = await client.post(projectPath(projectId), body, options);
      return response.data;
    },
    async latest(projectId, options) {
      const response = await client.get(`${projectPath(projectId)}/latest`, options);
      return response.data;
    },
    async latestJob(projectId, options) {
      const response = await client.get(
        `${base(projectId)}/jobs/latest?jobType=LEGAL_REVIEW`,
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
        `${base(projectId)}/structured-plans/latest`,
        options,
      );
      return response.data;
    },
    /** 검토가 본 그 버전을 정확히 조회한다 — latest는 재검토 루프에서 다른 버전일 수 있다. */
    async planById(projectId, planId, options) {
      const response = await client.get(
        `${base(projectId)}/structured-plans/${encodeURIComponent(planId)}`,
        options,
      );
      return response.data;
    },
    async activeCycle(projectId, options) {
      const response = await client.get(`${base(projectId)}/review-cycles/active`, options);
      return response.data;
    },
    async planVersions(projectId, options) {
      const response = await client.get(`${base(projectId)}/plan-versions`, options);
      return response.data;
    },
    async latestPublication(projectId, options) {
      const response = await client.get(`${base(projectId)}/publications/latest`, options);
      return response.data;
    },
    async acceptSuggestion(projectId, requestId, suggestionId, options) {
      const response = await client.post(
        `${base(projectId)}/revision-requests/${encodeURIComponent(requestId)}/accept`,
        { suggestionId },
        options,
      );
      return response.data;
    },
    async dismissRequest(projectId, requestId, options) {
      const response = await client.post(
        `${base(projectId)}/revision-requests/${encodeURIComponent(requestId)}/dismiss`,
        undefined,
        options,
      );
      return response.data;
    },
    async answerQuestion(projectId, questionId, { answer, factKey, source }, options) {
      const response = await client.post(
        `${base(projectId)}/legal-questions/${encodeURIComponent(questionId)}/answer`,
        { answer, factKey, source },
        options,
      );
      return response.data;
    },
    async publish(projectId, cycleId, completedActions, options) {
      const response = await client.post(
        `${base(projectId)}/review-cycles/${encodeURIComponent(cycleId)}/publish`,
        { completedActions },
        options,
      );
      return response.data;
    },
  };
}
