import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import ConceptReveal from './ConceptReveal.jsx';

describe('ConceptReveal legal evidence report', () => {
  it('shows official source metadata and pre-review limitations without raw legal text', () => {
    render(<ConceptReveal concepts={[{
      conceptId: 'concept-1', slotNumber: 1, title: '예약 도우미', summary: '예약 확인을 돕습니다.',
      legalStatus: 'IMPLEMENTABLE_WITH_CONTROLS', candidate: {
        valueProposition: '확인 업무 절감', solutionMechanism: '예약 알림', operatingModel: '예약 중개', partnerRequirements: [],
      },
      legalReview: {
        status: 'IMPLEMENTABLE_WITH_CONTROLS', safeSummary: '통제를 반영하면 구현 가능성이 있습니다.',
        assessment: {
          reviewedActivities: ['예약 연락처 처리'], requiredControls: ['처리방침 공개'],
          requiredPartnersAndQualifications: [], requiredDisclosures: ['결제 조건 고지'], prohibitedVariants: ['무단 활용'],
          unknownFacts: ['보관 기간'], expertReviewRecommended: true,
          reviewLimitations: '공식 법령의 제한된 조문과 확인 시점을 기준으로 한 사전검토입니다.',
        },
        evidence: [{ sourceType: 'OFFICIAL_LAW', lawId: 'LAW-100', lawName: '개인정보 보호법',
          articleReference: '제30조', title: '개인정보 처리방침', effectiveDate: '2025-03-13',
          retrievedAt: '2026-08-07T00:00:00', officialSourceUri: 'https://www.law.go.kr/법령/개인정보보호법' }],
      },
    }]} />);

    expect(screen.getByRole('heading', { name: /공식 근거 기반 법률 구현 가능성 사전검토/ })).toBeInTheDocument();
    expect(screen.getByText('예약 연락처 처리')).toBeInTheDocument();
    expect(screen.getByText(/전문가 검토 권장: 예/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /개인정보 보호법 제30조/ })).toHaveAttribute(
      'href', 'https://www.law.go.kr/법령/개인정보보호법');
    expect(screen.getByText(/시행 기준 2025-03-13/)).toBeInTheDocument();
    expect(screen.queryByText(/제한된 조문 원문/)).not.toBeInTheDocument();
  });
});
