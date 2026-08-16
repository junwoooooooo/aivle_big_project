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
    { label: '사업 기획', route: newest ? projectRoutes.idea(newest.projectId) : null },
    { label: '사업 검증', route: newest ? projectRoutes.market(newest.projectId) : null },
    { label: '출시 준비', route: newest ? projectRoutes.techOps(newest.projectId) : null },
    { label: '시장 인터뷰', route: newest ? projectRoutes.marketInterview(newest.projectId) : null },
    { label: '마케팅 전략과 최종 보고서', route: newest ? projectRoutes.marketing(newest.projectId) : null },
  ];
  return <aside className="getting-started-rail" aria-labelledby="workspace-getting-started-title"><p>6단계 업무 흐름</p><h2 id="workspace-getting-started-title">사업 검증 여정을 시작하세요</h2><ol>{items.map((item, index) => {
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
  const continueProject = recent.find(({ presentationState }) => presentationState !== 'COMPLETED') ?? recent[0];
  const attentionProjects = projects.filter(({ presentationState }) => presentationState === 'NEEDS_ATTENTION').slice(0, 3);
  return <div className="workspace-home"><PageHeader eyebrow="내 워크스페이스" title={`안녕하세요, ${displayName(user)}님`} description="이어서 할 일과 확인이 필요한 프로젝트를 한눈에 살펴보세요." actions={<Link className="primary-link" to={appRoutes.newProject} aria-disabled={writeRestriction.blocked} title={writeRestriction.blocked ? writeRestriction.message : undefined} onClick={(event) => { if (writeRestriction.blocked) event.preventDefault(); }} state={overlayState(location)}>새 프로젝트</Link>} />
    <div className={`workspace-home__layout ${showGettingStarted ? 'workspace-home__layout--with-rail' : 'workspace-home__layout--single'}`}>
      {showGettingStarted && <GettingStartedRail projects={projects} newest={newest} location={location} writeRestriction={writeRestriction} />}
      <div className="workspace-home__content">
        {continueProject && <section className="workspace-home__continue-project" aria-labelledby="workspace-continue-title"><div><p>이어서 할 프로젝트</p><h2 id="workspace-continue-title">{continueProject.name}</h2><span>{continueProject.industryCategory || '사업 분야 미입력'}</span></div><dl><div><dt>현재 단계</dt><dd>{continueProject.stageLabel}</dd></div><div><dt>진행률</dt><dd>{continueProject.journeyCompleted} / 6</dd></div><div><dt>다음 할 일</dt><dd>{continueProject.nextAction.label}</dd></div></dl><Link to={continueProject.nextAction.route}>{continueProject.nextAction.actionLabel}</Link></section>}
        <section className="workspace-home__attention" aria-labelledby="workspace-attention-title"><div className="section-heading"><div><p>확인할 항목</p><h2 id="workspace-attention-title">지금 살펴볼 프로젝트</h2></div></div>{attentionProjects.length ? <ul>{attentionProjects.map((project) => <li key={project.projectId}><Link to={project.nextAction.route}><strong>{project.name}</strong><span>{project.attentionReason || '확인이 필요한 항목이 있습니다.'}</span><small>{project.stageLabel}</small></Link></li>)}</ul> : <p className="workspace-home__clear">지금 확인이 필요한 항목이 없습니다.</p>}</section>
        {recent.length > 0 ? <section className="workspace-home__recent" aria-labelledby="workspace-recent-title"><div className="section-heading"><div><p>최근 프로젝트</p><h2 id="workspace-recent-title">최근 수정한 프로젝트</h2></div><Link to={appRoutes.projects}>모든 프로젝트 보기</Link></div><div className="workspace-home__recent-list">{recent.map((project) => <ProjectRow key={project.projectId} project={project} density="compact" showNextAction={false} onDelete={() => setDeleteTarget(project)} />)}</div></section> : <EmptyState title="첫 프로젝트를 만들어 보세요" description="프로젝트를 만든 뒤 이어서 할 일과 확인할 항목을 이 화면에서 안내합니다." action={<Link className="primary-link" to={appRoutes.newProject} aria-disabled={writeRestriction.blocked} title={writeRestriction.blocked ? writeRestriction.message : undefined} onClick={(event) => { if (writeRestriction.blocked) event.preventDefault(); }} state={overlayState(location)}>프로젝트 만들기</Link>} />}
      </div>
    </div><ProjectDeleteDialog project={deleteTarget} open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)} onDeleted={async () => { setDeleteTarget(null); await retry(); }} />
  </div>;
}
