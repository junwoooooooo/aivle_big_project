export function createStructuredPlanApi(client) {
  return {
    async getLatest(projectId, options) {
      const response = await client.get(
        `/projects/${encodeURIComponent(projectId)}/structured-plans/latest`,
        options,
      );
      return response.data;
    },
    async updateMissingField(projectId, planId, fieldId, payload, options) {
      const response = await client.patch(
        `/projects/${encodeURIComponent(projectId)}/structured-plans/${encodeURIComponent(planId)}/missing-fields/${encodeURIComponent(fieldId)}`,
        payload,
        options,
      );
      return response.data;
    },
    async confirm(projectId, planId, payload, options) {
      const response = await client.post(
        `/projects/${encodeURIComponent(projectId)}/structured-plans/${encodeURIComponent(planId)}/confirm`,
        payload,
        options,
      );
      return response.data;
    },
  };
}
