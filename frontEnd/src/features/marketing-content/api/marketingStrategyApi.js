const root = (projectId) =>
  `/api/v3/projects/${encodeURIComponent(projectId)}/marketing-strategy`;

const unwrap = (response) => response.data;

export function createMarketingStrategyApi(client) {
  return Object.freeze({
    current: async (projectId, options) =>
      unwrap(await client.get(
        root(projectId),
        options,
      )),

    generate: async (
      projectId,
      idempotencyKey,
      options = {},
    ) =>
      unwrap(await client.post(
        `${root(projectId)}/generate`,
        {},
        {
          ...options,
          headers: {
            ...options.headers,
            'Idempotency-Key': idempotencyKey,
            'X-Correlation-Id': idempotencyKey,
          },
        },
      )),

    download: async (
      projectId,
      reportId,
      options,
    ) =>
      client.download(
        `${root(projectId)}/${encodeURIComponent(reportId)}/pdf`,
        options,
      ),
  });
}
