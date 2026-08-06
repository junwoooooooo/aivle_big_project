import { describe, expect, it } from 'vitest';
import {
  APPLICABILITY_LABELS, LEGAL_CATEGORY_LABELS, parseStringList, RISK_LABELS,
} from './legalReviewViewModel.js';

describe('legal review view model', () => {
  it('provides labels for the complete category, risk and applicability contracts', () => {
    expect(Object.keys(LEGAL_CATEGORY_LABELS)).toHaveLength(10);
    expect(Object.keys(RISK_LABELS)).toHaveLength(5);
    expect(Object.keys(APPLICABILITY_LABELS)).toHaveLength(4);
  });

  it('parses evidence defensively without rendering malformed provider data', () => {
    expect(parseStringList('["근거"]')).toEqual(['근거']);
    expect(parseStringList('{bad json')).toEqual([]);
    expect(parseStringList(null)).toEqual([]);
  });
});
