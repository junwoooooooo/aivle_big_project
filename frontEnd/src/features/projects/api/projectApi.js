export function createProjectApi(client) {
  return {
    async list() {
      const response = await client.get('/projects');
      return response.data;
    },
    async create(input) {
      const response = await client.post('/projects', input);
      return response.data;
    },
    async get(projectId) {
      const response = await client.get(`/projects/${encodeURIComponent(projectId)}`);
      return response.data;
    },
    async update(projectId, input) {
      const response = await client.patch(`/projects/${encodeURIComponent(projectId)}`, input);
      return response.data;
    },
    async remove(projectId) {
      await client.delete(`/projects/${encodeURIComponent(projectId)}`);
    },
  };
}
