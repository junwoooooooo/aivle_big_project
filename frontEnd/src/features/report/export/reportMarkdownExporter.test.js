import { describe, expect, it, vi } from 'vitest';

import { toIntegratedReportViewModel } from '../model/reportViewModel.js';
import { fullResources, projectFixture } from '../tests/reportTestFixtures.js';
import {
  createReportMarkdown,
  downloadReportMarkdown,
  safeReportFileName,
} from './reportMarkdownExporter.js';

const report = () => toIntegratedReportViewModel(structuredClone(projectFixture), fullResources());

describe('report markdown export', () => {
  it('exports every visible report family', () => {
    const markdown = createReportMarkdown(report());
    for (const heading of ['사업 검증 결과 및 개선 제안서', '사업계획 구조화 결과', '법률·규제 검토', '사업 타당성 분석', '고객 및 시장 검증', '권장 다음 행동', '출처와 생성 정보']) {
      expect(markdown).toContain(heading);
    }
  });

  it('uses UTF-8 Korean content', () => {
    expect(createReportMarkdown(report())).toContain('프로젝트명: 검증 프로젝트');
  });

  it.each(['accessToken', 'refreshToken', 'storagePath', 'rawResponse', 'rawPrompt', 'auditMetadata'])(
    'does not export hidden field %s',
    (field) => {
      const value = report();
      value[field] = 'DO_NOT_EXPORT';
      expect(createReportMarkdown(value)).not.toContain('DO_NOT_EXPORT');
    },
  );

  it('exports partial reports without fake section text', () => {
    const value = report();
    value.legal.data = null;
    value.legal.importantFindings = [];
    expect(createReportMarkdown(value)).toContain('아직 법률 사전검토 결과가 없습니다.');
  });

  it('escapes HTML and Markdown from user-controlled text', () => {
    const value = report();
    value.project.name = '<script>*위험*</script>';
    const markdown = createReportMarkdown(value);
    expect(markdown).not.toContain('<script>');
    expect(markdown).toContain('&lt;script&gt;');
    expect(markdown).toContain('\\*위험\\*');
  });

  it.each([
    ['A/B 프로젝트', 'A-B-프로젝트_사업검증_결과및개선제안서_2026-07-24.md'],
    ['..', 'project_사업검증_결과및개선제안서_2026-07-24.md'],
    ['', 'project_사업검증_결과및개선제안서_2026-07-24.md'],
    ['A:*?<>|B', 'A-B_사업검증_결과및개선제안서_2026-07-24.md'],
  ])('creates a safe filename for %s', (name, expected) => {
    expect(safeReportFileName(name, new Date('2026-07-24T00:00:00Z'))).toBe(expected);
  });

  it('limits excessively long filenames', () => {
    expect(safeReportFileName('가'.repeat(200), new Date('2026-07-24T00:00:00Z')).length).toBeLessThanOrEqual(110);
  });

  it('downloads a BOM-prefixed markdown blob and revokes its URL', () => {
    const click = vi.fn();
    const documentRef = { createElement: vi.fn(() => ({ click })) };
    const urlApi = { createObjectURL: vi.fn(() => 'blob:test'), revokeObjectURL: vi.fn() };
    downloadReportMarkdown(report(), documentRef, urlApi);
    expect(documentRef.createElement).toHaveBeenCalledWith('a');
    expect(click).toHaveBeenCalledOnce();
    expect(urlApi.revokeObjectURL).toHaveBeenCalledWith('blob:test');
  });
});
