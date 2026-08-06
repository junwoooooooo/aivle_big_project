export const MESSAGE_SCHEMA_VERSION = '1.0';
export const MESSAGE_TYPES = new Set(['TEXT', 'QUESTION_SET', 'BRIEF_REVIEW', 'ATTACHMENT_SUMMARY', 'JOB_STATUS', 'ERROR']);

const exact = (value, keys) => value && typeof value === 'object' && !Array.isArray(value)
  && Object.keys(value).length === keys.length && keys.every((key) => Object.hasOwn(value, key));

function validQuestion(question) {
  return exact(question, ['id', 'fieldKey', 'prompt', 'type', 'options', 'allowUndecided'])
    && typeof question.id === 'string' && typeof question.fieldKey === 'string'
    && typeof question.prompt === 'string' && ['FREE_TEXT', 'SINGLE_SELECT', 'MULTI_SELECT', 'UNDECIDED'].includes(question.type)
    && Array.isArray(question.options) && question.options.every((value) => typeof value === 'string')
    && typeof question.allowUndecided === 'boolean';
}

export function parseMessageEnvelope(value) {
  if (!exact(value, ['schemaVersion', 'messageType', 'payload'])
      || value.schemaVersion !== MESSAGE_SCHEMA_VERSION || !MESSAGE_TYPES.has(value.messageType)) {
    throw new Error('MESSAGE_ENVELOPE_UNSUPPORTED');
  }
  const payload = value.payload;
  const valid = (() => {
    switch (value.messageType) {
      case 'TEXT': return exact(payload, ['text']) && typeof payload.text === 'string';
      case 'QUESTION_SET': return exact(payload, ['text', 'questions', 'contradictions', 'readiness'])
        && typeof payload.text === 'string' && Array.isArray(payload.questions) && payload.questions.every(validQuestion)
        && Array.isArray(payload.contradictions) && payload.contradictions.every((item) => typeof item === 'string')
        && typeof payload.readiness === 'string';
      case 'BRIEF_REVIEW': return exact(payload, ['text', 'contradictions', 'readiness'])
        && typeof payload.text === 'string' && Array.isArray(payload.contradictions)
        && payload.contradictions.every((item) => typeof item === 'string') && typeof payload.readiness === 'string';
      case 'ATTACHMENT_SUMMARY': return exact(payload, ['text', 'attachmentId'])
        && typeof payload.text === 'string' && Number.isSafeInteger(payload.attachmentId);
      case 'JOB_STATUS': return exact(payload, ['messageKey', 'messageParams'])
        && typeof payload.messageKey === 'string' && exact(payload.messageParams, Object.keys(payload.messageParams));
      case 'ERROR': return exact(payload, ['messageKey']) && typeof payload.messageKey === 'string';
      default: return false;
    }
  })();
  if (!valid) throw new Error('MESSAGE_ENVELOPE_INVALID');
  return Object.freeze({ schemaVersion: value.schemaVersion, messageType: value.messageType, payload });
}

export function validateWorkspaceMessages(workspace) {
  if (!workspace?.messages) return workspace;
  return {
    ...workspace,
    messages: workspace.messages.map((message) => {
      if (message.role === 'USER') {
        if (message.type !== 'TEXT' || message.envelope != null) throw new Error('USER_MESSAGE_CONTRACT_INVALID');
        return message;
      }
      return { ...message, envelope: parseMessageEnvelope(message.envelope) };
    }),
  };
}
