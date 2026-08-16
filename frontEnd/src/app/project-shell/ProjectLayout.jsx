import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, Outlet, useLocation, useParams } from 'react-router-dom';

import { AppIcon, ErrorState, LoadingState, ScrollToTopButton } from '../../shared/ui/index.js';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { useProjectEvents } from '../../shared/async-events/index.js';
import { ProjectProvider, useProjectContext } from '../../features/projects/ProjectContext.jsx';
import { getModuleStatusView, getProjectModules, MODULE_STATUS } from '../module-status/projectModuleModel.js';
import { getJourneyByPath, getJourneyStatusView, getProjectJourneys, JOURNEY_STATUS } from '../module-status/projectJourneyModel.js';
import { useProjectModuleStatuses } from '../module-status/useProjectModuleStatuses.js';
import { createConceptPortfolioApi } from '../../features/concept-portfolio/api/conceptPortfolioApi.js';
import { startNewConceptPortfolioRun } from '../../features/concept-portfolio/hooks/useConceptPortfolio.js';
import { createFinalReportApi } from '../../features/final-report/finalReportApi.js';
import { projectRoutes } from '../routing/projectRoutes.js';
import { useProjectChrome } from './ProjectChromeContext.jsx';
import { deriveProjectPresentationState, getProjectPresentationView } from '../../features/projects/model/projectPresentation.js';
import './project-shell.css';
import './project-shell-polish.css';

export function JourneySubsteps({ journey, currentModule }) {
  if (!journey?.children || journey.children.length < 2) return null;
  return <nav className="journey-substeps" aria-label={`${journey.shortLabel} 세부 업무`}>
    {journey.children.map((module, index) => <div key={module.id} className={module.id === currentModule.id ? 'is-current' : ''}>
      <Link to={module.href} aria-current={module.id === currentModule.id ? 'step' : undefined}><span className={module.status === MODULE_STATUS.COMPLETED ? 'is-complete' : ''} aria-hidden="true" />{module.shortLabel}</Link>
      {index < journey.children.length - 1 && <i aria-hidden="true" />}
    </div>)}
  </nav>;
}

export function ProjectLocationRow({ projectId, currentJourney }) {
  const isOverview = currentJourney?.id === 'overview';
  return <div className="pipeline-shell__location-row">
    <nav className="pipeline-shell__breadcrumb" aria-label="현재 위치">
      <Link to={projectRoutes.overview(projectId)}>프로젝트 개요</Link>
      {!isOverview && <><span aria-hidden="true">/</span><span aria-current="page">{currentJourney?.shortLabel}</span></>}
    </nav>
    {!isOverview && <Link className="pipeline-shell__overview-return" to={projectRoutes.overview(projectId)}
      aria-label="프로젝트 개요로 돌아가기" title="프로젝트 개요로 돌아가기">
      <AppIcon name="chevronLeft" size={18} />
    </Link>}
  </div>;
}

