import {
  useEffect,
} from 'react';

import {
  useLocation,
  useNavigate,
} from 'react-router-dom';

import {
  useAuth,
} from '../auth/AuthContext.jsx';

import './Dashboard.css';

/* =========================================================
   데모 대시보드 데이터

   추후 API 응답으로 교체한다.
========================================================= */

const DASHBOARD_SUMMARY = [
  {
    id: 'active-projects',
    label: '진행 중 프로젝트',
    value: '3개',
  },
  {
    id: 'completed-analysis',
    label: '완료된 분석',
    value: '12개',
  },
  {
    id: 'drafts',
    label: '임시 저장',
    value: '2개',
  },
  {
    id: 'latest-analysis',
    label: '최근 분석',
    value: '2026.05.23',
  },
];

const RECENT_PROJECTS = [
  {
    id: 1,
    name: 'AI 학습 플랫폼 사업',
    currentStage: '재무 분석',
    updatedAt: '2024.05.20',
    status: '완료',
  },
  {
    id: 2,
    name: '스마트홈 기기 사업',
    currentStage: '가상 시장검증',
    updatedAt: '2024.09.19',
    status: '완료',
  },
  {
    id: 3,
    name: '친환경 소재 사업',
    currentStage: '보고서 생성',
    updatedAt: '2025.06.18',
    status: '진행 중',
  },
  {
    id: 4,
    name: '모바일 헬스케어 서비스',
    currentStage: 'BM 분석',
    updatedAt: '2026.05.23',
    status: '진행 중',
  },
];

const QUICK_ACTIONS = [
  {
    id: 'new-project',
    label: '새 프로젝트 생성',
    path: '/project-create',
  },
  {
    id: 'project-plan',
    label: '기획서 등록',
    path: '/project-create',
  },
  {
    id: 'marketing',
    label: '마케팅 콘텐츠 제작',
    path: '/virtual-market',
    state: {
      section: 'marketing-content',
    },
  },
];

/* =========================================================
   사용자 대시보드
========================================================= */

function Dashboard() {
  const navigate = useNavigate();
  const location = useLocation();

  const {
    user,
  } = useAuth();

  const accessDenied =
    location.state?.accessDenied === true;

  const signupCompleted =
    location.state?.signupCompleted === true;

  useEffect(() => {
    if (!accessDenied && !signupCompleted) {
      return undefined;
    }

    const timerId = window.setTimeout(() => {
      navigate(location.pathname, {
        replace: true,
        state: null,
      });
    }, 4000);

    return () => {
      window.clearTimeout(timerId);
    };
  }, [accessDenied, location.pathname, navigate, signupCompleted]);

  const handleProjectClick = (project) => {
    navigate('/business-analysis', {
      state: {
        projectId: project.id,
      },
    });
  };

  const handleQuickAction = (action) => {
    navigate(action.path, {
      state: action.state,
    });
  };

  return (
    <section className="dashboard-page">
      {accessDenied && (
        <div
          className="dashboard-access-message"
          role="alert"
        >
          관리자 권한이 필요한 페이지입니다.
          사용자 대시보드로 이동했습니다.
        </div>
      )}

      {signupCompleted && (
        <div
          className="dashboard-access-message dashboard-signup-message"
          role="status"
        >
          회원가입이 완료되었습니다. 자동으로 로그인했습니다.
        </div>
      )}

      <header className="dashboard-header">
        <span className="dashboard-title-chip">
          대시보드
        </span>

        <h1>
          안녕하세요, {user?.name || '사용자'}님!
        </h1>
      </header>

      <section
        className="dashboard-summary-grid"
        aria-label="프로젝트 요약"
      >
        {DASHBOARD_SUMMARY.map(
          (summary) => (
            <article
              className="dashboard-summary-card"
              key={summary.id}
            >
              <span>{summary.label}</span>
              <strong>{summary.value}</strong>
            </article>
          ),
        )}
      </section>

      <div className="dashboard-main-grid">
        <section className="dashboard-project-section">
          <div className="dashboard-section-header">
            <h2>최근 프로젝트</h2>

            <button
              type="button"
              onClick={() =>
                navigate('/project-create')
              }
            >
              새 프로젝트
            </button>
          </div>

          <div className="dashboard-project-table-wrap">
            <table className="dashboard-project-table">
              <thead>
                <tr>
                  <th>프로젝트명</th>
                  <th>진행 단계</th>
                  <th>최종 업데이트</th>
                  <th>상태</th>
                </tr>
              </thead>

              <tbody>
                {RECENT_PROJECTS.map(
                  (project) => (
                    <tr
                      key={project.id}
                      onClick={() =>
                        handleProjectClick(
                          project,
                        )
                      }
                    >
                      <td>{project.name}</td>
                      <td>
                        {project.currentStage}
                      </td>
                      <td>
                        {project.updatedAt}
                      </td>
                      <td>
                        <span
                          className={
                            project.status ===
                            '완료'
                              ? 'dashboard-status completed'
                              : 'dashboard-status progressing'
                          }
                        >
                          {project.status}
                        </span>
                      </td>
                    </tr>
                  ),
                )}
              </tbody>
            </table>
          </div>
        </section>

        <aside className="dashboard-quick-section">
          <h2>바로가기</h2>

          <div className="dashboard-quick-list">
            {QUICK_ACTIONS.map(
              (action) => (
                <button
                  type="button"
                  key={action.id}
                  onClick={() =>
                    handleQuickAction(action)
                  }
                >
                  <span aria-hidden="true">
                    ✦
                  </span>

                  {action.label}
                </button>
              ),
            )}
          </div>
        </aside>
      </div>
    </section>
  );
}

export default Dashboard;
