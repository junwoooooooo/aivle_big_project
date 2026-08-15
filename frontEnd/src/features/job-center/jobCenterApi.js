export function createJobCenterApi(client) {
  return {
    async active(projectId, options = {}) {
      const response = await client.get(`/api/v3/projects/${encodeURIComponent(projectId)}/active-jobs`, options);
      return response.data ?? [];
    },
    async recent(projectId, options = {}) {
      const response = await client.get(`/api/v3/projects/${encodeURIComponent(projectId)}/recent-jobs`, options);
      return response.data ?? [];
    },
    async history(projectId, page = 0, size = 20, options = {}) {
      const response = await client.get(`/api/v3/projects/${encodeURIComponent(projectId)}/jobs/history?page=${page}&size=${size}`, options);
      return response.data ?? { items: [], page, size, hasMore: false, totalElements: 0 };
    },
    async events(jobId, after = 0, options = {}) {
      const response = await client.get(`/api/v2/jobs/${encodeURIComponent(jobId)}/events?after=${after}`, options);
      return response.data ?? { events: [], nextSequence: after, latestSequence: after, hasMore: false };
    },
  };
}
