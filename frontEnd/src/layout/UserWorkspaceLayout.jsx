import {
  NavLink,
  Outlet,
  useNavigate,
} from 'react-router-dom';

import {
  useAuth,
} from '../auth/AuthContext.jsx';

import './UserWorkspaceLayout.css';

/* =========================================================
   로그인 사용자 공통 작업영역 레이아웃
========================================================= */

function UserWorkspaceLayout() {
  const navigate = useNavigate();

  const {
    user,
    logout,
    isAdmin,
  } = useAuth();

  const handleLogout = () => {
    logout();

    navigate('/', {
      replace: true,
    });
  };

  const getMenuClassName = ({
    isActive,
  }) => {
    return isActive
      ? 'workspace-menu-link active'
      : 'workspace-menu-link';
  };

  return (
    <div className="workspace-layout">
      <aside className="workspace-sidebar">
        <button
          type="button"
          className="workspace-home-button"
          onClick={() => navigate('/')}
          aria-label="공개 홈으로 이동"
        >
          <span aria-hidden="true">⌂</span>
          <span>홈</span>
        </button>

        <nav
          className="workspace-menu"
          aria-label="사용자 작업 메뉴"
        >
          <NavLink
            to="/dashboard"
            className={getMenuClassName}
          >
            대시보드
          </NavLink>

          <NavLink
            to="/project-create"
            className={getMenuClassName}
          >
            기획서 등록
          </NavLink>

          <NavLink
            to="/legal-check"
            className={getMenuClassName}
          >
            법률/규제 검토
          </NavLink>

          <NavLink
            to="/business-analysis"
            className={getMenuClassName}
          >
            사업성 분석
          </NavLink>

          <NavLink
            to="/virtual-market"
            className={getMenuClassName}
          >
            가상 시장 검증
          </NavLink>

        </nav>

        <div className="workspace-sidebar-footer">
          <div className="workspace-user-summary">
            <div className="workspace-user-avatar">
              {user?.name?.slice(0, 1) || 'U'}
            </div>

            <div>
              <strong>{user?.name || '사용자'}</strong>

              <span>{user?.email}</span>
            </div>
          </div>

          {isAdmin && (
            <button
              type="button"
              className="workspace-admin-button"
              onClick={() =>
                navigate('/admin')
              }
            >
              관리자 페이지
            </button>
          )}

          <button
            type="button"
            className="workspace-logout-button"
            onClick={handleLogout}
          >
            로그아웃
          </button>
        </div>
      </aside>

      <main className="workspace-content">
        <Outlet />
      </main>
    </div>
  );
}

export default UserWorkspaceLayout;
