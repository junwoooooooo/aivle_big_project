import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useLocation, useNavigate, useParams } from 'react-router-dom';

import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { Alert, AppIcon, Button, ErrorState, LoadingState, SideSheet, TextInput, Textarea } from '../../shared/ui/index.js';
import { createProjectApi } from './api/projectApi.js';
import { useProjectContext } from './ProjectContext.jsx';
import { appRoutes, projectRoutes } from './routing/projectRoutes.js';
import { getProjectNameError } from './projectNameError.js';
import ProjectDeleteDialog from './components/ProjectDeleteDialog.jsx';
import { useServicePolicy } from '../service-policy/useServicePolicy.js';
import { getWriteRestriction, isServicePolicyError } from '../service-policy/servicePolicyRestrictions.js';
import './projects.css';

const EXIT_FALLBACK_MS = 360;

export function ProjectActionMenu({ project, onDelete, onOpenChange }) {
  const servicePolicy = useServicePolicy();
  const restriction = getWriteRestriction(servicePolicy);
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState(null);
  const triggerRef = useRef(null);
  const menuId = useId();
  const navigate = useNavigate();
  const location = useLocation();
  const close = useCallback(() => {
    setOpen(false);
    onOpenChange?.(false);
    requestAnimationFrame(() => triggerRef.current?.focus());
  }, [onOpenChange]);
  const toggle = (event) => {
    event.preventDefault();
    event.stopPropagation();
    const next = !open;
    if (next) {
      const rect = triggerRef.current?.getBoundingClientRect();
      setPosition(rect ? { top: rect.bottom + 6, left: rect.right - 176 } : null);
    }
    setOpen(next);
    onOpenChange?.(next);
  };
  useEffect(() => {
    if (!open) return undefined;
    const closeOnOutside = (event) => {
      if (!triggerRef.current?.contains(event.target) && !event.target.closest?.(`#${menuId}`)) close();
    };
    const onKey = (event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        close();
      }
    };
    window.addEventListener('pointerdown', closeOnOutside);
    window.addEventListener('keydown', onKey);
    window.addEventListener('resize', close);
    window.addEventListener('scroll', close, true);
    return () => {
      window.removeEventListener('pointerdown', closeOnOutside);
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('resize', close);
      window.removeEventListener('scroll', close, true);
    };
  }, [close, menuId, open]);
  const go = (to, overlay = to.endsWith('/settings')) => {
    close();
    window.setTimeout(() => {
      if (overlay) navigate(to, { state: { backgroundLocation: location, returnTo: `${location.pathname}${location.search}` } });
      else navigate(to);
    }, 180);
  };
  const menu = open && position && createPortal(
    <div id={menuId} className="project-action-menu__panel" role="menu" style={{ top: position.top, left: Math.max(8, position.left) }}>
      <button type="button" role="menuitem" onClick={() => go(projectRoutes.overview(project.projectId))}>프로젝트 열기</button>
      <button type="button" role="menuitem" onClick={() => go(projectRoutes.settings(project.projectId))}><AppIcon name="settings" />프로젝트 설정</button>
      {onDelete && <button type="button" role="menuitem" className="is-danger" disabled={restriction.blocked} title={restriction.blocked ? restriction.message : undefined} onClick={() => { onDelete(); close(); }}><AppIcon name="trash" />프로젝트 삭제</button>}
    </div>,
    document.body,
  );
  return <div className="project-action-menu"><Button ref={triggerRef} type="button" variant="ghost" className="project-action-menu__trigger" aria-label={`${project.name} 프로젝트 메뉴`} aria-haspopup="menu" aria-controls={open ? menuId : undefined} aria-expanded={open} onClick={toggle}><AppIcon name="more" /></Button>{menu}</div>;
}

