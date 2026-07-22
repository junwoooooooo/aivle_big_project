import {
  Navigate,
  Outlet,
  useLocation,
} from 'react-router-dom';

import {
  useAuth,
} from './AuthContext.jsx';

/* =========================================================
   로그인 사용자 전용 라우트

   로그인하지 않은 사용자가 접근하면
   로그인 페이지로 이동시킨다.

   접근하려던 주소는 location.state.from으로 보관하여
   로그인 성공 후 기존 페이지로 복귀할 수 있도록 한다.
========================================================= */

function ProtectedRoute() {
  const location = useLocation();

  const {
    isAuthenticated,
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

  return <Outlet />;
}

export default ProtectedRoute;
