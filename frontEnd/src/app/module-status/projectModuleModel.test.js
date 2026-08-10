import { describe, expect, it } from 'vitest';

import {
  MODULE_STATUS,
  PROJECT_MODULES,
  getProjectModuleByPath,
  getProjectModules,
  normalizeProjectModuleStatuses,
} from './projectModuleModel.js';

describe('project module model', () => {
  it('provides all independent module statuses and canonical routes without project.stage', () => {
    expect(Object.keys(MODULE_STATUS)).toEqual([
      'NOT_READY', 'READY', 'QUEUED', 'RUNNING', 'NEEDS_INPUT',
      'COMPLETED', 'FAILED', 'STALE', 'NOT_CONNECTED',
    ]);
    expect(PROJECT_MODULES).toHaveLength(11);
    expect(getProjectModules('project / 1').map((module) => module.href)).toEqual([
      '/app/projects/project%20%2F%201/overview',
      '/app/projects/project%20%2F%201/idea',
      '/app/projects/project%20%2F%201/concepts',
      '/app/projects/project%20%2F%201/concepts/compare',
      '/app/projects/project%20%2F%201/market',
      '/app/projects/project%20%2F%201/business-model',
      '/app/projects/project%20%2F%201/tech-ops',
      '/app/projects/project%20%2F%201/finance',
      '/app/projects/project%20%2F%201/panel-survey',
      '/app/projects/project%20%2F%201/marketing',
      '/app/projects/project%20%2F%201/settings',
    ]);
  });

  it('resolves the current module from the route and falls back to overview', () => {
    expect(getProjectModuleByPath('7', '/app/projects/7/concepts/compare').id).toBe('conceptCompare');
    expect(getProjectModuleByPath('7', '/app/projects/7/unknown').id).toBe('overview');
  });

  it('maps the v3 module response without trusting unknown module or status values', () => {
    const statuses = normalizeProjectModuleStatuses([
      { module: 'IDEA', status: 'READY', requiredInputs: [], activeRunId: null },
      { module: 'MARKET_ANALYSIS', status: 'NOT_CONNECTED', requiredInputs: ['selectedConceptSnapshotId'] },
      { module: 'BUSINESS_MODEL', status: 'NOT_CONNECTED', requiredInputs: ['businessModelModuleConnection'] },
      { module: 'TECH_OPS', status: 'NEEDS_INPUT', requiredInputs: ['techOpsRequiredFacts'] },
      { module: 'FINANCE', status: 'READY', requiredInputs: [] },
      { module: 'UNKNOWN', status: 'READY' },
      { module: 'MARKETING', status: 'UNKNOWN' },
    ]);

    expect(statuses.idea.status).toBe('READY');
    expect(statuses.market.requiredInputs).toEqual(['selectedConceptSnapshotId']);
    expect(statuses.businessModel.requiredInputs).toEqual(['businessModelModuleConnection']);
    expect(statuses.techOps.status).toBe('NEEDS_INPUT');
    expect(statuses.finance.status).toBe('READY');
    expect(statuses.UNKNOWN).toBeUndefined();
    expect(statuses.marketing).toBeUndefined();
  });
});
