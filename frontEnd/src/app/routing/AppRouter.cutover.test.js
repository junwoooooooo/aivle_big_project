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
    expect(routerSource).toContain('path="tech-ops" element={<TechOpsPage />}');
    expect(routerSource).toContain('path="finance" element={<FinancePage />}');
    expect(routerSource).toContain('path="launch-readiness" element={<LaunchReadinessPage />}');
  });

  it('사업 검증 canonical route와 Market/BM 호환 redirect를 함께 유지한다', () => {
    expect(routerSource).toContain('path="business-validation" element={<BusinessValidationPage />}');
    expect(routerSource).toContain('path="market" element={<ProjectRedirect routeKey="businessValidation" />}');
    expect(routerSource).toContain('path="business-model" element={<ProjectRedirect routeKey="businessValidation" />}');
  });

  it('시장 인터뷰 canonical route와 이전 Virtual Interview redirect를 제공하고 Twin Survey를 분리한다', () => {
    expect(routerSource).toContain('path="market-interview" element={<MarketInterviewPage />}');
    expect(routerSource).toContain('path="virtual-interview" element={<ProjectRedirect routeKey="marketInterview" />}');
    expect(routerSource).toContain('path="twin-survey" element={<ProjectRedirect routeKey="marketInterview" />}');
    expect(routerSource).not.toContain("import TwinSurveyPage");
  });
});
