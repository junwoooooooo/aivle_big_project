import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const page = readFileSync('src/features/launch-readiness/pages/LaunchReadinessPage.jsx', 'utf8');

describe('Launch Readiness product contract', () => {
  it('기술·운영·재무·보고서를 한 사용자 화면에 함께 제공한다', () => {
    expect(page).toContain('기술 분석');
    expect(page).toContain('운영 분석');
    expect(page).toContain('재무 분석');
    expect(page).toContain('보고서 다운로드');
    expect(page).toContain('<ProfessionalModule module="technology"');
    expect(page).toContain('<ProfessionalModule module="operations"');
    expect(page).toContain('<FinanceModule');
    expect(page).toContain('<ReportDownload');
  });

  it('가짜 진행률 없이 실제 작업 이벤트와 작업센터 상세 연결을 사용한다', () => {
    expect(page).toContain('useJobEvents(activeJobId)');
    expect(page).toContain('작업센터에서 상세 기록 보기');
    expect(page).toContain('outlet?.openWorkCenterJob');
    expect(page).not.toMatch(/\b(?:8|45|92)%\b/);
    expect(page).not.toMatch(/사용자 문서 Snapshot|입력 Snapshot|TaskRun으로/);
  });

  it('완료 보고서는 포털 기반 PDF 미리보기 후 다운로드할 수 있다', () => {
    expect(page).toContain('<Dialog open={Boolean(preview)}');
    expect(page).toContain('useBodyScrollLock(Boolean(preview))');
    expect(page).toContain('PDF 다운로드');
  });
});
