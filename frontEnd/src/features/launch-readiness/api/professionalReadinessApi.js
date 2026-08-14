const base = (projectId, module) => `/api/v3/projects/${encodeURIComponent(projectId)}/launch-readiness/${module}`;

export const createProfessionalReadinessApi = (client) => Object.freeze({
  template: (projectId, module) => client.get(`${base(projectId, module)}/template`, { responseType: 'blob' }),
  current: async (projectId, module) => (await client.get(`${base(projectId, module)}/current`)).data,
  analyze: async (projectId, module, file) => {
    const form = new FormData(); form.append('file', file);
    return (await client.upload(`${base(projectId, module)}/analyze`, form, { timeoutMs: 240000 })).data;
  },
  report: (projectId, module) => client.get(`${base(projectId, module)}/report`, { responseType: 'blob' }),
});
