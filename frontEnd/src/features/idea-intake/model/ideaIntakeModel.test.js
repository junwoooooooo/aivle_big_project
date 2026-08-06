import { describe, expect, it } from 'vitest';

import {
  applyQuestionAnswers,
  CANONICAL_FIELD_CATALOG,
  createIdeaIntakeDraft,
  DECISION_STATE,
  draftFromIdeaBrief,
  FIELD_SOURCE,
  hydrateBriefFromIntake,
  ideaIntakeDraftReducer,
} from './ideaIntakeModel.js';

describe('ideaIntakeModel', () => {
  it('keeps answers and edits in the canonical draft without locking all user input', () => {
    let draft = createIdeaIntakeDraft();
    draft = ideaIntakeDraftReducer(draft, {
      type: 'UPDATE_INTAKE', field: 'problem', value: '지역 소상공인의 재고 폐기',
    });
    draft = hydrateBriefFromIntake(draft);
    draft = ideaIntakeDraftReducer(draft, {
      type: 'ANSWER_QUESTION', questionId: 'beneficiary', value: '동네 상점 운영자',
    });
    draft = applyQuestionAnswers(draft, [{ id: 'beneficiary', fieldKey: 'beneficiaries' }]);
    draft = ideaIntakeDraftReducer(draft, {
      type: 'UPDATE_BRIEF_FIELD', field: 'beneficiaries', value: '동네 상점과 지역 운영자',
    });

    expect(draft.answers.beneficiary).toBe('동네 상점 운영자');
    expect(draft.fields.problem).toEqual({
      value: '지역 소상공인의 재고 폐기',
      source: FIELD_SOURCE.USER_INPUT,
      decisionState: DECISION_STATE.PREFERRED,
    });
    expect(draft.fields.beneficiaries).toEqual({
      value: '동네 상점과 지역 운영자',
      source: FIELD_SOURCE.USER_INPUT,
      decisionState: DECISION_STATE.PREFERRED,
    });
  });

  it('keeps overview separate from assumptions and aligns all canonical metadata', () => {
    const draft = draftFromIdeaBrief({
      overview: '원문 개요',
      fields: [{
        fieldKey: 'assumptions', value: '검증할 가정', decisionState: 'ASSUMPTION', provenance: 'AI_PROPOSED',
      }],
    });

    expect(CANONICAL_FIELD_CATALOG).toHaveLength(15);
    expect(CANONICAL_FIELD_CATALOG.map((field) => field.key)).toEqual(Object.keys(draft.fields));
    expect(draft.intake.overview).toBe('원문 개요');
    expect(draft.fields.assumptions.value).toBe('검증할 가정');
    expect(draft.fields.fixedConditions.decisionState).toBe(DECISION_STATE.LOCKED);
  });
});
