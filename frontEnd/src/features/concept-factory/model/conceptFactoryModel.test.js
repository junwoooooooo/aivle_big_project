import { describe, expect, it } from 'vitest';

import { dedupeTimeline, evaluateRevealGate, workboardSummary } from './conceptFactoryModel.js';

const slots = Array.from({ length: 5 }, (_, index) => ({
  slotNumber: index + 1, status: 'ELIGIBLE', attemptCount: index + 1, legalRedesignCount: index === 0 ? 1 : 0,
}));
const concepts = Array.from({ length: 5 }, (_, index) => ({
  conceptId: `c-${index}`, sourceSnapshotHash: 'sha256:same', canonicalHash: `canonical-${index}`,
  majorFieldHash: `major-${index}`, stale: false, legalStatus: 'IMPLEMENTABLE',
}));

describe('concept factory workboard model', () => {
  it('deduplicates replayed events and calculates bounded counters', () => {
    const events = [{ sequence: 2, eventType: 'job.concept.slot.rejected' }, { sequence: 1 }, { sequence: 2, eventType: 'job.concept.slot.rejected' }];
    expect(dedupeTimeline(events).map((event) => event.sequence)).toEqual([1, 2]);
    expect(workboardSummary({ replacementRounds: 2 }, slots, events)).toEqual({ eligible: 5, inspected: 15, redesigned: 1, replaced: 2, discarded: 1 });
  });

  it('reveals only one complete, non-stale, non-duplicate snapshot set', () => {
    const run = { status: 'COMPLETED', sourceSnapshotHash: 'sha256:same' };
    expect(evaluateRevealGate(run, slots, concepts).canReveal).toBe(true);
    expect(evaluateRevealGate(run, slots, concepts.map((value, index) => index === 4 ? { ...value, canonicalHash: 'canonical-0' } : value)).canReveal).toBe(false);
    expect(evaluateRevealGate({ ...run, status: 'VALIDATING' }, slots, concepts).canReveal).toBe(false);
  });
});
