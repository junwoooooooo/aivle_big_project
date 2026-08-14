import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

import JobCenter from '../../features/job-center/JobCenter.jsx';
import { AppIcon, useBodyScrollLock } from '../../shared/ui/index.js';
import { getJourneyStatusView } from '../module-status/projectJourneyModel.js';
import { projectRoutes } from '../routing/projectRoutes.js';
import { useProjectChrome } from './ProjectChromeContext.jsx';

const TOOL_IDS = Object.freeze({ helper: 'project-helper-popover', navigator: 'project-navigator-popover', workCenter: 'project-work-center-popover' });

function HelpPopover({ model }) {
  return <section id={TOOL_IDS.helper} className="project-tool-popover project-helper-popover" role="dialog" aria-label="현재 업무 도움말">
    <p>지금 하는 일</p>
    <h2>{model.currentModule.shortLabel}</h2>
    <dl><div><dt>현재 상태</dt><dd>{model.currentStatus.label}</dd></div><div><dt>다음에 할 일</dt><dd>{model.currentModule.nextAction?.label ?? '화면의 안내를 확인하고 다음 입력을 진행해 주세요.'}</dd></div></dl>
  </section>;
}

function JourneyPopover({ model, expanded, onExpandedChange }) {
  const sequence = [{ id: 'overview', shortLabel: '프로젝트 개요', href: projectRoutes.overview(model.projectId) }, ...model.journeys];
  const currentIndex = sequence.findIndex(({ id }) => id === model.currentJourney.id);
  const previous = currentIndex > 0 ? sequence[currentIndex - 1] : null;
  const next = currentIndex < sequence.length - 1 ? sequence[currentIndex + 1] : null;
  return <section id={TOOL_IDS.navigator} className="project-tool-popover project-journey-popover" aria-label="프로젝트 단계 탐색">
    <div className="project-journey-remote"><strong>{model.currentJourney.shortLabel}<small>{currentIndex + 1} / {sequence.length}</small></strong><div className="project-journey-remote__directions"><span>{previous ? <Link to={previous.href} aria-label={`이전 단계: ${previous.shortLabel}`}><AppIcon name="chevronLeft" size={24} /><small>{previous.shortLabel}</small></Link> : <button type="button" disabled aria-label="이전 단계 없음"><AppIcon name="chevronLeft" size={24} /></button>}</span><span>{next ? <Link to={next.href} aria-label={`다음 단계: ${next.shortLabel}`}><AppIcon name="chevronRight" size={24} /><small>{next.shortLabel}</small></Link> : <button type="button" disabled aria-label="다음 단계 없음"><AppIcon name="chevronRight" size={24} /></button>}</span></div></div>
    <button type="button" className="project-journey-expand" aria-expanded={expanded} onClick={() => onExpandedChange(!expanded)}>{expanded ? '간단히 보기' : '전체 단계 보기'} <AppIcon name={expanded ? 'chevronUp' : 'chevronDown'} size={15} /></button>
    {expanded && <nav aria-label="프로젝트 전체 단계"><Link className={model.currentJourney.id === 'overview' ? 'is-current' : ''} to={projectRoutes.overview(model.projectId)}><span>프로젝트 개요</span><small>열기</small></Link>{model.journeys.map((journey) => { const view = getJourneyStatusView(journey.status); return <Link key={journey.id} className={model.currentJourney.id === journey.id ? 'is-current' : ''} to={journey.href}><span>{journey.label}</span><small data-tone={view.tone}>{view.label}</small></Link>; })}</nav>}
  </section>;
}

function initialSheet() {
  return { mounted: false, phase: 'closed', view: 'list', focusJobId: null, direction: 'forward' };
}

export default function ProjectContextTools() {
  const { model, registerToolActions } = useProjectChrome();
  const location = useLocation();
  const [openState, setOpenState] = useState({ id: null, path: location.pathname });
  const openTool = openState.path === location.pathname ? openState.id : null;
  const [navigatorExpanded, setNavigatorExpanded] = useState(false);
  const [sheet, setSheet] = useState(initialSheet);
  const rootRef = useRef(null);
  const triggers = useRef({});
  const closeTimer = useRef(null);
  const previousPath = useRef(location.pathname);

  const closeTool = useCallback((restoreFocus = false) => {
    const trigger = openTool ? triggers.current[openTool] : null;
    setOpenState({ id: null, path: location.pathname });
    setNavigatorExpanded(false);
    if (restoreFocus) requestAnimationFrame(() => trigger?.focus());
  }, [location.pathname, openTool]);
  const closeSheet = useCallback(() => {
    window.clearTimeout(closeTimer.current);
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

  useEffect(() => registerToolActions({ openWorkCenterJob: openSheet }), [openSheet, registerToolActions]);

  const closeSheetImmediately = useCallback(() => {
    window.clearTimeout(closeTimer.current);
    setSheet(initialSheet());
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
  useBodyScrollLock(sheet.mounted);
  useEffect(() => {
    if (previousPath.current !== location.pathname) {
      window.clearTimeout(closeTimer.current);
      setSheet(initialSheet());
      setOpenState({ id: null, path: location.pathname });
      setNavigatorExpanded(false);
      previousPath.current = location.pathname;
    }
  }, [location.pathname]);
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
    {(openTool === 'workCenter' || sheet.mounted) && <JobCenter projectId={model.projectId} compact quickOpen={openTool === 'workCenter' && !sheet.mounted} quickContainerId={TOOL_IDS.workCenter} refreshKey={model.refreshKey} onTerminal={model.onTerminal} onRetryJob={model.onRetryJob} sheet={sheet} onOpenList={() => { closeTool(); openSheet(); }} onOpenJob={(jobId) => { closeTool(); openSheet(jobId); }} onCloseSheet={closeSheet} onNavigate={closeSheetImmediately} onShowList={() => setSheet((current) => ({ ...current, view: 'list', focusJobId: null, direction: 'backward' }))} />}
  </div>;
}
