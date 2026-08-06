import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { Button } from '../../../shared/ui/index.js';
import { getAdminErrorMessage } from '../api/adminErrorResolver.js';
import AdminPagination from '../components/AdminPagination.jsx';
import AdminProjectDetailSheet from '../components/AdminProjectDetailSheet.jsx';
import AdminProjectsFilterBar from '../components/AdminProjectsFilterBar.jsx';
import AdminProjectsTable from '../components/AdminProjectsTable.jsx';
import useAdminProjects from '../hooks/useAdminProjects.js';
import '../admin.css';

const DEFAULTS = {
  keyword: '', owner: '', area: '', status: '', stage: '', industryCategory: '',
  createdFrom: '', createdTo: '', page: 0, size: 20, sort: 'updatedAt,desc',
};
const AREAS = new Set(['PLAN', 'REVIEW', 'VALIDATE', 'REPORT']);
const STATUSES = new Set(['DRAFT', 'ACTIVE', 'PAUSED', 'COMPLETED', 'ARCHIVED']);
const STAGES = new Set([
  'DOCUMENT', 'STRUCTURING', 'LEGAL_REVIEW', 'FEASIBILITY', 'FINANCIAL',
  'PERSONA_CONFIGURATION', 'PANEL_SURVEY', 'PANEL_DISCUSSION', 'REPORT', 'MARKETING', 'COMPLETED',
]);
const SIZES = new Set([10, 20, 50, 100]);
const SORTS = new Set([
  'updatedAt,desc', 'createdAt,desc', 'createdAt,asc',
  'title,asc', 'status,asc', 'stage,asc',
]);

function integer(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  return Number.isInteger(parsed) ? parsed : fallback;
}

function parseFilters(params) {
  const area = params.get('area') || '';
  const status = params.get('status') || '';
  const stage = params.get('stage') || '';
  const size = integer(params.get('size'), DEFAULTS.size);
  const sort = params.get('sort') || DEFAULTS.sort;
  return {
    keyword: params.get('keyword')?.trim() || '',
    owner: params.get('owner')?.trim() || '',
    area: AREAS.has(area) ? area : '',
    status: STATUSES.has(status) ? status : '',
    stage: STAGES.has(stage) ? stage : '',
    industryCategory: params.get('industryCategory')?.trim() || '',
    createdFrom: params.get('createdFrom') || '',
    createdTo: params.get('createdTo') || '',
    page: Math.max(0, integer(params.get('page'), DEFAULTS.page)),
    size: SIZES.has(size) ? size : DEFAULTS.size,
    sort: SORTS.has(sort) ? sort : DEFAULTS.sort,
  };
}

function safeReturnTo(location) {
  const returnTo = location.state?.returnTo;
  if (typeof returnTo === 'string' && returnTo.startsWith('/admin/projects')) return returnTo;
  const background = location.state?.backgroundLocation;
  if (background?.pathname?.startsWith('/admin/projects')) {
    return `${background.pathname}${background.search || ''}`;
  }
  return '/admin/projects';
}

export function AdminProjectDetailOverlay() {
  const { projectId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  return (
    <AdminProjectDetailSheet
      projectId={projectId}
      onRequestClose={() => navigate(safeReturnTo(location), { replace: true })}
    />
  );
}

export default function AdminProjectsPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { projectId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const searchKey = searchParams.toString();
  const filters = useMemo(() => parseFilters(new URLSearchParams(searchKey)), [searchKey]);
  const query = useMemo(() => ({ ...filters }), [filters]);
  const { data, loading, refreshing, error, refresh } = useAdminProjects(query);
  const [keywordInput, setKeywordInput] = useState(filters.keyword);
  const [ownerInput, setOwnerInput] = useState(filters.owner);
  const [composingField, setComposingField] = useState('');

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      setKeywordInput(filters.keyword);
      setOwnerInput(filters.owner);
    }, 0);
    return () => window.clearTimeout(timeout);
  }, [filters.keyword, filters.owner]);

  useEffect(() => {
    if (composingField) return undefined;
    const keyword = keywordInput.trim();
    const owner = ownerInput.trim();
    if (keyword === filters.keyword && owner === filters.owner) return undefined;
    const timeout = window.setTimeout(() => {
      const next = new URLSearchParams(searchKey);
      if (keyword) next.set('keyword', keyword);
      else next.delete('keyword');
      if (owner) next.set('owner', owner);
      else next.delete('owner');
      next.set('page', '0');
      setSearchParams(next, { replace: true });
    }, 300);
    return () => window.clearTimeout(timeout);
  }, [composingField, filters.keyword, filters.owner, keywordInput, ownerInput, searchKey, setSearchParams]);

  function updateFilter(name, value) {
    const next = new URLSearchParams(searchParams);
    if (value === '') next.delete(name);
    else next.set(name, String(value));
    if (name !== 'page') next.set('page', '0');
    setSearchParams(next);
  }

  function resetFilters() {
    setKeywordInput('');
    setOwnerInput('');
    setSearchParams({ page: '0', size: String(DEFAULTS.size), sort: DEFAULTS.sort });
  }

  const hasFilters = [
    filters.keyword, filters.owner, filters.area, filters.status, filters.stage,
    filters.industryCategory, filters.createdFrom, filters.createdTo,
  ].some(Boolean);
  const total = data?.totalElements ?? 0;
  return (
    <div className="admin-page">
      <header className="admin-page-header">
        <nav className="admin-breadcrumb" aria-label="현재 위치">Admin / Projects</nav>
        <h1>Projects</h1>
        <p>모든 활성 프로젝트의 소유자, Workflow 위치와 처리 상태를 읽기 전용으로 조회합니다.</p>
      </header>

      <section className="admin-panel" aria-busy={refreshing}>
        <AdminProjectsFilterBar
          values={filters}
          keyword={keywordInput}
          owner={ownerInput}
          onSearchChange={(field, value) => {
            if (field === 'keyword') setKeywordInput(value);
            else setOwnerInput(value);
          }}
          onCompositionStart={setComposingField}
          onCompositionEnd={(field, value) => {
            if (field === 'keyword') setKeywordInput(value);
            else setOwnerInput(value);
            setComposingField('');
          }}
          onFilterChange={updateFilter}
        />

        {refreshing && <div className="admin-query-progress" role="status">프로젝트 목록을 갱신하는 중입니다.</div>}
        {loading && <p className="admin-empty" role="status">프로젝트 목록을 불러오는 중입니다.</p>}
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
            {data.content.length > 0 && <AdminProjectsTable projects={data.content} location={location} />}
            {data.content.length === 0 && (
              <div className="admin-empty">
                <p>{hasFilters ? '현재 조건에 맞는 프로젝트가 없습니다.' : '아직 생성된 프로젝트가 없습니다.'}</p>
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
              itemLabel="프로젝트"
              unit="개"
            />
            <p className="visually-hidden" aria-live="polite">프로젝트 {total}개가 조회되었습니다.</p>
          </>
        )}
      </section>

      {projectId && (
        <AdminProjectDetailSheet
          projectId={projectId}
          onRequestClose={() => navigate(safeReturnTo(location), { replace: true })}
        />
      )}
    </div>
  );
}
