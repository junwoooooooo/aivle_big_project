import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';

import { useAuth } from '../../features/auth/AuthProvider.jsx';
import { appRoutes } from '../routing/projectRoutes.js';
import { AppIcon, Drawer } from '../../shared/ui/index.js';
import { useAuthTransition } from '../transitions/AuthTransitionProvider.jsx';
import './admin-layout.css';

const navigation = [
  { to: '/admin', label: 'Overview', icon: 'home', end: true },
  { to: '/admin/users', label: 'Users', icon: 'user' },
  { to: '/admin/projects', label: 'Projects', icon: 'project' },
  { to: '/admin/audit', label: 'Audit', icon: 'clock' },
  { to: '/admin/settings', label: 'Settings', icon: 'settings' },
  { to: '/admin/operations', label: 'Operations', icon: 'alert' },
  { to: '/admin/jobs', label: 'AI Jobs', icon: 'sparkles' },
];

function userLabel(user) {
  return user?.displayName || user?.username || '관리자';
}

function environmentLabel() {
  const configured = import.meta.env.VITE_APP_ENVIRONMENT?.trim();
  if (configured) return configured;
  if (import.meta.env.MODE === 'production') return 'Production';
  if (import.meta.env.MODE === 'development') return 'Development';
  return null;
}

function breadcrumbs(pathname) {
  const exact = pathname === '/admin';
  const match = exact
    ? navigation[0]
    : navigation.find((item) => item.to !== '/admin' && pathname.startsWith(item.to));
  const items = [{ label: 'Admin', to: '/admin' }];
  if (!match) return items;
  items.push({ label: match.label, to: match.to });
  if (pathname !== match.to) {
    const detailLabel = match.to === '/admin/users'
      ? '사용자 상세'
      : match.to === '/admin/projects'
        ? '프로젝트 상세'
        : match.to === '/admin/audit'
          ? '감사 상세'
          : null;
    if (detailLabel) items.push({ label: detailLabel });
  }
  return items;
}

function AdminNavigation({ onNavigate }) {
  return (
    <nav className="admin-navigation" aria-label="관리자 메뉴">
      {navigation.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          onClick={onNavigate}
        >
          <AppIcon name={item.icon} />
          <span>{item.label}</span>
        </NavLink>
      ))}
    </nav>
  );
}

function AdminAccountLinks({ onNavigate, onLogout, firstItemRef }) {
  return (
    <>
      <div className="admin-account-menu__identity">
        <strong>관리자 계정</strong>
        <span>ADMIN 권한으로 운영 중입니다.</span>
      </div>
      <Link ref={firstItemRef} to={appRoutes.profileSettings} role="menuitem" onClick={onNavigate}>
        <AppIcon name="user" />
        프로필 설정
      </Link>
      <Link to={appRoutes.securitySettings} role="menuitem" onClick={onNavigate}>
        <AppIcon name="lock" />
        보안 설정
      </Link>
      <Link to={appRoutes.home} role="menuitem" onClick={onNavigate}>
        <AppIcon name="home" />
        사용자 워크스페이스
      </Link>
      <button type="button" role="menuitem" onClick={onLogout}>
        <AppIcon name="chevronLeft" />
        로그아웃
      </button>
    </>
  );
}

