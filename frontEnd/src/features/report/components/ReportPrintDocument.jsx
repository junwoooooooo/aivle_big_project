function List({ items = [] }) {
  return items.length ? <ul>{items.map((item, index) => <li key={`${index}-${String(item)}`}>{item}</li>)}</ul> : <p>분석 결과가 아직 생성되지 않았습니다.</p>;
}

export default function ReportPrintDocument({ report }) {
  return <article className="report-print" hidden aria-hidden="true">
    <header className="report-print__cover"><p>Venture Verify</p><h1>사업 검증 결과 및 개선 제안서</h1><dl><div><dt>프로젝트명</dt><dd>{report.project.name}</dd></div><div><dt>사업 분야</dt><dd>{report.project.industryCategory || '정보 없음'}</dd></div><div><dt>생성일</dt><dd>{report.generatedAtLabel}</dd></div><div><dt>보고서 상태</dt><dd>{`상태: ${report.reportStatusLabel}`}</dd></div></dl></header>
    <section><h2>Executive Summary</h2><p>{`요약: ${report.reportStatusLabel}`}</p></section>
    <section><h2>1. 사업계획 구조화 결과</h2><p>{report.plan.summary}</p><List items={report.plan.sections.map((item) => `${item.displayName}: ${item.extractedContent || '추출 내용 없음'}`)} /></section>
    <section><h2>2. 법률·규제 검토</h2><p>{report.legal.summary}</p><List items={report.legal.importantFindings.map((item) => `${item.categoryLabel}: ${item.finding} — 권장 행동: ${item.recommendedAction}`)} /></section>
    <section><h2>3. 사업 타당성 분석</h2><p>{report.feasibility.summary}</p><List items={report.feasibility.dimensions.map((item) => `${item.label}: ${item.finding}`)} /></section>
    <section><h2>4. 고객 및 시장 검증</h2><p>{report.persona.summary}</p><List items={report.persona.items.slice(0, 3).map((item) => `${item.baselinePersona?.displayName || '추천 세그먼트'}: ${item.interpretation}`)} /></section>
    <section><h2>5. 권장 다음 행동</h2><List items={report.validationTasks.map((item) => `${item.title} — ${item.method}`)} /></section>
    <section><h2>출처 및 한계</h2><List items={report.provenance.map((item) => `${item.section}: ${item.provider} · ${item.model}`)} /><List items={report.limitations} /></section>
  </article>;
}
