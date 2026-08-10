import { describe, expect, it } from 'vitest';
import { createSetupModel, setupIsValid, toCreateRequest } from './marketingContentModel.js';

describe('marketing setup model', () => {
  it('maps CTA and phrase fields into the closed marketing source request contract', () => {
    const setup = { ...createSetupModel('source-1'), channel: 'Instagram', purpose: 'launch',
      callToAction: '자세히 보기', requiredPhrases: '지역 기반, 신선함\n지역 기반', excludedPhrases: '최저가' };
    const request = toCreateRequest(setup);
    expect(setupIsValid(setup)).toBe(true);
    expect(request).toEqual(expect.objectContaining({ contract: 'marketing-content-request-v1', marketingSourceSnapshotId: 'source-1', contentType: 'SOCIAL_POST' }));
    expect(request.requiredPhrases).toEqual(['자세히 보기', '지역 기반', '신선함']);
    expect(request.additionalInstruction).toContain("CTA는 '자세히 보기'");
    expect(request).not.toHaveProperty('callToAction');
  });
});
