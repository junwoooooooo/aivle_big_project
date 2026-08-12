import { Link, useOutletContext } from 'react-router-dom';

import { getModuleStatusView } from '../module-status/projectModuleModel.js';

const MODULE_DESCRIPTIONS = Object.freeze({
  idea: '아이디어를 정리하고 후속 질문과 검토를 거쳐 확정합니다.',
  concepts: '컨셉 5개를 만들고 공식 법률 근거를 확인합니다.',
  conceptCompare: '완성된 컨셉의 차이와 법률 사전검토 결과를 비교하고 하나를 선택합니다.',
  market: '선택한 컨셉과 확정 가설·법률 결과로 시장 근거를 수집하고 분석합니다.',
  businessModel: '시장 근거와 실행 계획을 결합해 Business Model을 분석합니다.',
  twinSurvey: '재무 분석 다음에 선택한 컨셉의 비교안을 만들고 Twin 표본으로 방향과 측정 가능성을 확인합니다.',
  techOps: 'current Concept·Market·BM과 확정 사용자 입력으로 기술·운영 상용화 자문을 생성합니다.',
  finance: 'current Market·BM 근거와 추가 재무 입력으로 FinancialInputSnapshot을 준비합니다.',
  marketing: '선택 컨셉과 확정 가설, 법률 결과로 마케팅 콘텐츠를 제작합니다.',
});

function ModuleStatusBadge({ status }) {
  const view = getModuleStatusView(status);
  return <span className="pipeline-status" data-tone={view.tone}>{view.label}</span>;
}

export function ProjectOverviewPage() {
  const { modules: shellModules } = useOutletContext();
  const modules = shellModules.filter(({ id }) => !['overview', 'settings'].includes(id));
  return <section className="pipeline-overview" aria-labelledby="project-overview-title">
    <div className="pipeline-page-heading"><p>8단계 파이프라인</p><h2 id="project-overview-title">프로젝트 개요</h2><span>각 모듈은 독립 상태로 표시되며 상태와 관계없이 언제든 화면을 열 수 있습니다.</span></div>
    <div className="pipeline-overview__grid">{modules.map((module) => (
      <article key={module.id}>
        <div><h3>{module.label}</h3><ModuleStatusBadge status={module.status} /></div>
        <p>{MODULE_DESCRIPTIONS[module.id]}</p>
        <Link to={module.href}>{module.label} 열기</Link>
      </article>
    ))}</div>
  </section>;
}
