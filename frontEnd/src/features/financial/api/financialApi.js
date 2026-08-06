const root = (projectId) => `/projects/${encodeURIComponent(projectId)}/financial-analyses`;
const sourceRoot = (projectId) => `/projects/${encodeURIComponent(projectId)}/financial-analysis/source`;

export function createFinancialApi(client) {
  return {
    source: async (projectId, options) => (await client.get(sourceRoot(projectId), options)).data,
    list: async (projectId, options) => (await client.get(root(projectId), options)).data,
    detail: async (projectId, analysisId, options) => (await client.get(`${root(projectId)}/${encodeURIComponent(analysisId)}`, options)).data,
    create: async (projectId, body, options) => (await client.post(root(projectId), body, options)).data,
    update: async (projectId, analysisId, body, options) => (await client.patch(`${root(projectId)}/${encodeURIComponent(analysisId)}`, body, options)).data,
    run: async (projectId, analysisId, options) => (await client.post(`${root(projectId)}/${encodeURIComponent(analysisId)}/run`, undefined, options)).data,
    duplicate: async (projectId, analysisId, options) => (await client.post(`${root(projectId)}/${encodeURIComponent(analysisId)}/duplicate`, undefined, options)).data,
    remove: (projectId, analysisId, options) => client.delete(`${root(projectId)}/${encodeURIComponent(analysisId)}`, options),
  };
}
