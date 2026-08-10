import { Link, NavLink, Outlet, useLocation, useNavigate, useParams } from 'react-router-dom';

import { ErrorState, LoadingState } from '../../shared/ui/index.js';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { ProjectProvider, useProjectContext } from '../../features/projects/ProjectContext.jsx';
import { getModuleStatusView, getProjectModuleByPath, getProjectModules } from '../module-status/projectModuleModel.js';
import { useProjectModuleStatuses } from '../module-status/useProjectModuleStatuses.js';
import JobCenter from '../../features/job-center/JobCenter.jsx';
import { projectRoutes } from '../routing/projectRoutes.js';
import './project-shell.css';

function ProjectLayoutContent() {
  const { projectId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { status, project, retry } = useProjectContext();
  const moduleState = useProjectModuleStatuses(projectId);
  if (status === 'loading') return <LoadingState label="프로젝트를 불러오고 있습니다" />;
  if (status === 'error' || !project) return <ErrorState title="프로젝트를 찾을 수 없습니다" description="프로젝트가 없거나 접근 권한이 없습니다." onRetry={retry} />;

  const modules = getProjectModules(projectId, moduleState.modules);
  const current = getProjectModuleByPath(projectId, location.pathname, moduleState.modules);
  const currentIndex = modules.findIndex(({ id }) => id === current.id);
  const previous = currentIndex > 0 ? modules[currentIndex - 1] : null;
  const next = currentIndex < modules.length - 1 ? modules[currentIndex + 1] : null;
  const currentStatus = moduleState.status === 'error'
    ? { label: '상태 확인 필요', tone: 'danger' }
    : moduleState.status === 'loading'
      ? { label: '불러오는 중', tone: 'neutral' }
      : getModuleStatusView(current.status);

  return <div className="pipeline-shell">
    <header className="pipeline-shell__header">
      <div className="pipeline-shell__project"><p>{project.industryCategory || '사업 분야 미입력'}</p><h1>{project.name}</h1></div>
      <div className="pipeline-shell__module"><div><span>현재 모듈</span><h2>{current.label}</h2></div><span className="pipeline-status" data-tone={currentStatus.tone}>{currentStatus.label}</span></div>
      <div className="pipeline-shell__actions">
        <a href="#project-task-center">작업 센터</a>
        <Link to={projectRoutes.settings(projectId)} state={{ backgroundLocation: location, returnTo: location.pathname }}>프로젝트 설정</Link>
      </div>
    </header>

    <div className="pipeline-shell__mobile-controls">
      <label><span>현재 단계</span><select value={current.id} onChange={(event) => navigate(modules.find(({ id }) => id === event.target.value).href)}>{modules.map((module) => <option key={module.id} value={module.id}>{module.label}</option>)}</select></label>
      <nav aria-label="이전 및 다음 모듈">{previous ? <Link to={previous.href}>← 이전</Link> : <span />}{next ? <Link to={next.href}>다음 →</Link> : <span />}</nav>
    </div>

    <div className="pipeline-shell__body">
      <aside className="pipeline-shell__sidebar"><nav aria-label="프로젝트 모듈"><ul>{modules.map((module) => {
        const moduleStatus = getModuleStatusView(module.status);
        const statusView = moduleState.status === 'error'
          ? { label: '확인 실패', tone: 'danger' }
          : moduleState.status === 'loading'
            ? { label: '확인 중', tone: 'neutral' }
            : moduleStatus;
        return <li key={module.id}><NavLink to={module.href} aria-current={module.id === current.id ? 'page' : undefined}><span>{module.label}</span><small data-tone={statusView.tone}>{statusView.label}</small></NavLink></li>;
      })}</ul></nav></aside>
      <main className="pipeline-shell__main">
        {moduleState.status === 'error' && <section className="pipeline-module-status-error" role="alert"><div><strong>모듈 상태를 불러오지 못했습니다</strong><span>{getUserErrorMessage(moduleState.error)} 프로젝트 이동과 설정은 계속 사용할 수 있습니다.</span></div><button type="button" onClick={moduleState.retry}>다시 시도</button></section>}
        <Outlet context={{ modules, moduleState }} />
        <JobCenter projectId={projectId} onTerminal={moduleState.retry} />
      </main>
    </div>
  </div>;
}

export default function ProjectLayout() {
  const { projectId } = useParams();
  return <ProjectProvider projectId={projectId}><ProjectLayoutContent /></ProjectProvider>;
}
