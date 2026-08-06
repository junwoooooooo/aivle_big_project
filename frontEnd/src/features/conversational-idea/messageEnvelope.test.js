import { describe, expect, it } from 'vitest';
import { parseMessageEnvelope, validateWorkspaceMessages } from './messageEnvelope';

describe('message envelope v1.0', () => {
  it('accepts a strict question set', () => {
    const envelope = parseMessageEnvelope({ schemaVersion: '1.0', messageType: 'QUESTION_SET', payload: {
      text: '확인할 내용입니다.', contradictions: [], readiness: 'NEEDS_INPUT',
      questions: [{ id: 'q1', fieldKey: 'problem', prompt: '문제는?', type: 'FREE_TEXT', options: [], allowUndecided: true }],
    } });
    expect(envelope.messageType).toBe('QUESTION_SET');
  });

  it.each([
    { schemaVersion: '2.0', messageType: 'TEXT', payload: { text: 'x' } },
    { schemaVersion: '1.0', messageType: 'UNKNOWN', payload: {} },
    { schemaVersion: '1.0', messageType: 'TEXT', payload: { text: 'x', extra: true } },
  ])('rejects unsupported or non-strict envelopes', (value) => {
    expect(() => parseMessageEnvelope(value)).toThrow();
  });

  it('keeps user text separate from assistant envelopes', () => {
    expect(validateWorkspaceMessages({ messages: [{ role: 'USER', type: 'TEXT', text: '원문', envelope: null }] }).messages[0].text).toBe('원문');
    expect(() => validateWorkspaceMessages({ messages: [{ role: 'USER', type: 'TEXT', envelope: { schemaVersion: '1.0' } }] })).toThrow();
  });
});
