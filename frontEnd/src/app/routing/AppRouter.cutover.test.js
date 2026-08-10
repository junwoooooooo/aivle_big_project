import { existsSync, readFileSync } from 'node:fs';

import { describe, expect, it } from 'vitest';

const routerSource = readFileSync('src/app/routing/AppRouter.jsx', 'utf8');

describe('project route cutover', () => {
  it.each([
    ['idea', 'IdeaIntakePage'],
    ['concepts', 'ConceptFactoryPage'],
    ['concepts/compare', 'ConceptComparisonPage'],
    ['market', 'MarketResearchPage'],
    ['business-model', 'BmCanvasPage'],
    ['tech-ops', 'TechOpsPage'],
    ['finance', 'FinancePage'],
    ['panel-survey', 'TwinSurveyPage'],
    ['marketing', 'MarketingContentPage'],
  ])('renders the active %s module screen', (path, component) => {
    expect(routerSource).toContain(`path="${path}" element={<${component} />}`);
  });

  it('does not import or render a legacy project surface', () => {
    const removedPlaceholderName = ['ProjectModule', 'Placeholder'].join('');
    expect(routerSource).not.toMatch(/Journey|Legacy|business-persona-test|structured-plan|legal-review|market-validation/);
    expect(routerSource).not.toContain(removedPlaceholderName);
    expect(existsSync('src/app/layouts/ProjectLayout.jsx')).toBe(false);
    expect(existsSync('src/features/planning-revision/api/planningApi.js')).toBe(false);
    expect(existsSync('src/features/business-persona-integration/index.js')).toBe(false);
  });
});
