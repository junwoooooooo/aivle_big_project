import { useEffect, useMemo, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { ErrorState, LoadingState, ProjectWorkspace, scrollPageToTop } from '../../../shared/ui/index.js';
import { useProjectContext } from '../../projects/ProjectContext.jsx';
import { createLaunchReadinessApi } from '../api/launchReadinessApi.js';
import { FinanceReadinessReportDocument } from '../components/FinanceReadinessReportDocument.jsx';
import { IntegratedLaunchReadinessReportDocument } from '../components/IntegratedLaunchReadinessReportDocument.jsx';
import { LaunchReadinessReportDocument } from '../components/LaunchReadinessReportDocument.jsx';
import { printLaunchReadinessReport, reportModulesFromQuery } from '../model/reportDocumentPresentation.js';
import '../styles/launch-readiness.css';

const REPORT_TYPES = new Set(['technology', 'operations', 'finance', 'integrated']);

function hasCurrentReport(module, current) {
  if (current?.stale) return false;
  return module === 'finance' ? Boolean(current?.result) : Boolean(current?.analysis);
}

function latestCompletedAt(documents) {
  return documents.map(({ current }) => current?.completedAt).filter(Boolean)
    .sort((left, right) => new Date(right) - new Date(left))[0];
}

export function LaunchReadinessReportPageView({ reportType, project, documents, onPrint }) {
  const completedAt = latestCompletedAt(documents);
  return <ProjectWorkspace mode="document" className="launch-report-page">
    <nav className="launch-report-actions" aria-label="출시 준비 보고서 작업">
      <Link to={projectRoutes.launchReadiness(project.projectId)}>출시 준비로 돌아가기</Link>
      <button type="button" onClick={() => onPrint(completedAt)}>PDF로 저장</button>
    </nav>
    {reportType === 'integrated'
      ? <IntegratedLaunchReadinessReportDocument documents={documents} projectName={project.name} completedAt={completedAt} />
      : reportType === 'finance'
        ? <FinanceReadinessReportDocument current={documents[0].current} projectName={project.name} />
        : <LaunchReadinessReportDocument module={reportType} current={documents[0].current} projectName={project.name} />}
  </ProjectWorkspace>;
}

export default function LaunchReadinessReportPage() {
  const { projectId, reportType } = useParams();
  const [searchParams] = useSearchParams();
  const { project } = useProjectContext();
  const client = useApiClient();
  const api = useMemo(() => createLaunchReadinessApi(client), [client]);
  const modules = useMemo(() => reportModulesFromQuery(reportType, searchParams), [reportType, searchParams]);
  const moduleKey = modules.join(',');
  const requestKey = `${reportType}:${moduleKey}`;
  const selectionValid = REPORT_TYPES.has(reportType) && (reportType !== 'integrated' || modules.length >= 2);
  const [state, setState] = useState({ key: null, status: 'loading', documents: [], error: null });

  useEffect(() => scrollPageToTop({ smooth: false }), [reportType, moduleKey]);
  useEffect(() => {
    let cancelled = false;
    if (!selectionValid) return undefined;
    const requestedModules = moduleKey.split(',').filter(Boolean);
    Promise.all(requestedModules.map(async (module) => ({
      module,
      current: module === 'finance'
        ? await api.financeCurrent(projectId)
        : await api.professionalCurrent(projectId, module),
    }))).then((documents) => {
      if (cancelled) return;
      if (documents.some(({ module, current }) => !hasCurrentReport(module, current))) {
        setState({ key: requestKey, status: 'unavailable', documents: [], error: null });
        return;
      }
      setState({ key: requestKey, status: 'success', documents, error: null });
    }).catch((error) => { if (!cancelled) setState({ key: requestKey, status: 'error', documents: [], error }); });
    return () => { cancelled = true; };
  }, [api, moduleKey, projectId, reportType, requestKey, selectionValid]);

  if (!selectionValid) return <ErrorState title="보고서를 열 수 없습니다" description="보고서 선택과 현재 분석 상태를 확인한 뒤 다시 시도해 주세요." />;
  if (state.key !== requestKey || state.status === 'loading') return <LoadingState label="출시 준비 보고서를 불러오고 있습니다" />;
  if (state.status === 'unavailable') return <ErrorState title="현재 입력 기준 보고서가 준비되지 않았습니다" description="출시 준비 화면에서 최신 분석을 완료한 뒤 다시 열어 주세요." />;
  if (state.status === 'error') return <ErrorState title="보고서를 열 수 없습니다" description="보고서 선택과 현재 분석 상태를 확인한 뒤 다시 시도해 주세요." />;

  return <LaunchReadinessReportPageView reportType={reportType} project={project} documents={state.documents}
    onPrint={(completedAt) => printLaunchReadinessReport(project.name, reportType, completedAt)} />;
}
