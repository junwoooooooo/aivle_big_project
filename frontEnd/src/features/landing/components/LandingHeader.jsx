import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { navItems } from '../data/landingData.js';

export default function LandingHeader({ activeId, onNavigate }) {
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const update = () => setScrolled(window.scrollY > 20);
    update();
    window.addEventListener('scroll', update, { passive: true });
    return () => window.removeEventListener('scroll', update);
  }, []);

  useEffect(() => {
    const onKey = (event) => { if (event.key === 'Escape') setOpen(false); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  useEffect(() => {
    document.body.classList.toggle('landing-menu-open', open);
    return () => document.body.classList.remove('landing-menu-open');
  }, [open]);

  const navigate = (id) => { onNavigate(id); setOpen(false); };
  return (
    <header className={`landing-header${scrolled ? ' is-scrolled' : ''}`}>
      <div className="landing-header__inner">
        <a className="landing-brand" href="#top" onClick={(event) => { event.preventDefault(); navigate('top'); }}><span aria-hidden="true">V</span>Venture Verify</a>
        <nav className={`landing-nav${open ? ' is-open' : ''}`} id="landing-navigation" aria-label="서비스 탐색">
          {navItems.map(([id, label]) => <button type="button" key={id} className={activeId === id ? 'is-active' : ''} aria-current={activeId === id ? 'true' : undefined} onClick={() => navigate(id)}>{label}</button>)}
          <Link className="landing-nav__mobile-login" to="/auth/login" state={{ authTransition: true, source: 'landing', intent: 'login' }} onClick={() => setOpen(false)}>로그인</Link>
        </nav>
        <div className="landing-header__actions">
          <Link className="landing-header__login-action" to="/auth/login" state={{ authTransition: true, source: 'landing', intent: 'login' }}>로그인</Link>
          <Link className="landing-button landing-button--small landing-header__primary-action" to="/auth/signup" state={{ authTransition: true, source: 'landing', intent: 'signup' }}>무료로 시작하기</Link>
        </div>
        <button className="landing-menu-button" type="button" aria-label="메뉴 열기" aria-expanded={open} aria-controls="landing-navigation" onClick={() => setOpen((value) => !value)}><span /><span /><span /></button>
      </div>
    </header>
  );
}
