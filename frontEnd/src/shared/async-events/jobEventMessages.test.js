import { describe, expect, it } from 'vitest';
import { ACTIVE_JOB_EVENT_KEYS, isUserVisibleJobEvent, jobEventMessage } from './jobEventMessages.js';

describe('V2 job event message registry', () => {
  it('covers actual initial, continuation and selection action keys', () => {
    for (const key of ['job.concept-portfolio.queued', 'job.concept-portfolio.running',
      'job.concept-portfolio.ai-executing', 'job.concept-portfolio.materializing',
      'job.concept-portfolio.needs-input', 'job.concept-portfolio.completed',
      'job.concept-portfolio.failed', 'job.concept-portfolio.continuation.ai-executing',
      'job.concept-portfolio.continuation.completed', 'job.concept-portfolio.selection.running',
      'job.concept-portfolio.selection.completed']) expect(ACTIVE_JOB_EVENT_KEYS).toContain(key);
  });
  it('uses Product copy without the legacy exact-five message', () => {
    expect(jobEventMessage({ messageKey: 'job.concept-portfolio.running' })).toBe('사업 방향을 탐색하고 있습니다.');
    expect(jobEventMessage({ messageKey: 'job.concept-portfolio.completed' })).toBe('검토 가능한 사업안이 준비되었습니다.');
    expect(jobEventMessage({ messageKey: 'job.concept.run.completed' })).not.toContain('5개');
    expect(isUserVisibleJobEvent({ messageKey: 'job.claimed' })).toBe(false);
  });
  it('distinguishes safe Portfolio failure reasons', () => {
    expect(jobEventMessage({ status: 'FAILED', messageKey: 'job.concept-portfolio.failed',
      messageParams: { failureCode: 'DEADLINE_EXCEEDED', retryable: true } }))
      .toBe('처리 시간이 제한을 초과했습니다.');
    expect(jobEventMessage({ status: 'FAILED', messageKey: 'job.concept-portfolio.failed',
      messageParams: { failureCode: 'RATE_LIMITED', retryable: true } }))
      .toBe('외부 AI 서비스 요청이 일시적으로 제한되었습니다.');
    expect(jobEventMessage({ status: 'FAILED', messageKey: 'job.concept-portfolio.failed',
      messageParams: { failureCode: 'RESULT_SCHEMA_INVALID', retryable: false } }))
      .toContain('서비스 형식');
  });
});
