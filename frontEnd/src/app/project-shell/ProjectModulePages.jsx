import { Link, useOutletContext } from 'react-router-dom';

import { getModuleStatusView } from '../module-status/projectModuleModel.js';

const MODULE_DESCRIPTIONS = Object.freeze({
  idea: '아이디어를 정리하고 후속 질문과 검토를 거쳐 확정합니다.',
  concepts: '컨셉 5개를 만들고 공식 법률 근거를 확인합니다.',
  conceptCompare: '완성된 컨셉의 차이와 법률 사전검토 결과를 비교하고 하나를 선택합니다.',
  market: '선택한 컨셉의 시장 규모와 가격을 공개 통계·공시로 직접 조사합니다.',
  businessModel: '조사 결과를 근거로 BM 캔버스 9칸을 채웁니다.',
  techOps: '상위 확정값을 재사용해 기술·운영 입력을 준비하고 외부 모듈에 전달합니다.',
  finance: 'TechOps 승계값과 추가 재무 입력으로 FinancialInputSnapshot을 준비합니다.',
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
