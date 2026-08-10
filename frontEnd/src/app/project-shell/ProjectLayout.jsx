import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate, useParams } from 'react-router-dom';

import { ErrorState, LoadingState } from '../../shared/ui/index.js';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { useProjectEvents } from '../../shared/async-events/index.js';
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
  const live = useProjectEvents(projectId);
  const moduleState = useProjectModuleStatuses(projectId, live.revision);
  const [workCenter, setWorkCenter] = useState({ mounted: false, phase: 'closed', focusJobId: null });
  const workCenterTrigger = useRef(null);
  const closeTimer = useRef(null);
  const openWorkCenter = useCallback((focusJobId = null, trigger = null) => {
    clearTimeout(closeTimer.current);
    if (trigger) workCenterTrigger.current = trigger;
    setWorkCenter({ mounted: true, phase: 'opening', focusJobId });
    requestAnimationFrame(() => setWorkCenter((value) => ({ ...value, phase: 'open' })));
  }, []);
  const closeWorkCenter = useCallback(() => {
    setWorkCenter((value) => ({ ...value, phase: 'closing' }));
    closeTimer.current = setTimeout(() => {
      setWorkCenter({ mounted: false, phase: 'closed', focusJobId: null });
      requestAnimationFrame(() => workCenterTrigger.current?.focus());
    }, 240);
  }, []);
  useEffect(() => {
    if (!workCenter.mounted) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const escape = (event) => { if (event.key === 'Escape') closeWorkCenter(); };
    window.addEventListener('keydown', escape);
    return () => { document.body.style.overflow = previousOverflow; window.removeEventListener('keydown', escape); };
  }, [closeWorkCenter, workCenter.mounted]);
  useEffect(() => () => clearTimeout(closeTimer.current), []);
  if (status === 'loading') return <LoadingState label="프로젝트를 불러오고 있습니다" />;
  if (status === 'error' || !project) return <ErrorState title="프로젝트를 찾을 수 없습니다" description="프로젝트가 없거나 접근 권한이 없습니다." onRetry={retry} />;

  const modules = getProjectModules(projectId, moduleState.modules);
  const current = getProjectModuleByPath(projectId, location.pathname, moduleState.modules);
  const currentIndex = modules.findIndex(({ id }) => id === current.id);
  const previous = currentIndex > 0 ? modules[currentIndex - 1] : null;
  const next = currentIndex < modules.length - 1 ? modules[currentIndex + 1] : null;
  const currentStatus = moduleState.status === 'error' ? { label: '상태 확인 필요', tone: 'danger' }
    : moduleState.status === 'loading' ? { label: '불러오는 중', tone: 'neutral' } : getModuleStatusView(current.status);

  return <div className="pipeline-shell">
    <header className="pipeline-shell__header"><div className="pipeline-shell__project"><p>{project.industryCategory || '사업 분야 미입력'}</p><h1>{project.name}</h1></div><div className="pipeline-shell__module"><div><span>현재 단계</span><h2>{current.label}</h2></div><span className="pipeline-status" data-tone={currentStatus.tone}>{currentStatus.label}</span></div><div className="pipeline-shell__actions"><button type="button" onClick={(event) => openWorkCenter(null, event.currentTarget)}>작업 센터</button><Link to={projectRoutes.settings(projectId)} state={{ backgroundLocation: location, returnTo: location.pathname }}>프로젝트 설정</Link></div></header>
    <div className="pipeline-shell__mobile-controls"><label><span>현재 단계</span><select value={current.id} onChange={(event) => navigate(modules.find(({ id }) => id === event.target.value).href)}>{modules.map((module) => <option key={module.id} value={module.id}>{module.label}</option>)}</select></label><nav aria-label="이전 및 다음 단계">{previous ? <Link to={previous.href}>← 이전</Link> : <span />}{next ? <Link to={next.href}>다음 →</Link> : <span />}</nav></div>
    <div className="pipeline-shell__body"><aside className="pipeline-shell__sidebar"><nav aria-label="프로젝트 단계"><ul>{modules.map((module) => { const view = getModuleStatusView(module.status); return <li key={module.id}><NavLink to={module.href} aria-current={module.id === current.id ? 'page' : undefined}><span>{module.label}</span><small data-tone={view.tone}>{view.label}</small></NavLink></li>; })}</ul></nav></aside><main className="pipeline-shell__main">{moduleState.status === 'error' && <section className="pipeline-module-status-error" role="alert"><div><strong>단계 상태를 불러오지 못했습니다.</strong><span>{getUserErrorMessage(moduleState.error)} 작업 화면은 계속 사용할 수 있습니다.</span></div><button type="button" onClick={moduleState.retry}>다시 시도</button></section>}<Outlet context={{ modules, moduleState, liveRevision: live.revision, projectEventTransport: live.transport }} /></main><aside className="pipeline-shell__work-center"><JobCenter projectId={projectId} compact refreshKey={live.revision} onTerminal={moduleState.retry} sheet={workCenter} onOpenList={(event) => openWorkCenter(null, event?.currentTarget)} onOpenJob={(jobId, trigger) => openWorkCenter(jobId, trigger)} onCloseSheet={closeWorkCenter} onShowList={() => setWorkCenter((value) => ({ ...value, focusJobId: null }))} /></aside></div>
    <ProjectHelpControl current={current} currentStatus={currentStatus} />
  </div>;
}

export function ProjectHelper({ current, currentStatus, onClose }) {
  return <aside className="pipeline-shell__helper" role="dialog" aria-label="현재 단계 도움말"><header><strong>{current.label} 안내</strong><button type="button" onClick={onClose}>닫기</button></header><p>현재 상태: {currentStatus.label}</p><p>다음에 할 일: {current.nextAction?.label ?? '현재 화면의 안내를 확인해 주세요.'}</p><p>오른쪽 작업 센터에서 진행 중인 작업, 입력이 필요한 작업과 실제 처리 기록을 확인할 수 있습니다.</p></aside>;
}

export function ProjectHelpControl({ current, currentStatus }) {
  const [open, setOpen] = useState(false);
  return <>{open && <ProjectHelper current={current} currentStatus={currentStatus} onClose={() => setOpen(false)} />}<button type="button" className="pipeline-shell__help" aria-label="도움말과 가이드 열기" aria-expanded={open} onClick={() => setOpen((value) => !value)}>?</button></>;
}

export default function ProjectLayout() { const { projectId } = useParams(); return <ProjectProvider projectId={projectId}><ProjectLayoutContent /></ProjectProvider>; }
