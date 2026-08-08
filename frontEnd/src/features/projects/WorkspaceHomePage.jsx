import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { useAuth } from '../auth/AuthProvider.jsx';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../../shared/ui/index.js';
import { appRoutes, projectRoutes } from '../../app/routing/projectRoutes.js';
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
  const items = [
    { label: '프로젝트 만들기', route: appRoutes.newProject },
    { label: '아이디어 정리', route: newest ? projectRoutes.idea(newest.projectId) : null },
    { label: '컨셉 생성·법률검토', route: newest ? projectRoutes.concepts(newest.projectId) : null },
    { label: '컨셉 비교·선택', route: newest ? projectRoutes.conceptCompare(newest.projectId) : null },
    { label: '시장분석부터 콘텐츠 제작', route: newest ? projectRoutes.market(newest.projectId) : null },
  ];
  return <aside className="getting-started-rail" aria-labelledby="workspace-getting-started-title"><p>8단계 파이프라인</p><h2 id="workspace-getting-started-title">모든 모듈을 바로 확인할 수 있습니다</h2><ol>{items.map((item, index) => {
    const content = <><span>{index + 1}</span><strong>{item.label}</strong></>;
    const blocked = item.route === appRoutes.newProject && writeRestriction.blocked;
    return <li key={item.label} className={index === 0 && projects.length === 0 ? 'is-current' : ''}>{item.route ? <Link to={item.route} aria-disabled={blocked} title={blocked ? writeRestriction.message : undefined} onClick={(event) => { if (blocked) event.preventDefault(); }} state={item.route === appRoutes.newProject ? overlayState(location) : undefined}>{content}</Link> : content}</li>;
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
  const showGettingStarted = projects.length === 0;
  return <div className="workspace-home"><PageHeader eyebrow="Personal workspace" title={`안녕하세요, ${displayName(user)}님`} description="최근 프로젝트와 새 8단계 파이프라인의 모듈 상태를 확인하세요." />
    <div className="workspace-home__layout">
      {showGettingStarted && <GettingStartedRail projects={projects} newest={newest} location={location} writeRestriction={writeRestriction} />}
      <div className="workspace-home__content">
        <section className="workspace-home__quick" aria-labelledby="workspace-quick-title"><div><h2 id="workspace-quick-title">빠른 시작</h2><p>프로젝트를 만들고 아이디어 정리부터 마케팅 콘텐츠 제작까지 각 모듈을 확인하세요.</p></div><div className="workspace-home__quick-actions"><Link to={appRoutes.newProject} aria-disabled={writeRestriction.blocked} title={writeRestriction.blocked ? writeRestriction.message : undefined} onClick={(event) => { if (writeRestriction.blocked) event.preventDefault(); }} state={overlayState(location)}>새 프로젝트 만들기</Link></div></section>
        {recent.length > 0 ? <section className="workspace-home__recent" aria-labelledby="workspace-recent-title"><div className="section-heading"><div><p>Recent projects</p><h2 id="workspace-recent-title">최근 프로젝트</h2></div><Link to={appRoutes.projects}>모든 프로젝트 보기</Link></div><div className="workspace-home__recent-list">{recent.map((project) => <ProjectRow key={project.projectId} project={project} density="compact" showNextAction={false} onDelete={() => setDeleteTarget(project)} />)}</div></section> : <EmptyState title="첫 프로젝트를 만들어 보세요" description="프로젝트를 만든 뒤 각 모듈에서 필요한 입력과 연결 상태를 확인할 수 있습니다." action={<Link className="primary-link" to={appRoutes.newProject} aria-disabled={writeRestriction.blocked} title={writeRestriction.blocked ? writeRestriction.message : undefined} onClick={(event) => { if (writeRestriction.blocked) event.preventDefault(); }} state={overlayState(location)}>프로젝트 만들기</Link>} />}
      </div>
    </div><ProjectDeleteDialog project={deleteTarget} open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)} onDeleted={async () => { setDeleteTarget(null); await retry(); }} />
  </div>;
}
