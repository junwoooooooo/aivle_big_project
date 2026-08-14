import { describe, expect, it } from 'vitest';
import {
  CONSTRAINT_FIELDS, PLAN_FIELDS, buildConceptPlanSuggestions, draftFrom, emptyCellNames, emptyDraft, toPayload,
} from './bmPlan.js';

/**
 * 「비었다」의 정의를 재는 검사들.
 *
 * 이 규칙이 층마다 다르면 화면은 「썼다」고 하고 AI 는 「없다」고 읽는다.
 * 서버(`BmPlanPreparationService`)도 같은 규칙을 지킨다 — 둘이 갈라지면 여기가 먼저 빨개진다.
 */
describe('실행 계획 초안 → 보낼 값', () => {
  it('컨셉이 주지 않는 넷만 묻는다 — 수익모델·채널·차별점은 가설 4가 이미 정했다', () => {
    expect(PLAN_FIELDS.map(([key]) => key)).toEqual([
      'customer_relationship', 'key_activities', 'key_resources', 'key_partners',
    ]);
  });

  it('목록은 줄바꿈으로 가른다 — 빈 줄과 공백은 버린다', () => {
    const { plan } = toPayload({
      ...emptyDraft(),
      key_activities: '예약 통합\n\n  보증금 청구  \n',
    });
    expect(plan.key_activities).toEqual(['예약 통합', '보증금 청구']);
  });

  it('⭐ 빈 칸은 키 자체를 안 만든다 — 「안 썼다」와 「비웠다」를 섞지 않는다', () => {
    const { plan, constraints } = toPayload(emptyDraft());
    expect(plan).toEqual({});
    expect(constraints).toEqual({});
  });

  it('⭐ 비용은 정수로만 올라간다 — 소수는 canonical hash 가 거부한다', () => {
    const { constraints } = toPayload({
      ...emptyDraft(), budget_krw: '5000000.5', months: '10', team: '2',
    });
    expect(constraints).toEqual({ months: 10, team: 2 });
  });

  it('숫자가 아니면 0 으로 지어내지 않고 안 보낸다', () => {
    const { constraints } = toPayload({ ...emptyDraft(), budget_krw: '오천만원' });
    expect(constraints).toEqual({});
  });

  it('0 은 값이다 — 인원 0명은 안 쓴 것과 다르다', () => {
    const { constraints } = toPayload({ ...emptyDraft(), team: '0' });
    expect(constraints).toEqual({ team: 0 });
  });
});

describe('선택 사업안에서 가져온 운영 초안', () => {
  it('활동·자원·파트너 세 항목만 기존 사업안 재료로 구성한다', () => {
    const suggestions = buildConceptPlanSuggestions({ candidate: { candidate: {
      operatingModel: '예약 운영', transactionFlow: ['고객 신청', '매장 확정'],
      platformRole: '예약 연결', featureSet: ['알림', '대시보드'],
      partnerModel: '지역 매장', partnerRequirements: ['PG'],
    } } });
    expect(suggestions.key_activities).toContain('예약 운영\n고객 신청\n매장 확정');
    expect(suggestions.key_resources).toContain('예약 연결\n알림\n대시보드');
    expect(suggestions.key_partners).toBe('지역 매장\nPG');
    expect(suggestions).not.toHaveProperty('customer_relationship');
    expect(suggestions).not.toHaveProperty('budget_krw');
  });
});

describe('서버가 준 저장분 → 화면 초안', () => {
  it('목록은 줄바꿈으로 합쳐 돌아온다 — 저장·재편집이 왕복한다', () => {
    const draft = draftFrom({
      plan: { key_partners: ['PG', '예약 플랫폼'], customer_relationship: '자동 알림' },
      constraints: { budget_krw: 5000000 },
    });
    expect(draft.key_partners).toBe('PG\n예약 플랫폼');
    expect(draft.customer_relationship).toBe('자동 알림');
    expect(draft.budget_krw).toBe('5000000');
    expect(draft.months).toBe('');
  });

  it('저장분이 없으면 빈 초안이다 — 던지지 않는다', () => {
    expect(draftFrom(null)).toEqual(emptyDraft());
    expect(draftFrom({})).toEqual(emptyDraft());
  });
});

describe('제출 전 확인 — 무엇이 빌지 이름으로 말한다', () => {
  it('빈 칸을 사람이 읽는 이름으로 센다', () => {
    const names = emptyCellNames({ ...emptyDraft(), key_partners: 'PG' });
    expect(names).toContain('고객 관계');
    expect(names).toContain('비용 구조');
    expect(names).not.toContain('핵심 파트너');
  });

  it('전부 채우면 확인할 것이 없다', () => {
    const full = Object.fromEntries([
      ...PLAN_FIELDS.map(([key]) => [key, '값']),
      ...CONSTRAINT_FIELDS.map(([key]) => [key, '1']),
    ]);
    expect(emptyCellNames(full)).toEqual([]);
  });

  it('비용은 하나만 채워도 칸이 선다 — 셋 다 요구하지 않는다', () => {
    expect(emptyCellNames({ ...emptyDraft(), budget_krw: '5000000' }))
      .not.toContain('비용 구조');
  });
});
