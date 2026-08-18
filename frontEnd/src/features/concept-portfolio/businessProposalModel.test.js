import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import {
  CANDIDATE_FACT_FIELDS, HYPOTHESIS_TYPES, buildHypothesisChanges, candidateDefaultField,
    buildProposalPreview, businessDecisionReachability, businessDecisionStage, canOpenComparison, candidateFieldOptions,
  candidateRequests, comparisonRows, hypothesisDecisionLabel, createCandidateDraft,
  formatKoreanCurrencyAmount, groupLegalEvidence, hypothesisDisplay, hypothesisInputCount, hypothesisPresentation, legalStatusLabel, portfolioRunPresentation, serializeCandidateFact,
  serializeCandidateFacts, toggleComparedConcept,
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
    expect(candidateFieldOptions({ affectedFields: [] })).toEqual([]);
  });
  it('never creates a field outside the eight-field contract', () => {
    expect(serializeCandidateFact('unknownField', 'value')).toBeNull();
    expect(Object.keys(CANDIDATE_FACT_FIELDS)).toHaveLength(8);
    expect(readFileSync('src/features/concept-portfolio/pages/BusinessProposalWorkspace.jsx', 'utf8'))
      .not.toContain(['actual', 'Business', 'Fact'].join(''));
  });
  it('serializes every requested field together and requires every value', () => {
    const request = { affectedFields: ['personalDataUsage', 'paymentFlow'] };
    expect(createCandidateDraft(request)).toEqual({ values: { personalDataUsage: '', paymentFlow: '' } });
    expect(serializeCandidateFacts(request, { values: {
      personalDataUsage: '이름\n예약 이력', paymentFlow: '카드 결제\n판매자 정산',
    } })).toEqual({
      personalDataUsage: ['이름', '예약 이력'], paymentFlow: ['카드 결제', '판매자 정산'],
    });
    expect(serializeCandidateFacts(request, { values: {
      personalDataUsage: '이름', paymentFlow: '',
    } })).toBeNull();
  });
  it('keeps answered technical failures retryable without reopening input', () => {
    expect(candidateRequests([{ scope: 'CANDIDATE', status: 'ANSWERED', nextAction: 'RETRY_CONTINUATION' }])).toHaveLength(1);
  });
});

