const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}`;
const key = () => globalThis.crypto?.randomUUID?.() ?? `market-${Date.now()}-${Math.random()}`;

export function createMarketApi(client, projectId) {
  const root = base(projectId);
  return {
    // 시장조사(1단계) · BM 캔버스(2단계) 둘 다 **202 로 즉시 돌아오고**
    // Project SSE가 canonical current 재조회를 유도한다.
    // 1단계는 90~266초라 동기로 받을 방법이 없다. timeoutMs 는 **enqueue 응답**에만 걸린다.
    async startMarketResearch(asOf) {
      return (await client.post(`${root}/market-research`, { asOf },
        { timeoutMs: 30000, headers: { 'Idempotency-Key': key() } })).data;
    },
    async currentMarketResearch() { return (await client.get(`${root}/market-research/current`)).data; },
    async recollectMarketResearch(sourceMarketResearchVersionId, options = {}) {
      return (await client.post(`${root}/market-research/recollect`, {
        sourceMarketResearchVersionId, asOf: options.asOf,
        slots: options.slots ?? '', from: options.from ?? 'a4',
        slotsFrom: options.slotsFrom ?? 'source',
      }, { timeoutMs: 30000, headers: { 'Idempotency-Key': key() } })).data;
    },
    async currentCompetitorSeeds() { return (await client.get(`${root}/market-research/competitor-seeds`)).data; },
    async saveCompetitorSeeds(seeds) { return (await client.put(`${root}/market-research/competitor-seeds`, seeds)).data; },
    async startBusinessModel() {
      return (await client.post(`${root}/business-model`, {},
        { timeoutMs: 30000, headers: { 'Idempotency-Key': key() } })).data;
    },
    async currentBusinessModel() { return (await client.get(`${root}/business-model/current`)).data; },

    // 실행 계획 — BM 앞 단계에서 사용자가 채우는 칸. **실행과 따로 저장한다**:
    // 요청 바디에 실어 보내면 새로고침에 사라지고 감사 기록도 안 남는다.
    async currentBmPlan() { return (await client.get(`${root}/business-model/plan`)).data; },
    async saveBmPlan(plan, constraints) {
      return (await client.patch(`${root}/business-model/plan`, { plan, constraints })).data;
    },
  };
}
