const projectRoot = (projectId) => `/projects/${encodeURIComponent(projectId)}`;
const interviewRoot = (projectId) => `${projectRoot(projectId)}/panel-interviews`;
const marketRoot = (projectId) => `${projectRoot(projectId)}/market-responses`;

export function createValidationApi(client) {
  return {
    async personas(projectId, options) {
      return (await client.get(`${projectRoot(projectId)}/validation-personas`, options)).data;
    },
    async interviews(projectId, options) {
      return (await client.get(interviewRoot(projectId), options)).data;
    },
    async createInterview(projectId, payload, options) {
      return (await client.post(interviewRoot(projectId), payload, options)).data;
    },
    async interview(projectId, interviewId, options) {
      return (await client.get(`${interviewRoot(projectId)}/${encodeURIComponent(interviewId)}`, options)).data;
    },
    async updateInterview(projectId, interviewId, payload, options) {
      return (await client.patch(`${interviewRoot(projectId)}/${encodeURIComponent(interviewId)}`, payload, options)).data;
    },
    async runInterview(projectId, interviewId, options) {
      return (await client.post(`${interviewRoot(projectId)}/${encodeURIComponent(interviewId)}/run`, undefined, options)).data;
    },
    async deleteInterview(projectId, interviewId, options) {
      return client.delete(`${interviewRoot(projectId)}/${encodeURIComponent(interviewId)}`, options);
    },
    async marketResponses(projectId, options) {
      return (await client.get(marketRoot(projectId), options)).data;
    },
    async createMarketResponse(projectId, payload, options) {
      return (await client.post(marketRoot(projectId), payload, options)).data;
    },
    async marketResponse(projectId, predictionId, options) {
      return (await client.get(`${marketRoot(projectId)}/${encodeURIComponent(predictionId)}`, options)).data;
    },
    async updateMarketResponse(projectId, predictionId, payload, options) {
      return (await client.patch(`${marketRoot(projectId)}/${encodeURIComponent(predictionId)}`, payload, options)).data;
    },
    async runMarketResponse(projectId, predictionId, options) {
      return (await client.post(`${marketRoot(projectId)}/${encodeURIComponent(predictionId)}/run`, undefined, options)).data;
    },
    async deleteMarketResponse(projectId, predictionId, options) {
      return client.delete(`${marketRoot(projectId)}/${encodeURIComponent(predictionId)}`, options);
    },
  };
}
