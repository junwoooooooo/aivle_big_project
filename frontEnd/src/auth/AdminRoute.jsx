import {
  Navigate,
  Outlet,
  useLocation,
} from 'react-router-dom';

import {
  useAuth,
} from './AuthContext.jsx';

/* =========================================================
   관리자 전용 라우트

   1. 로그인하지 않은 사용자
      → 로그인 페이지로 이동

   2. 로그인했지만 일반 사용자
      → 사용자 대시보드로 이동

   3. 관리자
      → 관리자 페이지 접근 허용
========================================================= */

function AdminRoute() {
  const location = useLocation();

  const {
    isAuthenticated,
    isAdmin,
  } = useAuth();

  if (!isAuthenticated) {
    return (
      <Navigate
        to="/login"
        replace
        state={{
          from: location,
        }}
      />
    );
  }

  if (!isAdmin) {
    return (
      <Navigate
        to="/dashboard"
        replace
        state={{
          accessDenied: true,
        }}
      />
    );
  }

  return <Outlet />;
}

export default AdminRoute;
