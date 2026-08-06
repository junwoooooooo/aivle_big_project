import { Link } from 'react-router-dom';

import {
  Alert,
  Badge,
  Card,
  ErrorState,
  LoadingState,
  PageHeader,
  StatusBadge,
} from '../../shared/ui/index.js';
import { formatProjectDate } from '../projects/model/projectViewModel.js';
import { useIntegratedReport } from './hooks/useIntegratedReport.js';
import ReportStatusCard from './components/ReportStatusCard.jsx';
import './report.css';

export default function ProjectDashboard({ project }) {
  const state = useIntegratedReport(project);

  if (state.status === 'loading') {
    return <LoadingState label="프로젝트 진행 상태를 불러오는 중입니다" />;
  }
  if (state.status === 'error') {
    return (
      <ErrorState
        title="프로젝트 진행 상태를 불러오지 못했습니다"
        description={state.error?.message}
        onRetry={state.retry}
      />
    );
  }

  const { report } = state;
  return (
    <div className="project-dashboard">
      <PageHeader
        eyebrow={project.industryCategory || '사업 분야 미입력'}
        title={project.name}
        description={project.description || '등록된 프로젝트 설명이 없습니다.'}
        actions={<Link className="primary-link" to="../report">통합 보고서 보기</Link>}
      />

      {report.anyMock && (
        <Alert title="Mock 분석 결과 포함" tone="warning">
          현재 요약에는 외부 AI를 호출하지 않은 Mock 결과가 포함되어 있습니다.
        </Alert>
      )}

      <section className="dashboard-summary" aria-labelledby="dashboard-summary-title">
        <h2 id="dashboard-summary-title">프로젝트 진행 요약</h2>
        <div className="dashboard-summary__grid">
          <Card>
            <span>프로젝트 상태</span>
            <StatusBadge status={project.status} />
            <strong>{project.stageLabel}</strong>
          </Card>
          <Card>
            <span>현재 결과</span>
            <Badge tone={report.reportStatus === 'COMPLETED' ? 'success' : 'warning'}>
              {report.reportStatusLabel}
            </Badge>
            <strong>{report.completedCount} / 4 영역</strong>
          </Card>
          <Card>
            <span>최근 수정</span>
            <strong>{formatProjectDate(project.updatedAt)}</strong>
            <small>서버 프로젝트 기준</small>
          </Card>
        </div>
      </section>

      <section aria-labelledby="analysis-progress-title">
        <h2 id="analysis-progress-title">분석별 상태</h2>
        <div className="report-progress-grid">
          {report.sections.map((section) => (
            <ReportStatusCard key={section.title} section={section} compact />
          ))}
        </div>
      </section>

      <Card className="next-action-card" aria-labelledby="next-action-title">
        <div>
          <p>다음 행동</p>
          <h2 id="next-action-title">{report.nextAction.title}</h2>
          <span>{report.nextAction.description}</span>
        </div>
        <Link className="primary-link" to={report.nextAction.route}>계속 진행</Link>
      </Card>

      {report.failedCount > 0 && (
        <Alert title="확인할 분석 오류가 있습니다" tone="danger">
          실패한 영역의 상세 화면에서 최신 Job 상태와 재시도 가능 여부를 확인하세요.
        </Alert>
      )}
    </div>
  );
}
