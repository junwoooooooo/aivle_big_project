import { Link, useOutletContext } from 'react-router-dom';

import { getJourneyStatusView } from '../module-status/projectJourneyModel.js';

const JOURNEY_DESCRIPTIONS = Object.freeze({
  planning: '아이디어를 정리하고 법률 검토가 포함된 사업안을 확정합니다.',
  validation: '시장 근거를 수집하고 실행 가능한 사업 모델을 검증합니다.',
  launch: '기술·운영 구성과 재무 전망을 확정해 출시를 준비합니다.',
  interview: '가상 패널의 반응과 사용·구매 의향을 확인합니다.',
  marketingStrategy: '확정된 근거를 기반으로 메시지와 콘텐츠를 제작합니다.',
  finalReport: '각 단계의 정본 결과와 출처를 사업 타당성 문서로 정리합니다.',
});

function JourneyStatusBadge({ status }) {
  const view = getJourneyStatusView(status);
  return <span className="pipeline-status" data-tone={view.tone}>{view.label}</span>;
}

export function ProjectOverviewPage() {
  const { journeys } = useOutletContext();
  return <section className="pipeline-overview" aria-labelledby="project-overview-title">
    <div className="pipeline-page-heading"><p>6단계 업무 흐름</p><h2 id="project-overview-title">프로젝트 개요</h2><span>사업 기획부터 최종 보고서까지 현재 업무 상태와 다음 단계를 확인하세요.</span></div>
    <div className="pipeline-overview__grid">{journeys.map((journey) => (
      <article key={journey.id}>
        <div><h3>{journey.label}</h3><JourneyStatusBadge status={journey.status} /></div>
        <p>{JOURNEY_DESCRIPTIONS[journey.id]}</p>
        <Link to={journey.href}>{journey.shortLabel} 열기</Link>
      </article>
    ))}</div>
  </section>;
}
