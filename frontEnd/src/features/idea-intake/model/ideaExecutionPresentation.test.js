import { describe, expect, it } from 'vitest';

import { ideaExecutionPresentation } from './ideaExecutionPresentation.js';

describe('idea execution presentation', () => {
  it('원시 이벤트를 사용자 단계로 바꾸고 unknown은 안전한 문구를 쓴다', () => {
    expect(ideaExecutionPresentation([{ stage: 'SAFETY_REVIEW', messageKey: 'job.idea.extracting', status: 'RUNNING' }]))
      .toMatchObject({ currentPhaseId: 'ELIGIBILITY', state: 'RUNNING' });
    expect(ideaExecutionPresentation([{ stage: 'IDEA_INTERPRETATION', messageKey: 'job.idea.questions.preparing', status: 'RUNNING' }]))
      .toMatchObject({ currentPhaseId: 'INTERPRETATION', state: 'RUNNING' });
    expect(ideaExecutionPresentation([{ messageKey: 'job.idea.future-stage', status: 'RUNNING' }]).activity)
      .toBe('결과를 준비하고 있습니다.');
  });

  it('실패와 입력 필요 상태를 보존한다', () => {
    expect(ideaExecutionPresentation([{ messageKey: 'job.idea.extracting', status: 'FAILED' }]).state).toBe('FAILED');
    expect(ideaExecutionPresentation([{ messageKey: 'job.idea.extracting', status: 'NEEDS_INPUT' }]).state).toBe('NEEDS_INPUT');
  });
});
