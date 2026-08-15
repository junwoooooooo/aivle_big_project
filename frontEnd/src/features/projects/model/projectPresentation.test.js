import { describe, expect, it } from 'vitest';

import { JOURNEY_STATUS } from '../../../app/module-status/projectJourneyModel.js';
import { deriveProjectPresentationState, getProjectPresentationView } from './projectPresentation.js';

describe('project presentation', () => {
  it('keeps a pristine default input journey in not-started state', () => {
    expect(deriveProjectPresentationState([{
      status: JOURNEY_STATUS.NEEDS_INPUT,
      children: [{ status: JOURNEY_STATUS.NEEDS_INPUT, updatedAt: null }],
    }])).toBe('NOT_STARTED');
  });

  it('시작 가능 상태만 있는 새 프로젝트를 시작 전으로 표시한다', () => {
    expect(deriveProjectPresentationState([{ status: JOURNEY_STATUS.READY }, { status: JOURNEY_STATUS.NOT_STARTED }]))
      .toBe('NOT_STARTED');
  });

  it('입력·업데이트가 필요한 실제 진행 상태를 확인 필요로 표시한다', () => {
    expect(deriveProjectPresentationState([{ status: JOURNEY_STATUS.COMPLETED }, { status: JOURNEY_STATUS.STALE }]))
      .toBe('NEEDS_ATTENTION');
    expect(getProjectPresentationView('NEEDS_ATTENTION').label).toBe('확인 필요');
  });

  it('모든 여정을 완료하면 완료로 표시한다', () => {
    expect(deriveProjectPresentationState(Array.from({ length: 6 }, () => ({ status: JOURNEY_STATUS.COMPLETED }))))
      .toBe('COMPLETED');
  });
});
