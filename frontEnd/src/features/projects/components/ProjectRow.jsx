import { Link } from 'react-router-dom';

import { Badge } from '../../../shared/ui/index.js';
import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import { ProjectActionMenu } from '../ProjectSettingsSheet.jsx';

function formatProjectDate(value) {
  if (!value) return '날짜 정보 없음';
  return new Intl.DateTimeFormat('ko-KR', { month: 'short', day: 'numeric' }).format(new Date(value));
}

export default function ProjectRow({ project, density = 'default', showNextAction = true, menuOpen = false, onMenuOpenChange, onDelete }) {
  return <article className={`project-row project-row--${density} ${menuOpen ? 'project-row--menu-open' : ''}`} role="listitem">
    <Link className="project-row__main-link" to={projectRoutes.overview(project.projectId)} aria-labelledby={`project-row-title-${project.projectId}`}>
      <div className="project-row__project"><span className="project-row__initial" aria-hidden="true">{Array.from(project.name)[0]}</span><div><h2 id={`project-row-title-${project.projectId}`}>{project.name}</h2><p>{project.industryCategory || '사업 분야 미입력'}</p></div></div>
      <div className="project-row__journey"><span>현재 업무</span><strong>{project.stageLabel}</strong></div>
      <div className="project-row__progress"><span>진행률</span><strong>{project.journeyCompleted} / {project.journeyTotal}</strong></div>
      <Badge tone={project.statusTone}>{project.statusLabel}</Badge>
      <div className="project-row__continue"><span>최근 수정</span><time dateTime={project.updatedAt}>{formatProjectDate(project.updatedAt)}</time>{showNextAction && <small>{project.nextAction.label}</small>}</div>
    </Link>
    <ProjectActionMenu project={project} onOpenChange={onMenuOpenChange} onDelete={onDelete} />
  </article>;
}
