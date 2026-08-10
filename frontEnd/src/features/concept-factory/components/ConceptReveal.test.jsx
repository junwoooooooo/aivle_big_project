import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import ConceptReveal from './ConceptReveal.jsx';

describe('ConceptReveal V2', () => {
  it('shows source semantics, pre-market SOM warning, and official legal evidence', () => {
    render(<ConceptReveal concepts={[{
      conceptId: 'concept-1', slotNumber: 1, title: '예약 도우미', summary: '예약 확인을 돕습니다.',
      legalStatus: 'IMPLEMENTABLE_WITH_CONTROLS', candidate: {
        generationStrategy: 'AS_IS', originalCandidate: true, introduction: '예약 업무를 단순화합니다.',
        conceptDefinition: '예약 확인 자동화', coreValue: '확인 업무 절감', targetUsers: '소형 매장',
        industryCategory: '예약 관리', researchScope: '국내 소형 매장', targetRegion: '대한민국',
        revenueModel: '월 구독', price: '월 9,900원', channels: '직접 영업', differentiators: '당일 도입',
        problemScenario: '반복 확인이 필요합니다.', solutionMechanism: '예약 알림', featureSet: ['자동 알림'],
        actorRoles: ['매장', '고객'], operatingModel: '예약 중개', partnerModel: '매장 직접 가입',
        platformRole: '예약 정보 중개', providerRole: '플랫폼 기능 제공자', sellerRole: '예약 사업자',
        intermediaryRole: '예약 정보 중개자', paymentFlow: ['매장이 플랫폼에 구독료 결제'],
        partnerRequirements: [], qualificationRequirements: [],
        preMarketSomShareHypothesis: { targetSharePercent: 2, horizonYears: 3, rationale: '초기 집중' },
        preMarketSomHypothesis: { amount: 100000000, currency: 'KRW', period: '연간', confidence: 'LOW' },
        valueSemantics: [{ fieldKey: 'revenueModel', source: 'USER_INPUT', authority: 'LOCKED', decision: 'ACCEPTED' }],
      },
      legalReview: {
        status: 'IMPLEMENTABLE_WITH_CONTROLS', safeSummary: '통제를 반영하면 구현할 수 있습니다.',
        assessment: { reviewedActivities: ['예약 연락처 처리'], requiredControls: ['처리방침 공개'],
          requiredPartnersAndQualifications: [], requiredDisclosures: ['결제 조건 고지'], prohibitedVariants: ['무단 사용'],
          unknownFacts: ['보관 기간'], expertReviewRecommended: true, reviewLimitations: '공식 법령 기반 사전검토입니다.',
          legalFactPattern: { commercialRoles: { sellerRole: { value: '매장이 최종 판매자' } },
            paymentFlow: { value: ['매장이 플랫폼에 월 구독료 결제'] } } },
        evidence: [{ lawName: '개인정보 보호법', articleReference: '제30조', title: '처리방침', effectiveDate: '2025-03-13',
          retrievedAt: '2026-08-07T00:00:00', officialSourceUri: 'https://www.law.go.kr/법령/개인정보보호법' }],
      },
    }]} />);

    expect(screen.getByText('사용자 원안 구조화')).toBeInTheDocument();
    expect(screen.getByText('USER_INPUT · LOCKED · ACCEPTED')).toBeInTheDocument();
    expect(screen.getByText(/실제 시장분석 결과가 아닙니다/)).toBeInTheDocument();
    expect(screen.getByText('매장이 최종 판매자')).toBeInTheDocument();
    expect(screen.getByText('매장이 플랫폼에 월 구독료 결제')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /개인정보 보호법 제30조/ })).toHaveAttribute(
      'href', 'https://www.law.go.kr/법령/개인정보보호법');
  });
});
