import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const page = readFileSync('src/features/launch-readiness/pages/LaunchReadinessPage.jsx', 'utf8');

describe('Launch Readiness product contract', () => {
  it('현재 journey의 3단계와 DOCX 독립 실행 기준을 명시한다', () => {
    expect(page).toContain('step={3}');
    expect(page).toContain('출시 계획 DOCX');
    expect(page).toContain("professionalCurrent(projectId, 'launch')");
  });

  it('출시 준비와 보고서만 제공하고 다른 독립 surface를 포함하지 않는다', () => {
    expect(page).toContain('출시 준비 분석');
    expect(page).toContain('보고서 보기');
    expect(page).not.toContain('재무 분석');
    expect(page).not.toContain('기술 분석');
    expect(page).not.toContain('운영 분석');
    expect(page).not.toContain('FinanceModule');
    expect(page).not.toContain('ReportToolbar');
  });

  it('템플릿 → 업로드 → 실제 진행 → 결과 → 보고서 → 재실행 흐름을 제공한다', () => {
    for (const value of ['입력 템플릿 다운로드', '출시 준비 DOCX 업로드', '작성한 DOCX로 분석 시작',
      'useJobEvents(activeJobId)', 'ResultSummary', '보고서 보기', '새 DOCX로 재실행']) expect(page).toContain(value);
  });

  it('가짜 진행률 없이 실제 작업 이벤트와 작업센터 상세 연결을 사용한다', () => {
    expect(page).toContain('useJobEvents(activeJobId)');
    expect(page).toContain('작업센터에서 상세 기록 보기');
    expect(page).toContain('outlet?.openWorkCenterJob');
    expect(page).not.toMatch(/\b(?:8|45|92)%\b/);
    expect(page).not.toMatch(/사용자 문서 Snapshot|입력 Snapshot|TaskRun으로/);
  });

  it('보고서 보기는 report route command만 사용하고 PDF.js 또는 binary preview를 사용하지 않는다', () => {
    expect(page).toContain('projectRoutes.launchReadinessReport');
    expect(page).toContain('onViewReport={viewReport}');
    expect(page).not.toContain('downloadProfessionalReport');
    expect(page).not.toContain('downloadFinanceReport');
    expect(page).not.toContain('downloadReports');
    expect(page).not.toContain('ReportPreviewDialog');
    expect(page).not.toContain('PdfCanvasViewer');
    expect(page).not.toContain('usePdfPreview');
  });
});
