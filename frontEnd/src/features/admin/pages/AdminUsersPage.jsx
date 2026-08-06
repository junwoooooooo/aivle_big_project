import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { Button } from '../../../shared/ui/index.js';
import { getAdminErrorMessage } from '../api/adminErrorResolver.js';
import AdminPagination from '../components/AdminPagination.jsx';
import AdminUserDetailSheet from '../components/AdminUserDetailSheet.jsx';
import AdminUsersFilterBar from '../components/AdminUsersFilterBar.jsx';
import AdminUsersTable from '../components/AdminUsersTable.jsx';
import useAdminUsers from '../hooks/useAdminUsers.js';
import '../admin.css';

const DEFAULTS = {
  keyword: '',
  role: '',
  status: '',
  page: 0,
  size: 20,
  sort: 'createdAt,desc',
};
const ROLES = new Set(['USER', 'ADMIN']);
const STATUSES = new Set(['ACTIVE', 'LOCKED', 'DISABLED']);
const SIZES = new Set([10, 20, 50, 100]);
const SORTS = new Set([
  'createdAt,desc',
  'createdAt,asc',
  'lastLoginAt,desc',
  'username,asc',
  'displayName,asc',
]);

function integer(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  return Number.isInteger(parsed) ? parsed : fallback;
}

function parseFilters(searchParams) {
  const role = searchParams.get('role') || '';
  const status = searchParams.get('status') || '';
  const size = integer(searchParams.get('size'), DEFAULTS.size);
  const sort = searchParams.get('sort') || DEFAULTS.sort;
  return {
    keyword: searchParams.get('keyword')?.trim() || '',
    role: ROLES.has(role) ? role : '',
    status: STATUSES.has(status) ? status : '',
    page: Math.max(0, integer(searchParams.get('page'), DEFAULTS.page)),
    size: SIZES.has(size) ? size : DEFAULTS.size,
    sort: SORTS.has(sort) ? sort : DEFAULTS.sort,
  };
}

function safeReturnTo(location) {
  const returnTo = location.state?.returnTo;
  if (typeof returnTo === 'string' && returnTo.startsWith('/admin/users')) return returnTo;
  const background = location.state?.backgroundLocation;
  if (background?.pathname?.startsWith('/admin/users')) {
    return `${background.pathname}${background.search || ''}`;
  }
  return '/admin/users';
}

export function AdminUserDetailOverlay({ onChanged }) {
  const { userId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  return (
    <AdminUserDetailSheet
      userId={userId}
      onChanged={onChanged || ((message) => {
        window.dispatchEvent(new CustomEvent('admin-users-changed', { detail: { message } }));
      })}
      onRequestClose={() => navigate(safeReturnTo(location), { replace: true })}
    />
  );
}

export default function AdminUsersPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { userId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const searchKey = searchParams.toString();
  const filters = useMemo(() => parseFilters(new URLSearchParams(searchKey)), [searchKey]);
  const query = useMemo(() => ({ ...filters }), [filters]);
  const { data, loading, refreshing, error, refresh } = useAdminUsers(query);
  const [keywordInput, setKeywordInput] = useState(filters.keyword);
  const [isComposing, setIsComposing] = useState(false);
  const [notice, setNotice] = useState('');
  useEffect(() => {
    const timeout = window.setTimeout(() => setKeywordInput(filters.keyword), 0);
    return () => window.clearTimeout(timeout);
  }, [filters.keyword]);

  useEffect(() => {
    function handleUsersChanged(event) {
      setNotice(event.detail?.message || '사용자 정보가 변경되었습니다.');
      refresh();
    }
    window.addEventListener('admin-users-changed', handleUsersChanged);
    return () => window.removeEventListener('admin-users-changed', handleUsersChanged);
  }, [refresh]);

  useEffect(() => {
    if (isComposing || keywordInput.trim() === filters.keyword) return undefined;
    const timeout = window.setTimeout(() => {
      const next = new URLSearchParams(searchKey);
      const keyword = keywordInput.trim();
      if (keyword) next.set('keyword', keyword);
      else next.delete('keyword');
      next.set('page', '0');
      setSearchParams(next, { replace: true });
    }, 300);
    return () => window.clearTimeout(timeout);
  }, [filters.keyword, isComposing, keywordInput, searchKey, setSearchParams]);

  function updateFilter(name, value) {
    const next = new URLSearchParams(searchParams);
    if (value === '') next.delete(name);
    else next.set(name, String(value));
    if (name !== 'page') next.set('page', '0');
    setSearchParams(next);
  }

  function resetFilters() {
    setKeywordInput('');
    setSearchParams({
      page: String(DEFAULTS.page),
      size: String(DEFAULTS.size),
      sort: DEFAULTS.sort,
    });
  }

  function handleChanged(message) {
    setNotice(message);
    refresh();
  }

  const hasFilters = Boolean(filters.keyword || filters.role || filters.status);
  const total = data?.totalElements ?? 0;
  return (
    <div className="admin-page">
      <header className="admin-page-header">
        <nav className="admin-breadcrumb" aria-label="현재 위치">Admin / Users</nav>
        <h1>Users</h1>
        <p>사용자 계정, 역할, 상태와 세션을 서버 정책에 따라 관리합니다.</p>
      </header>

      {notice && <p className="admin-success" role="status">{notice}</p>}
      <section className="admin-panel" aria-busy={refreshing}>
        <AdminUsersFilterBar
          keyword={keywordInput}
          role={filters.role}
          status={filters.status}
          size={filters.size}
          sort={filters.sort}
          onKeywordChange={setKeywordInput}
          onCompositionStart={() => setIsComposing(true)}
          onCompositionEnd={(value) => {
            setKeywordInput(value);
            setIsComposing(false);
          }}
          onFilterChange={updateFilter}
        />

        {refreshing && <div className="admin-query-progress" role="status">사용자 목록을 갱신하는 중입니다.</div>}
        {loading && <p className="admin-empty" role="status">사용자 목록을 불러오는 중입니다.</p>}
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
            {data.content.length > 0 && <AdminUsersTable users={data.content} location={location} />}
            {data.content.length === 0 && (
              <div className="admin-empty">
                <p>{hasFilters ? '현재 조건에 맞는 사용자가 없습니다.' : '아직 등록된 사용자가 없습니다.'}</p>
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
            />
            <p className="visually-hidden" aria-live="polite">사용자 {total}명이 조회되었습니다.</p>
          </>
        )}
      </section>

      {userId && (
        <AdminUserDetailSheet
          userId={userId}
          onChanged={handleChanged}
          onRequestClose={() => navigate(safeReturnTo(location), { replace: true })}
        />
      )}
    </div>
  );
}
