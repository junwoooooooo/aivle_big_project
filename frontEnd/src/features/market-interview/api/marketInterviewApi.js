const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}/market-interview`;
const key = () => globalThis.crypto?.randomUUID?.() ?? `market-interview-${Date.now()}-${Math.random()}`;

export function createMarketInterviewApi(client, projectId) {
  const root = base(projectId);
  const command = (path = '', body = {}) => client.post(`${root}${path}`, body, {
    timeoutMs: 30000, headers: { 'Idempotency-Key': key() },
  }).then((response) => response.data);
  return {
    current: () => client.get(`${root}/current`).then((response) => response.data),
    start: (sampleSize = 20) => command('', { sampleSize }),
    retry: () => command('/retry'),
  };
}
