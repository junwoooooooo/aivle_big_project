import { AppIcon } from '../../../shared/ui/index.js';

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
const STATUS = { READY: '준비', CAUTION: '확인 필요', RISK: '위험', PASS: '통과', OPEN: '미완료', BLOCKED: '차단' };
const SEVERITY = { HIGH: '높음', MEDIUM: '중간', LOW: '낮음' };

function valuesOf(value) {
  return Array.isArray(value) ? value : [];
}

export function LaunchReadinessReportPreviewDocument({ module, current }) {
  const result = current?.analysis;
  if (!result) return <article className="launch-preview-document"><p>표시할 분석 결과가 없습니다.</p></article>;
  const label = module === 'technology' ? '기술' : '운영';
  const inputs = Object.entries(current.professionalInput ?? {})
    .filter(([, value]) => typeof value === 'string' && value.trim());
  return <article className="launch-preview-document" data-module={module}>
    <header className="launch-preview-document__cover">
      <p>{label} 출시 준비도 보고서</p>
      <h2>{label === '기술' ? '기술 출시 준비 상태를 확인하세요' : '운영 실행 준비 상태를 확인하세요'}</h2>
      <span>작성한 {label} 계획과 외부 보조 근거를 바탕으로 정리한 현재 분석 결과입니다.</span>
    </header>
    {current.stale && <p className="launch-preview-stale"><AppIcon name="alert" size={16} />이전 입력 기준 결과입니다.</p>}

    <section className="launch-preview-section launch-preview-overview" aria-labelledby={`${module}-preview-overview`}>
      <div>
        <p className="launch-preview-kicker">종합 평가</p>
        <h3 id={`${module}-preview-overview`}>{DECISIONS[result.decision] ?? '검토 필요'}</h3>
        <p>{result.summary}</p>
      </div>
      <div className="launch-preview-score">
        <strong>AI 출시 준비도 평가 {result.score}점</strong>
        <small>작성한 기술·운영 계획을 바탕으로 AI가 평가한 준비도입니다. 정해진 재무 산식처럼 계산된 점수는 아닙니다.</small>
        {current.quality?.passed === true && <span><AppIcon name="check" size={14} />독립 AI 검증 통과</span>}
      </div>
    </section>

    <section className="launch-preview-section" aria-labelledby={`${module}-preview-input`}>
      <p className="launch-preview-kicker">입력 기준</p>
      <h3 id={`${module}-preview-input`}>사용자가 작성한 {label} 계획</h3>
      {current.sourceDocumentName && <p className="launch-preview-source"><AppIcon name="file" size={15} />{current.sourceDocumentName}</p>}
      {inputs.length > 0
        ? <dl className="launch-preview-definition-list">{inputs.map(([key, value]) => <div key={key}><dt>{INPUT_LABELS[module]?.[key] ?? '입력 항목'}</dt><dd>{value}</dd></div>)}</dl>
        : <p className="launch-preview-empty">입력 문서가 분석 기준으로 사용되었습니다.</p>}
    </section>

    <section className="launch-preview-section" aria-labelledby={`${module}-preview-dimensions`}>
      <p className="launch-preview-kicker">영역별 평가</p>
      <h3 id={`${module}-preview-dimensions`}>준비 상태를 영역별로 살펴보세요</h3>
      <div className="launch-preview-card-grid">{valuesOf(result.dimensions).map((item) => <article key={item.name}>
        <div><strong>{item.name}</strong><span>{STATUS[item.status] ?? item.status}</span></div>
        <b>AI 평가 {item.score}점</b><p>{item.finding}</p>
      </article>)}</div>
    </section>

    <section className="launch-preview-section" aria-labelledby={`${module}-preview-risks`}>
      <p className="launch-preview-kicker">핵심 위험</p>
      <h3 id={`${module}-preview-risks`}>출시 전에 관리할 위험</h3>
      <div className="launch-preview-rows">{valuesOf(result.risks).map((item) => <article key={item.title}>
        <header><strong>{item.title}</strong><span>{SEVERITY[item.severity] ?? item.severity}</span></header>
        <p><b>영향</b>{item.impact}</p><p><b>대응</b>{item.mitigation}</p>
      </article>)}</div>
    </section>

    <section className="launch-preview-section" aria-labelledby={`${module}-preview-gates`}>
      <p className="launch-preview-kicker">출시 Gate</p>
      <h3 id={`${module}-preview-gates`}>출시 전에 확인할 기준</h3>
      <div className="launch-preview-rows">{valuesOf(result.gates).map((item) => <article key={item.title}>
        <header><strong>{item.title}</strong><span>{STATUS[item.status] ?? item.status}</span></header>
        <p><b>통과 기준</b>{item.criterion}</p><p><b>필요한 증빙</b>{item.evidenceNeeded}</p>
      </article>)}</div>
    </section>

    <section className="launch-preview-section" aria-labelledby={`${module}-preview-actions`}>
      <p className="launch-preview-kicker">우선 실행 과제</p>
      <h3 id={`${module}-preview-actions`}>다음으로 실행할 일</h3>
      <ol className="launch-preview-actions">{valuesOf(result.actions).map((item) => <li key={`${item.priority}-${item.title}`}>
        <b>{item.priority}</b><div><strong>{item.title}</strong><span>담당: {item.owner}</span><small>완료 확인: {item.completionEvidence}</small></div>
      </li>)}</ol>
    </section>

    <section className="launch-preview-section" aria-labelledby={`${module}-preview-evidence`}>
      <p className="launch-preview-kicker">외부 참고자료</p>
      <h3 id={`${module}-preview-evidence`}>분석에 함께 참고한 자료</h3>
      {valuesOf(current.externalEvidence).length
        ? <ul className="launch-preview-links">{current.externalEvidence.map((item) => <li key={`${item.title}-${item.url}`}><a href={item.url} target="_blank" rel="noreferrer">{item.title}<AppIcon name="arrowUpRight" size={14} /></a></li>)}</ul>
        : <p className="launch-preview-empty">별도로 표시할 외부 참고자료가 없습니다.</p>}
    </section>
  </article>;
}
