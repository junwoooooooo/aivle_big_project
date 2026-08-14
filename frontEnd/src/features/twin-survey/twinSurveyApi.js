const base = (projectId) => `/api/v3/projects/${encodeURIComponent(projectId)}`;
const key = () => globalThis.crypto?.randomUUID?.() ?? `twin-${Date.now()}-${Math.random()}`;

export function createTwinSurveyApi(client, projectId) {
  const root = base(projectId);
  return {
    // 202 로 즉시 돌아오고 Project SSE가 canonical current 재조회를 유도한다. n=300 이면 분 단위라 동기로 받을
    // 방법이 없다. timeoutMs 는 **enqueue 응답**에만 걸린다 — 조사 자체의 예산이 아니다.
    async startSurvey(situation, pairs, sampleSize) {
      return (await client.post(`${root}/twin-survey`, { situation, pairs, sampleSize },
        { timeoutMs: 30000, headers: { 'Idempotency-Key': key() } })).data;
    },
    async currentSurvey() { return (await client.get(`${root}/twin-survey/current`)).data; },
    // 자극 초안도 202 TaskRun이며 결과는 SSE 뒤 canonical current에서 읽는다.
    // 공식 source는 Backend가 검증한 current selected Concept다.
    async draftStimulus() {
      return (await client.post(`${root}/twin-survey/stimulus-draft`, {},
        { timeoutMs: 30000, headers: { 'Idempotency-Key': key() } })).data;
    },
    async currentStimulusDraft() {
      return (await client.get(`${root}/twin-survey/stimulus-draft/current`)).data;
    },
  };
}
