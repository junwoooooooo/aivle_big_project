import { useEffect, useRef, useState } from 'react';
import ProductPreview from './ProductPreview.jsx';
import { workflowSteps } from '../data/landingData.js';

const WHEEL_THRESHOLD = 120;
const TRANSITION_MS = 820;
const COOLDOWN_MS = 220;
const LAST_STEP_SETTLE_MS = 520;
const MOMENTUM_RESET_MS = 160;
const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

function WorkflowPreview({ outgoing, active, direction }) {
  return <div className="workflow-preview-frame" aria-label={`${active.number}단계 제품 화면`}>
    <div className="product-preview__bar"><span /><span /><span /><strong>검증 진행 상황</strong></div>
    <div className="workflow-preview-frame__viewport">
      {outgoing && <div className={`workflow-preview-layer workflow-preview-layer--outgoing is-${direction}`} aria-hidden="true"><ProductPreview kind={outgoing.kind} /></div>}
      <div className={`workflow-preview-layer workflow-preview-layer--active${outgoing ? ` is-${direction}` : ''}`}><ProductPreview kind={active.kind} /></div>
    </div>
    <small>예시 프로젝트의 가상 데이터입니다.</small>
  </div>;
}

export default function WorkflowSection({ onNavigate }) {
  const [activeStep, setActiveStep] = useState(0);
  const [outgoingStep, setOutgoingStep] = useState(null);
  const [direction, setDirection] = useState('down');
  const desktopRef = useRef(null);
  const stageRef = useRef(null);
  const accumulatedDelta = useRef(0);
  const locked = useRef(false);
  const transitionTimer = useRef(null);
  const cooldownTimer = useRef(null);
  const momentumTimer = useRef(null);
  const transitionFinished = useRef(false);
  const targetStep = useRef(0);

  const finishTransition = () => {
    if (!locked.current || transitionFinished.current) return;
    transitionFinished.current = true;
    window.clearTimeout(transitionTimer.current);
    setOutgoingStep(null);
    cooldownTimer.current = window.setTimeout(() => { locked.current = false; },
      targetStep.current === workflowSteps.length - 1 ? LAST_STEP_SETTLE_MS : COOLDOWN_MS);
  };

  const triggerStep = (next) => {
    if (locked.current || next < 0 || next >= workflowSteps.length || next === activeStep) return;
    locked.current = true;
    transitionFinished.current = false;
    targetStep.current = next;
    accumulatedDelta.current = 0;
    setDirection(next > activeStep ? 'down' : 'up');
    setOutgoingStep(activeStep);
    setActiveStep(next);
    window.clearTimeout(transitionTimer.current);
    window.clearTimeout(cooldownTimer.current);
    transitionTimer.current = window.setTimeout(finishTransition, TRANSITION_MS + 80);
  };

  useEffect(() => {
    const desktop = desktopRef.current;
    const stage = stageRef.current;
    const desktopQuery = typeof window.matchMedia === 'function' ? window.matchMedia('(min-width: 1024px)') : null;
    if (!desktop || !stage || (desktopQuery && !desktopQuery.matches)) return undefined;
    const onWheel = (event) => {
      const rect = stage.getBoundingClientRect();
      const viewport = window.innerHeight || 1;
      const stageActive = rect.top <= 4 && rect.bottom >= viewport - 4;
      if (!stageActive || !event.deltaY) return;
      const movingDown = event.deltaY > 0;
      if ((movingDown && activeStep === workflowSteps.length - 1) || (!movingDown && activeStep === 0)) return;
      if (locked.current) {
        event.preventDefault();
        accumulatedDelta.current = 0;
        return;
      }
      event.preventDefault();
      if ((accumulatedDelta.current > 0) !== movingDown) accumulatedDelta.current = 0;
      accumulatedDelta.current += event.deltaY;
      window.clearTimeout(momentumTimer.current);
      momentumTimer.current = window.setTimeout(() => { accumulatedDelta.current = 0; }, MOMENTUM_RESET_MS);
      if (Math.abs(accumulatedDelta.current) >= WHEEL_THRESHOLD) triggerStep(activeStep + (movingDown ? 1 : -1));
    };
    const onAnimationEnd = (event) => {
      if (event.target.classList?.contains('workflow-slide--outgoing')
        && event.animationName.startsWith('workflow-out-')) finishTransition();
    };
    desktop.addEventListener('wheel', onWheel, { passive: false });
    desktop.addEventListener('animationend', onAnimationEnd);
    return () => {
      desktop.removeEventListener('wheel', onWheel);
      desktop.removeEventListener('animationend', onAnimationEnd);
    };
  // The listener intentionally rebinds on a settled step; triggerStep reads
  // that current step and prevents queued wheel input during the transition.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeStep]);

  useEffect(() => () => { window.clearTimeout(transitionTimer.current); window.clearTimeout(cooldownTimer.current); window.clearTimeout(momentumTimer.current); }, []);
  const goToStep = (index) => triggerStep(clamp(index, 0, workflowSteps.length - 1));
  const onKeyDown = (event) => {
    const next = event.key === 'ArrowDown' || event.key === 'PageDown' ? activeStep + 1
      : event.key === 'ArrowUp' || event.key === 'PageUp' ? activeStep - 1
        : event.key === 'Home' ? 0
          : event.key === 'End' ? workflowSteps.length - 1 : null;
    if (next !== null) { event.preventDefault(); goToStep(next); }
  };

  const active = workflowSteps[activeStep];
  const outgoing = outgoingStep === null ? null : workflowSteps[outgoingStep];
  return <section id="workflow" className="landing-section landing-workflow" aria-labelledby="workflow-title"><div className="landing-container"><p className="landing-eyebrow">HOW IT WORKS</p><h2 id="workflow-title">하나의 아이디어가,<br />하나의 검증 여정이 됩니다.</h2><p className="landing-section__lede">사업 기획부터 최종 보고서까지, 저장된 결과와 사용자 선택을 따라 6단계로 진행합니다.</p><div ref={desktopRef} className="workflow-desktop" tabIndex="0" onKeyDown={onKeyDown} aria-label="6단계 사업 검증 흐름">
    <div ref={stageRef} className="workflow-stage"><div className="workflow-morph">
      <div className="workflow-copy-stack">{outgoing && <article className={`workflow-slide workflow-slide--outgoing is-${direction}`} aria-hidden="true"><p className="workflow-stage__eyebrow">현재 단계 <b>{outgoing.number}</b> / 06</p><h3>{outgoing.title}</h3><p>{outgoing.description}</p></article>}<article className={`workflow-slide workflow-slide--active${outgoing ? ` is-${direction}` : ''}`}><p className="workflow-stage__eyebrow">현재 단계 <b>{active.number}</b> / 06</p><h3>{active.title}</h3><p>{active.description}</p>{active.number === '06' && <button type="button" className="landing-text-button" onClick={() => onNavigate('demo')}>샘플 결과 보기</button>}</article></div>
      <WorkflowPreview outgoing={outgoing} active={active} direction={direction} />
    </div><div className="workflow-rail" aria-label="단계 선택">{workflowSteps.map((item, index) => <button type="button" key={item.number} data-workflow-rail={index} className={index === activeStep ? 'is-active' : ''} aria-current={index === activeStep ? 'step' : undefined} onClick={() => goToStep(index)}><span>{item.number}</span><i /></button>)}</div></div>
  </div><p className="visually-hidden" aria-live="polite">{active.number}단계, {active.title}</p><div className="workflow-mobile">{workflowSteps.map((item) => <article key={item.number}><span className="workflow-step__number">{item.number}</span><h3>{item.title}</h3><p>{item.description}</p><ProductPreview kind={item.kind} /></article>)}</div></div></section>;
}
