import { describe, expect, it } from 'vitest';

import { formatLocalTime } from './formatLocalTime.js';

describe('formatLocalTime', () => {
  it('formats Message and Job Event UTC timestamps on the same local-time basis', () => {
    const messageOccurredAt = '2026-08-05T10:16:57.894Z';
    const eventOccurredAt = '2026-08-05T10:16:57.894Z';

    expect(formatLocalTime(messageOccurredAt)).toBe(formatLocalTime(eventOccurredAt));
    expect(formatLocalTime(messageOccurredAt)).not.toBe('');
  });
});
