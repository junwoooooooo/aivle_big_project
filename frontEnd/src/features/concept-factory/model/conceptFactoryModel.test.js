import { describe, expect, it } from 'vitest';

import { dedupeTimeline, evaluateRevealGate, workboardSummary } from './conceptFactoryModel.js';

const slots = Array.from({ length: 5 }, (_, index) => ({
  slotNumber: index + 1, status: 'ELIGIBLE', candidateCount: index + 1, legalReviewAttemptCount: 2,
  legalRedesignCount: index === 0 ? 1 : 0,
}));
const concepts = Array.from({ length: 5 }, (_, index) => ({
  conceptId: `c-${index}`, sourceSnapshotHash: 'sha256:same', canonicalHash: `canonical-${index}`,
  majorFieldHash: `major-${index}`, stale: false, legalStatus: 'IMPLEMENTABLE',
}));

describe('concept factory workboard model', () => {
  it('deduplicates replayed events but calculates business metrics only from backend state', () => {
    const events = [{ sequence: 2, eventType: 'job.concept.slot.rejected' }, { sequence: 1 }, { sequence: 2, eventType: 'job.concept.slot.rejected' }];
    expect(dedupeTimeline(events).map((event) => event.sequence)).toEqual([1, 2]);
    expect(workboardSummary({ eligibleCount: 5, initialCandidateSuccessCount: 4, generatedCandidateCount: 9,
      candidateGenerationFailureCount: 3, inspectedCandidateCount: 9, redesignCount: 1,
      replacementCandidateCount: 4, discardedCandidateCount: 4,
      providerTransientRetryCount: 2 }, slots, events)).toEqual({
      eligible: 5, initialGenerated: 4, generatedTotal: 9, generationFailed: 3, inspected: 9,
      redesigned: 1, replaced: 4, discarded: 4, providerRetries: 2,
    });
  });

  it('reveals only one complete, non-stale, non-duplicate snapshot set', () => {
    const run = { status: 'COMPLETED', sourceSnapshotHash: 'sha256:same' };
    expect(evaluateRevealGate(run, slots, concepts).canReveal).toBe(true);
    expect(evaluateRevealGate(run, slots, concepts.map((value, index) => index === 4 ? { ...value, canonicalHash: 'canonical-0' } : value)).canReveal).toBe(false);
    expect(evaluateRevealGate({ ...run, status: 'VALIDATING' }, slots, concepts).canReveal).toBe(false);
  });
});
