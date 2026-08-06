import { describe, expect, it } from 'vitest';

import {
  PROJECT_AREAS,
  PROJECT_STATUS_VIEW,
  TASK_STATUS_VIEW,
  PROJECT_AREA_DEFINITIONS,
  STAGE_AREA,
  STAGE_VIEW,
  getAreaSummary,
  getProjectArea,
  getProjectNextAction,
  getProjectProgress,
  getProjectStatusView,
} from './projectWorkflowModel.js';

const project = {
  projectId: '12',
  status: 'ACTIVE',
  stage: 'LEGAL_REVIEW',
};

describe('project workflow model', () => {
  it('maps the durable project stage to one product area and a canonical next route', () => {
    expect(getProjectArea(project)).toBe(PROJECT_AREAS.REVIEW);
    expect(getProjectNextAction(project).route).toBe('/app/projects/12/legal');
  });

  it('keeps project status separate from unavailable task-level status', () => {
    expect(getAreaSummary(project)[1].taskStatus).toBe('UNKNOWN');
    expect(getProjectStatusView('ACTIVE').label).toBe('진행 중');
  });

  it('provides safe unknown fallbacks and bounded progress', () => {
    expect(getProjectStatusView('UNRECOGNIZED').label).toBe('상태 확인 필요');
    expect(getProjectProgress({ stage: 'UNRECOGNIZED' })).toBe(0);
  });

  it('preserves the public workflow areas while using the current journey route', () => {
    expect(PROJECT_STATUS_VIEW.ACTIVE.label).toBe('진행 중');
    expect(TASK_STATUS_VIEW.BLOCKED.label).toBe('선행 단계 필요');
    expect(PROJECT_AREA_DEFINITIONS.map((area) => area.id)).toEqual([
      'OVERVIEW', 'PLAN', 'REVIEW', 'VALIDATE', 'REPORT',
    ]);
    expect(STAGE_AREA.FINANCIAL).toBe(PROJECT_AREAS.REVIEW);
    expect(STAGE_VIEW.FINANCIAL).toEqual({
      label: '콘셉트 분석',
      route: 'journey/concept-analysis',
    });
    expect(getProjectNextAction({ projectId: '12', status: 'ACTIVE', stage: 'FINANCIAL' }).route)
      .toBe('/app/projects/12/journey/concept-analysis');
  });

  it('keeps completed projects pointing to the integrated report', () => {
    expect(getProjectNextAction({ projectId: '12', status: 'COMPLETED' }).route)
      .toBe('/app/projects/12/journey/final-report');
    expect(getAreaSummary({ stage: 'COMPLETED', status: 'COMPLETED' })
      .every((area) => area.taskStatus === 'COMPLETED')).toBe(true);
  });
});
