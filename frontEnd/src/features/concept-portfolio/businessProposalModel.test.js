import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import {
  CANDIDATE_FACT_FIELDS, HYPOTHESIS_TYPES, buildHypothesisChanges, candidateDefaultField,
  candidateFieldOptions, candidateRequests, hypothesisDecisionLabel, portfolioRunPresentation,
  serializeCandidateFact,
} from './businessProposalModel.js';

describe('candidate input strict contract', () => {
  it('serializes sellerRole as a string', () => {
    expect(serializeCandidateFact('sellerRole', ' 판매 법인 ')).toEqual({ sellerRole: '판매 법인' });
  });
  it.each(['paymentFlow', 'personalDataUsage'])('serializes %s as list[string]', (field) => {
    expect(serializeCandidateFact(field, '첫 항목\n\n둘째 항목 ')).toEqual({ [field]: ['첫 항목', '둘째 항목'] });
  });
  it('requires an explicit target for ambiguous or empty affectedFields', () => {
    expect(candidateDefaultField({ affectedFields: ['sellerRole', 'paymentFlow'] })).toBe('');
    expect(candidateFieldOptions({ affectedFields: ['sellerRole', 'paymentFlow'] })).toEqual(['sellerRole', 'paymentFlow']);
    expect(candidateDefaultField({ affectedFields: [] })).toBe('');
    expect(candidateFieldOptions({ affectedFields: [] })).toEqual(Object.keys(CANDIDATE_FACT_FIELDS));
  });
  it('never creates a field outside the eight-field contract', () => {
    expect(serializeCandidateFact('unknownField', 'value')).toBeNull();
    expect(Object.keys(CANDIDATE_FACT_FIELDS)).toHaveLength(8);
    expect(readFileSync('src/features/concept-portfolio/pages/BusinessProposalWorkspace.jsx', 'utf8'))
      .not.toContain(['actual', 'Business', 'Fact'].join(''));
  });
  it('keeps answered technical failures retryable without reopening input', () => {
    expect(candidateRequests([{ scope: 'CANDIDATE', status: 'ANSWERED', nextAction: 'RETRY_CONTINUATION' }])).toHaveLength(1);
  });
});

describe('hypothesis provenance', () => {
  const hypotheses = [
    { hypothesisType: 'PRICE', proposedValue: 1000, finalValue: 1200, locked: false },
    { hypothesisType: 'CHANNELS', proposedValue: ['온라인'], finalValue: null, locked: false },
    { hypothesisType: 'TARGET_REGION', proposedValue: '서울', finalValue: '서울', locked: true },
  ];
  it('sends only genuinely changed, unlocked values', () => {
    expect(buildHypothesisChanges(hypotheses, { PRICE: '1200', CHANNELS: '["오프라인"]', TARGET_REGION: '부산' }))
      .toEqual({ CHANNELS: ['오프라인'] });
    expect(buildHypothesisChanges(hypotheses, {})).toEqual({});
  });
  it('uses actual accepted statuses and user-facing locked copy', () => {
    expect(hypothesisDecisionLabel({ decisionStatus: 'ACCEPTED' })).toBe('확인됨');
    expect(hypothesisDecisionLabel({ decisionStatus: 'USER_EDITED_ACCEPTED' })).toBe('확인됨');
    expect(hypothesisDecisionLabel({ decisionStatus: 'PROPOSED' })).toBe('제안값');
    expect(hypothesisDecisionLabel({ locked: true })).toBe('확정된 사업 조건');
  });
  it('contains exactly seven validation assumptions', () => expect(HYPOTHESIS_TYPES).toHaveLength(7));
});

describe('portfolio run presentation', () => {
  it('never presents FAILED as completed', () => {
    expect(portfolioRunPresentation({ productStatus: 'FAILED' }).title).toBe('사업안 검토를 완료하지 못했습니다.');
    expect(portfolioRunPresentation({ productStatus: 'FAILED', failureCode: 'NO_ACCEPTED_CONCEPTS' }).title)
      .toBe('현재 조건에서 검토 가능한 사업안이 없습니다.');
  });
  it('explains partial results with open input', () => {
    expect(portfolioRunPresentation({ productStatus: 'RESULTS_WITH_OPEN_INPUT' })).toMatchObject({
      title: '검토 가능한 사업안이 준비되었습니다.', detail: '추가로 확인할 사업정보가 있습니다.',
    });
  });
});
