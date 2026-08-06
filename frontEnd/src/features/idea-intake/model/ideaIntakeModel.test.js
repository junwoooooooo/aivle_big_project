import { describe, expect, it } from 'vitest';

import {
  applyQuestionAnswers,
  createIdeaIntakeDraft,
  FIELD_SOURCE,
  hydrateBriefFromIntake,
  ideaIntakeDraftReducer,
} from './ideaIntakeModel.js';

describe('ideaIntakeModel', () => {
  it('keeps intake, question answers, and edited Brief fields in one draft without leaking source enums', () => {
    let draft = createIdeaIntakeDraft();
    draft = ideaIntakeDraftReducer(draft, { type: 'UPDATE_INTAKE', field: 'problem', value: '지역 소상공인의 재고 폐기' });
    draft = hydrateBriefFromIntake(draft);
    draft = ideaIntakeDraftReducer(draft, { type: 'ANSWER_QUESTION', questionId: 'beneficiary', value: '동네 식당 운영자' });
    draft = applyQuestionAnswers(draft, [{ id: 'beneficiary', fieldKey: 'beneficiaries' }]);
    draft = ideaIntakeDraftReducer(draft, { type: 'UPDATE_BRIEF_FIELD', field: 'beneficiaries', value: '동네 식당과 제과점 운영자' });

    expect(draft.intake.problem).toBe('지역 소상공인의 재고 폐기');
    expect(draft.answers.beneficiary).toBe('동네 식당 운영자');
    expect(draft.fields.problem).toEqual({ value: '지역 소상공인의 재고 폐기', source: FIELD_SOURCE.USER_INPUT });
    expect(draft.fields.beneficiaries).toEqual({ value: '동네 식당과 제과점 운영자', source: FIELD_SOURCE.USER_INPUT });
  });
});
