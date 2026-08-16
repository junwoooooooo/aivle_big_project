import { FinanceReadinessReportDocument } from './FinanceReadinessReportDocument.jsx';
import { LaunchReadinessReportDocument } from './LaunchReadinessReportDocument.jsx';
import { canonicalizeReportModules, formatReportDate } from '../model/reportDocumentPresentation.js';

const LABELS = { technology: '기술 분석 보고서', operations: '운영 분석 보고서', finance: '재무 분석 보고서' };

export function IntegratedLaunchReadinessReportDocument({ documents, projectName, completedAt }) {
  const orderedDocuments = canonicalizeReportModules(documents.map(({ module }) => module))
    .map((module) => documents.find((document) => document.module === module))
    .filter(Boolean);
  const sources = new Map();
  orderedDocuments.forEach(({ current }) => (current?.externalEvidence ?? []).forEach((item) => {
    if (item?.url && !sources.has(item.url)) sources.set(item.url, item);
  }));
  return <div className="launch-integrated-report" data-report-document="integrated">
    <header className="launch-report-document launch-integrated-report__cover">
      <div className="launch-report-document__cover"><p>VENTURE VERIFY · LAUNCH READINESS</p><h1>출시 준비 통합 보고서</h1><span>완료된 전문 분석을 한 문서에서 순서대로 확인합니다.</span><dl><div><dt>프로젝트명</dt><dd>{projectName || '자료 없음'}</dd></div><div><dt>보고서 기준일</dt><dd>{formatReportDate(completedAt)}</dd></div><div><dt>포함 보고서</dt><dd>{orderedDocuments.map(({ module }) => LABELS[module]).join(' · ')}</dd></div></dl></div>
    </header>
    {orderedDocuments.map(({ module, current }) => module === 'finance'
      ? <FinanceReadinessReportDocument key={module} current={current} projectName={projectName} embedded />
      : <LaunchReadinessReportDocument key={module} module={module} current={current} projectName={projectName} includeSources={false} embedded />)}
    <article className="launch-report-document is-embedded launch-integrated-report__sources"><section className="launch-report-document__section"><h2>통합 외부 참고 출처</h2>{sources.size > 0 ? <ul className="launch-report-document__sources">{[...sources.values()].map((item) => <li key={item.url}><a href={item.url}>{item.title}</a><small>{item.url}</small></li>)}</ul> : <p>외부 검색 근거 없음 · 사용자 전문 입력만으로 분석했습니다.</p>}</section></article>
  </div>;
}
