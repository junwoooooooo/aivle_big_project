import { existsSync, readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const routerSource = readFileSync('src/app/routing/AppRouter.jsx', 'utf8');

describe('project route cutover', () => {
  it.each([
    ['idea', 'IdeaIntakePage'],
    ['concepts', 'BusinessProposalWorkspace'],
    ['market', 'MarketResearchPage'],
    ['business-model', 'BmCanvasPage'],
    ['tech-ops', 'LaunchReadinessPage'],
    ['finance', 'LaunchReadinessPage'],
    ['panel-survey', 'TwinSurveyPage'],
    ['marketing', 'MarketingContentPage'],
  ])('renders the active %s module screen', (path, component) => {
    expect(routerSource).toContain(`path="${path}" element={<${component} />}`);
  });

  it('uses one canonical Business Proposal Workspace for both compatible routes', () => {
    expect(routerSource).toContain('path="concepts" element={<BusinessProposalWorkspace />}');
    expect(routerSource).toContain('path="concepts/compare" element={<BusinessProposalWorkspace initialMode="compare" />}');
    expect(routerSource).not.toContain('ConceptFactoryPage');
    expect(routerSource).not.toContain('ConceptComparisonPage');
  });

  it('keeps legacy source files without exposing them through official routes', () => {
    expect(existsSync('src/features/concept-factory/pages/ConceptFactoryPage.jsx')).toBe(true);
    expect(existsSync('src/features/concept-selection/pages/ConceptComparisonPage.jsx')).toBe(true);
  });
});