export default function AdminShell() {
  const { user, logout } = useAuth();
  const { start, isTransitioning } = useAuthTransition();
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const accountButtonRef = useRef(null);
  const accountMenuRef = useRef(null);
  const firstAccountItemRef = useRef(null);
  const environment = environmentLabel();
  const currentBreadcrumbs = breadcrumbs(location.pathname);

  const closeAccount = useCallback((restoreFocus = true) => {
    setAccountOpen(false);
    if (restoreFocus) {
      window.requestAnimationFrame(() => accountButtonRef.current?.focus());
    }
  }, []);

  useEffect(() => {
    if (!accountOpen) return undefined;
    firstAccountItemRef.current?.focus();
    function handlePointerDown(event) {
      if (
        !accountMenuRef.current?.contains(event.target)
        && !accountButtonRef.current?.contains(event.target)
      ) {
        closeAccount(false);
      }
    }
    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeAccount();
      }
    }
    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [accountOpen, closeAccount]);

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      setAccountOpen(false);
      setMobileOpen(false);
    }, 0);
    return () => window.clearTimeout(timeout);
  }, [location.pathname]);

  async function handleLogout() {
    setAccountOpen(false);
    setMobileOpen(false);
    await start({
      destination: '/auth/login',
      message: '안전하게 로그아웃하고 있습니다.',
      onCovered: async () => {
        window.sessionStorage.removeItem('authReturnTo');
        window.sessionStorage.removeItem('lastProtectedRoute');
        await logout();
      },
    });
  }

  return (
    <div className="admin-shell">
      <a className="skip-link" href="#admin-content">본문으로 바로가기</a>
      <header className="admin-topbar">
        <button
          type="button"
          className="admin-mobile-menu-button"
          aria-label="관리자 메뉴 열기"
          aria-expanded={mobileOpen}
          onClick={() => {
            setAccountOpen(false);
            setMobileOpen(true);
          }}
        >
          <AppIcon name="more" />
        </button>
        <Link to="/admin" className="admin-brand">
          Venture Verify <b>Admin</b>
        </Link>
        {environment && <span className="admin-environment">{environment}</span>}
        <div className="admin-topbar__actions">
          <Link className="admin-workspace-link" to={appRoutes.home}>
            <AppIcon name="home" />
            <span>사용자 워크스페이스 보기</span>
          </Link>
          <div className="admin-account">
            <button
              ref={accountButtonRef}
              type="button"
              className="admin-account-trigger"
              aria-label="관리자 계정 메뉴 열기"
              aria-haspopup="menu"
              aria-expanded={accountOpen}
              aria-controls="admin-account-menu"
              onClick={() => {
                setMobileOpen(false);
                setAccountOpen((open) => !open);
              }}
            >
              <span className="admin-account-trigger__identity">
                <strong>{userLabel(user)}</strong>
                <small>@{user?.username || 'admin'}</small>
              </span>
              <span className="admin-role-badge">ADMIN</span>
              <AppIcon name="chevronRight" />
            </button>
            {accountOpen && (
              <div
                ref={accountMenuRef}
                id="admin-account-menu"
                className="admin-account-menu"
                role="menu"
                aria-label="관리자 계정 메뉴"
              >
                <AdminAccountLinks
                  firstItemRef={firstAccountItemRef}
                  onNavigate={() => closeAccount(false)}
                  onLogout={handleLogout}
                />
              </div>
            )}
          </div>
        </div>
      </header>

      <aside className="admin-sidebar">
        <AdminNavigation />
      </aside>

      <main id="admin-content" className="admin-content" tabIndex="-1">
        <nav className="admin-shell-breadcrumb" aria-label="현재 위치">
          {currentBreadcrumbs.map((item, index) => {
            const last = index === currentBreadcrumbs.length - 1;
            return (
              <span key={`${item.label}-${index}`}>
                {index > 0 && <span aria-hidden="true"> / </span>}
                {!last && item.to
                  ? <Link to={item.to}>{item.label}</Link>
                  : <span aria-current="page">{item.label}</span>}
              </span>
            );
          })}
        </nav>
        <Outlet />
      </main>

      <Drawer
        open={mobileOpen}
        onClose={() => setMobileOpen(false)}
        title="관리자 메뉴"
      >
        <AdminNavigation onNavigate={() => setMobileOpen(false)} />
        <div className="admin-drawer-account" role="menu" aria-label="관리자 계정 메뉴">
          <div className="admin-drawer-account__user">
            <strong>{userLabel(user)}</strong>
            <span>@{user?.username || 'admin'} · ADMIN</span>
          </div>
          <AdminAccountLinks
            onNavigate={() => setMobileOpen(false)}
            onLogout={handleLogout}
          />
        </div>
      </Drawer>
      {isTransitioning && <span className="visually-hidden" role="status">로그아웃 처리 중</span>}
    </div>
  );
}
