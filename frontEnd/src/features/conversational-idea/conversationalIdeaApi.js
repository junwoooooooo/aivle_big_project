const root = (projectId) => `/api/v2/projects/${encodeURIComponent(projectId)}`;

export function createConversationalIdeaApi(client, projectId) {
  const base = root(projectId);
  return {
    async current() { return validateWorkspaceMessages((await client.get(`${base}/idea-conversations/current`)).data); },
    async create(importCurrentIdeaSource = false) {
      return validateWorkspaceMessages((await client.post(`${base}/idea-conversations`, { importCurrentIdeaSource })).data);
    },
    async send(conversationId, text, answers = []) {
      return (await client.post(`${base}/idea-conversations/${conversationId}/messages`, { text, answers })).data;
    },
    async attach(conversationId, file) {
      const form = new FormData(); form.append('file', file);
      return (await client.upload(`${base}/idea-conversations/${conversationId}/attachments`, form)).data;
    },
    async editField(conversationId, fieldKey, value, decisionStatus, sourceMessageId = null) {
      return (await client.put(`${base}/opportunity-brief/fields/${encodeURIComponent(fieldKey)}`,
        { conversationId, value, decisionStatus, sourceMessageId })).data;
    },
    async adoptField(conversationId, fieldKey) {
      return (await client.post(`${base}/opportunity-brief/fields/${encodeURIComponent(fieldKey)}/adopt`, { conversationId })).data;
    },
    async rejectField(conversationId, fieldKey) {
      return (await client.post(`${base}/opportunity-brief/fields/${encodeURIComponent(fieldKey)}/reject`, { conversationId })).data;
    },
    async confirm(conversationId) {
      return (await client.post(`${base}/opportunity-brief/confirm`, { conversationId })).data;
    },
    async currentBoundary() {
      return (await client.get(`${base}/regulatory-boundaries/current`)).data;
    },
    async startBoundary(confirmedBriefVersionId) {
      return (await client.post(`${base}/regulatory-boundaries`, { confirmedBriefVersionId })).data;
    },
  };
}
import { validateWorkspaceMessages } from './messageEnvelope';
