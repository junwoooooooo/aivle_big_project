export function createJobEventsApi(client) {
  return {
    async poll(jobId, after, { signal } = {}) {
      const payload = await client.get(
        `/api/v2/jobs/${encodeURIComponent(jobId)}/events?after=${after}`,
        { signal },
      );
      return payload?.data ?? {
        events: [],
        nextSequence: after,
        latestSequence: after,
        hasMore: false,
      };
    },
  };
}
