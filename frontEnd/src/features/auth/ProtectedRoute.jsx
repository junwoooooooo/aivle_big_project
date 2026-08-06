import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { LoadingState } from '../../shared/ui/index.js';
import { useAuth } from './AuthProvider.jsx';
import { AUTH_STATUS } from './authSession.js';

export default function ProtectedRoute() {
  const location = useLocation();
  const { status } = useAuth();

  if (status === AUTH_STATUS.UNKNOWN || status === AUTH_STATUS.REFRESHING) {
    return <LoadingState label="로그인 상태를 확인하고 있습니다" />;
  }
  if (status === AUTH_STATUS.UNAUTHENTICATED) {
    return (
      <Navigate
        to="/auth/login"
        replace
        state={{ returnTo: `${location.pathname}${location.search}` }}
      />
    );
  }
  return <Outlet />;
}

