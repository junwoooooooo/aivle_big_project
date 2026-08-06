import { Link, useOutletContext } from 'react-router-dom';

import { getModuleStatusView } from '../module-status/projectModuleModel.js';

const MODULE_CONTENT = Object.freeze({
  idea: {
    title: '아이디어 정리',
    description: '아이디어를 구조화하려면 사업 목적, 해결할 문제와 대상 고객 입력이 필요합니다.',
    stateTitle: '입력이 필요합니다',
    stateDescription: '아이디어 정리 기능은 다음 구현 단계에서 연결됩니다. 지금은 필요한 입력 범위를 확인할 수 있습니다.',
    inputs: ['해결하려는 문제', '대상 고객', '제안 가치와 사업 조건'],
    actionLabel: '프로젝트 개요 보기',
    actionRouteKey: 'overview',
  },
  concepts: {
    title: '컨셉 생성·법률검토',
    description: '확정된 아이디어를 바탕으로 컨셉 후보와 후보별 법률 검토 결과를 함께 다룹니다.',
    stateTitle: '아이디어 정리가 필요합니다',
    stateDescription: '컨셉 생성과 법률검토 실행 기능은 아직 연결되지 않았습니다.',
    inputs: ['확정된 Idea Brief', '검토할 사업 조건과 규제 민감 정보'],
    actionLabel: '아이디어 정리로 이동',
    actionRouteKey: 'idea',
  },
  conceptCompare: {
    title: '컨셉 비교·선택',
    description: '생성된 컨셉 후보의 핵심 차이와 법률 검토 결과를 비교하고 하나를 선택합니다.',
    stateTitle: '비교할 컨셉이 필요합니다',
    stateDescription: '컨셉 비교와 선택 기능은 아직 연결되지 않았습니다.',
    inputs: ['생성이 완료된 컨셉 후보', '후보별 법률 검토 결과'],
    actionLabel: '컨셉 생성으로 이동',
    actionRouteKey: 'concepts',
  },
  market: {
    title: '시장분석·기획 확정',
    description: '선택한 컨셉을 외부 시장분석 모듈에 전달하고 제안을 검토해 기획을 확정합니다.',
    stateTitle: '외부 모듈 연결 준비 중',
    stateDescription: '시장분석 연동과 실행 기능은 이번 단계에서 제공하지 않습니다.',
    inputs: ['선택된 컨셉 Snapshot', '시장분석 모듈 연결'],
    actionLabel: '컨셉 비교로 이동',
    actionRouteKey: 'conceptCompare',
  },
  businessPersonaTest: {
    title: 'BM·재무 분석 + 페르소나 응답 테스트',
    description: '확정된 기획의 비즈니스 모델과 재무 구조를 분석하고, 경쟁상품 대비 반응을 가상 페르소나 응답으로 확인합니다.',
    stateTitle: '외부 모듈 연결 준비 중',
    stateDescription: 'BM·재무 분석과 응답 테스트 연동은 아직 제공되지 않습니다.',
    inputs: ['확정된 기획 Snapshot', '외부 분석 모듈 연결'],
    actionLabel: '시장분석으로 이동',
    actionRouteKey: 'market',
  },
  marketing: {
    title: '마케팅 콘텐츠 제작',
    description: '확정된 기획과 분석 결과를 출처로 사용해 마케팅 콘텐츠를 제작합니다.',
    stateTitle: '연결 준비 중',
    stateDescription: '콘텐츠 생성과 편집 실행 기능은 후속 단계에서 연결됩니다.',
    inputs: ['확정된 기획', '사용 가능한 분석 결과와 콘텐츠 목적'],
    actionLabel: '프로젝트 개요 보기',
    actionRouteKey: 'overview',
  },
});

const REQUIRED_INPUT_LABELS = Object.freeze({
  ideaDescription: '해결하려는 문제와 대상 고객 설명',
  ideaBriefSnapshotId: '확정된 Idea Brief',
  eligibleConcepts: '생성이 완료된 컨셉 후보',
  selectedConceptSnapshotId: '선택된 컨셉 Snapshot',
  marketAnalysisConnection: '시장분석 모듈 연결',
  finalizedPlanningSnapshotId: '확정된 기획 Snapshot',
  businessPersonaModuleConnection: 'BM·재무·응답 테스트 모듈 연결',
});

function ModuleStatusBadge({ status }) {
  const view = getModuleStatusView(status);
  return <span className="pipeline-status" data-tone={view.tone}>{view.label}</span>;
}

export function ProjectOverviewPage() {
  const { modules: shellModules } = useOutletContext();
  const modules = shellModules.filter(({ id }) => !['overview', 'settings'].includes(id));
  return <section className="pipeline-overview" aria-labelledby="project-overview-title">
    <div className="pipeline-page-heading"><p>6단계 파이프라인</p><h2 id="project-overview-title">프로젝트 개요</h2><span>각 모듈은 독립 상태로 표시되며 언제든 화면을 열어 필요한 입력을 확인할 수 있습니다.</span></div>
    <div className="pipeline-overview__grid">{modules.map((module) => {
      const content = MODULE_CONTENT[module.id];
      return <article key={module.id}><div><h3>{module.label}</h3><ModuleStatusBadge status={module.status} /></div><p>{content.description}</p><Link to={module.href}>{content.stateTitle} 확인</Link></article>;
    })}</div>
  </section>;
}

export function ProjectModulePlaceholder({ moduleId }) {
  const { modules } = useOutletContext();
  const module = modules.find(({ id }) => id === moduleId);
  const content = MODULE_CONTENT[moduleId];
  const action = modules.find(({ routeKey }) => routeKey === content.actionRouteKey);
  const inputs = Array.isArray(module.requiredInputs)
    ? module.requiredInputs.map((input) => REQUIRED_INPUT_LABELS[input] ?? input)
    : content.inputs;
  const stateTitle = module.status === 'READY' ? '시작할 준비가 되었습니다' : content.stateTitle;
  return <section className="pipeline-placeholder" aria-labelledby={`${moduleId}-title`}>
    <div className="pipeline-page-heading"><p>새 프로젝트 파이프라인</p><h2 id={`${moduleId}-title`}>{content.title}</h2><span>{content.description}</span></div>
    <div className="pipeline-placeholder__state">
      <ModuleStatusBadge status={module.status} />
      <h3>{stateTitle}</h3>
      <p>{content.stateDescription}</p>
      <div><h4>필요한 입력</h4>{inputs.length > 0 ? <ul>{inputs.map((input) => <li key={input}>{input}</li>)}</ul> : <p>현재 추가 입력이 필요하지 않습니다.</p>}</div>
      <Link to={action.href}>{content.actionLabel}</Link>
    </div>
  </section>;
}
