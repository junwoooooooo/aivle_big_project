import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import DemoSimulator from './components/DemoSimulator.jsx';
import HeroSection from './components/HeroSection.jsx';
import LandingBootIntro from './components/LandingBootIntro.jsx';
import LandingFooter from './components/LandingFooter.jsx';
import LandingHeader from './components/LandingHeader.jsx';
import WorkflowSection from './components/WorkflowSection.jsx';
import { faqItems, featureItems, navItems } from './data/landingData.js';
import useLandingIntro from './hooks/useLandingIntro.js';
import useReducedMotion from './hooks/useReducedMotion.js';
import useScrollSpy from './hooks/useScrollSpy.js';
import useSectionScrollProgress from './hooks/useSectionScrollProgress.js';
import './landing.css';
import './intro.css';
import './validationIntro.css';
import './validationTransition.css';

function scrollToSection(id, reducedMotion, focus = false) {
  document.getElementById(id)?.scrollIntoView?.({
    behavior: reducedMotion ? 'auto' : 'smooth',
    block: 'start',
  });
  window.history.replaceState(null, '', `#${id}`);
  if (focus) {
    window.requestAnimationFrame(() => (
      document.getElementById(`${id}-title`)?.focus({ preventScroll: true })
    ));
  }
}

function IntroSection() {
  const problems = [
    ['근거와 가정 분리', '확인된 사실과 아직 검증할 가정을 구분합니다.'],
    ['단계별 의사결정', '각 단계의 결과를 확인하고 확정한 뒤 다음 분석으로 이동합니다.'],
    ['결과 복원', 'AI 결과와 사용자 선택을 저장해 새로고침 후에도 이어서 작업합니다.'],
  ];
  return (
    <section id="intro" className="landing-section landing-intro" aria-labelledby="intro-title">
      <div className="landing-container">
        <p className="landing-eyebrow">ONE CONNECTED JOURNEY</p>
        <h2 id="intro-title">아이디어 입력부터<br />사업 검증 보고서까지.</h2>
        <p className="landing-section__lede">
          아이디어를 구조화하고 법률 위험, 사업 콘셉트, 합성 Persona 인터뷰와 마케팅 가설을
          단계별로 검토합니다. 모든 결과는 프로젝트에 저장되어 다음 접속에서도 이어집니다.
        </p>
        <div className="problem-grid">
          {problems.map(([title, description], index) => (
            <article key={title}>
              <span>0{index + 1}</span>
              <h3>{title}</h3>
              <p>{description}</p>
            </article>
          ))}
        </div>
        <p className="landing-resolution">
          흩어진 검토를 하나의 흐름으로 연결해, <strong>지금 확인할 것과 다음 행동</strong>을 분명하게 만듭니다.
        </p>
      </div>
    </section>
  );
}

