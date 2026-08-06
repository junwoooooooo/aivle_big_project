import { useState } from 'react';
import useReducedMotion from '../../landing/hooks/useReducedMotion.js';
import { authBrandScenes } from '../data/authBrandScenes.js';
import useAuthBrandCycle from '../hooks/useAuthBrandCycle.js';
import useBrandCopyTyping from '../hooks/useBrandCopyTyping.js';
import useAuthMotion from '../hooks/useAuthMotion.js';

const copy = {
  login: {
    eyebrow: 'AI 기반 사업 아이디어 검증',
    title: ['사업 아이디어를', '검증 가능한 계획으로 바꿉니다.'],
    body: ['문서에서 핵심 정보를 구조화하고,', '근거와 위험을 확인한 뒤 다음 행동을 정리합니다.'],
  },
  signup: {
    eyebrow: 'START YOUR VALIDATION',
    title: ['사업 아이디어를', '검증 가능한 계획으로 바꿉니다.'],
    body: ['문서에서 핵심 정보를 구조화하고,', '근거와 위험을 확인한 뒤 다음 행동을 정리합니다.'],
  },
};

function TypingCursor({ active, name }) {
  const visibleFor = {
    TYPE_TITLE_FIRST: ['TYPE_TITLE_FIRST', 'PAUSE_TITLE_FIRST'],
    TYPE_TITLE_SECOND: ['TYPE_TITLE_SECOND', 'PAUSE_TITLE_COMPLETE'],
    TYPE_BODY_FIRST: ['TYPE_BODY_FIRST', 'PAUSE_BODY_FIRST'],
    TYPE_BODY_SECOND: ['TYPE_BODY_SECOND', 'HOLD_COMPLETE'],
  };
  return visibleFor[name]?.includes(active) ? <i className="auth-brand-copy__cursor" aria-hidden="true" /> : null;
}

export default function AuthBrandPanel({ mode }) {
  const reducedMotion = useReducedMotion();
  const { motionReady } = useAuthMotion();
  const [previewPaused, setPreviewPaused] = useState(false);
  const content = copy[mode];
  const typed = useBrandCopyTyping([...content.title, ...content.body], { enabled: motionReady, reducedMotion });
  const { sceneIndex, setSceneIndex } = useAuthBrandCycle({ enabled: motionReady, paused: previewPaused, reducedMotion, sceneCount: authBrandScenes.length });
  const scene = authBrandScenes[sceneIndex];
  const resumePreviewOnBlur = (event) => { if (!event.currentTarget.contains(event.relatedTarget)) setPreviewPaused(false); };

  return <aside className="auth-brand-panel">
    <p className="auth-brand-panel__eyebrow">{content.eyebrow}</p>
    <div className={`auth-brand-copy${typed.fading ? ' is-fading' : ''}`} aria-hidden="true">
      <section className="auth-brand-copy__title-block"><div className="auth-brand-copy__reserve"><span>{content.title[0]}</span><span>{content.title[1]}</span></div><h2 className="auth-brand-copy__animated"><span>{typed.values[0]}<TypingCursor active={typed.step} name="TYPE_TITLE_FIRST" /></span><span>{typed.values[1]}<TypingCursor active={typed.step} name="TYPE_TITLE_SECOND" /></span></h2></section>
      <section className="auth-brand-copy__body-block"><div className="auth-brand-copy__reserve"><span>{content.body[0]}</span><span>{content.body[1]}</span></div><p className="auth-brand-copy__animated"><span>{typed.values[2]}<TypingCursor active={typed.step} name="TYPE_BODY_FIRST" /></span><span>{typed.values[3]}<TypingCursor active={typed.step} name="TYPE_BODY_SECOND" /></span></p></section>
    </div>
    <p className="visually-hidden">사업 아이디어를 검증 가능한 계획으로 바꾸고, 문서의 핵심 정보와 근거, 위험, 다음 행동을 정리합니다.</p>
    <ol className="auth-brand-panel__flow"><li><b>01</b><span>문서 구조화</span></li><li><b>02</b><span>근거와 위험 확인</span></li><li><b>03</b><span>다음 행동 정리</span></li></ol>
    <section className="auth-preview" aria-label="제품 미리보기 예시" onMouseEnter={() => setPreviewPaused(true)} onMouseLeave={() => setPreviewPaused(false)} onFocusCapture={() => setPreviewPaused(true)} onBlurCapture={resumePreviewOnBlur}>
      <header className="auth-preview__header"><span>사업 검증 프로젝트</span><b>{scene.title}</b></header>
      <div className="auth-preview__viewport"><div className="auth-preview__scene" key={scene.id}>{scene.lines.map(([label, value]) => <p key={label}><span>{label}</span><b>{value}</b></p>)}</div></div>
      <footer className="auth-preview__footer"><div className="auth-preview__dots" aria-label="제품 미리보기 화면 선택">{authBrandScenes.map((item, index) => <button type="button" key={item.id} aria-label={`${index + 1}번째 화면: ${item.title}`} aria-pressed={sceneIndex === index} className={sceneIndex === index ? 'is-active' : ''} disabled={!motionReady} onClick={() => setSceneIndex(index)} />)}</div><small>가상 예시 데이터</small></footer>
    </section>
  </aside>;
}
