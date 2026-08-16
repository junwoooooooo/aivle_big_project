import { readFileSync } from 'node:fs';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import LegalRegulatoryReportDocument from './LegalRegulatoryReportDocument.jsx';
import { legalReportSuggestedFilename, printLegalReport } from '../legalReportPresentation.js';

const report = { reportId: 'report-17', basisDate: '2026-08-14', report: {
  finalLegalConclusion: { status: 'IMPLEMENTABLE_WITH_CONTROLS', safeSummary: '필요한 고지를 반영해야 합니다.' },
  selectedConcept: { conceptDefinition: '예약 운영 자동화', targetUsers: ['소상공인'] },
  finalHypotheses: [{ hypothesisType: 'TARGET_REGION', finalValue: '서울', decisionStatus: 'ACCEPTED' }],
  requiredControls: ['개인정보 동의'], requiredDisclosures: ['판매 주체 표시'], unknownFacts: ['파트너 계약 여부'],
  businessRoles: { sellerRole: '입점 매장' }, transactionFlow: ['고객→매장'], paymentFlow: ['고객→PG'], personalDataUsage: ['예약 정보'],
  officialEvidenceReferences: [{ lawName: '개인정보 보호법', articleReference: '제15조', title: '개인정보의 수집·이용', boundedProvisionSummary: '동의를 받아 수집합니다.', effectiveDate: '2025-01-01', officialSourceUri: 'https://law.go.kr/law', contentHash: 'secret-hash' }],
  sourceHashes: { selectedConcept: 'sha256:secret' },
} };

describe('법률·규제 전용 문서', () => {
  it('사용자 언어·법령 grouping·공식 링크를 유지하고 기술값과 컨트롤을 제외한다', () => {
    const view = render(<LegalRegulatoryReportDocument project={{ name: '예약 사업', industryCategory: '서비스' }} selection={{ conceptName: '매장 자동화' }} report={report} />);
    expect(screen.getByRole('heading', { name: '법률·규제 사전 검토 보고서' })).toBeInTheDocument();
    expect(screen.getByText('필요한 조치를 반영하면 진행 가능')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '법령 원문 보기' })).toHaveAttribute('href', 'https://law.go.kr/law');
    expect(view.container.textContent).not.toMatch(/IMPLEMENTABLE_WITH_CONTROLS|secret-hash|sha256:secret/);
    expect(view.container.querySelector('button')).toBeNull();
    for (const title of ['검토 개요', '종합 판단', '주요 검토 결과 요약', '선택 사업안 개요', '확정 사업 조건', '필요한 조치', '필수 고지사항', '파트너·자격·인허가', '사업 구조와 역할', '관련 법률·규제', '광고·표현 주의사항', '확인되지 않은 사항', '거래·결제·개인정보 등 상세 검토', '변경사항 재검토 이력', '검토 범위와 한계']) expect(view.container.textContent).toContain(title);
    expect(view.container.textContent).toContain('필요한 조치1건');
  });

  it('A4 print source에서 app chrome과 interactive UI를 숨긴다', () => {
    const css = readFileSync('src/features/concept-portfolio/styles/legal-regulatory-report.css', 'utf8');
    expect(css).toContain('@page { size: A4;');
    expect(css).toContain('.app-topbar');
    expect(css).toContain('.pipeline-shell__header');
    expect(css).toContain('.skip-link');
    expect(css).toContain('.legal-report-print-actions { display: none !important; }');
    expect(css).toContain('break-inside: avoid-page');
    expect(css).toContain('.legal-document__execution table');
    expect(css).toContain('border-collapse: collapse');
    expect(css).not.toContain('.legal-document__execution dl');
    expect(viewSource()).not.toContain('repeat(4, minmax(0, 1fr))');
  });

  it('사업안명과 generatedAt으로 Windows 유효 기본 파일명을 만든다', () => {
    expect(legalReportSuggestedFilename('스마트 식단 관리 서비스', '2026-08-14T16:58:00'))
      .toBe('스마트_식단_관리_서비스_법률규제_사전검토_보고서_20260814_1658');
    expect(legalReportSuggestedFilename('예약:/관리*서비스', '2026-08-14T16:58:00')).not.toMatch(/[<>:"/\\|?*]/);
  });

  it('print 직전에 report title을 설정하고 afterprint에서 원래 title을 복원한다', () => {
    const original = document.title;
    const print = vi.spyOn(window, 'print').mockImplementation(() => {});
    printLegalReport('스마트 식단', '2026-08-14T16:58:00');
    expect(document.title).toBe('스마트_식단_법률규제_사전검토_보고서_20260814_1658');
    expect(print).toHaveBeenCalled();
    window.dispatchEvent(new Event('afterprint'));
    expect(document.title).toBe(original);
    print.mockRestore();
  });

  it('PDF 요약은 건수만 표시하고 실제 법률 문장은 상세 section에서 한 번만 출력한다', () => {
    const view = render(<LegalRegulatoryReportDocument project={{ name: '중복 검토' }} selection={{ conceptName: '테스트' }} report={{ basisDate: '2026-08-14', report: {
      finalLegalConclusion: { status: 'IMPLEMENTABLE' },
      requiredControls: ['개인정보 동의', ' 개인정보   동의 '],
      requiredDisclosures: ['판매 주체 표시'],
      partnerRequirements: ['전문 파트너'], qualificationRequirements: ['전문 파트너가 필요함.'], requiredPartnersAndQualifications: ['전문 파트너'],
      advertisingExpressionCautions: { requiredDisclosures: ['판매 주체 표시', '광고 조건 표시'] },
    } }} />);
    expect(view.container.textContent).toContain('필요한 조치1건');
    expect(screen.getAllByText('개인정보 동의')).toHaveLength(1);
    expect(screen.getAllByText('전문 파트너')).toHaveLength(1);
    expect(screen.getAllByText('판매 주체 표시')).toHaveLength(1);
    expect(screen.getByText('광고 조건 표시')).toBeInTheDocument();
    expect(view.container.querySelector('.legal-document__execution table')).not.toBeNull();
  });
});

function viewSource() {
  return readFileSync('src/features/concept-portfolio/styles/legal-regulatory-report.css', 'utf8');
}
