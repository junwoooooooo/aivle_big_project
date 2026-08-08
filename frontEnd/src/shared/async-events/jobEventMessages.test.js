import { describe, expect, it } from 'vitest';

import {
  ACTIVE_JOB_EVENT_KEYS,
  isUserVisibleJobEvent,
  jobEventMessage,
} from './jobEventMessages.js';

describe('active job event message registry', () => {
  it('contains only events emitted by the current Idea, Concept, and Marketing workers', () => {
    expect(ACTIVE_JOB_EVENT_KEYS).toHaveLength(30);
    expect(new Set(ACTIVE_JOB_EVENT_KEYS).size).toBe(ACTIVE_JOB_EVENT_KEYS.length);
    expect(ACTIVE_JOB_EVENT_KEYS).toContain('job.idea.queued');
    expect(ACTIVE_JOB_EVENT_KEYS).toContain('job.concept.run.completed');
    expect(ACTIVE_JOB_EVENT_KEYS).toContain('job.concept.slot.validating_distinctness');
    expect(ACTIVE_JOB_EVENT_KEYS).toContain('job.marketing.failed');
    expect(ACTIVE_JOB_EVENT_KEYS.some((key) => key.startsWith('job.boundary.'))).toBe(false);
    expect(ACTIVE_JOB_EVENT_KEYS.some((key) => key.includes('attachment'))).toBe(false);
  });

  it('hides unknown and archived events instead of presenting fake progress', () => {
    expect(isUserVisibleJobEvent({ messageKey: 'job.idea.extracting' })).toBe(true);
    expect(isUserVisibleJobEvent({ messageKey: 'job.boundary.queued' })).toBe(false);
    expect(isUserVisibleJobEvent({ messageKey: 'job.claimed' })).toBe(false);
    expect(jobEventMessage({ messageKey: 'job.concept.run.completed' }))
      .toBe('검증된 컨셉 5개가 준비되었습니다.');
  });
});
