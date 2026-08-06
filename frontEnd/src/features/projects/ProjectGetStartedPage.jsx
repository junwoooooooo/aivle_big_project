import { Link, Navigate } from 'react-router-dom';

import { PageHeader } from '../../shared/ui/index.js';
import { ResourceDownload } from './BusinessPlanResources.jsx';
import { useProjectContext } from './ProjectContext.jsx';
import { BUSINESS_PLAN_RESOURCES } from './businessPlanResources.js';
import { projectRoutes } from './routing/projectRoutes.js';
import './projects.css';

export default function ProjectGetStartedPage() {
  const { project } = useProjectContext();
  if (project.stage && project.stage !== 'DOCUMENT') return <Navigate to={projectRoutes.overview(project.projectId)} replace />;
  return <section className="project-get-started"><PageHeader eyebrow="Get started" title="프로젝트가 생성되었습니다" description="사업 검증은 자동으로 실행되지 않습니다. 사업계획서 DOCX를 업로드하면 문서 분석과 구조화를 시작할 수 있습니다." /><div className="project-get-started__resources"><p>사업계획서가 아직 준비되지 않았나요?</p><div>{BUSINESS_PLAN_RESOURCES.map((resource) => <ResourceDownload key={resource.id} resource={resource} />)}</div></div><div className="project-get-started__choices"><Link className="project-get-started__primary" to={projectRoutes.documents(project.projectId)}><strong>사업계획서 업로드</strong><span>DOCX 파일을 확인한 뒤 직접 업로드하고 분석을 시작합니다.</span></Link><Link to={projectRoutes.overview(project.projectId)}><strong>프로젝트만 열기</strong><span>현재 상태를 확인하고 나중에 업로드합니다.</span></Link></div></section>;
}
