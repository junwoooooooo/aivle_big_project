import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const page = readFileSync('src/features/launch-readiness/pages/LaunchReadinessPage.jsx', 'utf8');

describe('Launch Readiness product contract', () => {
  it('현재 journey의 3단계와 professional input 독립 실행 기준을 명시한다', () => {
    expect(page).toContain('step={3}');
    expect(page).toContain('전문 입력 문서를 필수 근거로 사용하고');
    expect(page).toContain('프로젝트 정보는 보조 맥락으로만 활용');
  });

  it('기술·운영·재무·보고서를 한 사용자 화면에 함께 제공한다', () => {
    expect(page).toContain('기술 분석');
    expect(page).toContain('운영 분석');
    expect(page).toContain('재무 분석');
    expect(page).toContain('보고서 보기');
    expect(page).toContain('<ProfessionalModule module="technology"');
    expect(page).toContain('<ProfessionalModule module="operations"');
    expect(page).toContain('<FinanceModule');
    expect(page).toContain('<ReportToolbar');
  });

  it('세 분석의 독립 실행 authority를 상단에 두고 compact 보고서 toolbar를 분석 카드 위에 둔다', () => {
    expect(page).toContain('기술·운영·재무 준비 상태를 서로 독립적으로 확인');
    expect(page).not.toContain('현재 확정 사업안과 제출한 전문 입력 문서');
    expect(page).not.toContain('독립 사용 가능');
    expect(page).not.toContain('선택형 · 독립 문서 분석');
    expect(page).not.toContain('앞 단계의 분석 결과');
    expect(page).not.toContain("staleReason === 'CONCEPT_CHANGED'");
    expect(page.indexOf('<ReportToolbar')).toBeLessThan(page.indexOf('className="launch-analysis-grid"'));
    expect(page).not.toContain('className="launch-module launch-reports"');
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
