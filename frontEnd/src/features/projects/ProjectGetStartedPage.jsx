import { Link } from 'react-router-dom';

import { PageHeader } from '../../shared/ui/index.js';
import { ResourceDownload } from './BusinessPlanResources.jsx';
import { useProjectContext } from './ProjectContext.jsx';
import { BUSINESS_PLAN_RESOURCES } from './businessPlanResources.js';
import { projectRoutes } from '../../app/routing/projectRoutes.js';
import './projects.css';

export default function ProjectGetStartedPage() {
  const { project } = useProjectContext();
  return <section className="project-get-started"><PageHeader eyebrow="Get started" title="프로젝트가 생성되었습니다" description="새 파이프라인에서는 각 모듈을 바로 열고 필요한 입력을 확인할 수 있습니다." /><div className="project-get-started__resources"><p>아이디어 자료가 아직 준비되지 않았나요?</p><div>{BUSINESS_PLAN_RESOURCES.map((resource) => <ResourceDownload key={resource.id} resource={resource} />)}</div></div><div className="project-get-started__choices"><Link className="project-get-started__primary" to={projectRoutes.idea(project.projectId)}><strong>아이디어 정리 열기</strong><span>필요한 입력을 확인하고 준비합니다.</span></Link><Link to={projectRoutes.overview(project.projectId)}><strong>프로젝트 개요 열기</strong><span>전체 모듈 상태를 먼저 확인합니다.</span></Link></div></section>;
}
