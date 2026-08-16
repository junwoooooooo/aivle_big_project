import { formatReportDate } from '../model/reportDocumentPresentation.js';

const INPUT_LABELS = {
  technology: {
    systemArchitecture: '시스템·제품 구조', coreFunctions: '핵심 기능과 구현 상태',
    techStack: '기술 스택·인프라', integrations: '외부 연동·의존성',
    dataSecurity: '데이터·보안 기준', performanceTarget: '성능·확장 목표',
    developmentTeam: '개발 인력·역할', releaseSchedule: '개발·출시 일정',
    testPlan: '테스트·검증 계획', technicalRisks: '기술 위험과 대응',
  },
  operations: {
    operatingProcess: '운영 프로세스', staffing: '인력·역할 체계',
    supplyPartners: '공급·파트너 운영', customerSupport: '고객 지원 체계',
    qualityStandards: '품질·SLA 기준', incidentResponse: '장애·민원 대응',
    operatingKpis: '운영 KPI', pilotPlan: '파일럿 계획',
    scalabilityPlan: '확장 계획', operationalRisks: '운영 위험과 대응',
  },
};

const DECISIONS = { READY: '출시 준비', CONDITIONAL: '조건부 준비', REVISE: '보완 후 재검토' };
const STATUS = { READY: '준비', CAUTION: '주의', RISK: '위험', PASS: '준비', OPEN: '주의', BLOCKED: '위험' };
const SEVERITY = { CRITICAL: '위험', HIGH: '위험', MEDIUM: '주의', LOW: '낮음' };
const valuesOf = (value) => Array.isArray(value) ? value : [];

function ReportSection({ number, title, children, className = '' }) {
  return <section className={`launch-report-document__section ${className}`}><h2>{number}. {title}</h2>{children}</section>;
}

export function LaunchReadinessReportDocument({ module, current, projectName, includeSources = true, embedded = false }) {
  const result = current?.analysis;
  const label = module === 'technology' ? '기술' : '운영';
  if (!result) return <article className="launch-report-document"><p>표시할 {label} 분석 결과가 없습니다.</p></article>;
  const inputs = Object.entries(current.professionalInput ?? {})
    .filter(([, value]) => typeof value === 'string' && value.trim());
  const decision = DECISIONS[result.decision] ?? '검토 필요';

  return <article className={`launch-report-document${embedded ? ' is-embedded' : ''}`} data-report-document={module}>
    <header className="launch-report-document__cover">
      <p>VENTURE VERIFY · LAUNCH READINESS</p>
      <h1>{label} 출시 준비도 보고서</h1>
      <span>작성한 {label} 전문 계획과 공개 참고자료를 바탕으로 현재 출시 준비 상태를 정리했습니다.</span>
      <dl><div><dt>프로젝트명</dt><dd>{projectName || '자료 없음'}</dd></div><div><dt>분석 기준일</dt><dd>{formatReportDate(current.completedAt)}</dd></div><div><dt>입력 문서</dt><dd>{current.sourceDocumentName || '사용자 전문 입력 문서'}</dd></div></dl>
    </header>

    <section className="launch-report-document__summary" aria-label="종합 준비도">
      <div><span>AI 출시 준비도 평가</span><strong>{result.score}점</strong><small>작성한 전문 계획을 바탕으로 AI가 평가한 준비도이며 정해진 산식의 점수가 아닙니다.</small></div>
      <div><span>판정</span><strong>{decision}</strong><small>{current.quality?.passed === true ? '독립 AI 검증 통과' : '독립 검증 결과 확인 필요'}</small></div>
    </section>

    <ReportSection number="1" title="경영진 요약"><p className="launch-report-document__callout">{result.summary}</p></ReportSection>

    <ReportSection number="2" title="평가에 사용한 입력 근거">
      {inputs.length > 0 ? <table><thead><tr><th>입력 항목</th><th>사용자 입력 내용</th></tr></thead><tbody>{inputs.map(([key, value]) => <tr key={key}><th>{INPUT_LABELS[module]?.[key] ?? '입력 항목'}</th><td>{value}</td></tr>)}</tbody></table> : <p>사용자 전문 입력 문서가 분석 기준으로 사용되었습니다.</p>}
    </ReportSection>

    <ReportSection number="3" title="영역별 준비도와 판단 근거">
      <div className="launch-report-document__rows">{valuesOf(result.dimensions).map((item) => <article key={item.name}><header><strong>{item.name}</strong><span>AI 평가 {item.score}점 · {STATUS[item.status] ?? item.status}</span></header><p>{item.finding}</p></article>)}</div>
    </ReportSection>

    <ReportSection number="4" title="핵심 위험">
      <div className="launch-report-document__rows">{valuesOf(result.risks).map((item) => <article key={item.title}><header><strong>{item.title}</strong><span>{SEVERITY[item.severity] ?? item.severity}</span></header><dl><div><dt>사업 영향</dt><dd>{item.impact}</dd></div><div><dt>대응책</dt><dd>{item.mitigation}</dd></div></dl></article>)}</div>
    </ReportSection>

    <ReportSection number="5" title="출시 전 확인 기준">
      <div className="launch-report-document__rows">{valuesOf(result.gates).map((item) => <article key={item.title}><header><strong>{item.title}</strong><span>{STATUS[item.status] ?? item.status}</span></header><dl><div><dt>통과 기준</dt><dd>{item.criterion}</dd></div><div><dt>확인 자료</dt><dd>{item.evidenceNeeded}</dd></div></dl></article>)}</div>
    </ReportSection>

    <ReportSection number="6" title="우선 실행 과제">
      <ol className="launch-report-document__actions">{valuesOf(result.actions).map((item) => <li key={`${item.priority}-${item.title}`}><b>{item.priority}</b><div><strong>{item.title}</strong><span>담당: {item.owner}</span><small>완료 증빙: {item.completionEvidence}</small></div></li>)}</ol>
    </ReportSection>

    <ReportSection number="7" title="사업 적용 결론"><p className="launch-report-document__callout">현재 {label} 준비도는 ‘{decision}’입니다. {result.summary}</p></ReportSection>

    {includeSources && <ReportSection number="8" title="외부 참고 출처">
      {valuesOf(current.externalEvidence).length > 0 ? <ul className="launch-report-document__sources">{current.externalEvidence.map((item) => <li key={`${item.title}-${item.url}`}><a href={item.url}>{item.title}</a><small>{item.url}</small></li>)}</ul> : <p>외부 검색 근거 없음 · 사용자 전문 입력만으로 분석했습니다.</p>}
    </ReportSection>}

    <footer className="launch-report-document__disclaimer">본 보고서는 입력 자료와 공개 참고자료를 바탕으로 한 의사결정 지원 문서이며 인증 또는 성과를 보장하지 않습니다.</footer>
  </article>;
}
