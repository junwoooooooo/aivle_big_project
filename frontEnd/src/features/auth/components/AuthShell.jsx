import AuthTopBar from './AuthTopBar.jsx';
import { useLocation } from 'react-router-dom';
import useReducedMotion from '../../landing/hooks/useReducedMotion.js';
import useAuthMotionPhase from '../hooks/useAuthMotionPhase.js';
import { AuthMotionProvider } from './AuthMotionContext.jsx';

export default function AuthShell({ children, mode }) {
  const location = useLocation();
  const reducedMotion = useReducedMotion();
  const source = location.state?.source;
  const transitioning = location.state?.authTransition === true && ['landing', 'logout', 'auth-switch', 'signup-complete'].includes(source);
  const { completeEntry, phase } = useAuthMotionPhase({ reducedMotion, transitioning });
  const onAnimationEnd = (event) => {
    if (!event.target.classList.contains('auth-card') || !event.animationName.startsWith('auth-card-')) return;
    completeEntry();
  };
  return <AuthMotionProvider value={{ motionReady: phase === 'ready', phase }}><section className={`auth-shell auth-shell--${mode} auth-shell--phase-${phase}${transitioning ? ' auth-shell--transitioning' : ''}${source ? ` auth-shell--from-${source}` : ''}`} onAnimationEnd={onAnimationEnd}><div className="auth-shell__background" aria-hidden="true" /><AuthTopBar mode={mode} /><div className="auth-shell__grid">{children}</div><p className="auth-shell__legal">AI 분석 결과는 법률·재무·투자 또는 전문가의 최종 판단을 대체하지 않습니다.</p></section></AuthMotionProvider>;
}
