import { describe, expect, it } from 'vitest';
import { createSetupModel, setupIsValid, toCreateRequest } from './marketingContentModel.js';

describe('marketing setup model', () => {
  it('maps CTA and phrase fields into the closed marketing source request contract', () => {
    const setup = { ...createSetupModel('source-1'), marketingStrategyReportId: 'a'.repeat(64),
      channel: 'Instagram', purpose: 'launch',
      callToAction: '자세히 보기', requiredPhrases: '지역 기반, 신선함\n지역 기반', excludedPhrases: '최저가' };
    const request = toCreateRequest(setup);
    expect(setupIsValid(setup)).toBe(true);
    expect(request).toEqual(expect.objectContaining({ contract: 'marketing-content-request-v1',
      marketingSourceSnapshotId: 'source-1', marketingStrategyReportId: 'a'.repeat(64),
      contentType: 'SOCIAL_POST' }));
    expect(request.requiredPhrases).toEqual(['자세히 보기', '지역 기반', '신선함']);
    expect(request.additionalInstruction).toContain("CTA는 '자세히 보기'");
    expect(request).not.toHaveProperty('callToAction');
  });

  it('serializes the uploaded reference artifact id without dropping it', () => {
    const request = toCreateRequest({ ...createSetupModel('source-1'), marketingStrategyReportId: 'a'.repeat(64),
      channel: 'Instagram', purpose: 'launch' },
      '00000000-0000-4000-8000-000000000001');
    expect(request.referenceArtifactId).toBe('00000000-0000-4000-8000-000000000001');
  });

  it('allows content creation without a marketing strategy report', () => {
    const setup = { ...createSetupModel('source-1'), channel: 'B2B 제안', purpose: '상담 확보' };
    expect(setupIsValid(setup)).toBe(true);
    expect(toCreateRequest(setup).marketingStrategyReportId).toBeNull();
  });
});
