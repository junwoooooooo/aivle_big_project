import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Card, EmptyState, ErrorState, LoadingState, PageHeader, StatusBadge } from '../../../shared/ui/index.js';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { useServicePolicy } from '../../service-policy/useServicePolicy.js';
import { projectRoutes } from '../../projects/routing/projectRoutes.js';
import useFinancialAnalyses from '../hooks/useFinancialAnalyses.js';
import '../financial.css';

export default function FinancialAnalysisListPage() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const { items, source, loading, error, refresh } = useFinancialAnalyses(projectId);
  const { policy } = useServicePolicy();
  const blocked = policy.maintenanceMode;
  if (loading && !items.length) return <LoadingState label="재무 분석을 불러오는 중입니다." />;
  if (error && !items.length) return <ErrorState description={getUserErrorMessage(error)} onRetry={refresh} />;
  return <div className="financial-page">
    <PageHeader eyebrow="Review" title="재무·수익성 분석" description="확인한 가격·판매량·비용 가정을 바탕으로 매출, 손익, 현금흐름을 계산합니다." actions={<Button onClick={() => navigate(projectRoutes.financialNew(projectId))} disabled={blocked || !source?.ready}>새 재무 분석</Button>} />
    {blocked && <Alert tone="warning" title="점검 중에는 새 분석을 만들 수 없습니다.">기존 완료 결과는 계속 조회할 수 있습니다.</Alert>}
    {!source?.ready && <Alert tone="warning" title="완료된 사업 타당성 분석이 필요합니다.">사업 타당성 분석을 완료한 뒤 재무 가정을 입력할 수 있습니다.</Alert>}
    <Alert tone="info" title="계산 기준">이 결과는 확인한 가정을 바탕으로 계산한 예상값이며 실제 성과를 보장하지 않습니다.</Alert>
    {items.length === 0 ? <EmptyState title="아직 재무 분석이 없습니다." description="사업 타당성 결과를 바탕으로 첫 분석을 만들어 보세요." /> : <div className="financial-list">{items.map((item) => <Card key={item.id}><div className="financial-list__header"><div><StatusBadge status={item.status} /><h2>{item.title}</h2><p>{item.analysisPeriodMonths}개월 · 버전 {item.versionNumber}</p></div><Link className="button" to={projectRoutes.financialDetail(projectId, item.id)}>열기</Link></div>{item.completedAt && <p>완료 {new Date(item.completedAt).toLocaleString('ko-KR')}</p>}</Card>)}</div>}
  </div>;
}
