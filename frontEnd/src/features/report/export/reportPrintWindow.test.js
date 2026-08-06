import { describe, expect, it } from 'vitest';

import { buildReportPrintHtml } from './reportPrintWindow.js';

const report = {
  generatedAtLabel: '2026. 7. 29.',
  reportStatusLabel: '분석 준비 중',
  project: { name: '사업 검증 프로젝트', industryCategory: '서비스', description: '프로젝트 설명' },
  plan: { summary: '계획 요약', sections: [] },
  legal: { summary: '법률 요약', importantFindings: [] },
  feasibility: { summary: '타당성 요약', dimensions: [], risks: [] },
  persona: { summary: '고객 검증 요약', hypotheses: [] },
  validationTasks: [],
  nextAction: { title: '다음 행동', description: '검증을 이어갑니다.' },
  provenance: [],
};

describe('report print document', () => {
  it('builds an independent document with linked cover, toc, and sections', () => {
    const document = new DOMParser().parseFromString(buildReportPrintHtml(report), 'text/html');

    expect(document.querySelector('.report-cover a[href="#report-toc"]')).not.toBeNull();
    expect(document.querySelector('nav#report-toc')).not.toBeNull();
    expect(document.querySelector('a[href="#report-summary"]')).not.toBeNull();
    expect(document.querySelector('#report-summary a[href="#report-toc"]')).not.toBeNull();
    expect(document.querySelectorAll('[id]').length).toBe(new Set([...document.querySelectorAll('[id]')].map((node) => node.id)).size);
  });

  it('does not include application navigation or button-shaped skip actions', () => {
    const html = buildReportPrintHtml(report);

    expect(html).not.toContain('app-topbar');
    expect(html).not.toContain('project-navigation');
    expect(html).not.toContain('본문으로 바로가기');
    expect(html).not.toContain('<button');
    expect(html).toContain('@page{size:A4');
  });
});
