import { Link, Navigate, Outlet, useLocation } from 'react-router-dom';

import { LoadingState } from '../../shared/ui/index.js';
import { useAuth } from './AuthProvider.jsx';
import { AUTH_STATUS } from './authSession.js';

export default function AdminRoute() {
  const { status, isAdmin } = useAuth();
  const location = useLocation();
  if (status === AUTH_STATUS.UNKNOWN || status === AUTH_STATUS.REFRESHING) return <LoadingState label="관리자 권한을 확인하고 있습니다." />;
  if (status === AUTH_STATUS.UNAUTHENTICATED) return <Navigate to="/auth/login" replace state={{ returnTo: `${location.pathname}${location.search}` }} />;
  if (!isAdmin) return <main className="admin-forbidden"><p>403</p><h1>관리자 권한이 필요합니다.</h1><span>이 계정에는 관리자 콘솔 접근 권한이 없습니다.</span><Link to="/app">사용자 워크스페이스로 돌아가기</Link></main>;
  return <Outlet />;
}
