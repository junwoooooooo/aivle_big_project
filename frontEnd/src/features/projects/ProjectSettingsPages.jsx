import { useState } from 'react';
import { Link, Navigate, Outlet, useNavigate } from 'react-router-dom';

import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { Alert, Button, Dialog, PageHeader, TextInput, Textarea } from '../../shared/ui/index.js';
import { appRoutes, projectRoutes } from './routing/projectRoutes.js';
import { createProjectApi } from './api/projectApi.js';
import { useProjectContext } from './ProjectContext.jsx';
import { useServicePolicy } from '../service-policy/useServicePolicy.js';
import { getWriteRestriction, isServicePolicyError } from '../service-policy/servicePolicyRestrictions.js';
import './projects.css';

export function ProjectSettingsLayout() {
  const { project } = useProjectContext();
  return <section className="project-settings"><PageHeader eyebrow="Project settings" title={project.name} description="프로젝트 메타데이터와 보관 정책을 관리합니다." /><nav aria-label="프로젝트 설정"><Link to={projectRoutes.settings(project.projectId)}>General</Link><Link to={projectRoutes.danger(project.projectId)}>Danger zone</Link></nav><Outlet /> </section>;
}

export function ProjectSettingsRedirect() { const { project } = useProjectContext(); return <Navigate to={projectRoutes.settings(project.projectId)} replace />; }

export function ProjectGeneralSettingsPage() {
  const client = useApiClient(); const { project, retry } = useProjectContext();
  const servicePolicy = useServicePolicy(); const restriction = getWriteRestriction(servicePolicy);
  const [values, setValues] = useState({ title: project.name, industryCategory: project.industryCategory || '', description: project.description || '' });
  const [saving, setSaving] = useState(false); const [error, setError] = useState(''); const [message, setMessage] = useState('');
  const update = (field) => (event) => setValues((current) => ({ ...current, [field]: event.target.value }));
  const save = async (event) => { event.preventDefault(); if (saving || restriction.blocked || !values.title.trim()) return; setSaving(true); setError(''); setMessage(''); try { await createProjectApi(client).update(project.projectId, { title: values.title.trim(), industryCategory: values.industryCategory.trim() || null, description: values.description.trim() || null }); await retry(); setMessage('프로젝트 정보를 저장했습니다.'); } catch (nextError) { if (isServicePolicyError(nextError)) void servicePolicy.refresh().catch(() => undefined); setError(getUserErrorMessage(nextError)); } finally { setSaving(false); } };
  return <form className="project-form" onSubmit={save}>{error && <Alert tone="danger" title="프로젝트를 저장하지 못했습니다">{error}</Alert>}{message && <Alert tone="success" title="저장됨">{message}</Alert>}{restriction.blocked && <Alert tone={restriction.code === 'POLICY_UNAVAILABLE' ? 'danger' : 'warning'} title="프로젝트를 변경할 수 없습니다">{restriction.message}</Alert>}<TextInput id="settings-project-title" label="프로젝트 이름" value={values.title} maxLength="150" onChange={update('title')} disabled={restriction.blocked} required /><TextInput id="settings-project-industry" label="사업 분야" value={values.industryCategory} maxLength="100" onChange={update('industryCategory')} disabled={restriction.blocked} /><Textarea id="settings-project-description" label="프로젝트 설명" value={values.description} maxLength="10000" onChange={update('description')} disabled={restriction.blocked} /><Button type="submit" loading={saving} disabled={saving || restriction.blocked}>변경사항 저장</Button></form>;
}

export function ProjectDangerSettingsPage() {
  const client = useApiClient(); const navigate = useNavigate(); const { project } = useProjectContext();
  const servicePolicy = useServicePolicy(); const restriction = getWriteRestriction(servicePolicy);
  const [open, setOpen] = useState(false); const [confirmation, setConfirmation] = useState(''); const [deleting, setDeleting] = useState(false); const [error, setError] = useState('');
  const remove = async () => { if (confirmation !== project.name || deleting || restriction.blocked) return; setDeleting(true); setError(''); try { await createProjectApi(client).remove(project.projectId); navigate(appRoutes.projects, { replace: true }); } catch (nextError) { if (isServicePolicyError(nextError)) void servicePolicy.refresh().catch(() => undefined); setError(getUserErrorMessage(nextError)); setDeleting(false); } };
  return <section className="project-danger-zone"><h2>Danger zone</h2>{restriction.blocked && <Alert tone={restriction.code === 'POLICY_UNAVAILABLE' ? 'danger' : 'warning'} title="프로젝트를 삭제할 수 없습니다">{restriction.message}</Alert>}<div><div><h3>프로젝트 삭제</h3><p>프로젝트와 연결된 문서, 분석 결과, 패널 결과 및 보고서에 더 이상 접근할 수 없습니다. 이 작업은 되돌릴 수 없습니다.</p></div><Button variant="danger" disabled={restriction.blocked} onClick={() => setOpen(true)}>프로젝트 삭제</Button></div><Dialog open={open} onClose={() => !deleting && setOpen(false)} title="프로젝트를 삭제할까요?"><p><strong>{project.name}</strong>을(를) 삭제하려면 프로젝트 이름을 정확히 입력하세요.</p>{error && <Alert tone="danger" title="삭제하지 못했습니다">{error}</Alert>}<TextInput id="delete-project-confirmation" label="프로젝트 이름" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} disabled={restriction.blocked} /><div className="dialog-actions"><Button variant="outline" disabled={deleting} onClick={() => setOpen(false)}>취소</Button><Button variant="danger" loading={deleting} disabled={confirmation !== project.name || deleting || restriction.blocked} onClick={remove}>영구 삭제</Button></div></Dialog></section>;
}
