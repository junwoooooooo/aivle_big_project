import { describe, expect, it } from 'vitest';
import { DIMENSION_LABELS, parseJsonList } from './feasibilityViewModel.js';

describe('feasibility view model', () => {
  it('defines all ten dimension labels', () => {
    expect(Object.keys(DIMENSION_LABELS)).toHaveLength(10);
  });

  it('parses list JSON defensively', () => {
    expect(parseJsonList('["근거"]')).toEqual(['근거']);
    expect(parseJsonList('{"not":"a-list"}')).toEqual([]);
    expect(parseJsonList('broken')).toEqual([]);
  });
});
