import {
  describe,
  expect,
  it,
} from 'vitest';

import {
  BUSINESS_PLAN_MAX_SIZE,
  validateBusinessPlanFile,
} from './filePolicy.js';

describe('business plan file policy', () => {
  it('accepts a supported DOCX file', () => {
    const file = new File(
      ['content'],
      'plan.docx',
      {
        type:
          'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      },
    );

    expect(
      validateBusinessPlanFile(file),
    ).toBe('');
  });

  it('rejects files larger than 20MB', () => {
    const file = {
      name: 'plan.docx',
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      size: BUSINESS_PLAN_MAX_SIZE + 1,
    };

    expect(
      validateBusinessPlanFile(file),
    ).toContain('20MB');
  });

  it('rejects PDF and HWPX files', () => {
    expect(validateBusinessPlanFile({
      name: 'plan.pdf',
      type: 'application/pdf',
      size: 10,
    })).toContain('DOCX');
    expect(validateBusinessPlanFile({
      name: 'plan.hwpx',
      type: 'application/vnd.hancom.hwpx',
      size: 10,
    })).toContain('DOCX');
  });
});
