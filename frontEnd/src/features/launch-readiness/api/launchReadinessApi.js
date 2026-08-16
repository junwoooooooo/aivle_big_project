const root = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}`;
const professional = (projectId, module) => `${root(projectId)}/launch-readiness/${module}`;
const downloadBlob = async (request) => (await request).blob;

const commandOptions = () => {
  const key = globalThis.crypto?.randomUUID?.() ?? `launch-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return { headers: { 'Idempotency-Key': key }, requestId: key, timeoutMs: 30000 };
};

export const createLaunchReadinessApi = (client) => Object.freeze({
  professionalTemplate: (projectId, module) => downloadBlob(client.download(`${professional(projectId, module)}/template`)),
  professionalCurrent: async (projectId, module) => (await client.get(`${professional(projectId, module)}/current`)).data,
  startProfessional: async (projectId, module, file) => {
    const form = new FormData(); form.append('file', file);
    return (await client.upload(`${professional(projectId, module)}/analysis-runs`, form, commandOptions())).data;
  },
  downloadProfessionalReport: (projectId, module) => downloadBlob(client.download(`${professional(projectId, module)}/report`, { timeoutMs: 60000 })),
  financeTemplate: (projectId) => downloadBlob(client.download(`${root(projectId)}/finance/preparation/template`)),
  financeCurrent: async (projectId) => (await client.get(`${root(projectId)}/finance/analysis/current`)).data,
  startFinance: async (projectId, file) => {
    const form = new FormData(); form.append('file', file);
    return (await client.upload(`${root(projectId)}/finance/preparation/import`, form, commandOptions())).data;
  },
  downloadFinanceReport: (projectId) => downloadBlob(client.download(`${root(projectId)}/finance/analysis/report`, { timeoutMs: 60000 })),
  downloadReports: (projectId, modules) => downloadBlob(client.download(`${root(projectId)}/reports/download?${modules.map((module) => `modules=${encodeURIComponent(module)}`).join('&')}`, { timeoutMs: 120000 })),
});
