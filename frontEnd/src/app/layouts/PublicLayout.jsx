import { Link, Outlet, useLocation } from 'react-router-dom';

import './layouts.css';

export default function PublicLayout() {
  const { pathname } = useLocation();
  const isLanding = pathname === '/';
  const isAuth = pathname.startsWith('/auth/');

  return (
    <div className={`public-shell${isLanding ? ' public-shell--landing' : ''}${isAuth ? ' public-shell--auth' : ''}`}>
      <a className="skip-link" href="#main-content">본문으로 바로가기</a>
      {!isLanding && !isAuth && (
        <header className="public-header">
          <Link className="app-brand" to="/">
            <span className="app-brand__mark" aria-hidden="true">V</span>
            <span>사업검증 플랫폼</span>
          </Link>
          <nav aria-label="사용자 메뉴">
            <Link to="/auth/login">로그인</Link>
          </nav>
        </header>
      )}
      <main id="main-content" className="public-main" tabIndex="-1">
        <Outlet />
      </main>
      {!isLanding && !isAuth && (
        <footer className="public-footer">
          <small>AI 분석 결과는 의사결정을 돕는 참고 정보입니다.</small>
        </footer>
      )}
      <div className="ui-toast-region" aria-live="polite" />
    </div>
  );
}
