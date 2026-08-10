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
  };
}
