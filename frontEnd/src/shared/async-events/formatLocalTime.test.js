import { describe, expect, it } from 'vitest';

import { formatLocalTime } from './formatLocalTime.js';

describe('formatLocalTime', () => {
  it('formats Message and Job Event UTC timestamps on the same local-time basis', () => {
    const messageOccurredAt = '2026-08-05T10:16:57.894Z';
    const eventOccurredAt = '2026-08-05T10:16:57.894Z';

    expect(formatLocalTime(messageOccurredAt)).toBe(formatLocalTime(eventOccurredAt));
    expect(formatLocalTime(messageOccurredAt)).not.toBe('');
  });

  it('converts a UTC API timestamp to Asia/Seoul local time', () => {
    expect(formatLocalTime('2026-08-09T06:21:00Z', { timeZone: 'Asia/Seoul' }))
      .toBe('오후 03:21');
  });
});
