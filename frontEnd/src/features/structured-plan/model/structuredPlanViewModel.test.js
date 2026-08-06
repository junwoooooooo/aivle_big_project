import { describe, expect, it } from 'vitest';

import {
  CANONICAL_SECTION_ORDER,
  STATUS_VIEW,
  toMissingFieldViewModel,
  toStructuredPlanViewModel,
} from './structuredPlanViewModel.js';

describe('structured plan view model', () => {
  it('sorts all canonical sections and maps Korean display names', () => {
    const sections = [...CANONICAL_SECTION_ORDER].reverse().map((sectionCode) => ({
      sectionCode,
      status: 'PRESENT',
    }));
    const result = toStructuredPlanViewModel({
      provider: 'mock',
      sections,
      missingFields: null,
    });

    expect(result.sections.map((section) => section.sectionCode))
      .toEqual(CANONICAL_SECTION_ORDER);
    expect(result.sections[0].displayName).toBe('사업 개요');
    expect(result.sections).toHaveLength(12);
    expect(result.isMock).toBe(true);
  });

  it('preserves all five domain statuses instead of reducing them to booleans', () => {
    expect(Object.keys(STATUS_VIEW)).toEqual([
      'PRESENT', 'MISSING', 'PARTIAL', 'INVALID', 'UNKNOWN',
    ]);
    expect(STATUS_VIEW.PRESENT.shortLabel).toBe('PASS');
    expect(STATUS_VIEW.INVALID.group).toBe('review');
  });

  it('maps lock versions and sorts open high-priority fields in canonical order', () => {
    const result = toStructuredPlanViewModel({
      version: 7,
      status: 'NEEDS_INPUT',
      provider: 'real',
      sections: [],
      missingFields: [
        {
          fieldId: 2,
          fieldCode: 'MARKET',
          sectionCode: 'MARKET_SIZE',
          status: 'OPEN',
          priority: 'MEDIUM',
          required: true,
          version: 4,
        },
        {
          fieldId: 1,
          fieldCode: 'OVERVIEW',
          sectionCode: 'BUSINESS_OVERVIEW',
          status: 'FILLED',
          priority: 'HIGH',
          required: true,
          version: 3,
        },
        {
          fieldId: 3,
          fieldCode: 'TARGET',
          sectionCode: 'TARGET_CUSTOMER',
          status: 'OPEN',
          priority: 'HIGH',
          required: true,
          version: 5,
        },
      ],
    });

    expect(result.lockVersion).toBe(7);
    expect(result.missingFields.map((field) => field.fieldId)).toEqual([3, 2, 1]);
    expect(result.missingFields[0].lockVersion).toBe(5);
    expect(result.missingFields[0].sectionDisplayName).toBe('목표 고객');
    expect(result.openRequiredCount).toBe(2);
  });

  it('marks confirmed fields read-only without changing their domain status', () => {
    const field = toMissingFieldViewModel({
      fieldId: 1,
      status: 'WAIVED',
      priority: 'HIGH',
      version: 2,
    }, 'CONFIRMED');

    expect(field.status).toBe('WAIVED');
    expect(field.isResolved).toBe(true);
    expect(field.isEditable).toBe(false);
  });
});
