import { Link } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';
import { heroSlides } from '../data/landingData.js';
import HeroProductWindow from './HeroProductWindow.jsx';

export default function HeroSection({ introState = 'completed', reducedMotion, onNavigate }) {
  const introPrepared = introState === 'settling' || introState === 'completed';
  const [entered, setEntered] = useState(reducedMotion && introPrepared);
  const [scene, setScene] = useState(0);
  const [paused, setPaused] = useState(false);
  const [attention, setAttention] = useState(false);
  const interval = useRef();

  useEffect(() => {
    if (!introPrepared || entered) return undefined;
    const frame = window.requestAnimationFrame(() => setEntered(true));
    return () => window.cancelAnimationFrame(frame);
  }, [entered, introPrepared]);

  useEffect(() => {
    if (introState !== 'completed' || paused || reducedMotion) return undefined;
    interval.current = window.setInterval(() => { if (!document.hidden) setScene((value) => (value + 1) % heroSlides.length); }, 5500);
    return () => window.clearInterval(interval.current);
  }, [introState, paused, reducedMotion]);

  useEffect(() => {
    if (!entered || introState !== 'completed' || reducedMotion) return undefined;
    const timer = window.setTimeout(() => setAttention(true), 1000);
    return () => window.clearTimeout(timer);
  }, [entered, introState, reducedMotion]);

  const slide = heroSlides[scene];
  return <section id="top" className={`landing-hero${entered ? ' is-entered' : ''}`} aria-labelledby="landing-title"><div className="landing-container landing-hero__grid"><div className="landing-hero__copy"><p className="landing-eyebrow">AI 기반 사업 검증 Journey</p><h1 id="landing-title"><span>아이디어에서,</span><span>실행 판단을 위한 보고서까지.</span></h1><p className="landing-lede">법률 사전 검토, 콘셉트 비교, 합성 Persona 인터뷰와 마케팅 전략을 하나의 저장 가능한 흐름에서 확인하세요.</p><div className="landing-actions"><Link className="landing-button" to="/auth/signup" state={{ authTransition: true, source: 'landing', intent: 'signup' }}>프로젝트 시작하기</Link><button className={`landing-demo-cta${attention ? ' is-attention' : ''}`} type="button" onClick={() => onNavigate('demo', { focus: true })}><span className="landing-demo-cta__icon" aria-hidden="true">▶</span>Journey 미리보기</button></div><p className="landing-hero__principles">사실과 가정 분리 · 단계별 사용자 확정 · 결과 자동 저장</p></div><div className="hero-story" onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)} onFocus={() => setPaused(true)} onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setPaused(false); }}><div className="hero-story__copy"><p>{slide.title}</p><span>{slide.description}</span></div><HeroProductWindow scene={scene} /><div className="hero-story__indicators" aria-label="제품 이야기 장면 선택">{heroSlides.map((item, index) => <button type="button" key={item.title} aria-label={`${index + 1}번째 장면: ${item.title}`} aria-pressed={scene === index} className={scene === index ? 'is-active' : ''} onClick={() => setScene(index)} />)}</div></div></div><button className="landing-scroll-cue" type="button" onClick={() => onNavigate('intro')}><span>전체 Journey 보기</span><i aria-hidden="true">↓</i></button></section>;
}
