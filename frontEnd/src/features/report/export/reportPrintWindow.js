const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (character) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;',
})[character]);

const EMPTY_RESULT = '분석 결과가 아직 생성되지 않았습니다.';

function list(items = []) {
  if (!items.length) return `<p class="report-empty">${EMPTY_RESULT}</p>`;
  return `<ul>${items.map((item) => `<li>${escapeHtml(item)}</li>`).join('')}</ul>`;
}

function table(headers, rows) {
  if (!rows.length) return `<p class="report-empty">${EMPTY_RESULT}</p>`;
  return `<table class="report-table"><thead><tr>${headers.map((header) => `<th>${escapeHtml(header)}</th>`).join('')}</tr></thead><tbody>${rows.map((row) => `<tr>${row.map((cell) => `<td>${escapeHtml(cell)}</td>`).join('')}</tr>`).join('')}</tbody></table>`;
}

function section(id, number, title, body) {
  return `<section id="${id}" class="report-section"><div class="report-section__heading"><p>${number}</p><h2>${escapeHtml(title)}</h2><a class="report-section__toc-link" href="#report-toc">목차로 돌아가기 ↑</a></div>${body}</section>`;
}

function makeSections(report) {
  const project = report.project || {};
  const legalFindings = report.legal?.importantFindings || [];
  const feasibilityDimensions = report.feasibility?.dimensions || [];
  const validationTasks = report.validationTasks || [];

  return [
    ['report-summary', '01', 'Executive Summary', `<div class="report-summary-block"><h3>검토 목적</h3><p>본 문서는 사업계획의 구조, 규제 위험, 사업 타당성 및 고객 검증 결과를 종합해 의사결정을 지원하기 위해 작성되었습니다.</p><h3>핵심 결론</h3>${list([report.reportStatusLabel, report.nextAction?.title].filter(Boolean))}<h3>권장 다음 행동</h3><p>${escapeHtml(report.nextAction?.description || EMPTY_RESULT)}</p></div>`],
    ['report-project-overview', '02', '프로젝트 개요', `<dl class="report-facts"><div><dt>프로젝트명</dt><dd>${escapeHtml(project.name || '정보 없음')}</dd></div><div><dt>사업 분야</dt><dd>${escapeHtml(project.industryCategory || '정보 없음')}</dd></div><div><dt>최근 수정</dt><dd>${escapeHtml(report.projectUpdatedAtLabel || '기록 없음')}</dd></div></dl><p>${escapeHtml(project.description || EMPTY_RESULT)}</p>`],
    ['report-plan', '03', '사업계획 구조화 결과', `<p>${escapeHtml(report.plan?.summary || EMPTY_RESULT)}</p>${table(['항목', '추출 결과'], (report.plan?.sections || []).map((item) => [item.displayName, item.extractedContent || '추출 내용 없음']))}`],
    ['report-legal', '04', '법률·규제 검토', `<p>${escapeHtml(report.legal?.summary || EMPTY_RESULT)}</p>${table(['검토 항목', '위험 수준', '권장 조치'], legalFindings.map((item) => [item.categoryLabel, item.riskLevel || '확인 필요', item.recommendedAction || item.finding]))}`],
    ['report-feasibility', '05', '사업 타당성 분석', `<p>${escapeHtml(report.feasibility?.summary || EMPTY_RESULT)}</p>${table(['영역', '검토 결과', '상태'], feasibilityDimensions.map((item) => [item.label, item.finding || '분석 결과 없음', item.status || '확인 필요']))}`],
    ['report-validation', '06', '고객·시장 검증', `<p>${escapeHtml(report.persona?.summary || EMPTY_RESULT)}</p>${list((report.persona?.hypotheses || []).map((item) => item.statement || item.rationale).filter(Boolean))}`],
    ['report-risks', '07', '핵심 위험', table(['위험 항목', '영향', '권장 대응'], [
      ...legalFindings.map((item) => [item.finding || item.categoryLabel, item.riskLevel || '확인 필요', item.recommendedAction || '전문가 검토 권장']),
      ...(report.feasibility?.risks || []).map((item) => [item, '사업 타당성', '검증 과제 반영']),
    ])],
    ['report-recommendations', '08', '개선 제안', table(['우선순위', '개선 항목', '제안 내용'], validationTasks.map((item) => [item.priority || '확인 필요', item.title, item.reason || item.method || '검증 계획 확인']))],
    ['report-actions', '09', '권장 실행 계획', `<div class="report-action"><strong>${escapeHtml(report.nextAction?.title || EMPTY_RESULT)}</strong><p>${escapeHtml(report.nextAction?.description || '')}</p></div>${list(validationTasks.map((item) => item.title))}`],
    ['report-sources', '10', '근거 및 출처', table(['분석 영역', '제공자', '모델'], (report.provenance || []).map((item) => [item.section, item.provider, item.model]))],
  ];
}

