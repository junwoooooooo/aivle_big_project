const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}`;
const commandKey = () => globalThis.crypto?.randomUUID?.()
  ?? `business-validation-${Date.now()}-${Math.random()}`;

export function createBusinessValidationApi(client, projectId) {
  const root = base(projectId);
  return {
    async current() {
      return (await client.get(`${root}/business-validation/current`)).data;
    },
    async start(asOf) {
      return (await client.post(`${root}/business-validation/start`, { asOf }, {
        timeoutMs: 30000, headers: { 'Idempotency-Key': commandKey() },
      })).data;
    },
    async retryBusinessModel() {
      return (await client.post(`${root}/business-validation/retry-bm`, {}, {
        timeoutMs: 30000, headers: { 'Idempotency-Key': commandKey() },
      })).data;
    },
    async currentCompetitorSeeds() {
      return (await client.get(`${root}/market-research/competitor-seeds`)).data;
    },
    async saveCompetitorSeeds(seeds) {
      return (await client.put(`${root}/market-research/competitor-seeds`, seeds)).data;
    },
    async currentBmPlan() {
      return (await client.get(`${root}/business-model/plan`)).data;
    },
  };
}
