import { Link } from 'react-router-dom';

import { Progress, StatusBadge } from '../../../shared/ui/index.js';
import { getProjectProgress } from '../model/projectWorkflowModel.js';
import { projectRoutes } from '../routing/projectRoutes.js';
import { ProjectActionMenu } from '../ProjectSettingsSheet.jsx';

function formatProjectDate(value) {
  if (!value) return '날짜 정보 없음';
  return new Intl.DateTimeFormat('ko-KR', { month: 'short', day: 'numeric' }).format(new Date(value));
}

export default function ProjectRow({ project, density = 'default', showNextAction = true, menuOpen = false, onMenuOpenChange, onDelete }) {
  const progress = getProjectProgress(project);
  return <article className={`project-row project-row--${density} ${menuOpen ? 'project-row--menu-open' : ''}`} role="listitem">
    <Link className="project-row__main-link" to={projectRoutes.overview(project.projectId)} aria-labelledby={`project-row-title-${project.projectId}`}>
      <div className="project-row__project"><span className="project-row__initial" aria-hidden="true">{Array.from(project.name)[0]}</span><div><h2 id={`project-row-title-${project.projectId}`}>{project.name}</h2><p>{project.industryCategory || '사업 분야 미입력'}</p></div></div>
      <div className="project-row__journey"><Progress value={progress} label={project.stageLabel} /></div>
      <StatusBadge status={project.status} />
      {density !== 'compact' && <span className={`project-row__attention ${['PAUSED', 'DRAFT'].includes(project.status) ? 'is-needed' : ''}`}>{project.status === 'PAUSED' ? '주의 필요' : project.status === 'DRAFT' ? '입력 필요' : '정상 진행'}</span>}
      <div className="project-row__continue"><time dateTime={project.updatedAt}>{formatProjectDate(project.updatedAt)}</time>{showNextAction && <strong>{project.nextAction.label}</strong>}</div>
    </Link>
    <ProjectActionMenu project={project} onOpenChange={onMenuOpenChange} onDelete={onDelete} />
  </article>;
}
