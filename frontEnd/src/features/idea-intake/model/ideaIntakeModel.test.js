import { describe, expect, it } from 'vitest';

import {
  CANONICAL_FIELD_CATALOG,
  createDerivePayload,
  createIdeaIntakeDraft,
  draftFromIdeaBrief,
  ideaIntakeDraftReducer,
  validateIdeaIntake,
} from './ideaIntakeModel.js';

describe('V2 Market Seed 모델', () => {
  it('필수 필드를 정확히 세 개로 제한한다', () => {
    expect(CANONICAL_FIELD_CATALOG.filter((field) => field.requiredForConcept).map((field) => field.key))
      .toEqual(['ideaOverview', 'problem', 'targetUsers']);
    expect(CANONICAL_FIELD_CATALOG.map((field) => field.key)).not.toContain('payment');
    expect(CANONICAL_FIELD_CATALOG.map((field) => field.key)).not.toContain('personalData');
  });

  it('세 필드를 모두 요구하고 optional 누락은 허용한다', () => {
    let draft = createIdeaIntakeDraft();
    expect(Object.keys(validateIdeaIntake(draft))).toEqual(['ideaOverview', 'problem', 'targetUsers']);
    for (const [field, value] of [['ideaOverview', '개요'], ['problem', '문제'], ['targetUsers', '사용자']]) {
      draft = ideaIntakeDraftReducer(draft, { type: 'UPDATE_INTAKE', field, value });
    }
    expect(validateIdeaIntake(draft)).toEqual({});
    expect(createDerivePayload(draft)).toMatchObject({
      ideaOverview: '개요', problem: '문제', targetUsers: '사용자',
      optionalSeed: { targetRegion: '', constraints: { budgetConstraint: '' } },
    });
  });

  it('사용자 optional 값을 LOCKED 출처로 복원하고 AI 해석을 분리한다', () => {
    const draft = draftFromIdeaBrief({
      overview: '개요',
      fields: [
        { fieldKey: 'ideaOverview', value: '개요', decisionState: 'LOCKED', provenance: 'USER_INPUT' },
        { fieldKey: 'problem', value: '문제', decisionState: 'LOCKED', provenance: 'USER_INPUT' },
        { fieldKey: 'targetUsers', value: '사용자', decisionState: 'LOCKED', provenance: 'USER_INPUT' },
        { fieldKey: 'price', value: '월 9,900원', decisionState: 'LOCKED', provenance: 'USER_INPUT' },
      ],
      interpretation: { interpretedProblem: '문제 해석', interpretedTargetUsers: '사용자 해석' },
    });
    expect(draft.fields.price).toEqual({
      value: '월 9,900원', source: 'USER_INPUT', decisionState: 'LOCKED', provenance: 'USER_INPUT',
    });
    expect(draft.interpretation.interpretedProblem).toBe('문제 해석');
  });
});
