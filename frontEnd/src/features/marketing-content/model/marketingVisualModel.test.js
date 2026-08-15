import { describe, expect, it } from 'vitest';
import { visualDefaults } from './marketingVisualModel.js';

describe('visualDefaults', () => {
  it.each([
    [undefined, undefined],
    [null, null],
    [{}, null],
  ])('accepts an empty Marketing Content selection', (source, draft) => {
    expect(() => visualDefaults(source, draft)).not.toThrow();
    expect(visualDefaults(source, draft)).toMatchObject({
      promotionName: '',
      mainBanner: '',
      supportingCopy: '',
    });
  });

  it('keeps the existing draft-derived visual defaults', () => {
    expect(visualDefaults(
      { conceptName: 'Concept source' },
      { title: 'Draft title', body: 'Draft body', imageBrief: 'Image brief' },
    )).toMatchObject({
      promotionName: 'Concept source',
      mainBanner: 'Draft title',
      supportingCopy: 'Draft body',
    });
  });
});
