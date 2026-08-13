import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

import JobCenter from '../../features/job-center/JobCenter.jsx';
import { AppIcon } from '../../shared/ui/index.js';
import { getJourneyStatusView } from '../module-status/projectJourneyModel.js';
import { projectRoutes } from '../routing/projectRoutes.js';
import { useProjectChrome } from './ProjectChromeContext.jsx';

const TOOL_IDS = Object.freeze({ helper: 'project-helper-popover', navigator: 'project-navigator-popover', workCenter: 'project-work-center-popover' });

function HelpPopover({ model }) {
  return <section id={TOOL_IDS.helper} className="project-tool-popover project-helper-popover" role="dialog" aria-label="현재 업무 도움말">
    <p>{model.currentJourney.shortLabel} 안내</p>
    <h2>{model.currentModule.shortLabel}</h2>
    <dl><div><dt>현재 상태</dt><dd>{model.currentStatus.label}</dd></div><div><dt>다음 할 일</dt><dd>{model.currentModule.nextAction?.label ?? '현재 화면의 안내와 필요한 입력을 확인해 주세요.'}</dd></div></dl>
  </section>;
}

function JourneyPopover({ model, expanded, onExpandedChange }) {
  const currentIndex = model.journeys.findIndex(({ id }) => id === model.currentJourney.id);
  const previous = currentIndex > 0 ? model.journeys[currentIndex - 1] : null;
  const next = currentIndex >= 0 && currentIndex < model.journeys.length - 1 ? model.journeys[currentIndex + 1] : null;
  return <section id={TOOL_IDS.navigator} className="project-tool-popover project-journey-popover" aria-label="프로젝트 단계 탐색">
    <div className="project-journey-remote"><span>{previous ? <Link to={previous.href}>‹ {previous.shortLabel}</Link> : '프로젝트 개요'}</span><strong>{model.currentJourney.shortLabel}<small>{Math.max(currentIndex + 1, 0)} / 6</small></strong><span>{next ? <Link to={next.href}>{next.shortLabel} ›</Link> : '마지막 단계'}</span></div>
    <button type="button" className="project-journey-expand" aria-expanded={expanded} onClick={() => onExpandedChange(!expanded)}>{expanded ? '간단히 보기' : '전체 단계 보기'} <span aria-hidden="true">⌄</span></button>
    {expanded && <nav aria-label="프로젝트 전체 단계"><Link className={model.currentJourney.id === 'overview' ? 'is-current' : ''} to={projectRoutes.overview(model.projectId)}><span>프로젝트 개요</span><small>열기</small></Link>{model.journeys.map((journey) => { const view = getJourneyStatusView(journey.status); return <Link key={journey.id} className={model.currentJourney.id === journey.id ? 'is-current' : ''} to={journey.href}><span>{journey.label}</span><small data-tone={view.tone}>{view.label}</small></Link>; })}</nav>}
  </section>;
}

function initialSheet() {
  return { mounted: false, phase: 'closed', view: 'list', focusJobId: null, direction: 'forward' };
}

export default function ProjectContextTools() {
  const { model } = useProjectChrome();
  const location = useLocation();
  const [openState, setOpenState] = useState({ id: null, path: location.pathname });
  const openTool = openState.path === location.pathname ? openState.id : null;
  const [navigatorExpanded, setNavigatorExpanded] = useState(false);
  const [sheet, setSheet] = useState(initialSheet);
  const rootRef = useRef(null);
  const triggers = useRef({});
  const closeTimer = useRef(null);

  const closeTool = useCallback((restoreFocus = false) => {
    const trigger = openTool ? triggers.current[openTool] : null;
    setOpenState({ id: null, path: location.pathname });
    setNavigatorExpanded(false);
    if (restoreFocus) requestAnimationFrame(() => trigger?.focus());
  }, [location.pathname, openTool]);
  const closeSheet = useCallback(() => {
    setSheet((current) => ({ ...current, phase: 'closing' }));
    closeTimer.current = window.setTimeout(() => {
      setSheet(initialSheet());
      requestAnimationFrame(() => triggers.current.workCenter?.focus());
    }, 180);
  }, []);
  const openSheet = useCallback((jobId = null) => {
    window.clearTimeout(closeTimer.current);
    setSheet((current) => current.mounted
      ? { ...current, view: jobId ? 'detail' : 'list', focusJobId: jobId, direction: jobId ? 'forward' : 'backward' }
      : { mounted: true, phase: 'opening', view: jobId ? 'detail' : 'list', focusJobId: jobId, direction: 'forward' });
    requestAnimationFrame(() => setSheet((current) => ({ ...current, phase: 'open' })));
  }, []);

  useEffect(() => {
    if (!openTool && !sheet.mounted) return undefined;
    const onPointerDown = (event) => {
      if (!sheet.mounted && !rootRef.current?.contains(event.target)) closeTool(true);
    };
    const onKeyDown = (event) => {
      if (event.key !== 'Escape') return;
      if (sheet.mounted) closeSheet(); else closeTool(true);
    };
    window.addEventListener('pointerdown', onPointerDown);
    window.addEventListener('keydown', onKeyDown);
    return () => { window.removeEventListener('pointerdown', onPointerDown); window.removeEventListener('keydown', onKeyDown); };
  }, [closeSheet, closeTool, openTool, sheet.mounted]);
  useEffect(() => {
    if (!sheet.mounted) return undefined;
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = previous; };
  }, [sheet.mounted]);
  useEffect(() => () => window.clearTimeout(closeTimer.current), []);

  const buttons = useMemo(() => [
    { id: 'helper', label: '도움말', icon: 'sparkles' },
    { id: 'navigator', label: '단계', icon: 'project' },
    { id: 'workCenter', label: '작업', icon: 'clock' },
  ], []);
  if (!model) return null;

  return <div ref={rootRef} className="project-context-tools" aria-label="프로젝트 도구">
    <div className="project-context-tools__triggers">{buttons.map((button) => <button key={button.id} ref={(node) => { triggers.current[button.id] = node; }} type="button" aria-label={button.label} aria-expanded={openTool === button.id} aria-controls={TOOL_IDS[button.id]} onClick={() => { setNavigatorExpanded(false); setOpenState((current) => ({ id: current.path === location.pathname && current.id === button.id ? null : button.id, path: location.pathname })); }}><AppIcon name={button.icon} /><span>{button.label}</span></button>)}</div>
    {openTool === 'helper' && <HelpPopover model={model} />}
    {openTool === 'navigator' && <JourneyPopover model={model} expanded={navigatorExpanded} onExpandedChange={setNavigatorExpanded} />}
    {openTool === 'workCenter' && <div id={TOOL_IDS.workCenter} className="project-tool-popover project-work-popover"><JobCenter projectId={model.projectId} compact refreshKey={model.refreshKey} onTerminal={model.onTerminal} onRetryJob={model.onRetryJob} sheet={sheet} onOpenList={() => openSheet()} onOpenJob={(jobId) => openSheet(jobId)} onCloseSheet={closeSheet} onShowList={() => setSheet((current) => ({ ...current, view: 'list', focusJobId: null, direction: 'backward' }))} /></div>}
  </div>;
}