function reportCss() {
  return `@page{size:A4;margin:18mm 16mm 20mm}*{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;color:#172b35;font:10.5pt/1.7 "Noto Sans KR",Arial,sans-serif}.report-document{max-width:178mm;margin:0 auto}.report-cover{min-height:245mm;display:grid;align-content:center;gap:10mm;border-top:7mm solid #087f73;break-after:page}.report-brand{margin:0;color:#087f73;font-size:9pt;font-weight:800;letter-spacing:.18em}.report-kicker{margin:0;color:#607979;font-size:9pt;letter-spacing:.12em}.report-accent{width:48mm;border-top:2px solid #087f73}.report-cover h1{max-width:130mm;margin:0;color:#142e37;font-size:29pt;line-height:1.28;letter-spacing:-.04em}.report-cover__project{margin:4mm 0 22mm;font-size:17pt;font-weight:700}.report-cover__toc-link,.report-section__toc-link{color:#087f73;text-decoration:underline;text-underline-offset:3px}.report-cover__toc-link{font-size:9pt}.report-cover__meta{display:grid;gap:0;margin-top:10mm}.report-cover__meta div{display:grid;grid-template-columns:32mm 1fr;gap:5mm;padding:2.5mm 0;border-bottom:1px solid #dbe5e2}.report-cover__meta dt{color:#637778}.report-cover__meta dd{margin:0;font-weight:650}.report-toc{break-after:page}.report-toc h2{margin:0 0 8mm;font-size:20pt}.report-toc__link{display:grid;grid-template-columns:12mm minmax(0,1fr);gap:4mm;padding:3.2mm 0;border-bottom:1px dotted #aebbb6;color:inherit;text-decoration:none}.report-toc__number{color:#087f73;font-weight:800}.report-section{margin:0 0 14mm;break-inside:auto}.report-section__heading{display:grid;grid-template-columns:13mm minmax(0,1fr) auto;align-items:baseline;gap:3mm;padding:0 0 3.5mm;border-bottom:2px solid #087f73}.report-section__heading p{margin:0;color:#087f73;font-weight:800}.report-section__heading h2{margin:0;font-size:17pt;letter-spacing:-.02em}.report-section__toc-link{font-size:8.5pt;white-space:nowrap}.report-section h3{margin:6mm 0 2mm;font-size:11pt}.report-section p{margin:3mm 0}.report-summary-block{padding:5mm 6mm;border-left:3px solid #8cc9bf;background:#f4faf8}.report-facts{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));margin:5mm 0;border-top:1px solid #dbe5e2}.report-facts div{display:grid;gap:1mm;padding:3mm 0;border-bottom:1px solid #dbe5e2}.report-facts dt{color:#637778;font-size:8.8pt}.report-facts dd{margin:0;font-weight:650}.report-table{width:100%;margin:5mm 0;border-collapse:collapse;table-layout:fixed;break-inside:auto}.report-table th,.report-table td{padding:3mm;border-bottom:1px solid #dbe5e2;vertical-align:top;text-align:left;overflow-wrap:anywhere}.report-table th{background:#e6f2ef;color:#183e41;font-size:9pt;font-weight:800}.report-table tr{break-inside:avoid}.report-empty{color:#637778;font-style:italic}.report-action{padding:4mm 5mm;border-left:3px solid #087f73;background:#f4faf8}.report-footer{margin-top:16mm;padding-top:3mm;border-top:1px solid #dbe5e2;color:#637778;font-size:8.5pt}@media print{a[href]::after{content:none!important}.report-cover,.report-toc{break-after:page}.report-table tr{break-inside:avoid}h2,h3{break-after:avoid}}`;
}

export function buildReportPrintHtml(report) {
  const project = report.project || {};
  const sections = makeSections(report);
  const toc = sections.map(([id, number, title]) => `<a class="report-toc__link" href="#${id}"><span class="report-toc__number">${number}</span><span>${escapeHtml(title)}</span></a>`).join('');
  const content = sections.map(([id, number, title, body]) => section(id, number, title, body)).join('');

  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>${escapeHtml(project.name || '프로젝트')} - 사업 검증 결과 및 개선 제안서</title><style>${reportCss()}</style></head><body><main class="report-document"><header class="report-cover"><p class="report-brand">VENTURE VERIFY</p><p class="report-kicker">BUSINESS VALIDATION REPORT</p><div class="report-accent"></div><h1>사업 검증 결과 및<br>개선 제안서</h1><p class="report-cover__project">${escapeHtml(project.name || '프로젝트 정보 없음')}</p><dl class="report-cover__meta"><div><dt>사업 분야</dt><dd>${escapeHtml(project.industryCategory || '정보 없음')}</dd></div><div><dt>보고서 생성일</dt><dd>${escapeHtml(report.generatedAtLabel || '기록 없음')}</dd></div><div><dt>보고서 버전</dt><dd>${escapeHtml(report.structuredPlanVersion ? `v${report.structuredPlanVersion}` : 'v1.0')}</dd></div><div><dt>분석 상태</dt><dd>${escapeHtml(report.reportStatusLabel || '분석 준비 중')}</dd></div></dl><a class="report-cover__toc-link" href="#report-toc">목차 보기 →</a></header><nav id="report-toc" class="report-toc" aria-label="보고서 목차"><h2>목차</h2>${toc}</nav>${content}<footer class="report-footer">VENTURE VERIFY · ${escapeHtml(project.name || '프로젝트')} · ${escapeHtml(report.generatedAtLabel || '')}</footer></main></body></html>`;
}

export function openReportPrintWindow(report) {
  const frame = document.createElement('iframe');
  frame.setAttribute('title', '보고서 인쇄');
  frame.style.cssText = 'position:fixed;width:0;height:0;border:0;opacity:0;pointer-events:none';
  document.body.appendChild(frame);
  const printWindow = frame.contentWindow;
  if (!printWindow) {
    frame.remove();
    window.alert('보고서 인쇄 문서를 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    return false;
  }

  try {
    printWindow.document.open();
    printWindow.document.write(buildReportPrintHtml(report));
    printWindow.document.close();
  } catch (error) {
    frame.remove();
    console.error('Unable to create report print document.', error);
    window.alert('보고서 인쇄 문서를 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    return false;
  }

  window.setTimeout(() => {
    printWindow.focus();
    printWindow.print();
  }, 150);
  printWindow.addEventListener('afterprint', () => window.setTimeout(() => frame.remove(), 0), { once: true });
  return true;
}
