import { describe, expect, it } from 'vitest';
import { HYPOTHESIS_TYPES, canOpenComparison, normalizePortfolioConcepts, openCandidateRequests, selectedConceptId, toggleComparedConcept } from './businessProposalModel.js';

describe('Business Proposal Workspace contract', () => {
  it.each([1, 2, 3, 4, 5])('accepts %i reviewed business proposals without empty slots', (count) => {
    const values = Array.from({ length: count }, (_, index) => ({ conceptId: `c${index}`, selectable: true }));
    expect(normalizePortfolioConcepts(values)).toHaveLength(count);
  });
  it('limits optional comparison to two or three proposals', () => {
    let ids = [];
    for (const id of ['a', 'b', 'c', 'd']) ids = toggleComparedConcept(ids, id);
    expect(ids).toEqual(['a', 'b', 'c']);
    expect(canOpenComparison(['a'])).toBe(false);
    expect(canOpenComparison(['a', 'b'])).toBe(true);
    expect(canOpenComparison(['a', 'b', 'c'])).toBe(true);
  });
  it('keeps mixed candidate input actionable without changing explicit selection', () => {
    const requests = [{ candidateId: 'pending', scope: 'CANDIDATE', status: 'OPEN' }];
    expect(openCandidateRequests(requests)).toHaveLength(1);
    expect(selectedConceptId({ conceptId: 'accepted-a' })).toBe('accepted-a');
  });
  it('makes candidate input the primary state when there are no accepted proposals', () => {
    expect(normalizePortfolioConcepts([])).toEqual([]);
    expect(openCandidateRequests([{ candidateId: 'pending', scope: 'CANDIDATE', status: 'OPEN' }])).toHaveLength(1);
  });
  it('contains exactly seven validation assumptions', () => {
    expect(HYPOTHESIS_TYPES).toHaveLength(7);
  });
});
