import { describe, expect, it } from 'vitest';
import { PROJECT_MODULES, getProjectModuleByPath, getProjectModules, normalizeProjectModuleStatuses } from './projectModuleModel.js';

describe('project module model', () => {
  it('가상 인터뷰를 canonical 시장 인터뷰 슬롯 하나로 노출한다', () => {
    expect(PROJECT_MODULES.map((item) => item.id)).toEqual([
      'overview', 'idea', 'concepts', 'market', 'businessModel', 'conceptRefinement',
      'techOps', 'finance', 'launchReadiness', 'marketInterview', 'marketing', 'settings',
    ]);
    expect(getProjectModules('41').filter((item) => item.id === 'concepts')).toHaveLength(1);
    expect(getProjectModuleByPath('41', '/app/projects/41/concepts/compare').id).toBe('concepts');
    expect(getProjectModuleByPath('41', '/app/projects/41/concepts/legal-report').id).toBe('concepts');
    expect(getProjectModuleByPath('41', '/app/projects/41/business-validation').id).toBe('market');
  });

  it('기술·운영·재무·출시 준비 상태를 서로 독립적으로 projection한다', () => {
    const statuses = normalizeProjectModuleStatuses([
      { module: 'TECH_OPS', status: 'COMPLETED', requiredInputs: [] },
      { module: 'FINANCE', status: 'RUNNING', activeTaskRunId: 'finance-run' },
      { module: 'LAUNCH_READINESS', status: 'READY' },
    ]);
    expect(statuses.techOps.status).toBe('COMPLETED');
    expect(statuses.finance.status).toBe('RUNNING');
    expect(statuses.launchReadiness.status).toBe('READY');
    expect(getProjectModuleByPath('41', '/app/projects/41/technology').id).toBe('launchReadiness');
    expect(getProjectModuleByPath('41', '/app/projects/41/tech-ops').id).toBe('techOps');
    expect(getProjectModuleByPath('41', '/app/projects/41/finance').id).toBe('finance');
  });

  it('CONCEPT_REFINEMENT 상태를 사업 검증의 세 번째 근거로 보존한다', () => {
    const statuses = normalizeProjectModuleStatuses([
      { module: 'MARKET_ANALYSIS', status: 'COMPLETED' },
      { module: 'BUSINESS_MODEL', status: 'COMPLETED' },
      { module: 'CONCEPT_REFINEMENT', status: 'NEEDS_INPUT', activeRunId: 'round-1' },
    ]);
    expect(statuses.conceptRefinement).toMatchObject({ status: 'NEEDS_INPUT', activeRunId: 'round-1' });
  });

  it('legacy TWIN_SURVEY 완료를 canonical marketInterview 슬롯으로 projection한다', () => {
    const statuses = normalizeProjectModuleStatuses([
      { module: 'CONCEPT_PORTFOLIO', status: 'RUNNING', activeTaskRunId: 'task' },
      { module: 'MARKET_INTERVIEW', status: 'READY' },
      { module: 'TWIN_SURVEY', status: 'COMPLETED', activeRunId: 'legacy-twin' },
    ]);
    expect(statuses.concepts.activeTaskRunId).toBe('task');
    expect(statuses.marketInterview.status).toBe('COMPLETED');
    expect(statuses.marketInterview.module).toBe('TWIN_SURVEY');
    expect(statuses.twinSurvey).toBeUndefined();
  });

  it.each([
    ['COMPLETED', 'NOT_READY', 'COMPLETED'],
    ['RUNNING', 'COMPLETED', 'RUNNING'],
    ['FAILED', 'COMPLETED', 'FAILED'],
    ['COMPLETED', 'FAILED', 'COMPLETED'],
  ])('현재 Market Interview %s 상태를 Twin Survey %s와 무관하게 사용한다',
    (interviewStatus, twinStatus, expected) => {
      const statuses = normalizeProjectModuleStatuses([
        { module: 'MARKET_INTERVIEW', status: interviewStatus, activeRunId: 'current-interview' },
        { module: 'TWIN_SURVEY', status: twinStatus, activeRunId: 'legacy-twin' },
      ]);
      expect(statuses.marketInterview.status).toBe(expected);
      expect(statuses.marketInterview.module).toBe('MARKET_INTERVIEW');
    });
});
