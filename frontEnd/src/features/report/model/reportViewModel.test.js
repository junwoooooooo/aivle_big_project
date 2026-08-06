import { describe, expect, it } from 'vitest';

import { toIntegratedReportViewModel } from './reportViewModel.js';
import {
  emptyResources,
  fullResources,
  jobResource,
  projectFixture,
} from '../tests/reportTestFixtures.js';

describe('integrated report view model', () => {
  it('keeps a report partial when results still require validation', () => {
    const report = toIntegratedReportViewModel(projectFixture, fullResources());
    expect(report.reportStatus).toBe('PARTIAL');
    expect(report.completedCount).toBe(5);
    expect(report).not.toHaveProperty('overallScore');
  });

  it('marks a report complete only when every section is complete', () => {
    const resources = fullResources();
    resources.feasibilityAssessment.data.status = 'COMPLETED';
    resources.personaRecommendation.data.status = 'COMPLETED';
    const report = toIntegratedReportViewModel(projectFixture, resources);
    expect(report.reportStatus).toBe('COMPLETED');
  });

  it('marks the report partial when only the plan exists', () => {
    const resources = emptyResources();
    resources.plan = fullResources().plan;
    const report = toIntegratedReportViewModel(projectFixture, resources);
    expect(report.reportStatus).toBe('PARTIAL');
    expect(report.completedCount).toBe(1);
  });

  it('keeps null feasibility score as null', () => {
    const report = toIntegratedReportViewModel(projectFixture, fullResources());
    expect(report.feasibility.data.overallScore).toBeNull();
  });

  it('uses completed financial detail and marks missing financial work honestly', () => {
    const complete = toIntegratedReportViewModel(projectFixture, fullResources());
    expect(complete.financial.data.resultJson).toContain('totalRevenue');
    const missing = emptyResources();
    missing.plan = fullResources().plan;
    missing.feasibilityAssessment = fullResources().feasibilityAssessment;
    const incomplete = toIntegratedReportViewModel(projectFixture, missing);
    expect(incomplete.financial.summary).toBe('재무 분석 미완료');
    expect(incomplete.financial.data).toBeNull();
  });

  it('discloses mock provider results', () => {
    const report = toIntegratedReportViewModel(projectFixture, fullResources());
    expect(report.anyMock).toBe(true);
    expect(report.limitations.join(' ')).toContain('Mock');
  });

  it('maps plan evidence and user-filled fields separately', () => {
    const report = toIntegratedReportViewModel(projectFixture, fullResources());
    expect(report.plan.sections[0].evidence).toHaveLength(1);
    expect(report.plan.filledFields[0].userValue).toBe('초기 창업팀');
    expect(report.plan.waivedFields[0].reason).toBe('후속 검증');
  });

  it('selects only high-risk or professional legal findings', () => {
    const report = toIntegratedReportViewModel(projectFixture, fullResources());
    expect(report.legal.importantFindings).toHaveLength(1);
    expect(report.legal.riskLabel).toBe('높음');
  });

  it('preserves feasibility evidence type', () => {
    const report = toIntegratedReportViewModel(projectFixture, fullResources());
    expect(report.feasibility.dimensions[0].evidence[0].typeLabel).toBe('사용자 가정');
  });

  it('parses persona comparison arrays', () => {
    const report = toIntegratedReportViewModel(projectFixture, fullResources());
    expect(report.persona.items[0].matchReasons).toEqual(['디지털 탐색']);
    expect(report.persona.items[0].mismatchRisks).toEqual(['대표성 제한']);
  });

  it('aggregates feasibility tasks and persona validation plans', () => {
    const report = toIntegratedReportViewModel(projectFixture, fullResources());
    expect(report.validationTasks.map((task) => task.source)).toEqual(['사업 타당성', '페르소나']);
  });

  it.each([
    ['QUEUED', 'QUEUED'],
    ['RUNNING', 'RUNNING'],
    ['FAILED', 'FAILED'],
  ])('maps legal job %s to report status %s', (jobStatus, expected) => {
    const resources = emptyResources();
    resources.plan = fullResources().plan;
    resources.legalJob = jobResource(jobStatus);
    const report = toIntegratedReportViewModel(projectFixture, resources);
    expect(report.legal.status).toBe(expected);
  });

  it('marks an individual section error without failing the report', () => {
    const resources = fullResources();
    resources.legalReview = { state: 'error', data: null, error: new Error('legal failed') };
    const report = toIntegratedReportViewModel(projectFixture, resources);
    expect(report.legal.status).toBe('FAILED');
    expect(report.feasibility.data).not.toBeNull();
  });

  it.each([
    ['missing plan', (r) => r, '사업계획서 등록'],
    ['needs input', (r) => { r.plan.data.status = 'NEEDS_INPUT'; return r; }, '누락 항목 보완'],
    ['draft', (r) => { r.plan.data.status = 'DRAFT'; return r; }, '사업계획 확정'],
    ['legal missing', (r) => { r.legalReview = emptyResources().legalReview; r.feasibilityAssessment = emptyResources().feasibilityAssessment; r.personaRecommendation = emptyResources().personaRecommendation; return r; }, '법률 사전검토 시작'],
    ['feasibility missing', (r) => { r.feasibilityAssessment = emptyResources().feasibilityAssessment; r.personaRecommendation = emptyResources().personaRecommendation; return r; }, '사업 타당성 분석'],
    ['persona missing', (r) => { r.personaRecommendation = emptyResources().personaRecommendation; return r; }, '페르소나 추천'],
    ['all complete', (r) => r, '통합 보고서 확인'],
  ])('derives next action for %s', (name, mutate, expected) => {
    const base = name === 'missing plan' ? emptyResources() : fullResources();
    expect(toIntegratedReportViewModel(projectFixture, mutate(base)).nextAction.title).toBe(expected);
  });

  it.each([
    ['plan', '사업계획 구조화'],
    ['legal', '법률·규제 사전검토'],
    ['feasibility', '사업 타당성'],
    ['persona', '페르소나·고객 검증 계획'],
    ['financial', '재무·수익성 분석'],
  ])('exposes %s section with a user label', (key, label) => {
    expect(toIntegratedReportViewModel(projectFixture, fullResources())[key].title).toBe(label);
  });

  it('does not promote persona hypotheses to customer results', () => {
    const report = toIntegratedReportViewModel(projectFixture, fullResources());
    expect(report.persona.data.status).toBe('NEEDS_VALIDATION');
    expect(report.persona.status).toBe('PARTIAL');
  });

  it('preserves source versions and provider provenance', () => {
    const report = toIntegratedReportViewModel(projectFixture, fullResources());
    expect(report.sourceDocumentVersionId).toBe(3);
    expect(report.structuredPlanVersion).toBe(1);
    expect(report.provenance).toHaveLength(4);
  });
});
