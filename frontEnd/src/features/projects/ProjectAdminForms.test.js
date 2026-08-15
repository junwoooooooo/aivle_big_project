import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('project admin form boundary', () => {
  it('새 프로젝트와 설정을 workflow horizontal form에서 분리한다', () => {
    const createSource = readFileSync('src/features/projects/ProjectPages.jsx', 'utf8');
    const settingsSource = readFileSync('src/features/projects/ProjectSettingsSheet.jsx', 'utf8');
    expect(createSource).toContain('className="project-form" data-form-kind="admin"');
    expect(settingsSource).toContain('className="project-sheet__form" data-form-kind="admin"');
    expect(settingsSource).not.toContain('className="project-sheet__form project-form-layout"');
  });
});
