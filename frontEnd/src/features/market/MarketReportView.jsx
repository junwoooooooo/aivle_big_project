import {
  REPORT_SECTION_ORDER, REPORT_SECTION_TITLE, formatValue, sectionEvidence,
} from './marketResult.js';
import './marketReport.css';

/** Human report first; mechanically verified evidence remains a separate authority. */
export default function MarketReportView({ result, fallback }) {
  if (!result?.report) {
    return fallback;
  }
  const evidence = sectionEvidence(result);
  const deterministic = result.report.writtenBy === 'deterministic-evidence-renderer-v1';
  const sections = REPORT_SECTION_ORDER
    .map((subject) => result.report.sections.find((item) => item.subject === subject))
    .filter(Boolean);

  return <>
    <article className="market-human-report">
      <header><p>시장조사 보고서</p><h2>확인된 근거를 사업 관점에서 읽기</h2>
        <span>기준일 {result.asOf ?? '미확인'} · 근거 {result.evidence.length}건</span></header>
      <aside role="note" className="market-human-report__origin">
        <strong>{deterministic ? '원문 대조를 통과한 근거 요약입니다.' : 'AI가 작성한 해설입니다.'}</strong>
        {deterministic ? ' 자동 해석이나 시장 일반화를 추가하지 않았습니다.'
          : ' 기계적으로 검증된 근거와 구분해 읽고, 인용 전에 아래 원문 근거를 확인하세요.'}
        {result.report.writtenBy && !deterministic
          ? <small>작성 모델: {result.report.writtenBy}</small> : null}
        {result.report.unverifiedNumbers > 0
          ? <p>검증되지 않은 숫자 {result.report.unverifiedNumbers}개가 감지됐습니다.</p> : null}
        {result.report.conceptLeaks > 0
          ? <p>사업안 가정이 조사 결과처럼 섞인 표현 {result.report.conceptLeaks}개가 감지됐습니다.</p> : null}
      </aside>
      {result.report.lead ? <p className="market-human-report__lead">{result.report.lead}</p> : null}
      {sections.map((section, index) => <section key={section.subject}>
        <h3><span>{index + 1}</span>{REPORT_SECTION_TITLE[section.subject] ?? section.subject}</h3>
        <div className="market-human-report__prose">{section.markdown}</div>
        {(evidence[section.subject] ?? []).length > 0 ? <div className="market-human-report__facts">
          <strong>기계적으로 검증된 근거</strong>
          <ul>{evidence[section.subject].slice(0, 4).map((item) => <li key={item.id}>
            <span>{item.raw ?? formatValue(item.value, item.unit)}</span>
            <q>{item.quote}</q>{item.sourceUrl ? <a href={item.sourceUrl} target="_blank" rel="noreferrer">원문</a> : null}
          </li>)}</ul>
        </div> : null}
      </section>)}
      {result.report.tail ? <footer>{result.report.tail}</footer> : null}
    </article>
    <details className="market-human-report__verify">
      <summary>근거로 검산하기</summary>
      {fallback}
    </details>
  </>;
}
