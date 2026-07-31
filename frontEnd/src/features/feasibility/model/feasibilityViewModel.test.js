import { describe, expect, it } from 'vitest';
import {
  DIMENSION_LABELS, GROUP_LABELS, groupDimensions, parseJsonList,
} from './feasibilityViewModel.js';

describe('feasibility view model', () => {
  it('defines all ten dimension labels', () => {
    expect(Object.keys(DIMENSION_LABELS)).toHaveLength(10);
  });

  it('parses list JSON defensively', () => {
    expect(parseJsonList('["근거"]')).toEqual(['근거']);
    expect(parseJsonList('{"not":"a-list"}')).toEqual([]);
    expect(parseJsonList('broken')).toEqual([]);
  });

  it('assigns every dimension to exactly one group', () => {
    const assessment = {
      dimensions: Object.keys(DIMENSION_LABELS).map((code) => ({ code })),
      groups: [],
    };
    const groups = groupDimensions(assessment);
    expect(groups.map((group) => group.analysisType))
      .toEqual(['MARKET', 'BUSINESS_MODEL', 'TECHNOLOGY_OPERATION']);
    expect(groups.flatMap((group) => group.dimensions)).toHaveLength(10);
    expect(groups.map((group) => group.dimensions.length)).toEqual([4, 4, 2]);
    expect(groups.map((group) => group.label)).toEqual(Object.values(GROUP_LABELS));
  });

  it('keeps working when a group result is missing (older assessments)', () => {
    const groups = groupDimensions({
      dimensions: [{ code: 'MARKET_ATTRACTIVENESS' }],
      groups: undefined,
    });
    // 묶음 결과가 없어도 차원은 제자리에 놓이고, 서술만 비어 있다
    expect(groups).toHaveLength(1);
    expect(groups[0].analysisType).toBe('MARKET');
    expect(groups[0].score).toBeNull();
    expect(groups[0].headline).toBeNull();
    expect(groups[0].strengths).toEqual([]);
  });

  it('carries group narrative through', () => {
    const groups = groupDimensions({
      dimensions: [{ code: 'BUSINESS_MODEL' }],
      groups: [{
        analysisType: 'BUSINESS_MODEL', score: 70, verdict: 'CONDITIONAL',
        headline: '결론', summary: '요약', nextFocus: '먼저 할 일',
        strengthsJson: '["강점"]', risksJson: '["위험"]',
      }],
    });
    expect(groups[0].score).toBe(70);
    expect(groups[0].headline).toBe('결론');
    expect(groups[0].strengths).toEqual(['강점']);
    expect(groups[0].risks).toEqual(['위험']);
  });
});
