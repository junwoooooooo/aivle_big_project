import { describe, expect, it } from 'vitest';
import { getProjectModules, MODULE_STATUS } from './projectModuleModel.js';
import { aggregateJourneyStatus, getJourneyByPath, getProjectJourneys, JOURNEY_STATUS } from './projectJourneyModel.js';

describe('project journey model', () => {
  it.each([
    ['/idea', 'planning'], ['/concepts', 'planning'], ['/market', 'validation'],
    ['/business-model', 'validation'], ['/tech-ops', 'launch'], ['/finance', 'launch'],
    ['/twin-survey', 'interview'], ['/marketing', 'marketingStrategy'], ['/final-report', 'finalReport'],
  ])('%s 경로를 %s Journey로 연결한다', (path, journey) => {
    expect(getJourneyByPath(`/app/projects/41${path}`).id).toBe(journey);
  });

  it('하위 모듈 상태를 결정적으로 집계한다', () => {
    expect(aggregateJourneyStatus([MODULE_STATUS.COMPLETED, MODULE_STATUS.COMPLETED])).toBe(JOURNEY_STATUS.COMPLETED);
    expect(aggregateJourneyStatus([MODULE_STATUS.COMPLETED, MODULE_STATUS.READY])).toBe(JOURNEY_STATUS.IN_PROGRESS);
    expect(aggregateJourneyStatus([MODULE_STATUS.RUNNING, MODULE_STATUS.NOT_READY])).toBe(JOURNEY_STATUS.IN_PROGRESS);
    expect(aggregateJourneyStatus([MODULE_STATUS.COMPLETED, MODULE_STATUS.NEEDS_INPUT])).toBe(JOURNEY_STATUS.NEEDS_INPUT);
    expect(aggregateJourneyStatus([MODULE_STATUS.STALE])).toBe(JOURNEY_STATUS.STALE);
  });

  it('첫 미완료 substep을 상위 Journey 진입 경로로 사용한다', () => {
    const modules = getProjectModules('41', {
      market: { status: MODULE_STATUS.COMPLETED },
      businessModel: { status: MODULE_STATUS.READY },
    });
    expect(getProjectJourneys('41', modules).find(({ id }) => id === 'validation').href)
      .toBe('/app/projects/41/business-model');
  });
});
