const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}`;
const key = () => globalThis.crypto?.randomUUID?.() ?? `market-interview-${Date.now()}-${Math.random()}`;

export function createMarketInterviewApi(client, projectId) {
  const root = base(projectId);
  return {
    /**
     * 컨셉보드 — **LLM 0회**다. 확정된 사업안에서 여섯 칸을 결정론적으로 꺼낸다.
     * 확정 전이면 404 이고, 그때 화면은 「사업안을 먼저 확정하라」고 말한다.
     * 견본으로 떨어지는 길은 없다 — 조용한 기본값이 실제로 사고를 냈다.
     */
    async board() { return (await client.get(`${root}/market-interview/board`)).data; },

    // 202 로 즉시 돌아오고 화면이 current 를 폴링한다. n=80 이면 수집 80셀 + 코딩 1회라
    // 동기로 받을 방법이 없다. timeoutMs 는 **enqueue 응답**에만 걸린다.
    async startInterview(conceptBoard, sampleSize) {
      return (await client.post(`${root}/market-interview`, { conceptBoard, sampleSize },
        { timeoutMs: 30000, headers: { 'Idempotency-Key': key() } })).data;
    },
    async currentInterview() {
      return (await client.get(`${root}/market-interview/current`)).data;
    },
  };
}