describe('proposal comparison and structured SOM', () => {
  it('comparison contract is exactly two and excludes legal details', () => {
    expect(toggleComparedConcept([], 'a')).toEqual(['a']);
    expect(toggleComparedConcept(['a'], 'b')).toEqual(['a', 'b']);
    expect(toggleComparedConcept(['a', 'b'], 'c')).toEqual(['a', 'b']);
    expect(toggleComparedConcept(['a', 'b'], 'a')).toEqual(['b']);
    expect([0, 1, 2, 3].map((count) => canOpenComparison(Array.from({ length: count })))).toEqual([false, false, true, false]);
    const rows = comparisonRows([{ conceptId: 'a', candidate: { targetUsers: ['소상공인'], revenueModel: '구독' } },
      { conceptId: 'b', candidate: { targetUsers: ['창업자'], revenueModel: '수수료' } }]);
    expect(rows.find((row) => row.label === '주요 고객').values).toEqual(['소상공인', '창업자']);
    expect(rows.find((row) => row.label === '수익 방식').values).toEqual(['구독', '수수료']);
    expect(rows.some((row) => row.label.includes('법률'))).toBe(false);
  });
  it('builds candidate-specific preview from existing fields with fallback', () => {
    const concepts = [
      { conceptId: 'a', summary: '같은 요약', candidate: { targetUsers: ['매장'], revenueModel: '월 구독', coreValue: '자동화' } },
      { conceptId: 'b', summary: '같은 요약', candidate: { targetUsers: ['고객'], revenueModel: '거래 수수료', coreValue: '자동화' } },
      { conceptId: 'c', summary: '같은 요약', candidate: { targetUsers: ['파트너'], revenueModel: '광고', coreValue: '자동화' } },
    ];
    expect(buildProposalPreview(concepts[0], concepts).highlights).toEqual(expect.arrayContaining([
      expect.objectContaining({ label: '주요 고객', value: '매장' }),
      expect.objectContaining({ label: '수익 방식', value: '월 구독' }),
    ]));
    expect(buildProposalPreview(concepts[0], concepts).highlights).toHaveLength(3);
    expect(buildProposalPreview({ summary: 'fallback', candidate: { coreValue: '가치' } }, []).highlights[0])
      .toMatchObject({ label: '핵심 가치', value: '가치' });
  });
  it('formats structured SOM without exposing JSON', () => {
    expect(hypothesisDisplay('PRE_MARKET_SOM_SHARE', { targetSharePercent: 2.5, horizonYears: 3 })).toBe('2.5% · 3년');
    expect(hypothesisDisplay('PRE_MARKET_SOM', { amount: 240000000, currency: 'KRW', period: '3년' })).toBe('240,000,000 KRW · 3년');
    expect(formatKoreanCurrencyAmount(50000000, 'KRW')).toBe('5천만 원');
    expect(formatKoreanCurrencyAmount(125000000, 'USD')).toBe('1억 2천5백만 달러');
    expect(formatKoreanCurrencyAmount(1_000_020_000_000, 'EUR')).toBe('1조 2천만 유로');
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
  it('shows provenance without turning an AI proposal into a progress blocker', () => {
    expect(hypothesisDecisionLabel({ decisionStatus: 'ACCEPTED', source: 'AI' })).toBe('AI 제안');
    expect(hypothesisDecisionLabel({ decisionStatus: 'USER_EDITED_ACCEPTED' })).toBe('사용자 수정');
    expect(hypothesisDecisionLabel({ decisionStatus: 'PROPOSED' })).toBe('AI 제안');
  });
  it('counts visible proposed values as input completion', () => {
    const seven = HYPOTHESIS_TYPES.map((hypothesisType) => ({ hypothesisType, proposedValue: `${hypothesisType}-value`, decisionStatus: 'PROPOSED' }));
    expect(hypothesisInputCount(seven)).toBe(7);
    expect(hypothesisInputCount(seven.map((item, index) => index === 2 ? { ...item, proposedValue: '' } : item))).toBe(6);
  });
  it('separates value presence from canonical confirmation blockers', () => {
    expect(hypothesisPresentation({ proposedValue: '월 구독', hasCurrentValue: true,
      confirmable: true, semanticStatus: 'VALID' })).toMatchObject({ hasCurrentValue: true, confirmable: true });
    expect(hypothesisPresentation({ proposedValue: '조건에 따라 변동', hasCurrentValue: true,
      confirmable: false, semanticStatus: 'INVALID', blockingReason: '가격 기준을 구체화해 주세요.' }))
      .toMatchObject({ hasCurrentValue: true, confirmable: false,
        blockingReason: '가격 기준을 구체화해 주세요.' });
  });
  it('contains exactly seven validation assumptions', () => expect(HYPOTHESIS_TYPES).toHaveLength(7));
});

describe('decision flow and legal grouping', () => {
  it('derives the presentation stage only from backend selection state', () => {
    expect(businessDecisionStage(null)).toBe('PROPOSAL_SELECTION');
    expect(businessDecisionStage({ status: 'PENDING_HYPOTHESIS_CONFIRMATION' })).toBe('BUSINESS_BASIS');
    expect(businessDecisionStage({ status: 'READY_FOR_LEGAL_REPORT' })).toBe('BUSINESS_BASIS');
    expect(businessDecisionStage({ status: 'LEGAL_REPORT_READY' })).toBe('LEGAL_REVIEW');
    expect(businessDecisionStage({ status: 'READY_FOR_MARKET' })).toBe('VALIDATION_PREP');
  });
  it('서버 데이터에서 조회 가능한 Decision 단계를 결정한다', () => {
    expect(businessDecisionReachability({ concepts: [{}] })).toEqual({ PROPOSAL_SELECTION: true, BUSINESS_BASIS: false, LEGAL_REVIEW: false, VALIDATION_PREP: false });
    expect(businessDecisionReachability({ concepts: [{}], selection: { status: 'PENDING_HYPOTHESIS_CONFIRMATION' } })).toEqual({ PROPOSAL_SELECTION: true, BUSINESS_BASIS: true, LEGAL_REVIEW: false, VALIDATION_PREP: false });
    expect(businessDecisionReachability({ concepts: [{}], selection: { status: 'LEGAL_REPORT_READY' }, report: {} })).toEqual({ PROPOSAL_SELECTION: true, BUSINESS_BASIS: true, LEGAL_REVIEW: true, VALIDATION_PREP: false });
    expect(businessDecisionReachability({ concepts: [{}], selection: { status: 'READY_FOR_MARKET' }, report: {} })).toEqual({ PROPOSAL_SELECTION: true, BUSINESS_BASIS: true, LEGAL_REVIEW: true, VALIDATION_PREP: true });
  });
  it('maps every legal status without exposing raw enums', () => {
    expect(['IMPLEMENTABLE', 'IMPLEMENTABLE_WITH_CONTROLS', 'NEEDS_FACTS', 'REDESIGNABLE', 'REJECTED'].map(legalStatusLabel)).toEqual([
      '현재 조건으로 진행 가능', '필요한 조치를 반영하면 진행 가능', '추가 정보 확인 필요', '일부 구조 조정 후 진행 가능', '현재 형태로 진행하기 어려움',
    ]);
  });
  it('groups by normalized law name, deduplicates articles, and preserves source fields', () => {
    const evidence = [
      { lawName: ' 개인정보 보호법 ', articleReference: '제15조', officialSourceUri: 'https://law/15', boundedProvisionSummary: '수집·이용', effectiveDate: '2025-01-01', contentHash: 'h15' },
      { lawName: '개인정보 보호법', articleReference: '제17조', officialSourceUri: 'https://law/17', boundedProvisionSummary: '제공', contentHash: 'h17' },
      { lawName: '개인정보 보호법', articleReference: '제17조', officialSourceUri: 'https://law/17', boundedProvisionSummary: '제공', contentHash: 'h17' },
      { lawName: '전자상거래법', articleReference: '제13조', officialSourceUri: 'https://law/e13' },
    ];
    const groups = groupLegalEvidence(evidence);
    expect(groups).toHaveLength(2);
    expect(groups[0].articles).toHaveLength(2);
    expect(groups[0].articles[1]).toMatchObject({ articleReference: '제17조', officialSourceUri: 'https://law/17', contentHash: 'h17' });
  });
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
