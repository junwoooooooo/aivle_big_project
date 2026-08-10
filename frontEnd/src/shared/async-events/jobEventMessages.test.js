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
});