function ProjectLayoutContent() {
  const { projectId } = useParams();
  const location = useLocation();
  const apiClient = useApiClient();
  const portfolioApi = useMemo(() => createConceptPortfolioApi(apiClient), [apiClient]);
  const finalReportApi = useMemo(() => createFinalReportApi(apiClient), [apiClient]);
  const { register, toolActions } = useProjectChrome();
  const { status, project, retry } = useProjectContext();
  const live = useProjectEvents(projectId);
  const moduleState = useProjectModuleStatuses(projectId, live.revision);
  const [finalReportState, setFinalReportState] = useState({ projectId, status: 'loading', view: null });
  const retryModuleStatus = moduleState.retry;
  useEffect(() => {
    const controller = new AbortController();
    finalReportApi.current(projectId, { signal: controller.signal })
      .then((view) => { if (!controller.signal.aborted) setFinalReportState({ projectId, status: 'success', view }); })
      .catch(() => { if (!controller.signal.aborted) setFinalReportState({ projectId, status: 'error', view: null }); });
    return () => controller.abort();
  }, [finalReportApi, live.revision, projectId]);
  const finalReportJourneyStatus = finalReportState.projectId !== projectId || finalReportState.status !== 'success'
    ? JOURNEY_STATUS.NOT_STARTED : finalReportState.view?.state === 'CURRENT' ? JOURNEY_STATUS.COMPLETED
      : finalReportState.view?.state === 'STALE' ? JOURNEY_STATUS.STALE
        : finalReportState.view?.readiness?.some(({ status: itemStatus }) => itemStatus !== 'NOT_STARTED')
          ? JOURNEY_STATUS.IN_PROGRESS : JOURNEY_STATUS.NOT_STARTED;
  const modules = useMemo(() => getProjectModules(projectId, moduleState.modules), [moduleState.modules, projectId]);
  const journeys = useMemo(() => getProjectJourneys(projectId, modules, finalReportJourneyStatus), [finalReportJourneyStatus, modules, projectId]);
  const routeJourney = useMemo(() => getJourneyByPath(location.pathname), [location.pathname]);
  const currentJourney = useMemo(() => routeJourney.id === 'overview' ? routeJourney
    : journeys.find(({ id }) => id === routeJourney.id) ?? routeJourney, [journeys, routeJourney]);
  const currentModule = useMemo(() => {
    if (currentJourney.id === 'finalReport') return { id: 'finalReport', label: '최종 보고서', shortLabel: '최종 보고서', href: projectRoutes.finalReport(projectId), status: MODULE_STATUS.NOT_READY };
    const normalized = location.pathname.replace(/\/+$/, '');
    if (normalized.endsWith('/concepts/compare') || normalized.endsWith('/concepts/legal-report')) return modules.find(({ id }) => id === 'concepts');
    if (/\/(launch-readiness|technology|operations|tech-ops|finance)$/.test(normalized)
        || normalized.includes('/launch-readiness/reports/')) return modules.find(({ id }) => id === 'launchReadiness');
    return modules.find(({ href }) => href === normalized) ?? modules[0];
  }, [currentJourney.id, location.pathname, modules, projectId]);
  const currentStatus = useMemo(() => moduleState.status === 'error' ? { label: '상태 확인 필요', tone: 'danger' }
    : moduleState.status === 'loading' ? { label: '불러오는 중', tone: 'neutral' }
      : currentJourney.id === 'overview' ? getModuleStatusView(currentModule.status)
        : getJourneyStatusView(currentJourney.status ?? JOURNEY_STATUS.NOT_STARTED),
  [currentJourney, currentModule.status, moduleState.status]);
  const projectPresentation = useMemo(() => getProjectPresentationView(deriveProjectPresentationState(journeys)), [journeys]);
  const retryPortfolioJob = useCallback(async (job) => {
    if (job?.taskType !== 'CONCEPT_PORTFOLIO_V2_RUN') return;
    await startNewConceptPortfolioRun(portfolioApi, projectId);
    retryModuleStatus();
  }, [portfolioApi, projectId, retryModuleStatus]);
  const chromeModel = useMemo(() => ({
    projectId, project, journeys, currentJourney, currentModule, currentStatus,
    refreshKey: live.revision, onTerminal: retryModuleStatus, onRetryJob: retryPortfolioJob,
  }), [currentJourney, currentModule, currentStatus, journeys, live.revision, project, projectId, retryModuleStatus, retryPortfolioJob]);

  useEffect(() => {
    if (!project) return undefined;
    return register(chromeModel);
  }, [chromeModel, project, register]);

  if (status === 'loading') return <LoadingState label="프로젝트를 불러오고 있습니다" />;
  if (status === 'error' || !project) return <ErrorState title="프로젝트를 찾을 수 없습니다" description="프로젝트가 없거나 접근 권한이 없습니다." onRetry={retry} />;

  return <div className="pipeline-shell">
    <header className="pipeline-shell__header">
      <div className="pipeline-shell__project"><p>{project.industryCategory || '사업 분야 미입력'}</p><h1>{project.name}</h1><ProjectLocationRow projectId={projectId} currentJourney={currentJourney} /></div>
      <div className="pipeline-shell__actions"><span className="pipeline-status" data-tone={projectPresentation.tone}>{projectPresentation.label}</span><Link to={projectRoutes.settings(projectId)} state={{ backgroundLocation: location, returnTo: location.pathname }}><AppIcon name="settings" size={16} />프로젝트 설정</Link></div>
    </header>
    <main className="pipeline-shell__main">
      <JourneySubsteps journey={currentJourney} currentModule={currentModule} />
      {moduleState.status === 'error' && <section className="pipeline-module-status-error" role="alert"><div><strong>업무 상태를 불러오지 못했습니다.</strong><span>{getUserErrorMessage(moduleState.error)} 작업 화면은 계속 사용할 수 있습니다.</span></div><button type="button" onClick={moduleState.retry}>다시 시도</button></section>}
      <Outlet context={{ modules, journeys, moduleState, finalReportState, liveRevision: live.revision, projectEventTransport: live.transport, openWorkCenterJob: toolActions.openWorkCenterJob }} />
      <ScrollToTopButton />
    </main>
  </div>;
}

export default function ProjectLayout() {
  const { projectId } = useParams();
  return <ProjectProvider projectId={projectId}><ProjectLayoutContent /></ProjectProvider>;
}
