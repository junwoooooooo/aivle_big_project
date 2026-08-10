import { describe, expect, it } from 'vitest';
import { PROJECT_MODULES, getProjectModuleByPath, getProjectModules, normalizeProjectModuleStatuses } from './projectModuleModel.js';

describe('project module model', () => {
  it('shows Concept Portfolio as one business-proposal journey step', () => {
    expect(PROJECT_MODULES.map((item) => item.shortLabel)).toEqual([
      '개요', '아이디어', '사업안', '시장 분석', '사업 모델', '기술·운영', '재무', '마케팅', '설정',
    ]);
    expect(getProjectModules('41').filter((item) => item.id === 'concepts')).toHaveLength(1);
    expect(getProjectModuleByPath('41', '/app/projects/41/concepts/compare').id).toBe('concepts');
  });

  it('maps the V2 canonical module status and keeps legacy aliases transitional only', () => {
    const statuses = normalizeProjectModuleStatuses([{ module: 'CONCEPT_PORTFOLIO', status: 'RUNNING', activeTaskRunId: 'task' }]);
    expect(statuses.concepts.status).toBe('RUNNING');
    expect(statuses.concepts.activeTaskRunId).toBe('task');
  });
});
