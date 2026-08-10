const base = (projectId) => `/api/v2/projects/${encodeURIComponent(projectId)}`;

export function createMarketApi(client, projectId) {
  const root = base(projectId);
  return {
    // 시장조사(1단계) · BM 캔버스(2단계) 둘 다 **202 로 즉시 돌아오고** 화면이 current 를 폴링한다.
    // 1단계는 90~266초라 동기로 받을 방법이 없다. timeoutMs 는 **enqueue 응답**에만 걸린다.
    async startMarketResearch(conceptId, asOf, concept) {
      return (await client.post(`${root}/market-research`, { conceptId, asOf, concept }, { timeoutMs: 30000 })).data;
    },
    async currentMarketResearch() { return (await client.get(`${root}/market-research/current`)).data; },
    async startBusinessModel(conceptId, asOf) {
      return (await client.post(`${root}/business-model`, { conceptId, asOf }, { timeoutMs: 30000 })).data;
    },
    async currentBusinessModel() { return (await client.get(`${root}/business-model/current`)).data; },
  };
}
