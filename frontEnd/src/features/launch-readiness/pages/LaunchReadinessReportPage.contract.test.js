import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const page = readFileSync('src/features/launch-readiness/pages/LaunchReadinessReportPage.jsx', 'utf8');
const css = readFileSync('src/features/launch-readiness/styles/launch-readiness.css', 'utf8');
const professional = readFileSync('src/features/launch-readiness/components/LaunchReadinessReportDocument.jsx', 'utf8');
const finance = readFileSync('src/features/launch-readiness/components/FinanceReadinessReportDocument.jsx', 'utf8');
const launchPage = readFileSync('src/features/launch-readiness/pages/LaunchReadinessPage.jsx', 'utf8');

describe('V21.4 report page source contract', () => {
  it('화면과 print는 동일한 사용자-facing document component를 사용한다', () => {
    expect(page).toContain('<LaunchReadinessReportDocument');
    expect(page).toContain('<FinanceReadinessReportDocument');
    expect(page).toContain('<IntegratedLaunchReadinessReportDocument');
    expect(page).not.toMatch(/PreviewDocument|PrintDocument|ReportPreviewDialog/);
  });

  it('보고서 보기와 PDF 저장 경로에는 Backend PDF endpoint 호출이 없다', () => {
    expect(page).not.toMatch(/downloadProfessionalReport|downloadFinanceReport|downloadReports|\.blob\(/);
    expect(page).toContain('printLaunchReadinessReport');
  });

  it('print에서 앱 chrome과 모든 interactive control을 숨기고 A4를 사용한다', () => {
    expect(css).toContain('@page { size:A4;');
    ['.app-topbar', '.app-maintenance-banner', '.pipeline-shell__header', '.journey-substeps',
      '.skip-link', '.scroll-to-top', '.launch-report-actions']
      .forEach((selector) => expect(css).toContain(selector));
    expect(css).toContain('display:none!important');
  });

  it('전문·재무 보고서에는 Backend renderer의 의미 section과 print-safe Finance SVG가 있다', () => {
    ['경영진 요약', '평가에 사용한 입력 근거', '영역별 준비도와 판단 근거', '핵심 위험',
      '출시 전 확인 기준', '우선 실행 과제', '사업 적용 결론', '외부 참고 출처']
      .forEach((section) => expect(professional).toContain(section));
    ['핵심 결과', '3개년 추정 손익', '월별 매출·영업이익·누적 현금흐름', '스트레스 시나리오',
      'Monte Carlo 위험 분포', 'AI 해석과 권장 조치', '사업 적용 결론']
      .forEach((section) => expect(finance).toContain(section));
    expect(finance).toContain('<svg');
    expect(finance).toContain('result.cashFlowChart');
  });

  it('V21.6 메인은 3열 IA와 세로 workflow, compact report toolbar를 사용한다', () => {
    expect(launchPage).toContain('className="launch-analysis-grid"');
    expect(launchPage).toContain('launch-workflow launch-workflow--vertical');
    expect(launchPage).toContain('필요한 분석만 선택해 사용할 수 있습니다');
    expect(launchPage).toContain('<ReportToolbar');
    expect(launchPage).not.toContain('독립 사용 가능');
    expect(launchPage).not.toContain('선택형 · 독립 문서 분석');
    expect(launchPage).not.toContain('className="launch-readiness-nav"');
    expect(css).toContain('grid-template-columns:repeat(3,minmax(0,1fr))');
    expect(css).toContain('.launch-workflow li:not(:last-child)::after');
    expect(css).toContain('.launch-report-toolbar');
    expect(css).toContain('flex-wrap:wrap');
  });

  it('전문 보고서의 핵심 정보는 정형 표이고 deep blue header를 사용한다', () => {
    ['launch-report-table--inputs', 'launch-report-table--dimensions', 'launch-report-table--risks',
      'launch-report-table--gates', 'launch-report-table--actions']
      .forEach((className) => expect(professional).toContain(className));
    expect(css).toContain('background:#2e4e73');
    expect(css).toContain('display:table-header-group');
  });
});
