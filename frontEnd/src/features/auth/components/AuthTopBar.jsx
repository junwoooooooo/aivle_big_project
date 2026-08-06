import { Link } from 'react-router-dom';
import { buildAuthSwitchState } from '../authTransition.js';

export default function AuthTopBar({ mode }) {
  const signup = mode === 'signup';
  const target = signup ? 'login' : 'signup';
  return <header className="auth-topbar"><Link className="auth-topbar__home-link" to="/" state={{ skipLandingIntro: true, source: 'auth' }} aria-label="Venture Verify 서비스 소개로 돌아가기"><span className="auth-topbar__brand-mark" aria-hidden="true">V</span><span className="auth-topbar__brand-copy"><strong>Venture Verify</strong><small>서비스 소개로 돌아가기</small></span><span className="auth-topbar__home-icon" aria-hidden="true">↗</span></Link><p>{signup ? '이미 계정이 있나요?' : '계정이 없나요?'} <Link to={`/auth/${target}`} state={buildAuthSwitchState(target)}>{signup ? '로그인' : '무료로 시작하기'}</Link></p></header>;
}
