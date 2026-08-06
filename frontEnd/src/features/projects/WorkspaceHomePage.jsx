import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { useAuth } from '../auth/AuthProvider.jsx';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../../shared/ui/index.js';
import { appRoutes, projectRoutes } from './routing/projectRoutes.js';
import ProjectRow from './components/ProjectRow.jsx';
import ProjectDeleteDialog from './components/ProjectDeleteDialog.jsx';
import { useProjects } from './hooks/useProjects.js';
import { useServicePolicy } from '../service-policy/useServicePolicy.js';
import { getWriteRestriction } from '../service-policy/servicePolicyRestrictions.js';
import './projects.css';

function overlayState(location) {
  return { backgroundLocation: location, returnTo: `${location.pathname}${location.search}`, source: 'home' };
}

function displayName(user) {
  return user?.displayName || user?.username || '사용자';
}

function GettingStartedRail({ projects, newest, location, writeRestriction }) {
  const steps = [
    { label: '프로젝트 만들기', done: projects.length > 0, route: appRoutes.newProject },
    { label: '아이디어 입력과 확정', done: newest?.stage && !['DOCUMENT', 'STRUCTURING'].includes(newest.stage), route: newest ? projectRoutes.overview(newest.projectId) : null },
    { label: '법률 사전 검토', done: newest?.stage && !['DOCUMENT', 'STRUCTURING', 'LEGAL_REVIEW'].includes(newest.stage), route: newest ? projectRoutes.legal(newest.projectId) : null },
    { label: '적격 Concept 3개 확인', done: newest?.stage && ['MARKETING', 'REPORT', 'COMPLETED'].includes(newest.stage), route: newest ? `${projectRoutes.base(newest.projectId)}/journey/concept` : null },
  ];
  const current = steps.findIndex((step) => !step.done);
  return <aside className="getting-started-rail" aria-labelledby="workspace-getting-started-title"><p>Current Journey</p><h2 id="workspace-getting-started-title">Idea → Legal → Concept 범위</h2><ol>{steps.map((step, index) => {
    const className = step.done ? 'is-done' : index === current ? 'is-current' : '';
    const content = <><span>{step.done ? '✓' : index + 1}</span><strong>{step.label}</strong></>;
    const blocked = step.route === appRoutes.newProject && writeRestriction.blocked;
    return <li key={step.label} className={className}>{step.route ? <Link to={step.route} aria-disabled={blocked} title={blocked ? writeRestriction.message : undefined} onClick={(event) => { if (blocked) event.preventDefault(); }} state={step.route === appRoutes.newProject ? overlayState(location) : undefined}>{content}</Link> : content}</li>;
  })}</ol></aside>;
}

export default function WorkspaceHomePage() {
  const { user } = useAuth();
  const servicePolicy = useServicePolicy();
  const writeRestriction = getWriteRestriction(servicePolicy);
  const location = useLocation();
  const { status, projects, retry } = useProjects();
  const [deleteTarget, setDeleteTarget] = useState(null);
  if (status === 'loading') return <LoadingState label="워크스페이스를 불러오고 있습니다" />;
  if (status === 'error') return <ErrorState title="워크스페이스를 불러오지 못했습니다" onRetry={retry} />;
  const recent = [...projects]
    .sort((left, right) => new Date(right.updatedAt) - new Date(left.updatedAt))
    .slice(0, 3);
  const newest = recent[0];
  const showGettingStarted = projects.length === 0 || projects.every((project) => project.stage === 'DOCUMENT');
  return <div className="workspace-home"><PageHeader eyebrow="Personal workspace" title={`안녕하세요, ${displayName(user)}님`} description="현재 Journey 단계와 최근 저장 상태를 확인하고 다음 작업을 이어가세요." />
    <div className="workspace-home__layout">
      {showGettingStarted && <GettingStartedRail projects={projects} newest={newest} location={location} writeRestriction={writeRestriction} />}
      <div className="workspace-home__content">
        <section className="workspace-home__quick" aria-labelledby="workspace-quick-title"><div><h2 id="workspace-quick-title">빠른 시작</h2><p>프로젝트를 만들고 아이디어를 입력해 실제 AI 기반 검토 Journey를 시작하세요.</p></div><div className="workspace-home__quick-actions"><Link to={appRoutes.newProject} aria-disabled={writeRestriction.blocked} title={writeRestriction.blocked ? writeRestriction.message : undefined} onClick={(event) => { if (writeRestriction.blocked) event.preventDefault(); }} state={overlayState(location)}>새 프로젝트 만들기</Link></div></section>
        {recent.length > 0 ? <section className="workspace-home__recent" aria-labelledby="workspace-recent-title"><div className="section-heading"><div><p>Recent projects</p><h2 id="workspace-recent-title">최근 프로젝트</h2></div><Link to={appRoutes.projects}>모든 프로젝트 보기</Link></div><div className="workspace-home__recent-list">{recent.map((project) => <ProjectRow key={project.projectId} project={project} density="compact" showNextAction={false} onDelete={() => setDeleteTarget(project)} />)}</div></section> : <EmptyState title="첫 사업 검증 프로젝트를 만들어 보세요" description="프로젝트를 만든 뒤 아이디어를 입력하면 법률, Concept, Persona, Marketing과 Report Journey를 시작할 수 있습니다." action={<Link className="primary-link" to={appRoutes.newProject} aria-disabled={writeRestriction.blocked} title={writeRestriction.blocked ? writeRestriction.message : undefined} onClick={(event) => { if (writeRestriction.blocked) event.preventDefault(); }} state={overlayState(location)}>프로젝트 만들기</Link>} />}
      </div>
    </div><ProjectDeleteDialog project={deleteTarget} open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)} onDeleted={async () => { setDeleteTarget(null); await retry(); }} />
  </div>;
}
