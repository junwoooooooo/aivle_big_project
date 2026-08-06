import { describe, expect, it } from 'vitest';

import { eventsForSlot, publicConceptGate, sortSlots } from './conceptWorkboardModel.js';

const batch = {
  status: 'COMPLETED', confirmedBriefVersionId: 10, briefHash: 'sha256:brief',
  regulatoryBoundaryVersionId: 20, boundaryHash: 'sha256:boundary',
};
const slots = [0, 1, 2].map((slotIndex) => ({ slotIndex, status: 'ELIGIBLE', eligible: true }));
const concept = (id) => ({
  conceptId: id, confirmedBriefVersionId: 10, briefHash: 'sha256:brief',
  regulatoryBoundaryVersionId: 20, boundaryHash: 'sha256:boundary', stale: false,
  duplicateStatus: 'UNIQUE', legalState: id === 2 ? 'IMPLEMENTABLE_WITH_CONTROLS' : 'IMPLEMENTABLE',
});

describe('conceptWorkboardModel', () => {
  it('keeps slots stable by slot index regardless of query or event order', () => {
    expect(sortSlots([{ slotIndex: 2 }, { slotIndex: 0 }, { slotIndex: 1 }]).map((slot) => slot.slotIndex)).toEqual([0, 1, 2]);
    expect(eventsForSlot([
      { sequence: 3, messageParams: { slotIndex: 1 } },
      { sequence: 2, messageParams: { slotIndex: 0 } },
      { sequence: 1, messageParams: { slotIndex: 1 } },
      { sequence: 1, messageParams: { slotIndex: 1 } },
    ], 1).map((event) => event.sequence)).toEqual([1, 3]);
  });

  it('opens the public gate only for exactly three matching eligible concepts', () => {
    expect(publicConceptGate(batch, slots, [concept(1), concept(2), concept(3)])).toEqual({ allowed: true, reason: null });
    expect(publicConceptGate(batch, slots, [concept(1), concept(2)]).reason).toBe('PUBLIC_CONCEPT_COUNT_MISMATCH');
    expect(publicConceptGate(batch, slots, [concept(1), concept(2), concept(3), concept(4)]).reason).toBe('PUBLIC_CONCEPT_COUNT_MISMATCH');
  });

  it('keeps stale, duplicate, mismatched and non-public legal states hidden', () => {
    for (const override of [
      { stale: true }, { duplicateStatus: 'DUPLICATE' }, { briefHash: 'sha256:other' }, { legalState: 'HARD_BLOCK' },
    ]) {
      expect(publicConceptGate(batch, slots, [concept(1), concept(2), { ...concept(3), ...override }]).allowed).toBe(false);
    }
  });
});
