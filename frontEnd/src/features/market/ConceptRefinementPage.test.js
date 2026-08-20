import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { cwd } from 'node:process';
import { describe, expect, it } from 'vitest';

describe('ConceptRefinementPage completion boundary', () => {
  it('enters launch readiness without jumping directly to tech ops', () => {
    const source = readFileSync(
      join(cwd(), 'src/features/market/ConceptRefinementPage.jsx'), 'utf8');

    expect(source).toContain('onNext={() => navigate(projectRoutes.launchReadiness(projectId))}');
    expect(source).not.toContain('onNext={() => navigate(projectRoutes.techOps(projectId))}');
  });
});
