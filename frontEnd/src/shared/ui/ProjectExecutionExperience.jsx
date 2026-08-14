import { AppIcon } from './icons.jsx';

function formatElapsed(seconds) {
  const safe = Math.max(0, Number(seconds) || 0);
  return `${String(Math.floor(safe / 60)).padStart(2, '0')}:${String(Math.floor(safe % 60)).padStart(2, '0')}`;
}

export function ProjectExecutionExperience({
  title,
  phases,
  currentPhaseId,
  activity,
  state = 'RUNNING',
  elapsedSeconds,
  latestUpdate,
  metric,
  failureMessage,
  needsInputMessage,
  onDetail,
  children,
}) {
  const currentIndex = Math.max(0, phases.findIndex(({ id }) => id === currentPhaseId));
  const completedAll = state === 'COMPLETED';
  return <section className="project-execution" data-state={state} style={{ '--execution-phase-count': phases.length }} aria-live="polite">
    <header>
      <div><p>현재 처리</p><h2>{title}</h2></div>
      {elapsedSeconds != null && <span>경과 {formatElapsed(elapsedSeconds)}</span>}
    </header>
    <ol className="project-execution__rail" aria-label="처리 단계">
      {phases.map((phase, index) => {
        const phaseState = completedAll || index < currentIndex ? 'completed' : index === currentIndex ? 'current' : 'upcoming';
        return <li key={phase.id} data-phase-state={phaseState} aria-current={phaseState === 'current' ? 'step' : undefined}>
          <span className="project-execution__station" aria-hidden="true">
            {phaseState === 'completed' ? <AppIcon name="check" size={14} /> : index + 1}
          </span>
          <strong>{phase.label}</strong>
        </li>;
      })}
    </ol>
    <div className="project-execution__activity">
      <strong>{state === 'FAILED' ? failureMessage : state === 'NEEDS_INPUT' ? needsInputMessage : activity}</strong>
      {metric && <span>{metric}</span>}
      {latestUpdate && <small>{latestUpdate}</small>}
    </div>
    {(children || onDetail) && <footer>{children}{onDetail && <button type="button" onClick={onDetail}>작업센터에서 상세 기록 보기<AppIcon name="arrowUpRight" size={15} /></button>}</footer>}
  </section>;
}
