import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import {
  Alert,
  Button,
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  SideSheet,
  Textarea,
  TextInput,
} from '../../shared/ui/index.js';
import { createProjectApi } from './api/projectApi.js';
import { useProjectContext } from './ProjectContext.jsx';
import { useProjects } from './hooks/useProjects.js';
import ProjectRow from './components/ProjectRow.jsx';
import ProjectDeleteDialog from './components/ProjectDeleteDialog.jsx';
import { PROJECT_AREA_DEFINITIONS, PROJECT_STATUS_VIEW } from './model/projectWorkflowModel.js';
import { appRoutes, projectRoutes } from './routing/projectRoutes.js';
import { getProjectNameError } from './projectNameError.js';
import { useServicePolicy } from '../service-policy/useServicePolicy.js';
import { getWriteRestriction, isServicePolicyError } from '../service-policy/servicePolicyRestrictions.js';
import './projects.css';

function filterMatches(project, filter) {
  if (filter === 'all') return true;
  return project.status === filter;
}

function PolicyNotice({ restriction, onRetry }) {
  if (!restriction.blocked) return null;
  return (
    <Alert tone={restriction.code === 'POLICY_UNAVAILABLE' ? 'danger' : 'warning'} title="변경 작업을 사용할 수 없습니다">
      <p>{restriction.message}</p>
      {restriction.code === 'POLICY_UNAVAILABLE' && <Button type="button" variant="outline" size="small" onClick={onRetry}>다시 시도</Button>}
    </Alert>
  );
}

function PolicyLink({ restriction, children, ...props }) {
  return (
    <Link
      {...props}
      aria-disabled={restriction.blocked}
      title={restriction.blocked ? restriction.message : props.title}
      className={`${props.className ?? ''} ${restriction.blocked ? 'is-policy-disabled' : ''}`.trim()}
      onClick={(event) => {
        if (restriction.blocked) event.preventDefault();
        else props.onClick?.(event);
      }}
    >
      {children}
    </Link>
  );
}

export function ProjectStatusHelpRail() {
  const [open, setOpen] = useState(false);
  const railRef = useRef(null);
  useEffect(() => {
    if (!open) return undefined;
    const closeOnOutside = (event) => { if (!railRef.current?.contains(event.target)) setOpen(false); };
    const closeOnEscape = (event) => { if (event.key === 'Escape') setOpen(false); };
    window.addEventListener('pointerdown', closeOnOutside);
    window.addEventListener('keydown', closeOnEscape);
    return () => { window.removeEventListener('pointerdown', closeOnOutside); window.removeEventListener('keydown', closeOnEscape); };
  }, [open]);
  return <aside ref={railRef} className={`project-status-help ${open ? 'is-open' : ''}`} aria-label="프로젝트 상태 안내">
    <button type="button" className="project-status-help__trigger" aria-expanded={open} aria-controls="project-status-help-content" onClick={() => setOpen((value) => !value)} onKeyDown={(event) => { if (event.key === 'Escape') setOpen(false); }}><span aria-hidden="true">?</span><span>상태 안내</span></button>
    {open && <div id="project-status-help-content" className="project-status-help__content"><div><h2>Area</h2><p>프로젝트가 위치한 사업 검증 영역입니다.</p><dl>{PROJECT_AREA_DEFINITIONS.map((area) => <div key={area.id}><dt>{area.label}</dt><dd>{({ OVERVIEW: '전체 현황', PLAN: '사업계획서 업로드와 구조화', REVIEW: '법률·규제와 사업성 분석', VALIDATE: 'AI 패널과 시장 반응 검증', REPORT: '통합 결과' })[area.id]}</dd></div>)}</dl></div><div><h2>Status</h2><p>프로젝트 전체 처리 상태입니다.</p><dl>{Object.entries(PROJECT_STATUS_VIEW).map(([status, view]) => <div key={status}><dt>{view.label}</dt><dd>{({ DRAFT: '준비 중인 프로젝트', ACTIVE: '작업을 진행 중인 프로젝트', PAUSED: '일시 중지된 프로젝트', COMPLETED: '검증이 완료된 프로젝트', ARCHIVED: '보관된 프로젝트' })[status]}</dd></div>)}</dl></div></div>}
  </aside>;
}