function ProjectSettingsContent({ project, retry, onFinalClose }) {
  const client = useApiClient();
  const servicePolicy = useServicePolicy();
  const restriction = getWriteRestriction(servicePolicy);
  const navigate = useNavigate();
  const [values, setValues] = useState(() => ({ title: project.name, industryCategory: project.industryCategory || '', description: project.description || '' }));
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const titleInputRef = useRef(null);
  const [closing, setClosing] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const closeTimer = useRef(null);

  const finishClose = useCallback(() => {
    if (closeTimer.current) window.clearTimeout(closeTimer.current);
    onFinalClose();
  }, [onFinalClose]);
  const requestClose = useCallback(() => {
    if (saving || closing) return;
    setClosing(true);
    closeTimer.current = window.setTimeout(finishClose, EXIT_FALLBACK_MS);
  }, [closing, finishClose, saving]);
  useEffect(() => () => window.clearTimeout(closeTimer.current), []);

  const update = (field) => (event) => { setValues((current) => ({ ...current, [field]: event.target.value })); setFieldErrors((current) => ({ ...current, [field]: undefined })); };
  const save = async (event) => {
    event.preventDefault();
    if (saving || restriction.blocked || !values.title.trim()) return;
    setSaving(true); setError(''); setMessage(''); setFieldErrors({});
    try {
      await createProjectApi(client).update(project.projectId, {
        title: values.title.trim(),
        industryCategory: values.industryCategory.trim() || null,
        description: values.description.trim() || null,
      });
      await retry();
      setMessage('변경사항을 저장했습니다.');
    } catch (nextError) {
      if (isServicePolicyError(nextError)) {
        void servicePolicy.refresh().catch(() => undefined);
      }
      const titleError = getProjectNameError(nextError);
      if (titleError) {
        setFieldErrors({ title: titleError });
        requestAnimationFrame(() => titleInputRef.current?.focus());
        return;
      }
      setError(getUserErrorMessage(nextError));
    } finally {
      setSaving(false);
    }
  };

  const requestDelete = () => setConfirmingDelete(true);

  return <><SideSheet open title="프로젝트 설정" label="프로젝트 설정" phase={closing ? 'exiting' : 'entered'} onExited={finishClose} onClose={requestClose} footer={<><Button variant="outline" size="small" disabled={saving || closing} onClick={requestClose}>취소</Button><Button type="submit" size="small" form="project-settings-form" loading={saving} disabled={saving || closing || restriction.blocked}>변경사항 저장</Button></>}>
    <div className="project-sheet__heading"><span><AppIcon name="settings" size={20} /></span><div><h2>프로젝트 설정</h2><p>프로젝트 기본 정보와 삭제를 관리합니다.</p></div></div>
    <form id="project-settings-form" className="project-sheet__form" onSubmit={save}>
      {error && <Alert tone="danger" title="저장하지 못했습니다.">{error}</Alert>}
      {message && <Alert tone="success" title="저장됨">{message}</Alert>}
      {restriction.blocked && <Alert tone={restriction.code === 'POLICY_UNAVAILABLE' ? 'danger' : 'warning'} title="프로젝트를 변경할 수 없습니다"><p>{restriction.message}</p>{restriction.code === 'POLICY_UNAVAILABLE' && <Button type="button" variant="outline" size="small" onClick={() => void servicePolicy.refresh().catch(() => undefined)}>다시 시도</Button>}</Alert>}
      <TextInput ref={titleInputRef} id="project-settings-title" label="프로젝트 이름" value={values.title} error={fieldErrors.title} maxLength="150" required onChange={update('title')} disabled={restriction.blocked} />
      <TextInput id="project-settings-industry" label="사업 분야" value={values.industryCategory} maxLength="100" onChange={update('industryCategory')} disabled={restriction.blocked} />
      <Textarea id="project-settings-description" label="프로젝트 설명" value={values.description} maxLength="10000" onChange={update('description')} disabled={restriction.blocked} />
    </form>
    <section className="project-sheet__danger"><div><span><AppIcon name="trash" /></span><div><h2>프로젝트 삭제</h2><p>프로젝트와 연결된 문서 및 분석 결과에 더 이상 접근할 수 없습니다. 이 작업은 되돌릴 수 없습니다.</p></div></div><Button variant="danger" size="small" disabled={restriction.blocked} title={restriction.blocked ? restriction.message : undefined} onClick={requestDelete}><AppIcon name="trash" />프로젝트 삭제</Button></section>
  </SideSheet>
  <ProjectDeleteDialog project={project} open={confirmingDelete} onClose={() => setConfirmingDelete(false)} onDeleted={() => navigate(appRoutes.projects, { replace: true, state: null })} />
  </>;
}

export default function ProjectSettingsSheet() {
  const navigate = useNavigate();
  const location = useLocation();
  const { projectId } = useParams();
  const { project, retry, status } = useProjectContext();
  const returnTo = location.state?.returnTo;
  const onFinalClose = useCallback(() => navigate(returnTo || projectRoutes.overview(projectId), { replace: Boolean(returnTo), state: null }), [navigate, projectId, returnTo]);

  if (status === 'loading') return <SideSheet open title="프로젝트 설정" label="프로젝트 설정" onClose={onFinalClose}><LoadingState label="프로젝트 설정을 불러오는 중입니다." /></SideSheet>;
  if (status === 'error' || !project) return <SideSheet open title="프로젝트 설정" label="프로젝트 설정" onClose={onFinalClose}><ErrorState title="프로젝트 설정을 불러오지 못했습니다." description="잠시 후 다시 시도하거나 이전 화면으로 돌아가세요." onRetry={retry} /></SideSheet>;
  return <ProjectSettingsContent project={project} retry={retry} onFinalClose={onFinalClose} />;
}
