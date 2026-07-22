import {
  useState,
} from 'react';

import {
  Link,
  useNavigate,
} from 'react-router-dom';

import {
  useAuth,
} from '../auth/AuthContext.jsx';

import './SignupPage.css';

/* =========================================================
   회원가입 초기값
========================================================= */

const INITIAL_FORM = {
  name: '',
  email: '',
  password: '',
  passwordConfirm: '',
  rememberMe: false,
  termsAgreed: false,
};

/* =========================================================
   회원가입 페이지
========================================================= */

function SignupPage() {
  const navigate = useNavigate();

  const {
    signupAndLogin,
  } = useAuth();

  const [formData, setFormData] =
    useState(INITIAL_FORM);

  const [errorMessage, setErrorMessage] =
    useState('');

  const [errorField, setErrorField] =
    useState('');

  const [isSubmitting, setIsSubmitting] =
    useState(false);

  /* =======================================================
     입력값 변경
  ======================================================= */

  const handleChange = (event) => {
    const {
      name,
      value,
      checked,
      type,
    } = event.target;

    setFormData((currentForm) => ({
      ...currentForm,
      [name]:
        type === 'checkbox'
          ? checked
          : value,
    }));

    if (
      errorField === name ||
      errorField === 'form'
    ) {
      setErrorMessage('');
      setErrorField('');
    }
  };

  /* =======================================================
     프런트 입력 검증
  ======================================================= */

  const validateForm = () => {
    if (!formData.name.trim()) {
      return {
        field: 'name',
        message:
          '이름을 입력해 주세요.',
      };
    }

    if (!formData.email.trim()) {
      return {
        field: 'email',
        message:
          '이메일을 입력해 주세요.',
      };
    }

    if (formData.password.length < 8) {
      return {
        field: 'password',
        message:
          '비밀번호는 8자 이상이어야 합니다.',
      };
    }

    if (
      formData.password !==
      formData.passwordConfirm
    ) {
      return {
        field: 'passwordConfirm',
        message:
          '비밀번호가 일치하지 않습니다.',
      };
    }

    if (!formData.termsAgreed) {
      return {
        field: 'termsAgreed',
        message:
          '서비스 이용약관에 동의해 주세요.',
      };
    }

    return null;
  };

  /* =======================================================
     회원가입 제출
  ======================================================= */

  const handleSubmit = (event) => {
    event.preventDefault();

    if (isSubmitting) {
      return;
    }

    setErrorMessage('');
    setErrorField('');

    const validationError =
      validateForm();

    if (validationError) {
      setErrorField(
        validationError.field,
      );

      setErrorMessage(
        validationError.message,
      );

      return;
    }

    setIsSubmitting(true);

    try {
      const result = signupAndLogin({
        name: formData.name,
        email: formData.email,
        password: formData.password,
        rememberMe:
          formData.rememberMe,
      });

      if (!result.success) {
        setErrorField(
          result.field || 'form',
        );

        setErrorMessage(
          result.message ||
            '회원가입에 실패했습니다.',
        );

        return;
      }

      navigate('/dashboard', {
        replace: true,
        state: {
          signupCompleted: true,
        },
      });
    } catch (error) {
      console.error(
        '회원가입 처리 중 오류가 발생했습니다.',
        error,
      );

      setErrorField('form');

      setErrorMessage(
        '회원가입 처리 중 오류가 발생했습니다. 다시 시도해 주세요.',
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const getInputClassName = (
    fieldName,
  ) =>
    errorField === fieldName
      ? 'signup-input error'
      : 'signup-input';

  return (
    <main className="signup-page">
      <section className="signup-card">
        <Link
          to="/"
          className="signup-brand"
        >
          페르소나 플랫폼
        </Link>

        <header className="signup-header">
          <h1>회원가입</h1>

          <p>
            계정을 만들고 AI 시장검증을
            시작하세요.
          </p>
        </header>

        {errorMessage && (
          <div
            className="signup-error-message"
            role="alert"
          >
            {errorMessage}
          </div>
        )}

        <form
          className="signup-form"
          onSubmit={handleSubmit}
          noValidate
        >
          <div className="signup-field">
            <label htmlFor="signup-name">
              이름
            </label>

            <input
              id="signup-name"
              name="name"
              type="text"
              className={getInputClassName(
                'name',
              )}
              value={formData.name}
              onChange={handleChange}
              placeholder="이름을 입력하세요"
              autoComplete="name"
              disabled={isSubmitting}
            />
          </div>

          <div className="signup-field">
            <label htmlFor="signup-email">
              이메일
            </label>

            <input
              id="signup-email"
              name="email"
              type="email"
              className={getInputClassName(
                'email',
              )}
              value={formData.email}
              onChange={handleChange}
              placeholder="example@email.com"
              autoComplete="email"
              disabled={isSubmitting}
            />
          </div>

          <div className="signup-field">
            <label htmlFor="signup-password">
              비밀번호
            </label>

            <input
              id="signup-password"
              name="password"
              type="password"
              className={getInputClassName(
                'password',
              )}
              value={formData.password}
              onChange={handleChange}
              placeholder="8자 이상 입력하세요"
              autoComplete="new-password"
              disabled={isSubmitting}
            />
          </div>

          <div className="signup-field">
            <label htmlFor="signup-password-confirm">
              비밀번호 확인
            </label>

            <input
              id="signup-password-confirm"
              name="passwordConfirm"
              type="password"
              className={getInputClassName(
                'passwordConfirm',
              )}
              value={
                formData.passwordConfirm
              }
              onChange={handleChange}
              placeholder="비밀번호를 다시 입력하세요"
              autoComplete="new-password"
              disabled={isSubmitting}
            />
          </div>

          <label
            className={
              errorField ===
              'termsAgreed'
                ? 'signup-checkbox error'
                : 'signup-checkbox'
            }
          >
            <input
              name="termsAgreed"
              type="checkbox"
              checked={
                formData.termsAgreed
              }
              onChange={handleChange}
              disabled={isSubmitting}
            />

            <span>
              서비스 이용약관 및 개인정보
              처리방침에 동의합니다.
            </span>
          </label>

          <label className="signup-checkbox">
            <input
              name="rememberMe"
              type="checkbox"
              checked={
                formData.rememberMe
              }
              onChange={handleChange}
              disabled={isSubmitting}
            />

            <span>
              로그인 상태 유지
            </span>
          </label>

          <button
            type="submit"
            className="signup-submit-button"
            disabled={isSubmitting}
          >
            {isSubmitting
              ? '계정 생성 중...'
              : '회원가입하고 시작하기'}
          </button>
        </form>

        <div className="signup-login-link">
          이미 계정이 있나요?

          <Link to="/login">
            로그인
          </Link>
        </div>
      </section>
    </main>
  );
}

export default SignupPage;