export function ProjectListPage() {
  const location = useLocation();
  const servicePolicy = useServicePolicy();
  const restriction = getWriteRestriction(servicePolicy);
  const { status, projects, retry } = useProjects();
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('all');
  const [sort, setSort] = useState('updated');
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [menuOpenProjectId, setMenuOpenProjectId] = useState(null);
  const visible = useMemo(() => projects
    .filter((project) => filterMatches(project, filter)
      && `${project.name} ${project.industryCategory}`.toLowerCase().includes(query.toLowerCase()))
    .sort((a, b) => {
      if (sort === 'name') return a.name.localeCompare(b.name, 'ko');
      if (sort === 'created') return new Date(b.createdAt) - new Date(a.createdAt);
      return new Date(b.updatedAt) - new Date(a.updatedAt);
    }), [projects, query, filter, sort]);

  if (status === 'loading') return <LoadingState label="프로젝트를 불러오고 있습니다" />;
  if (status === 'error') {
    return <ErrorState title="프로젝트를 불러오지 못했습니다" description="네트워크 연결을 확인한 뒤 다시 시도해 주세요." onRetry={retry} />;
  }

  return (
    <div className="project-hub">
      <PageHeader
        eyebrow="내 워크스페이스"
        title="프로젝트"
        description="아이디어부터 최종 보고서까지 현재 단계와 다음 작업을 확인하세요."
        actions={<PolicyLink restriction={restriction} className="primary-link" to={appRoutes.newProject} state={{ backgroundLocation: location, returnTo: `${location.pathname}${location.search}` }}>새 프로젝트</PolicyLink>}
      />
      <div className="project-hub__body"><div className="project-hub__content">{!projects.length ? (
        <EmptyState
          title="아직 프로젝트가 없습니다"
          description="첫 사업 검증 프로젝트를 만들어 시작하세요."
          action={<PolicyLink restriction={restriction} className="primary-link" to={appRoutes.newProject}>프로젝트 만들기</PolicyLink>}
        />
      ) : (
        <>
          <div className="project-toolbar">
            <label>
              <span className="visually-hidden">프로젝트 검색</span>
              <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="프로젝트 또는 사업 분야 검색" />
            </label>
            <div role="group" aria-label="프로젝트 상태 필터">
              {[['all', '전체'], ['ACTIVE', '진행 중'], ['DRAFT', '초안'], ['PAUSED', '일시 중지'], ['COMPLETED', '완료']].map(([value, label]) => (
                <button key={value} type="button" className={filter === value ? 'is-active' : ''} onClick={() => setFilter(value)}>{label}</button>
              ))}
            </div>
            <select aria-label="프로젝트 정렬" value={sort} onChange={(event) => setSort(event.target.value)}>
              <option value="updated">최근 수정순</option>
              <option value="created">최근 생성순</option>
              <option value="name">이름순</option>
            </select>
          </div>
          <div className="project-row-list project-card-grid" role="list" aria-label="프로젝트 목록">
            {visible.map((project) => <ProjectRow key={project.projectId} project={project} menuOpen={menuOpenProjectId === project.projectId} onMenuOpenChange={(open) => setMenuOpenProjectId(open ? project.projectId : null)} onDelete={() => setDeleteTarget(project)} />)}
          </div>
          {!visible.length && <p className="project-search-empty">조건에 맞는 프로젝트가 없습니다.</p>}
        </>
      )}</div></div>
      <ProjectDeleteDialog project={deleteTarget} open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)} onDeleted={async () => { setDeleteTarget(null); await retry(); }} />
    </div>
  );
}

