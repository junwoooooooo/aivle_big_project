import { readFileSync } from 'node:fs';

import { describe, expect, it } from 'vitest';

const routerSource = readFileSync('src/app/routing/AppRouter.jsx', 'utf8');

describe('project route cutover', () => {
  it.each([
    ['idea', 'IdeaIntakePage'],
    ['concepts', 'ConceptFactoryPage'],
    ['concepts/compare', 'ConceptComparisonPage'],
    ['market', 'MarketIntegrationPage'],
    ['business-persona-test', 'BusinessPersonaIntegrationPage'],
    ['marketing', 'MarketingContentPage'],
  ])('renders the active %s module screen', (path, component) => {
    expect(routerSource).toContain(`path="${path}" element={<${component} />}`);
  });

  it('does not import or render a legacy project surface', () => {
    const removedPlaceholderName = ['ProjectModule', 'Placeholder'].join('');
    expect(routerSource).not.toMatch(/Journey|Legacy/);
    expect(routerSource).not.toContain(removedPlaceholderName);
    expect(routerSource).toContain('path="legal" element={<ProjectRedirect routeKey="concepts" />}');
  });
});
