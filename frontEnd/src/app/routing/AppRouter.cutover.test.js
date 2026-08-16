import { existsSync, readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const routerSource = readFileSync('src/app/routing/AppRouter.jsx', 'utf8');

describe('project route cutover', () => {
  it('uses one canonical Business Proposal Workspace for both compatible routes', () => {
    expect(routerSource).toContain('path="concepts" element={<BusinessProposalWorkspace />}');
    expect(routerSource).toContain('path="concepts/compare" element={<BusinessProposalWorkspace initialMode="compare" />}');
    expect(routerSource).toContain('path="concepts/legal-report" element={<LegalRegulatoryReportPage />}');
    expect(routerSource).not.toContain('ConceptFactoryPage');
    expect(routerSource).not.toContain('ConceptComparisonPage');
  });

  it('keeps legacy source files without exposing them through official routes', () => {
    expect(existsSync('src/features/concept-factory/pages/ConceptFactoryPage.jsx')).toBe(true);
    expect(existsSync('src/features/concept-selection/pages/ConceptComparisonPage.jsx')).toBe(true);
  });

  it('출시 준비의 canonical 및 호환 경로를 하나의 화면으로 연결한다', () => {
    expect(routerSource).toContain('path="launch-readiness" element={<LaunchReadinessPage />}');
    expect(routerSource).toContain('path="launch-readiness/reports/:reportType" element={<LaunchReadinessReportPage />}');
    expect(routerSource).toContain('path="technology" element={<LaunchReadinessPage initialFocus="technology" />}');
    expect(routerSource).toContain('path="operations" element={<LaunchReadinessPage initialFocus="operations" />}');
    expect(routerSource).toContain('path="tech-ops" element={<LaunchReadinessPage />}');
    expect(routerSource).toContain('path="finance" element={<LaunchReadinessPage initialFocus="finance" />}');
    expect(routerSource).not.toContain('element={<TechOpsPage />}');
    expect(routerSource).not.toContain('element={<FinancePage />}');
  });
});
