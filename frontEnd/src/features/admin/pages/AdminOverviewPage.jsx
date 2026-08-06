import { useCallback, useMemo } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createAdminApi } from '../api/adminApi.js';
import AdminAvailabilityNotice from '../components/AdminAvailabilityNotice.jsx';
import AdminErrorState from '../components/AdminErrorState.jsx';
import AdminMetricCard from '../components/AdminMetricCard.jsx';
import AdminPageHeader from '../components/AdminPageHeader.jsx';
import useAdminResource from '../hooks/useAdminResource.js';
import '../admin.css';

export default function AdminOverviewPage() {
  const client = useApiClient();
  const api = useMemo(() => createAdminApi(client), [client]);
  const request = useCallback((signal) => api.overview({ signal }), [api]);
  const { data, loading, refreshing, error, refresh } = useAdminResource(request);

  return (
    <div className="admin-page">
      <AdminPageHeader
        title="Overview"
        description="Soft-delete 데이터를 제외한 현재 운영 지표입니다."
      />
      {loading && <section className="admin-panel" aria-busy="true">운영 지표를 불러오는 중입니다.</section>}
      {error && !data && <AdminErrorState error={error} onRetry={refresh} />}
      {refreshing && <p className="admin-query-progress" role="status">운영 지표를 갱신하고 있습니다.</p>}
      {data && (
        <>
          <section className="admin-overview-section" aria-labelledby="overview-users">
            <h2 id="overview-users">사용자</h2>
            <div className="admin-metrics">
              <AdminMetricCard label="전체 사용자" value={data.users.total} />
              <AdminMetricCard label="활성 사용자" value={data.users.active} />
              <AdminMetricCard label="잠긴 사용자" value={data.users.locked} />
              <AdminMetricCard label="비활성 사용자" value={data.users.disabled} />
              <AdminMetricCard label="관리자 수" value={data.users.admins} />
            </div>
          </section>
          <section className="admin-overview-section" aria-labelledby="overview-projects">
            <h2 id="overview-projects">프로젝트</h2>
            <div className="admin-metrics">
              <AdminMetricCard label="전체 프로젝트" value={data.projects.total} />
              <AdminMetricCard label="진행 중" value={data.projects.inProgress} />
              <AdminMetricCard label="일시 정지" value={data.projects.paused} />
              <AdminMetricCard label="완료" value={data.projects.completed} />
              <AdminMetricCard label="최근 7일 생성" value={data.projects.createdLast7Days} />
            </div>
          </section>
          <AdminAvailabilityNotice title="AI 작업" availability={data.jobs} />
        </>
      )}
    </div>
  );
}
