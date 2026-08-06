import { useCallback, useMemo } from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createAdminApi } from '../api/adminApi.js';
import AdminErrorState from '../components/AdminErrorState.jsx';
import AdminMetricCard from '../components/AdminMetricCard.jsx';
import AdminPageHeader from '../components/AdminPageHeader.jsx';
import AdminStatusBadge from '../components/AdminStatusBadge.jsx';
import useAdminResource from '../hooks/useAdminResource.js';
import { Button } from '../../../shared/ui/index.js';
import '../admin.css';

function date(value) {
  return value ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '-';
}

export default function AdminJobsPage() {
  const client = useApiClient();
  const api = useMemo(() => createAdminApi(client), [client]);
  const request = useCallback((signal) => api.jobs({ signal }), [api]);
  const { data, loading, refreshing, error, refresh } = useAdminResource(request);
  return <div className="admin-page">
    <AdminPageHeader title="AI Task Runs" description="최근 AI 실행의 상태, 유형과 오류 코드를 읽기 전용으로 확인합니다. Provider 자격증명과 입력·응답 원문은 표시하지 않습니다." />
    {loading && <section className="admin-panel admin-task-skeleton" aria-busy="true"><span /><span /><span /></section>}
    {error && !data && <AdminErrorState error={error} onRetry={refresh} />}
    {refreshing && <p className="admin-query-progress" role="status">TaskRun 목록을 갱신하고 있습니다.</p>}
    {data && <>
      <section className="admin-panel admin-ai-status"><div><span>AI 실행 설정</span><strong>{data.configurationStatus}</strong></div><div><span>내부 서비스 연결</span><strong>{data.availabilityStatus}</strong></div><Button type="button" variant="outline" size="small" onClick={refresh}>새로고침</Button></section>
      <div className="admin-metrics"><AdminMetricCard label="대기" value={data.pending} /><AdminMetricCard label="실행 중" value={data.running} /><AdminMetricCard label="실패/시간 초과" value={data.failed} /></div>
      <section className="admin-panel"><div className="admin-table-wrap"><table className="admin-task-table"><thead><tr><th>최근 업데이트</th><th>프로젝트</th><th>Task Type</th><th>상태</th><th>시도</th><th>최근 Error</th></tr></thead><tbody>{data.items.map((item) => <tr key={item.id}><td>{date(item.updatedAt)}</td><td>{item.projectName}<small>#{item.projectId}</small></td><td><code>{item.taskType}</code></td><td><AdminStatusBadge value={item.state} /></td><td>{item.attemptCount}{item.retryable ? ' · 재시도 가능' : ''}</td><td><code>{item.lastError || '-'}</code></td></tr>)}</tbody></table></div>{!data.items.length && <p className="admin-empty">아직 기록된 AI TaskRun이 없습니다.</p>}</section>
    </>}
  </div>;
}
