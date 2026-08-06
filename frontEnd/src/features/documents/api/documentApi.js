export function createDocumentApi(client) {
  return {
    async upload(projectId, file, idempotencyKey, options = {}) {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('documentType', 'BUSINESS_PLAN');
      const response = await client.upload(
        `/projects/${encodeURIComponent(projectId)}/documents`,
        formData,
        {
          ...options,
          headers: {
            ...options.headers,
            'Idempotency-Key': idempotencyKey,
          },
        },
      );
      return response.data;
    },
    async list(projectId, options) {
      const response = await client.get(
        `/projects/${encodeURIComponent(projectId)}/documents`,
        options,
      );
      return response.data;
    },
    async getVersion(documentId, versionId, options) {
      const response = await client.get(
        `/documents/${encodeURIComponent(documentId)}/versions/${encodeURIComponent(versionId)}`,
        options,
      );
      return response.data;
    },
    async getJob(jobId, options) {
      const response = await client.get(`/jobs/${encodeURIComponent(jobId)}`, options);
      return response.data;
    },
    async getLatestJob(projectId, options) {
      const response = await client.get(
        `/projects/${encodeURIComponent(projectId)}/jobs/latest?jobType=DOCUMENT_PARSE`,
        options,
      );
      return response.data;
    },
  };
}