export function ProjectCreatePage() {
  const client = useApiClient();
  const navigate = useNavigate();
  const location = useLocation();
  const errorRef = useRef(null);
  const titleInputRef = useRef(null);
  const [values, setValues] = useState({ title: '', description: '', industryCategory: '' });
  const [errors, setErrors] = useState({});
  const [globalError, setGlobalError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [closing, setClosing] = useState(false);
  const servicePolicy = useServicePolicy();
  const restriction = getWriteRestriction(servicePolicy);
  const pendingRouteRef = useRef(null);
  const closeTimerRef = useRef(null);

  const finishClose = () => {
    window.clearTimeout(closeTimerRef.current);
    navigate(pendingRouteRef.current || location.state?.returnTo || appRoutes.projects, { replace: Boolean(pendingRouteRef.current || location.state?.returnTo), state: null });
  };
  const requestClose = (nextRoute) => {
    if (closing || (submitting && !nextRoute)) return;
    pendingRouteRef.current = nextRoute;
    setClosing(true);
    closeTimerRef.current = window.setTimeout(finishClose, 360);
  };

  const update = (field) => (event) => {
    setValues((current) => ({ ...current, [field]: event.target.value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting || restriction.blocked) return;
    if (!values.title.trim()) {
      setErrors({ title: '프로젝트 이름을 입력해 주세요.' });
      return;
    }
    setSubmitting(true);
    setGlobalError('');
    try {
      const nextProject = await createProjectApi(client).create({
        title: values.title.trim(),
        description: values.description.trim() || null,
        industryCategory: values.industryCategory.trim() || null,
      });
      requestClose(projectRoutes.base(nextProject.id));
    } catch (error) {
      if (isServicePolicyError(error)) {
        void servicePolicy.refresh().catch(() => undefined);
      }
      const titleError = getProjectNameError(error);
      if (titleError) {
        setErrors({ title: titleError });
        requestAnimationFrame(() => titleInputRef.current?.focus());
        return;
      }
      setErrors(Object.fromEntries((error.fieldErrors ?? []).map((item) => [item.field, item.message])));
      setGlobalError(getUserErrorMessage(error));
      requestAnimationFrame(() => errorRef.current?.focus());
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      {!location.state?.backgroundLocation && <ProjectListPage />}
      <SideSheet open title="New Project" label="새 프로젝트" phase={closing ? 'exiting' : 'entered'} onExited={finishClose} onClose={() => requestClose()}>
    <div className="project-create project-create--sheet">
      <PageHeader eyebrow="새 프로젝트" title="검증할 사업 아이디어를 만드세요" description="지금은 최소 정보만 필요합니다. 세부 자료와 분석 실행은 프로젝트 안에서 직접 시작합니다." />
      {globalError && <div ref={errorRef} tabIndex="-1"><Alert tone="danger" title="프로젝트를 만들지 못했습니다">{globalError}</Alert></div>}
      <PolicyNotice restriction={restriction} onRetry={() => void servicePolicy.refresh().catch(() => undefined)} />
      <form className="project-form" onSubmit={handleSubmit} noValidate>
        <TextInput ref={titleInputRef} id="project-title" label="프로젝트 이름" value={values.title} error={errors.title} maxLength="150" onChange={update('title')} disabled={restriction.blocked} required />
        <TextInput id="project-category" label="사업 분야" description="선택 입력입니다." value={values.industryCategory} error={errors.industryCategory} maxLength="100" onChange={update('industryCategory')} disabled={restriction.blocked} />
        <Textarea id="project-description" label="간단한 설명" description="선택 입력입니다." value={values.description} error={errors.description} maxLength="10000" onChange={update('description')} disabled={restriction.blocked} />
        <div className="project-form__actions">
          <Button type="submit" loading={submitting} disabled={submitting || closing || restriction.blocked}>프로젝트 만들기</Button>
          <Button type="button" variant="outline" disabled={submitting || closing} onClick={() => requestClose()}>취소</Button>
        </div>
      </form>
    </div>
      </SideSheet>
    </>
  );
}

export function ProjectBriefInputPage() {
  const client = useApiClient();
  const navigate = useNavigate();
  const { project } = useProjectContext();
  const [values, setValues] = useState({
    title: project.name,
    industryCategory: project.industryCategory || '',
    description: project.description || '',
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const servicePolicy = useServicePolicy();
  const restriction = getWriteRestriction(servicePolicy);

  const update = (field) => (event) => setValues((current) => ({ ...current, [field]: event.target.value }));

  async function handleSubmit(event) {
    event.preventDefault();
    if (saving || restriction.blocked || !values.title.trim()) return;
    setSaving(true);
    setError('');
    try {
      await createProjectApi(client).update(project.projectId, {
        title: values.title.trim(),
        industryCategory: values.industryCategory.trim() || null,
        description: values.description.trim() || null,
      });
      navigate(`/app/projects/${project.projectId}`);
    } catch (requestError) {
      if (isServicePolicyError(requestError)) {
        void servicePolicy.refresh().catch(() => undefined);
      }
      setError(getUserErrorMessage(requestError));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="project-create project-brief-input">
      <PageHeader
        eyebrow="사업계획 입력"
        title="사업 개요를 직접 작성하세요"
        description="현재 단계에서는 프로젝트 기본 정보와 사업 설명을 저장합니다. 상세 계획 문서는 별도로 업로드할 수 있습니다."
        actions={<Link to={`/app/projects/${project.projectId}/plan/documents`}>문서 업로드로 시작</Link>}
      />
      {error && <Alert tone="danger" title="사업 개요를 저장하지 못했습니다">{error}</Alert>}
      <PolicyNotice restriction={restriction} onRetry={() => void servicePolicy.refresh().catch(() => undefined)} />
      <form className="project-form" onSubmit={handleSubmit} noValidate>
        <TextInput id="project-brief-title" label="프로젝트 이름" value={values.title} maxLength="150" onChange={update('title')} disabled={restriction.blocked} required />
        <TextInput id="project-brief-category" label="사업 분야" value={values.industryCategory} maxLength="100" onChange={update('industryCategory')} disabled={restriction.blocked} />
        <Textarea id="project-brief-description" label="사업 개요" description="누구의 어떤 문제를 어떻게 해결하는지 자유롭게 작성해 주세요." value={values.description} maxLength="10000" onChange={update('description')} disabled={restriction.blocked} />
        <div className="project-form__actions">
          <Button type="submit" loading={saving} disabled={saving || restriction.blocked}>저장하고 개요로 이동</Button>
          <Button type="button" variant="outline" disabled={saving} onClick={() => navigate(`/app/projects/${project.projectId}`)}>나중에 작성</Button>
        </div>
      </form>
    </div>
  );
}
