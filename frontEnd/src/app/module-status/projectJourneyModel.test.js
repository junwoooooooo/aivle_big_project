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

  it('출시 준비는 canonical 한 단계이며 기술·운영·재무를 하위 Journey로 노출하지 않는다', () => {
    const modules = getProjectModules('41', {
      techOps: { status: MODULE_STATUS.READY },
      finance: { status: MODULE_STATUS.FAILED },
      launchReadiness: { status: MODULE_STATUS.READY },
    });
    const launch = getProjectJourneys('41', modules).find(({ id }) => id === 'launch');
    expect(launch.href).toBe('/app/projects/41/launch-readiness');
    expect(launch.children).toEqual([]);
    expect(launch.status).toBe(JOURNEY_STATUS.ATTENTION);
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
      conceptRefinement: { status: MODULE_STATUS.NOT_READY },
    });
    expect(getProjectJourneys('41', modules).find(({ id }) => id === 'validation').href)
      .toBe('/app/projects/41/business-validation');
    expect(getProjectJourneys('41', modules).find(({ id }) => id === 'validation').children).toHaveLength(1);
  });

  it('사업 검증은 Market과 BM만 완료되어도 refinement 전에는 완료되지 않는다', () => {
    const waiting = getProjectModules('41', {
      market: { status: MODULE_STATUS.COMPLETED },
      businessModel: { status: MODULE_STATUS.COMPLETED },
      conceptRefinement: { status: MODULE_STATUS.READY },
    });
    expect(getProjectJourneys('41', waiting).find(({ id }) => id === 'validation').status)
      .toBe(JOURNEY_STATUS.IN_PROGRESS);
    const completed = getProjectModules('41', {
      market: { status: MODULE_STATUS.COMPLETED },
      businessModel: { status: MODULE_STATUS.COMPLETED },
      conceptRefinement: { status: MODULE_STATUS.COMPLETED },
    });
    expect(getProjectJourneys('41', completed).find(({ id }) => id === 'validation').status)
      .toBe(JOURNEY_STATUS.COMPLETED);
  });

  it('가상 인터뷰 Journey를 canonical 시장 인터뷰 슬롯 하나로 집계한다', () => {
    const modules = getProjectModules('41', {
      marketInterview: { status: MODULE_STATUS.COMPLETED },
    });
    const journeys = getProjectJourneys('41', modules);
    const interview = journeys.find(({ id }) => id === 'interview');
    expect(interview.href).toBe('/app/projects/41/market-interview');
    expect(interview.children.map(({ id }) => id)).toEqual(['marketInterview']);
    expect(interview.status).toBe(JOURNEY_STATUS.COMPLETED);
    expect(journeys.map(({ id }) => id)).toEqual([
      'planning', 'validation', 'launch', 'interview', 'marketingStrategy', 'finalReport',
    ]);
  });
});
