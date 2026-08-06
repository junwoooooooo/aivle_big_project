import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { Button } from '../../../shared/ui/index.js';
import { getAdminErrorMessage } from '../api/adminErrorResolver.js';
import AdminAuditDetailSheet from '../components/AdminAuditDetailSheet.jsx';
import AdminAuditFilterBar from '../components/AdminAuditFilterBar.jsx';
import AdminAuditTable from '../components/AdminAuditTable.jsx';
import AdminPagination from '../components/AdminPagination.jsx';
import useAdminAudit from '../hooks/useAdminAudit.js';
import { ADMIN_AUDIT_ACTIONS } from '../model/auditLabels.js';
import '../admin.css';

const DEFAULTS = {
  actor: '', action: '', result: '', targetType: '', requestId: '',
  occurredFrom: '', occurredTo: '', page: 0, size: 50, sort: 'occurredAt,desc',
};
const ACTIONS = new Set(ADMIN_AUDIT_ACTIONS);
const RESULTS = new Set(['SUCCESS', 'FAILED']);
const TARGET_TYPES = new Set(['USER', 'PROJECT', 'SERVICE_SETTING', 'ADMIN_AUTH', 'OTHER']);
const SIZES = new Set([20, 50, 100]);
const SORTS = new Set(['occurredAt,desc', 'occurredAt,asc', 'actorUsername,asc', 'action,asc', 'result,asc']);

function integer(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  return Number.isInteger(parsed) ? parsed : fallback;
}

function parseFilters(params) {
  const action = params.get('action') || '';
  const result = params.get('result') || '';
  const targetType = params.get('targetType') || '';
  const size = integer(params.get('size'), DEFAULTS.size);
  const sort = params.get('sort') || DEFAULTS.sort;
  return {
    actor: params.get('actor')?.trim() || '',
    action: ACTIONS.has(action) ? action : '',
    result: RESULTS.has(result) ? result : '',
    targetType: TARGET_TYPES.has(targetType) ? targetType : '',
    requestId: params.get('requestId')?.trim() || '',
    occurredFrom: params.get('occurredFrom') || '',
    occurredTo: params.get('occurredTo') || '',
    page: Math.max(0, integer(params.get('page'), DEFAULTS.page)),
    size: SIZES.has(size) ? size : DEFAULTS.size,
    sort: SORTS.has(sort) ? sort : DEFAULTS.sort,
  };
}

function safeReturnTo(location) {
  const returnTo = location.state?.returnTo;
  if (typeof returnTo === 'string' && returnTo.startsWith('/admin/audit')) return returnTo;
  const background = location.state?.backgroundLocation;
  if (background?.pathname?.startsWith('/admin/audit')) return `${background.pathname}${background.search || ''}`;
  return '/admin/audit';
}

export function AdminAuditDetailOverlay() {
  const { auditId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  return <AdminAuditDetailSheet auditId={auditId} onRequestClose={() => navigate(safeReturnTo(location), { replace: true })} />;
}

export default function AdminAuditPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { auditId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const searchKey = searchParams.toString();
  const filters = useMemo(() => parseFilters(new URLSearchParams(searchKey)), [searchKey]);
  const query = useMemo(() => ({ ...filters }), [filters]);
  const { data, loading, refreshing, error, refresh } = useAdminAudit(query);
  const [actorInput, setActorInput] = useState(filters.actor);
  const [requestIdInput, setRequestIdInput] = useState(filters.requestId);
  const [composingField, setComposingField] = useState('');

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      setActorInput(filters.actor);
      setRequestIdInput(filters.requestId);
    }, 0);
    return () => window.clearTimeout(timeout);
  }, [filters.actor, filters.requestId]);

  useEffect(() => {
    if (composingField) return undefined;
    const actor = actorInput.trim();
    const requestId = requestIdInput.trim();
    if (actor === filters.actor && requestId === filters.requestId) return undefined;
    const timeout = window.setTimeout(() => {
      const next = new URLSearchParams(searchKey);
      if (actor) next.set('actor', actor);
      else next.delete('actor');
      if (requestId) next.set('requestId', requestId);
      else next.delete('requestId');
      next.set('page', '0');
      setSearchParams(next, { replace: true });
    }, 300);
    return () => window.clearTimeout(timeout);
  }, [actorInput, composingField, filters.actor, filters.requestId, requestIdInput, searchKey, setSearchParams]);

  function updateFilter(name, value) {
    const next = new URLSearchParams(searchParams);
    if (value === '') next.delete(name);
    else next.set(name, String(value));
    if (name !== 'page') next.set('page', '0');
    setSearchParams(next);
  }

  function resetFilters() {
    setActorInput('');
    setRequestIdInput('');
    setSearchParams({ page: '0', size: String(DEFAULTS.size), sort: DEFAULTS.sort });
  }

  const hasFilters = [
    filters.actor, filters.action, filters.result, filters.targetType,
    filters.requestId, filters.occurredFrom, filters.occurredTo,
  ].some(Boolean);
  const total = data?.totalElements ?? 0;
  return (
    <div className="admin-page">
      <header className="admin-page-header">
        <nav className="admin-breadcrumb" aria-label="현재 위치">Admin / Audit</nav>
        <h1>Audit</h1>
        <p>관리자 작업의 결과, 변경 내용과 Request ID를 수정 불가능한 기록으로 조회합니다.</p>
      </header>

      <section className="admin-panel" aria-busy={refreshing}>
        <AdminAuditFilterBar
          values={filters}
          actor={actorInput}
          requestId={requestIdInput}
          onSearchChange={(field, value) => {
            if (field === 'actor') setActorInput(value);
            else setRequestIdInput(value);
          }}
          onCompositionStart={setComposingField}
          onCompositionEnd={(field, value) => {
            if (field === 'actor') setActorInput(value);
            else setRequestIdInput(value);
            setComposingField('');
          }}
          onFilterChange={updateFilter}
        />
        {refreshing && <div className="admin-query-progress" role="status">감사 로그를 갱신하는 중입니다.</div>}
        {loading && <p className="admin-empty" role="status">감사 로그를 불러오는 중입니다.</p>}
        {!loading && error && (
          <div className="admin-error-state" role="alert">
            <p>{getAdminErrorMessage(error)}</p>
            {error?.status === 403
              ? <Button onClick={() => navigate('/app')}>사용자 워크스페이스로 이동</Button>
              : <Button variant="outline" onClick={refresh}>다시 시도</Button>}
          </div>
        )}
        {!loading && data && (
          <>
            {data.content.length > 0 && <AdminAuditTable events={data.content} location={location} />}
            {data.content.length === 0 && (
              <div className="admin-empty">
                <p>{hasFilters ? '현재 조건에 맞는 감사 기록이 없습니다.' : '아직 감사 기록이 없습니다.'}</p>
                {hasFilters && <Button size="small" variant="outline" onClick={resetFilters}>필터 초기화</Button>}
              </div>
            )}
            <AdminPagination
              page={data.number}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              first={data.first}
              last={data.last}
              onChange={(page) => updateFilter('page', page)}
              itemLabel="감사 기록"
              unit="건"
            />
            <p className="visually-hidden" aria-live="polite">감사 기록 {total}건이 조회되었습니다.</p>
          </>
        )}
      </section>

      {auditId && <AdminAuditDetailSheet auditId={auditId} onRequestClose={() => navigate(safeReturnTo(location), { replace: true })} />}
    </div>
  );
}
