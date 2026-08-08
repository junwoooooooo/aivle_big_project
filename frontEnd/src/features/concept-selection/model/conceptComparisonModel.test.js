import { describe, expect, it } from 'vitest';

import { createLocalSelectionDraft, deriveComparisonTags, toComparisonModel } from './conceptComparisonModel.js';

const concept = {
  conceptId: 'concept-1', slotNumber: 1, variationFocus: 'LOW_RISK_FAST_EXECUTION', title: '정기 운영 도우미', summary: '기업 매장의 반복 업무를 돕습니다.',
  legalStatus: 'IMPLEMENTABLE_WITH_CONTROLS',
  candidate: {
    targetUsers: 'B2B 기업 매장', problemScenario: '반복 운영이 어렵다.', coreValue: '업무를 단순화한다.',
    differentiators: '당일 도입',
    transactionFlow: ['신청', '배정'], platformRole: '중개 및 기록', featureSet: ['예약', '정산'], actorRoles: ['기업', '운영자'],
    partnerRequirements: [], physicalActivities: [], revenueModel: '월간 구독', price: '월 정액',
    operatingModel: '직영 운영', constraintCompliance: ['수요 변동'], solutionMechanism: '작은 지역부터 시작', channels: '직접 영업',
  },
  legalReview: { assessment: { requiredControls: ['사전 고지'] } },
};

describe('concept comparison model', () => {
  it('maps server fields and derives only deterministic comparison tags', () => {
    const model = toComparisonModel(concept);
    expect(model.targetCustomer).toBe('B2B 기업 매장');
    expect(model.requiredControls).toBe('사전 고지');
    expect(deriveComparisonTags(concept)).toEqual(['빠른 실행', '초기 비용 낮음', '반복 수익형', 'B2B 중심', '규제 통제 필요']);
  });

  it('creates a session-only draft for 2 to 5 compared concepts and one preferred candidate', () => {
    const draft = createLocalSelectionDraft(7, ['a', 'b'], 'b');
    expect(draft).toMatchObject({ projectId: '7', comparedConceptIds: ['a', 'b'], preferredConceptId: 'b', persistence: 'SESSION_LOCAL_ONLY' });
    expect(() => createLocalSelectionDraft(7, ['a'], 'a')).toThrow('2개 이상');
    expect(() => createLocalSelectionDraft(7, ['a', 'b'], 'c')).toThrow('선택 후보');
  });
});