function FeatureSection() {
  return (
    <section id="features" className="landing-section landing-features" aria-labelledby="features-title">
      <div className="landing-container">
        <p className="landing-eyebrow">JOURNEY CAPABILITIES</p>
        <h2 id="features-title">검토 결과가 다음 행동으로<br />자연스럽게 이어집니다.</h2>
        <div className="feature-grid">
          {featureItems.map(([title, description, size], index) => (
            <article key={title} className={`feature-card ${size}`}>
              <span className="feature-card__number">0{index + 1}</span>
              <h3>{title}</h3>
              <p>{description}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

function TrustAndOutcome() {
  return (
    <>
      <section className="landing-trust" aria-labelledby="trust-title">
        <div className="landing-container">
          <p className="landing-eyebrow">RESPONSIBLE ASSISTANCE</p>
          <h2 id="trust-title">AI의 제안과 사용자의 결정을 구분합니다.</h2>
          <div className="trust-grid">
            <article><h3>근거를 구분합니다</h3><p>사실, 가정, 위험과 추가 조사 필요를 서로 다른 항목으로 표시합니다.</p></article>
            <article><h3>사용자가 확정합니다</h3><p>아이디어, 콘셉트, 마케팅 Asset과 최종 Decision은 사용자가 선택합니다.</p></article>
            <article><h3>한계를 표시합니다</h3><p>법률 검토와 합성 Persona 결과가 공식 자문이나 실제 고객 조사로 오해되지 않도록 안내합니다.</p></article>
          </div>
          <p className="trust-disclaimer">AI 결과는 의사결정 지원 자료이며 법률·재무·투자 자문이나 사업 성과를 보장하지 않습니다.</p>
        </div>
      </section>
      <section className="landing-section landing-outcome" aria-labelledby="outcome-title">
        <div className="landing-container">
          <p className="landing-eyebrow">FROM IDEA TO DECISION</p>
          <h2 id="outcome-title">막연한 아이디어를 검토 가능한 의사결정 자료로.</h2>
          <div className="outcome-grid">
            <article>
              <p>BEFORE</p>
              <ul><li>정리되지 않은 아이디어</li><li>확인되지 않은 법률·시장 가정</li><li>흩어진 메시지와 선택 근거</li></ul>
            </article>
            <span aria-hidden="true">→</span>
            <article className="is-after">
              <p>AFTER</p>
              <ul><li>확정된 Idea Version</li><li>비교 가능한 Concept와 Persona Insight</li><li>선택 근거가 담긴 Final Report</li></ul>
            </article>
          </div>
        </div>
      </section>
    </>
  );
}

function FaqSection() {
  const [open, setOpen] = useState(null);
  return (
    <section id="faq" className="landing-section landing-faq" aria-labelledby="faq-title">
      <div className="landing-container landing-container--narrow">
        <p className="landing-eyebrow">FAQ</p>
        <h2 id="faq-title">시작하기 전에 확인하세요.</h2>
        <div className="faq-list">
          {faqItems.map(([question, answer], index) => {
            const expanded = open === index;
            const panelId = `landing-faq-panel-${index}`;
            return (
              <article key={question}>
                <h3>
                  <button
                    type="button"
                    aria-controls={panelId}
                    aria-expanded={expanded}
                    onClick={() => setOpen(expanded ? null : index)}
                  >
                    {question}<span aria-hidden="true">{expanded ? '−' : '+'}</span>
                  </button>
                </h3>
                <div id={panelId} className={expanded ? 'is-open' : ''}><p>{answer}</p></div>
              </article>
            );
          })}
        </div>
      </div>
    </section>
  );
}

function DemoSection({ reducedMotion }) {
  return (
    <section id="demo" className="landing-section landing-demo" aria-labelledby="demo-title">
      <div className="landing-container">
        <p className="landing-eyebrow">JOURNEY PREVIEW</p>
        <h2 id="demo-title" tabIndex="-1">서비스 흐름을 먼저 살펴보세요.</h2>
        <p className="landing-section__lede">
          아래 화면은 전체 파이프라인의 상호작용을 설명하는 가상 예시입니다. 실제 프로젝트에서는 로그인 후
          Provider 실행 결과와 사용자 선택이 저장됩니다.
        </p>
        <DemoSimulator reducedMotion={reducedMotion} />
        <p className="demo-disclaimer">예시 데이터는 실제 고객 반응, 법률 판단 또는 사업 성과 예측이 아닙니다.</p>
      </div>
    </section>
  );
}

function FinalCta() {
  return (
    <section className="landing-final-cta" aria-labelledby="cta-title">
      <div className="landing-container">
        <h2 id="cta-title">사업 아이디어를<br />검토 가능한 여정으로 만드세요.</h2>
        <p>프로젝트를 만들고 아이디어를 입력하면 현재 단계와 다음 작업을 한 화면에서 확인할 수 있습니다.</p>
        <div className="landing-actions">
          <Link className="landing-button" to="/auth/signup" state={{ authTransition: true, source: 'landing', intent: 'signup' }}>프로젝트 시작하기</Link>
          <Link className="landing-button landing-button--ghost" to="/auth/login" state={{ authTransition: true, source: 'landing', intent: 'login' }}>로그인</Link>
        </div>
      </div>
    </section>
  );
}

export default function LandingPage() {
  const location = useLocation();
  const routerNavigate = useNavigate();
  const ids = useMemo(() => navItems.map(([id]) => id), []);
  const activeId = useScrollSpy(ids);
  const reducedMotion = useReducedMotion();
  const skipFromInternalRoute = location.state?.skipLandingIntro === true;
  const intro = useLandingIntro(reducedMotion, { skipFromInternalRoute });
  const navigate = useCallback(
    (id, options = {}) => scrollToSection(id, reducedMotion, options.focus),
    [reducedMotion],
  );

  useEffect(() => {
    document.documentElement.classList.toggle('landing-scroll-snap', !reducedMotion);
    return () => document.documentElement.classList.remove('landing-scroll-snap');
  }, [reducedMotion]);

  useEffect(() => {
    if (!skipFromInternalRoute) return;
    const nextState = { ...location.state };
    delete nextState.skipLandingIntro;
    routerNavigate(`${location.pathname}${location.hash}`, {
      replace: true,
      state: Object.keys(nextState).length ? nextState : null,
    });
  }, [location.hash, location.pathname, location.state, routerNavigate, skipFromInternalRoute]);

  const interactive = intro.state === 'settling' || intro.complete;
  useSectionScrollProgress({ enabled: interactive, reducedMotion });

  return (
    <div className="landing-page">
      <LandingBootIntro onSkip={intro.skip} reducedMotion={reducedMotion} state={intro.state} />
      <div className={`landing-page__content is-${intro.state}`} inert={interactive ? undefined : true}>
        <LandingHeader activeId={activeId} onNavigate={navigate} />
        <HeroSection introState={intro.state} reducedMotion={reducedMotion} onNavigate={navigate} />
        <IntroSection />
        <WorkflowSection onNavigate={navigate} />
        <FeatureSection />
        <TrustAndOutcome />
        <FaqSection />
        <DemoSection reducedMotion={reducedMotion} />
        <FinalCta />
        <LandingFooter onNavigate={navigate} />
      </div>
    </div>
  );
}
