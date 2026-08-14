import { useEffect } from 'react';
import { Link, useOutletContext, useParams } from 'react-router-dom';

import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import { useProjectContext } from '../../projects/ProjectContext.jsx';
import { ErrorState, LoadingState, ProjectWorkspace, scrollPageToTop } from '../../../shared/ui/index.js';
import LegalRegulatoryReportDocument from '../components/LegalRegulatoryReportDocument.jsx';
import { useConceptPortfolio } from '../hooks/useConceptPortfolio.js';
import { printLegalReport } from '../legalReportPresentation.js';
import '../styles/legal-regulatory-report.css';

export default function LegalRegulatoryReportPage() {
  const { projectId } = useParams();
  const { liveRevision = 0 } = useOutletContext() ?? {};
  const { project } = useProjectContext();
  const portfolio = useConceptPortfolio(projectId, liveRevision);

  useEffect(() => scrollPageToTop({ smooth: false }), []);

  if (portfolio.loading) return <LoadingState label="법률·규제 보고서를 불러오고 있습니다" />;
  if (!portfolio.report) return <ErrorState title="법률·규제 보고서가 아직 준비되지 않았습니다" description="사업 기준값을 확정하고 현재 값으로 진행한 뒤 다시 열어 주세요." onRetry={portfolio.refresh} />;

  const body = portfolio.report.report ?? {};
  const conceptName = portfolio.selection?.conceptName ?? body.selectedConcept?.conceptName ?? '선택 사업안';
  return <ProjectWorkspace mode="document" className="legal-report-print-page">
    <nav className="legal-report-print-actions" aria-label="법률·규제 보고서 작업"><Link to={projectRoutes.concepts(projectId)}>사업안 결정 화면으로 돌아가기</Link><button type="button" onClick={() => printLegalReport(conceptName, portfolio.report.generatedAt)}>PDF로 저장</button></nav>
    <LegalRegulatoryReportDocument project={project} selection={portfolio.selection} report={portfolio.report} />
  </ProjectWorkspace>;
}
