import {
  useEffect,
  useRef,
  useState,
} from 'react';

import {
  Link,
  NavLink,
  useNavigate,
} from 'react-router-dom';

import {
  useAuth,
} from './auth/AuthContext.jsx';

import './Head.css';

/* =========================================================
   공개 영역 공통 헤더
========================================================= */

function Head() {
  const navigate = useNavigate();

  const {
    user,
    isAuthenticated,
    isAdmin,
    logout,
  } = useAuth();

  const userMenuRef = useRef(null);

  const [isUserMenuOpen, setIsUserMenuOpen] =
    useState(false);

  /* =======================================================
     메뉴 외부 클릭 시 사용자 메뉴 닫기
  ======================================================= */

  useEffect(() => {
    const handleOutsideClick = (event) => {
      if (
        userMenuRef.current &&
        !userMenuRef.current.contains(
          event.target,
        )
      ) {
        setIsUserMenuOpen(false);
      }
    };

    document.addEventListener(
      'mousedown',
      handleOutsideClick,
    );

    return () => {
      document.removeEventListener(
        'mousedown',
        handleOutsideClick,
      );
    };
  }, []);

  /* =======================================================
     로그아웃
  ======================================================= */

  const handleLogout = () => {
    setIsUserMenuOpen(false);

    logout();

    navigate('/', {
      replace: true,
    });
  };

  /* =======================================================
     사용자 기본 작업 화면 이동
  ======================================================= */

  const handleWorkspaceMove = () => {
    setIsUserMenuOpen(false);

    navigate('/dashboard');
  };

  const handleAdminMove = () => {
    setIsUserMenuOpen(false);

    navigate('/admin');
  };

  return (
    <header className="header">
      <div className="logo-group">
        <Link
          to="/"
          className="logo-text"
        >
          페르소나 플랫폼
        </Link>
      </div>

      <nav
        className="nav"
        aria-label="공개 메뉴"
      >
        <NavLink
          to="/service"
          className={({ isActive }) =>
            isActive
              ? 'nav-link active'
              : 'nav-link'
          }
        >
          서비스 이용
        </NavLink>

        <NavLink
          to="/contact"
          className={({ isActive }) =>
            isActive
              ? 'nav-link active'
              : 'nav-link'
          }
        >
          고객 문의
        </NavLink>

        {isAuthenticated && (
          <NavLink
            to="/dashboard"
            className={({ isActive }) =>
              isActive
                ? 'nav-link active'
                : 'nav-link'
            }
          >
            대시보드
          </NavLink>
        )}

        {isAdmin && (
          <NavLink
            to="/admin"
            className={({ isActive }) =>
              isActive
                ? 'nav-link active'
                : 'nav-link'
            }
          >
            관리자 페이지
          </NavLink>
        )}
      </nav>

      <div className="user-section">
        {!isAuthenticated ? (
          <Link
            to="/login"
            className="login-button"
            aria-label="로그인"
          >
            <UserIcon />

            <span className="login-button-text">
              로그인
            </span>
          </Link>
        ) : (
          <div
            className="header-user-menu"
            ref={userMenuRef}
          >
            <button
              type="button"
              className="header-user-trigger"
              aria-expanded={isUserMenuOpen}
              aria-haspopup="menu"
              onClick={() =>
                setIsUserMenuOpen(
                  (previous) => !previous,
                )
              }
            >
              <span className="header-user-avatar">
                {user?.name?.slice(0, 1) ||
                  'U'}
              </span>

              <span className="header-user-name">
                {user?.name || '사용자'}님
              </span>

              <span
                className={
                  isUserMenuOpen
                    ? 'header-user-arrow open'
                    : 'header-user-arrow'
                }
                aria-hidden="true"
              >
                ▾
              </span>
            </button>

            {isUserMenuOpen && (
              <div
                className="header-user-dropdown"
                role="menu"
              >
                <div className="header-user-info">
                  <strong>
                    {user?.name || '사용자'}
                  </strong>

                  <span>{user?.email}</span>

                  <small>
                    {isAdmin
                      ? '관리자 계정'
                      : '일반 사용자'}
                  </small>
                </div>

                <div className="header-user-divider" />

                <button
                  type="button"
                  role="menuitem"
                  onClick={handleWorkspaceMove}
                >
                  사용자 대시보드
                </button>

                {isAdmin && (
                  <button
                    type="button"
                    role="menuitem"
                    onClick={handleAdminMove}
                  >
                    관리자 페이지
                  </button>
                )}

                <div className="header-user-divider" />

                <button
                  type="button"
                  role="menuitem"
                  className="header-logout-menu"
                  onClick={handleLogout}
                >
                  로그아웃
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </header>
  );
}

/* =========================================================
   사용자 아이콘
========================================================= */

function UserIcon() {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </svg>
  );
}

export default Head;
