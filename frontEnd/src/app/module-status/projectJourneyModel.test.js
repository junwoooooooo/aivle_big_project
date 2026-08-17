import { describe, expect, it } from 'vitest';
import { getProjectModules, MODULE_STATUS } from './projectModuleModel.js';
import { aggregateJourneyStatus, getJourneyByPath, getProjectJourneys, JOURNEY_STATUS, PROJECT_JOURNEYS } from './projectJourneyModel.js';

describe('project journey model', () => {
  it.each([
    ['/idea', 'planning'], ['/concepts', 'planning'], ['/concepts/compare', 'planning'], ['/concepts/legal-report', 'planning'], ['/business-validation', 'validation'], ['/market', 'validation'],
    ['/business-model', 'validation'], ['/launch-readiness', 'launch'], ['/technology', 'launch'],
    ['/launch-readiness/reports/technology', 'launch'],
    ['/operations', 'launch'], ['/tech-ops', 'launch'], ['/finance', 'launch'],
    ['/market-interview', 'interview'], ['/virtual-interview', 'interview'], ['/twin-survey', 'interview'], ['/marketing', 'marketingStrategy'], ['/final-report', 'finalReport'],
  ])('%s 경로를 %s Journey로 연결한다', (path, journey) => {
    expect(getJourneyByPath(`/app/projects/41${path}`).id).toBe(journey);
  });

  it('top-level Journey를 canonical 여섯 단계로 고정한다', () => {
    expect(PROJECT_JOURNEYS).toHaveLength(6);
    expect(PROJECT_JOURNEYS.map(({ id }) => id)).toEqual([
      'planning', 'validation', 'launch', 'interview', 'marketingStrategy', 'finalReport',
    ]);
    expect(PROJECT_JOURNEYS.map(({ shortLabel }) => shortLabel)).toEqual([
      '사업 기획', '사업 검증', '출시 준비', '가상 인터뷰', '마케팅 전략', '최종 보고서',
    ]);
  });

  it('하위 모듈 상태를 결정적으로 집계한다', () => {
    expect(aggregateJourneyStatus([MODULE_STATUS.COMPLETED, MODULE_STATUS.COMPLETED])).toBe(JOURNEY_STATUS.COMPLETED);
    expect(aggregateJourneyStatus([MODULE_STATUS.COMPLETED, MODULE_STATUS.READY])).toBe(JOURNEY_STATUS.IN_PROGRESS);
    expect(aggregateJourneyStatus([MODULE_STATUS.RUNNING, MODULE_STATUS.NOT_READY])).toBe(JOURNEY_STATUS.IN_PROGRESS);
    expect(aggregateJourneyStatus([MODULE_STATUS.COMPLETED, MODULE_STATUS.NEEDS_INPUT])).toBe(JOURNEY_STATUS.NEEDS_INPUT);
    expect(aggregateJourneyStatus([MODULE_STATUS.STALE])).toBe(JOURNEY_STATUS.STALE);
  });

  it('사업 검증 Journey는 시장과 BM 상태를 묶어 canonical route 하나를 사용한다', () => {
    const modules = getProjectModules('41', {
      market: { status: MODULE_STATUS.COMPLETED },
      businessModel: { status: MODULE_STATUS.READY },
    });
    expect(getProjectJourneys('41', modules).find(({ id }) => id === 'validation').href)
      .toBe('/app/projects/41/business-validation');
    expect(getProjectJourneys('41', modules).find(({ id }) => id === 'validation').children).toHaveLength(1);
  });

  it('가상 인터뷰 Journey 안에서 시장 인터뷰 다음 트윈 패널 route를 선택한다', () => {
    const modules = getProjectModules('41', {
      marketInterview: { status: MODULE_STATUS.READY }, twinSurvey: { status: MODULE_STATUS.NOT_READY },
    });
    const journeys = getProjectJourneys('41', modules);
    const interview = journeys.find(({ id }) => id === 'interview');
    expect(interview.href).toBe('/app/projects/41/market-interview');
    expect(interview.children.map(({ id }) => id)).toEqual(['marketInterview', 'twinSurvey']);
    expect(journeys.map(({ id }) => id)).toEqual([
      'planning', 'validation', 'launch', 'interview', 'marketingStrategy', 'finalReport',
    ]);

    const completedMarketInterview = getProjectModules('41', {
      marketInterview: { status: MODULE_STATUS.COMPLETED }, twinSurvey: { status: MODULE_STATUS.READY },
    });
    expect(getProjectJourneys('41', completedMarketInterview).find(({ id }) => id === 'interview').href)
      .toBe('/app/projects/41/twin-survey');
  });
});
