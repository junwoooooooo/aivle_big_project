const root = (projectId) => `/api/v2/projects/${encodeURIComponent(projectId)}`;

export function createConceptWorkboardApi(client, projectId) {
  const base = root(projectId);
  return {
    async current() {
      return (await client.get(`${base}/concept-explorations/current`)).data;
    },
    async batch(batchId) {
      return (await client.get(`${base}/concept-explorations/${encodeURIComponent(batchId)}`)).data;
    },
    async slots(batchId) {
      return (await client.get(`${base}/concept-explorations/${encodeURIComponent(batchId)}/slots`)).data;
    },
    async concepts() {
      return (await client.get(`${base}/concepts?contract=concept-core-v1`)).data;
    },
    async start(confirmedBriefVersionId, regulatoryBoundaryVersionId) {
      return (await client.post(`${base}/concept-explorations`, {
        confirmedBriefVersionId,
        regulatoryBoundaryVersionId,
      })).data;
    },
    async retry(batchId) {
      return (await client.post(
        `${base}/concept-explorations/${encodeURIComponent(batchId)}/retry`,
        undefined,
        { headers: { 'Idempotency-Key': `concept-workboard-retry-${batchId}` } },
      )).data;
    },
  };
}
