export function createProjectModuleApi(client) {
  return {
    async findAll(projectId, options = {}) {
      const response = await client.get(
        `/api/v3/projects/${encodeURIComponent(projectId)}/modules`,
        options,
      );
      return response.data;
    },
  };
}
