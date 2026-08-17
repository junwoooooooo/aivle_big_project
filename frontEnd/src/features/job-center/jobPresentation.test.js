import { describe, expect, it } from 'vitest';
import { jobTaskLabel } from './jobPresentation.js';

describe('Work Center task labels', () => {
  it('uses human labels for the cutover modules', () => {
    expect(jobTaskLabel('MARKET_RESEARCH', 'MARKET_RESEARCH_FULL')).toBe('시장 분석');
    expect(jobTaskLabel('MARKET_RESEARCH', 'MARKET_RESEARCH_BM')).toBe('수익 구조 분석');
    expect(jobTaskLabel('TECH_OPS_PROPOSAL')).toBe('기술·운영 계획 만들기');
    expect(jobTaskLabel('MARKETING_VISUAL_GENERATION')).toBe('마케팅 이미지 생성');
  });
});
