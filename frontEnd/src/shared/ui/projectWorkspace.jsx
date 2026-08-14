import { AppIcon } from './icons.jsx';

export function ProjectSplitWorkspace({ primary, secondary, className = '' }) {
  return <div className={`project-split-workspace ${className}`}>
    <div className="project-split-workspace__primary">{primary}</div>
    <aside className="project-split-workspace__secondary">{secondary}</aside>
  </div>;
}

export function ProjectOptionalFields({ title = '선택 정보', completed, total, description, children }) {
  return <section className="project-optional-fields" aria-labelledby="project-optional-fields-title">
    <header><div><p>선택 입력</p><h3 id="project-optional-fields-title">{title}</h3>{description && <span>{description}</span>}</div><strong>{completed} / {total} 입력</strong></header>
    <div>{children}</div>
  </section>;
}

export function ProjectOptionalField({ id, label, summary, expanded, error, onToggle, children }) {
  const panelId = `${id}-optional-panel`;
  return <section className={`project-optional-field ${expanded ? 'is-expanded' : ''} ${error ? 'has-error' : ''}`}>
    <button type="button" aria-expanded={expanded} aria-controls={panelId} onClick={onToggle}>
      <span><strong>{label}</strong><small>{summary || '아직 입력하지 않음'}</small></span>
      <AppIcon name={expanded ? 'chevronUp' : 'chevronDown'} size={17} />
    </button>
    {expanded && <div id={panelId} className="project-optional-field__panel">{children}</div>}
  </section>;
}
