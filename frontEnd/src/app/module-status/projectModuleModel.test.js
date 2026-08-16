import { describe, expect, it } from 'vitest';
import { PROJECT_MODULES, getProjectModuleByPath, getProjectModules, normalizeProjectModuleStatuses } from './projectModuleModel.js';

describe('project module model', () => {
  it('keeps the complete cutover journey including Twin Survey', () => {
    expect(PROJECT_MODULES.map((item) => item.id)).toEqual([
      'overview', 'idea', 'concepts', 'market', 'businessModel', 'launchReadiness',
      'twinSurvey', 'marketing', 'settings',
    ]);
    expect(getProjectModules('41').filter((item) => item.id === 'concepts')).toHaveLength(1);
    expect(getProjectModuleByPath('41', '/app/projects/41/concepts/compare').id).toBe('concepts');
    expect(getProjectModuleByPath('41', '/app/projects/41/concepts/legal-report').id).toBe('concepts');
  });

  it('기술·운영과 재무 상태를 하나의 출시 준비 상태로 보수적으로 집계한다', () => {
    const statuses = normalizeProjectModuleStatuses([
      { module: 'TECH_OPS', status: 'COMPLETED', requiredInputs: [] },
      { module: 'FINANCE', status: 'RUNNING', activeTaskRunId: 'finance-run' },
    ]);
    expect(statuses.launchReadiness.status).toBe('RUNNING');
    expect(getProjectModuleByPath('41', '/app/projects/41/technology').id).toBe('launchReadiness');
    expect(getProjectModuleByPath('41', '/app/projects/41/finance').id).toBe('launchReadiness');
  });

  it('maps canonical module status identifiers', () => {
    const statuses = normalizeProjectModuleStatuses([
      { module: 'CONCEPT_PORTFOLIO', status: 'RUNNING', activeTaskRunId: 'task' },
      { module: 'TWIN_SURVEY', status: 'COMPLETED' },
    ]);
    expect(statuses.concepts.activeTaskRunId).toBe('task');
    expect(statuses.twinSurvey.status).toBe('COMPLETED');
  });
});
