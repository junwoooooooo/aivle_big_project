import {
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from 'react-router-dom';

import Head from './Head';
import './App.css';

import ProtectedRoute
  from './auth/ProtectedRoute.jsx';

import AdminRoute
  from './auth/AdminRoute.jsx';

import UserWorkspaceLayout
  from './layout/UserWorkspaceLayout.jsx';

import ContactPage
  from './page/ContactPage.jsx';

import ServicePage
  from './page/ServicePage.jsx';

import Login
  from './page/Login.jsx';

import SignupPage
  from './page/SignupPage.jsx';

import Dashboard
  from './page/Dashboard.jsx';

import ProjectCreate
  from './page/ProjectCreate.jsx';

import LegalCheckPage
  from './page/LegalCheckPage.jsx';

import BusinessAnalysis
  from './page/BusinessAnalysis.jsx';

import VirtualMarket
  from './page/VirtualMarket.jsx';

import Admin
  from './page/Admin.jsx';

import {
  useAuth,
} from './auth/AuthContext.jsx';


/* =========================================================
   공개 홈
========================================================= */

function Home() {
  const navigate = useNavigate();

  const {
    isAuthenticated,
    isAdmin,
  } = useAuth();

  const handleStart = () => {
    if (!isAuthenticated) {
      navigate('/login');
      return;
    }

    if (isAdmin) {
      navigate('/admin');
      return;
    }

    navigate('/dashboard');
  };

  const handleProjectCreate = () => {
    if (!isAuthenticated) {
      navigate('/login', {
        state: {
          from: {
            pathname: '/project-create',
          },
        },
      });
      return;
    }

    navigate('/project-create');
  };

  return (
    <main className="main-content">
      <div className="hero-card">
        <span className="hero-label">
          AI 기반 가상 시장 검증
        </span>

        <h2 className="hero-title">
          유저가 없어도,
          <br />
          시장에 물어볼 수 있습니다
        </h2>

        <p className="hero-description">
          출시 전 아이디어를 가상 고객 수백 명에게
          미리 테스트하세요.
          <br />
          컨셉, 가격, 기능에 대한 시장 반응을
          몇 분 안에 확인할 수 있습니다.
        </p>

        <div className="hero-actions">
          <button
            type="button"
            className="hero-primary-button"
            onClick={handleStart}
          >
            {!isAuthenticated
              ? '로그인하고 시작하기'
              : isAdmin
                ? '관리자 페이지로 이동'
                : '대시보드로 이동'}
          </button>

          <button
            type="button"
            className="hero-secondary-button"
            onClick={handleProjectCreate}
          >
            기획서 등록하기
          </button>
        </div>
      </div>
    </main>
  );
}

/* =========================================================
   404
========================================================= */

function NotFound() {
  return (
    <main className="main-content">
      <div className="hero-card">
        <h2 className="hero-title">
          페이지를 찾을 수 없습니다.
        </h2>

        <a href="/">
          홈으로 돌아가기
        </a>
      </div>
    </main>
  );
}

/* =========================================================
   애플리케이션
========================================================= */

function App() {
  const location = useLocation();

  const isAdminPage =
    location.pathname.startsWith('/admin');

  const isWorkspacePage = [
    '/dashboard',
    '/project-create',
    '/legal-check',
    '/business-analysis',
    '/virtual-market',
  ].some((path) =>
    location.pathname.startsWith(path),
  );

  /*
   * 관리자 페이지와 사용자 작업영역은
   * 각각 독립적인 레이아웃을 사용하므로
   * 공개용 Head를 출력하지 않는다.
   */

  const shouldShowPublicHeader =
    !isAdminPage && !isWorkspacePage;

  return (
    <div className="container">
      {shouldShowPublicHeader && <Head />}

      <Routes>
        <Route
          path="/"
          element={<Home />}
        />

        <Route
          path="/login"
          element={<Login />}
        />

        <Route
          path="/signup"
          element={<SignupPage />}
        />

        <Route
          path="/service"
          element={<ServicePage />}
        />

        <Route
          path="/contact"
          element={<ContactPage />}
        />

        <Route element={<ProtectedRoute />}>
          <Route
            element={
              <UserWorkspaceLayout />
            }
          >
            <Route
              path="/dashboard"
              element={<Dashboard />}
            />

            <Route
              path="/project-create"
              element={<ProjectCreate />}
            />

            <Route
              path="/legal-check"
              element={<LegalCheckPage />}
            />

            <Route
              path="/business-analysis"
              element={<BusinessAnalysis />}
            />

            <Route
              path="/virtual-market"
              element={<VirtualMarket />}
            />
          </Route>
        </Route>

        <Route element={<AdminRoute />}>
          <Route
            path="/admin"
            element={<Admin />}
          />
        </Route>

        <Route
          path="/marketing"
          element={
            <Navigate
              to="/virtual-market"
              replace
            />
          }
        />

        <Route
          path="*"
          element={<NotFound />}
        />
      </Routes>
    </div>
  );
}

export default App;
