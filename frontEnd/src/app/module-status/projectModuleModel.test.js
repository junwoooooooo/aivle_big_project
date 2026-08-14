import { describe, expect, it } from 'vitest';
import { MODULE_STATUS, PROJECT_MODULES, getProjectModuleByPath, getProjectModules, normalizeProjectModuleStatuses } from './projectModuleModel.js';

describe('project module model', () => {
  it('shows Concept Portfolio as one business-proposal journey step', () => {
    expect(PROJECT_MODULES.map((item) => item.shortLabel)).toEqual([
      '개요', '아이디어', '사업안', '시장 분석', 'BM 분석', '출시 준비', '트윈 조사', '마케팅 콘텐츠', '설정',
    ]);
    expect(getProjectModules('41').filter((item) => item.id === 'concepts')).toHaveLength(1);
    expect(getProjectModuleByPath('41', '/app/projects/41/concepts/compare').id).toBe('concepts');
  });

  it('provides all independent module statuses and canonical routes without project.stage', () => {
    expect(Object.keys(MODULE_STATUS)).toEqual([
      'NOT_READY', 'READY', 'QUEUED', 'RUNNING', 'NEEDS_INPUT',
      'COMPLETED', 'FAILED', 'STALE', 'NOT_CONNECTED',
    ]);
    expect(PROJECT_MODULES).toHaveLength(9);
    expect(getProjectModules('project / 1').map((module) => module.href)).toEqual([
      '/app/projects/project%20%2F%201/overview',
      '/app/projects/project%20%2F%201/idea',
      '/app/projects/project%20%2F%201/concepts',
      '/app/projects/project%20%2F%201/market',
      '/app/projects/project%20%2F%201/business-model',
      '/app/projects/project%20%2F%201/tech-ops',
      '/app/projects/project%20%2F%201/panel-survey',
      '/app/projects/project%20%2F%201/marketing',
      '/app/projects/project%20%2F%201/settings',
    ]);
  });

  it('maps the V2 canonical module status and keeps legacy aliases transitional only', () => {
    const statuses = normalizeProjectModuleStatuses([{ module: 'CONCEPT_PORTFOLIO', status: 'RUNNING', activeTaskRunId: 'task' }]);
    expect(statuses.concepts.status).toBe('RUNNING');
    expect(statuses.concepts.activeTaskRunId).toBe('task');
  });

  it('keeps the panel twin survey on its own slot', () => {
    const statuses = normalizeProjectModuleStatuses([{ module: 'PANEL_SURVEY', status: 'COMPLETED' }]);
    expect(statuses.panelSurvey.status).toBe('COMPLETED');
  });
});
