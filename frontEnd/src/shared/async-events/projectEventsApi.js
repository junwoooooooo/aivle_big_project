export function createProjectEventsApi(client) {
  return {
    async poll(projectId, after, { signal } = {}) {
      const payload = await client.get(
        `/api/v2/projects/${encodeURIComponent(projectId)}/events?after=${after}`,
        { signal },
      );
      return payload?.data ?? { events: [], nextEventId: after, latestEventId: after, hasMore: false };
    },
  };
}
