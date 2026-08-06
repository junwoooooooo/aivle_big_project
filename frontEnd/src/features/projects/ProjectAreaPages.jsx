import { Link } from 'react-router-dom';

import { Badge, Card, PageHeader, Progress, StatusBadge } from '../../shared/ui/index.js';
import { useProjectContext } from './ProjectContext.jsx';
import {
  PROJECT_AREAS,
  TASK_STATUS_VIEW,
  getAreaSummary,
  getProjectBasePath,
  getProjectNextAction,
  getProjectProgress,
} from './model/projectWorkflowModel.js';

const AREA_CONTENT = {
  [PROJECT_AREAS.PLAN]: {
    title: 'Plan',
    description: '사업 개요와 문서를 입력하고, 구조화 결과를 확인합니다.',
    items: [
      ['Business Brief', 'plan/brief', '프로젝트 이름, 사업 분야와 사업 개요를 직접 저장합니다.'],
      ['Documents', 'plan/documents', '기존 DOCX 문서를 업로드하고 구조화를 시작합니다.'],
      ['Structured Plan', 'plan/structure', '추출된 사업계획 항목과 근거를 확인합니다.'],
    ],
  },
  [PROJECT_AREAS.REVIEW]: {
    title: 'Review',
    description: '법률·규제와 사업 타당성 결과를 검토합니다.',
    items: [
      ['Legal', 'review/legal', '법률·규제 검토 결과와 확인 항목을 봅니다.'],
      ['Feasibility', 'review/market', '시장, 비즈니스 모델, 운영과 재무 관점의 결과를 확인합니다.'],
    ],
  },
  [PROJECT_AREAS.VALIDATE]: {
    title: 'Validate',
    description: '페르소나 기반의 고객 검증을 진행합니다.',
    items: [['Personas', 'validate/personas', '데이터 근거를 바탕으로 한 페르소나 추천 결과를 확인합니다.']],
  },
  [PROJECT_AREAS.REPORT]: {
    title: 'Report',
    description: '검증 결과를 통합 보고서에서 확인합니다.',
    items: [['Integrated Report', 'report', '현재 생성된 결과와 근거를 통합해 검토합니다.']],
  },
};

function AreaSummaryPage({ area }) {
  const { project } = useProjectContext();
  const content = AREA_CONTENT[area];
  const basePath = getProjectBasePath(project.projectId);
  return (
    <section className="project-area-summary">
      <PageHeader title={content.title} description={content.description} />
      <div className="project-area-summary__list">
        {content.items.map(([title, route, description]) => (
          <article key={route}>
            <div><h2>{title}</h2><p>{description}</p></div>
            <Link to={`${basePath}/${route}`}>열기</Link>
          </article>
        ))}
      </div>
    </section>
  );
}

export function ProjectOverviewPage() {
  const { project } = useProjectContext();
  const nextAction = getProjectNextAction(project);
  const basePath = getProjectBasePath(project.projectId);
  const areas = getAreaSummary(project);
  return (
    <div className="project-overview">
      <header className="project-overview__header">
        <p>Project overview</p>
        <div><h2>현재 검증 상태</h2><StatusBadge status={project.status} /></div>
        <span>{project.description || '등록된 프로젝트 설명이 없습니다.'}</span>
      </header>
      <Card className="project-overview__next-action">
        <div><p>Next action</p><h2>{nextAction.label}</h2><span>{nextAction.description}</span></div>
        <Link className="primary-link" to={nextAction.route}>계속하기</Link>
      </Card>
      <section className="project-overview__workflow" aria-labelledby="workflow-summary-title">
        <div className="project-overview__section-heading"><h2 id="workflow-summary-title">Workflow</h2><Progress value={getProjectProgress(project)} label="전체 진행" /></div>
        <div className="project-overview__areas">
          {areas.map((area) => {
            const statusView = TASK_STATUS_VIEW[area.taskStatus] ?? TASK_STATUS_VIEW.UNKNOWN;
            return <Link key={area.id} to={`${basePath}/${area.path}`}><strong>{area.label}</strong><Badge tone={statusView.tone}>{statusView.label}</Badge></Link>;
          })}
        </div>
      </section>
      <p className="project-overview__notice">실행 이력, 위험 항목과 활동 정보는 해당 데이터를 제공하는 API가 연결될 때 표시됩니다.</p>
    </div>
  );
}

export function PlanSummaryPage() { return <AreaSummaryPage area={PROJECT_AREAS.PLAN} />; }
export function ReviewSummaryPage() { return <AreaSummaryPage area={PROJECT_AREAS.REVIEW} />; }
export function ValidateSummaryPage() { return <AreaSummaryPage area={PROJECT_AREAS.VALIDATE} />; }
