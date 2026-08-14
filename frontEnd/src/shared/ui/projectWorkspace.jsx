import { AppIcon } from './icons.jsx';

const PROJECT_WORKSPACE_MODE = Object.freeze({
  COMPOSE: 'compose', REVIEW: 'review', ANALYZE: 'analyze', DECIDE: 'decide', DOCUMENT: 'document',
});

export function ProjectWorkspace({ as: Element = 'div', mode = PROJECT_WORKSPACE_MODE.ANALYZE,
  className = '', children, ...props }) {
  return <Element className={`project-workspace project-workspace--${mode} ${className}`} {...props}>{children}</Element>;
}

export function ProjectStageHeader({ step, eyebrow, title, titleId, description, status, actions, className = '' }) {
  return <header className={`project-stage-header ${className}`}>
    <div className="project-stage-header__context">
      {step != null && <span className="project-stage-header__step" aria-label={`${step}단계`}>{step}</span>}
      <div>{eyebrow && <p>{eyebrow}</p>}<h1 id={titleId}>{title}</h1>{description && <span>{description}</span>}</div>
    </div>
    {(status || actions) && <div className="project-stage-header__aside">{status}{actions}</div>}
  </header>;
}

export function ProjectStatusStrip({ label = '현재 상태', tone = 'neutral', children, action }) {
  return <section className="project-status-strip" data-tone={tone} role="status">
    <div><span>{label}</span><strong>{children}</strong></div>{action}
  </section>;
}

export function ProjectSection({ as: Element = 'section', eyebrow, title, description, actions,
  surface = 'base', className = '', children, ...props }) {
  return <Element className={`project-section project-section--${surface} ${className}`} {...props}>
    {(eyebrow || title || description || actions) && <header><div>{eyebrow && <p>{eyebrow}</p>}
      {title && <h2>{title}</h2>}{description && <span>{description}</span>}</div>{actions}</header>}
    {children}
  </Element>;
}

export function ProjectProgressPanel({ title = '진행 상황', description, children }) {
  return <section className="project-progress-panel" aria-live="polite">
    <span className="project-progress-panel__indicator" aria-hidden="true" />
    <div><h2>{title}</h2>{description && <p>{description}</p>}{children}</div>
  </section>;
}

export function ProjectDisclosure({ summary, children }) {
  return <details className="project-disclosure"><summary>{summary}<AppIcon name="chevronDown" size={17} /></summary>
    <div>{children}</div></details>;
}

export function ProjectSplitWorkspace({ primary, secondary, className = '' }) {
  return <div className={`project-split-workspace ${className}`}>
    <div className="project-split-workspace__primary">{primary}</div>
    <aside className="project-split-workspace__secondary">{secondary}</aside>
  </div>;
}

export function ProjectWorkspaceActions({ children, className = '' }) {
  return <div className={`project-workspace-actions ${className}`}>{children}</div>;
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
