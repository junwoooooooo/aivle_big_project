import {
  useEffect,
  useState,
} from 'react';

import {
  Link,
  useLocation,
  useNavigate,
} from 'react-router-dom';

import {
  USER_ROLE,
  useAuth,
} from '../auth/AuthContext.jsx';

/* =========================================================
   데모 계정

   화면 흐름 확인을 위한 정보다.
   실제 백엔드 연동 시 제거한다.
========================================================= */

const DEMO_ACCOUNT = {
  USER: {
    email: 'user@test.com',
    password: 'user1234',
  },

  ADMIN: {
    email: 'admin@test.com',
    password: 'admin1234',
  },
};

/* =========================================================
   로그인 페이지
========================================================= */

function Login() {
  const navigate = useNavigate();
  const location = useLocation();

  const {
    user,
    isAuthenticated,
    login,
  } = useAuth();

  const [email, setEmail] =
    useState('');

  const [password, setPassword] =
    useState('');

  const [rememberMe, setRememberMe] =
    useState(false);

  const [errorMessage, setErrorMessage] =
    useState('');

  const [isSubmitting, setIsSubmitting] =
    useState(false);

  /* =======================================================
     로그인 전 접근하려던 경로

     ProtectedRoute 구현 이후 사용된다.
  ======================================================= */

  const requestedPath =
    location.state?.from?.pathname;

  /* =======================================================
     이미 로그인한 사용자의 로그인 페이지 접근 처리
  ======================================================= */

  useEffect(() => {
    if (!isAuthenticated || !user) {
      return;
    }

    const destination =
      user.role === USER_ROLE.ADMIN
        ? '/admin'
        : '/dashboard';

    navigate(destination, {
      replace: true,
    });
  }, [
    isAuthenticated,
    navigate,
    user,
  ]);

  /* =======================================================
     입력값 변경
  ======================================================= */

  const handleEmailChange = (event) => {
    setEmail(event.target.value);

    if (errorMessage) {
      setErrorMessage('');
    }
  };

  const handlePasswordChange = (event) => {
    setPassword(event.target.value);

    if (errorMessage) {
      setErrorMessage('');
    }
  };

  /* =======================================================
     데모 계정 자동 입력
  ======================================================= */

  const handleFillDemoAccount = (
    accountType,
  ) => {
    const account =
      DEMO_ACCOUNT[accountType];

    setEmail(account.email);
    setPassword(account.password);
    setErrorMessage('');
  };

  /* =======================================================
     로그인 제출
  ======================================================= */

  const handleSubmit = (event) => {
    event.preventDefault();

    if (isSubmitting) {
      return;
    }

    setErrorMessage('');
    setIsSubmitting(true);

    try {
      const result = login({
        email,
        password,
        rememberMe,
      });

      if (!result.success) {
        setErrorMessage(result.message);
        return;
      }

      /*
       * 관리자는 항상 관리자 페이지로 이동한다.
       *
       * 일반 사용자는 ProtectedRoute에서
       * 전달된 기존 접근 경로가 있으면 그곳으로,
       * 없으면 대시보드로 이동한다.
       */

      if (
        result.user.role ===
        USER_ROLE.ADMIN
      ) {
        navigate('/admin', {
          replace: true,
        });

        return;
      }

      const userDestination =
        requestedPath &&
        requestedPath !== '/admin'
          ? requestedPath
          : '/dashboard';

      navigate(userDestination, {
        replace: true,
      });
    } catch (error) {
      console.error(
        '로그인 처리 중 오류가 발생했습니다.',
        error,
      );

      setErrorMessage(
        '로그인 처리 중 오류가 발생했습니다. 다시 시도해 주세요.',
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="login-wrapper">
      <form
        className="form-login"
        onSubmit={handleSubmit}
      >
        <Link
          to="/"
          className="brand-logo mb-4"
          aria-label="홈으로 이동"
        >
          P
        </Link>

        <h1 className="h3 mb-3 font-weight-normal">
          로그인
        </h1>

        <p className="login-description">
          페르소나 플랫폼 서비스를 이용하려면
          로그인해 주세요.
        </p>

        {errorMessage && (
          <div
            className="login-error-message"
            role="alert"
          >
            {errorMessage}
          </div>
        )}

        <label
          htmlFor="inputEmail"
          className="sr-only"
        >
          이메일
        </label>

        <input
          type="email"
          id="inputEmail"
          className="form-control"
          placeholder="이메일"
          required
          autoFocus
          autoComplete="email"
          value={email}
          onChange={handleEmailChange}
          disabled={isSubmitting}
        />

        <label
          htmlFor="inputPassword"
          className="sr-only"
        >
          비밀번호
        </label>

        <input
          type="password"
          id="inputPassword"
          className="form-control"
          placeholder="비밀번호"
          required
          autoComplete="current-password"
          value={password}
          onChange={handlePasswordChange}
          disabled={isSubmitting}
        />

        <div className="checkbox-signup-group mb-3">
          <label className="remember-label">
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={(event) =>
                setRememberMe(
                  event.target.checked,
                )
              }
              disabled={isSubmitting}
            />

            <span>로그인 상태 유지</span>
          </label>

          <Link
            to="/signup"
            className="signup-link"
          >
            회원가입
          </Link>
        </div>

        <button
          className="btn btn-lg btn-primary btn-block"
          type="submit"
          disabled={isSubmitting}
        >
          {isSubmitting
            ? '로그인 중...'
            : '로그인'}
        </button>

        <div className="login-demo-section">
          <p>데모 계정</p>

          <div className="login-demo-buttons">
            <button
              type="button"
              onClick={() =>
                handleFillDemoAccount(
                  'USER',
                )
              }
              disabled={isSubmitting}
            >
              일반 사용자 계정 입력
            </button>

            <button
              type="button"
              onClick={() =>
                handleFillDemoAccount(
                  'ADMIN',
                )
              }
              disabled={isSubmitting}
            >
              관리자 계정 입력
            </button>
          </div>

          <div className="login-demo-info">
            <span>
              일반 사용자:
              user@test.com / user1234
            </span>

            <span>
              관리자:
              admin@test.com / admin1234
            </span>
          </div>
        </div>

        <p className="mt-5 mb-3 text-muted">
          © KT AIVLE Persona Platform
        </p>
      </form>
    </div>
  );
}

export default Login;
