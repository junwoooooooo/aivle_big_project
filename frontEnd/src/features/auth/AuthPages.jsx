import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { Alert, Button, PasswordInput, TextInput } from '../../shared/ui/index.js';
import { useServicePolicy } from '../service-policy/useServicePolicy.js';
import { useAuth } from './AuthProvider.jsx';
import AuthBrandPanel from './components/AuthBrandPanel.jsx';
import AuthCard from './components/AuthCard.jsx';
import LoginRateLimitNotice from './components/LoginRateLimitNotice.jsx';
import AuthShell from './components/AuthShell.jsx';
import PasswordRequirements from './components/PasswordRequirements.jsx';
import useCapsLock from './hooks/useCapsLock.js';
import usePasswordChecks from './hooks/usePasswordChecks.js';
import useLoginRetryCountdown from './hooks/useLoginRetryCountdown.js';
import './auth.css';
import './auth-card-heading.css';
import './auth-enhancements.css';
import './auth-polish.css';
import './auth-motion.css';

const usernamePattern = /^[a-z0-9][a-z0-9._-]{3,29}$/;
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const warningStorageKey = 'authLoginAttemptWarning';

function persistAttemptWarning(attempt) {
  if (!attempt || attempt.warningLevel === 'NONE' || attempt.warningLevel === 'LIMITED') {
    window.sessionStorage.removeItem(warningStorageKey);
    window.dispatchEvent(new Event('auth-login-attempt-warning'));
    return;
  }
  window.sessionStorage.setItem(warningStorageKey, JSON.stringify({
    warningLevel: attempt.warningLevel,
    remainingAttempts: attempt.remainingAttempts,
    expiresAt: Date.now() + (10 * 60 * 1000),
  }));
  window.dispatchEvent(new Event('auth-login-attempt-warning'));
}

function fieldErrorsFrom(error) {
  const fields = Object.fromEntries((error?.fieldErrors ?? []).map(({ field, message }) => [field, message]));
  if (error?.code === 'EMAIL_ALREADY_EXISTS') fields.email ??= '이미 가입된 이메일입니다.';
  if (error?.code === 'USERNAME_ALREADY_EXISTS') fields.username ??= '이미 사용 중인 아이디입니다.';
  return fields;
}

function focusFirstError(errors, fallback) {
  const ids = { username: 'username', email: 'email', displayName: 'display-name', password: 'password', confirmPassword: 'password-confirm' };
  const field = Object.keys(errors)[0];
  window.requestAnimationFrame(() => field ? document.getElementById(`${fallback}-${ids[field]}`)?.focus() : fallback?.focus());
}

function AuthError({ errorRef, message, title }) {
  if (!message) return null;
  return <div className="auth-error-summary" ref={errorRef} tabIndex="-1"><Alert tone="danger" title={title}>{message}</Alert></div>;
}

function AuthSuccess({ message, title }) {
  const signupComplete = title === '계정이 준비되었습니다';
  return <div className="auth-success-state" role="status" aria-live="polite">
    <b>{title}</b>
    <span>{message}</span>
    {signupComplete && <>
      <span>이제 생성한 아이디로 로그인해 주세요.</span>
      <div className="auth-success-state__actions">
        <Link className="auth-success-state__action" to="/auth/login" state={{ authTransition: true, source: 'signup-complete', intent: 'login', signupCompleted: true }}>로그인하러 가기</Link>
        <Link className="auth-success-state__secondary-action" to="/" state={{ skipLandingIntro: true, source: 'auth' }}>서비스 소개로 돌아가기</Link>
      </div>
    </>}
  </div>;
}

function AuthPage({ children, mode }) {
  const location = useLocation();
  const pageTitle = mode === 'signup' ? '회원가입' : '로그인';
  const [spaceTransition, setSpaceTransition] = useState(Boolean(location.state?.authSpaceTransition === 'enter-login'));
  useEffect(() => {
    if (location.state?.authSpaceTransition !== 'enter-login') return undefined;
    const timer = window.setTimeout(() => setSpaceTransition(false), 780);
    return () => window.clearTimeout(timer);
  }, [location.state?.authSpaceTransition]);
  return <AuthShell mode={mode}>{spaceTransition && <div className="auth-space-transition auth-space-transition--login" role="status" aria-live="polite" aria-busy="true"><span aria-hidden="true">V</span><p>안전하게 로그아웃하고 있습니다.</p></div>}<h1 className="visually-hidden">{pageTitle}</h1>{mode === 'login' && location.state?.signupCompleted && <div className="auth-route-success" role="status">회원가입이 완료되었습니다. 생성한 아이디로 로그인해 주세요.</div>}{mode === 'login' && location.state?.source === 'logout' && <div className="auth-route-success" role="status">안전하게 로그아웃되었습니다. 다시 로그인해 작업을 이어갈 수 있습니다.</div>}<AuthBrandPanel mode={mode} />{children}</AuthShell>;
}

export function LoginPage() {
  const { login } = useAuth();
  const errorRef = useRef(null);
  const timerRef = useRef(null);
  const { isCapsLockOn, handleBlur: handleCapsLockBlur, handleFocus: handleCapsLockFocus, handleKeyDown: handleCapsLockKeyDown, handleKeyUp: handleCapsLockKeyUp } = useCapsLock();
  const [values, setValues] = useState({ username: '', password: '' });
  const [errors, setErrors] = useState({});
  const [globalError, setGlobalError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);
  const { clearRetryCountdown, isLimited, remainingSeconds, startRetryCountdown } = useLoginRetryCountdown();
  useEffect(() => () => window.clearTimeout(timerRef.current), []);
  const update = (field) => (event) => { const value = field === 'username' ? event.target.value.toLowerCase() : event.target.value; setValues((current) => ({ ...current, [field]: value })); setErrors((current) => ({ ...current, [field]: undefined })); setGlobalError(''); };
  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting || isLimited) return;
    const nextErrors = {};
    const username = values.username.trim().toLowerCase();
    if (!username) nextErrors.username = '아이디를 입력해 주세요.';
    else if (username.length < 4) nextErrors.username = '아이디는 4자 이상이어야 합니다.';
    else if (username.length > 30) nextErrors.username = '아이디는 30자 이하로 입력해 주세요.';
    else if (!usernamePattern.test(username)) nextErrors.username = '사용할 수 없는 문자가 포함되어 있습니다.';
    if (!values.password) nextErrors.password = '비밀번호를 입력해 주세요.';
    if (Object.keys(nextErrors).length) { setErrors(nextErrors); focusFirstError(nextErrors, 'login'); return; }
    setSubmitting(true); setErrors({}); setGlobalError('');
    try {
      await login({ username, password: values.password });
      clearRetryCountdown();
      window.sessionStorage.removeItem(warningStorageKey);
      window.dispatchEvent(new Event('auth-login-attempt-warning'));
      setErrors({});
      setGlobalError('');
      setSuccess(true);
    } catch (error) {
      if (error?.code === 'LOGIN_RATE_LIMITED') {
        startRetryCountdown(error.retryAfterSeconds);
        window.sessionStorage.removeItem(warningStorageKey);
        window.dispatchEvent(new Event('auth-login-attempt-warning'));
      } else if (error?.loginAttempt?.warningLevel && error.loginAttempt.warningLevel !== 'NONE') {
        persistAttemptWarning(error.loginAttempt);
      } else {
        window.sessionStorage.removeItem(warningStorageKey);
        window.dispatchEvent(new Event('auth-login-attempt-warning'));
      }
      const fields = fieldErrorsFrom(error); setErrors(fields); setGlobalError(getUserErrorMessage(error));
      window.requestAnimationFrame(() => Object.keys(fields).length ? focusFirstError(fields, 'login') : errorRef.current?.focus());
    } finally { setSubmitting(false); }
  }
  return <AuthPage mode="login"><AuthCard title="다시 만나서 반갑습니다" description="진행 중인 사업 검증 프로젝트를 이어서 확인하세요."><AuthError errorRef={errorRef} message={globalError} title="로그인하지 못했습니다" /><LoginRateLimitNotice remainingSeconds={remainingSeconds} />{success ? <AuthSuccess title="로그인 완료" message="프로젝트로 이동하고 있습니다." /> : <form className="auth-form" onSubmit={handleSubmit} noValidate><TextInput id="login-username" label="아이디" placeholder="ventureuser" autoComplete="username" value={values.username} error={errors.username} onChange={update('username')} required /><PasswordInput id="login-password" label="비밀번호" placeholder="비밀번호를 입력하세요" autoComplete="current-password" value={values.password} error={errors.password} onChange={update('password')} onFocus={handleCapsLockFocus} onBlur={handleCapsLockBlur} onKeyUp={handleCapsLockKeyUp} onKeyDown={handleCapsLockKeyDown} required />{isCapsLockOn && <p className="auth-caps-lock" aria-live="polite">Caps Lock이 켜져 있습니다.</p>}<Button className="auth-form__submit" type="submit" size="large" loading={submitting} disabled={isLimited}>{isLimited ? `${Math.ceil(remainingSeconds / 60)}분 후 다시 시도` : submitting ? '로그인 중...' : '로그인'}</Button></form>}<p className="auth-card__switch">아직 계정이 없나요? <Link to="/auth/signup" state={{ authTransition: true, source: 'auth-switch', intent: 'signup' }}>무료로 시작하기</Link></p></AuthCard></AuthPage>;
}

export function SignupPage() {
  const { signup } = useAuth();
  const {
    loading: policyLoading,
    policy,
    error: policyError,
    refresh: refreshPolicy,
  } = useServicePolicy();
  const errorRef = useRef(null);
  const timerRef = useRef(null);
  const { isCapsLockOn, handleBlur: handleCapsLockBlur, handleFocus: handleCapsLockFocus, handleKeyDown: handleCapsLockKeyDown, handleKeyUp: handleCapsLockKeyUp } = useCapsLock();
  const [values, setValues] = useState({ username: '', email: '', displayName: '', password: '', confirmPassword: '', organizationName: '', departmentName: '', jobTitle: '' });
  const [errors, setErrors] = useState({});
  const [globalError, setGlobalError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);
  const [serverRegistrationDisabled, setServerRegistrationDisabled] = useState(false);
  const passwordChecks = usePasswordChecks(values.password, values.confirmPassword, values.username, values.displayName);
  useEffect(() => () => window.clearTimeout(timerRef.current), []);
  useEffect(() => {
    const refreshTimer = window.setTimeout(() => {
      void refreshPolicy().catch(() => undefined);
    }, 0);
    return () => window.clearTimeout(refreshTimer);
  }, [refreshPolicy]);
  const registrationPaused = serverRegistrationDisabled
    || (!policyLoading && !policyError && !policy.registrationEnabled);
  const signupUnavailable = policyLoading || Boolean(policyError) || registrationPaused;
  const update = (field) => (event) => { const value = field === 'username' ? event.target.value.toLowerCase() : event.target.value; setValues((current) => ({ ...current, [field]: value })); setErrors((current) => ({ ...current, [field]: undefined })); setGlobalError(''); };
  const retryPolicy = () => {
    setServerRegistrationDisabled(false);
    setGlobalError('');
    void refreshPolicy().catch(() => undefined);
  };
  function validate() {
    const next = {}; const username = values.username.trim().toLowerCase(); const email = values.email.trim();
    if (!username) next.username = '아이디를 입력해 주세요.'; else if (username.length < 4) next.username = '아이디는 4자 이상이어야 합니다.'; else if (username.length > 30) next.username = '아이디는 30자 이하로 입력해 주세요.'; else if (!usernamePattern.test(username)) next.username = '사용할 수 없는 문자가 포함되어 있습니다.';
    if (email && !emailPattern.test(email)) next.email = '이메일 주소 형식을 확인해 주세요.';
    if (!values.displayName.trim()) next.displayName = '이름 또는 닉네임을 입력해 주세요.';
    if (!passwordChecks.hasInput) next.password = '비밀번호를 입력해 주세요.';
    else if (!passwordChecks.hasMinimumLength) next.password = '비밀번호는 15자 이상이어야 합니다.';
    else if (!passwordChecks.isWithinMaximumLength) next.password = '비밀번호가 너무 깁니다. 조금 더 짧게 입력해 주세요.';
    else if (!passwordChecks.isNotCommonOrSimilar) next.password = '흔히 사용되거나 계정 정보와 유사한 비밀번호는 사용할 수 없습니다.';
    if (!passwordChecks.confirmationMatches) next.confirmPassword = '비밀번호가 일치하지 않습니다.';
    return next;
  }
  async function handleSubmit(event) {
    event.preventDefault(); if (submitting || signupUnavailable) return;
    const nextErrors = validate(); if (Object.keys(nextErrors).length) { setErrors(nextErrors); focusFirstError(nextErrors, 'signup'); return; }
    setSubmitting(true); setGlobalError('');
    try {
      await signup({
        username: values.username.trim().toLowerCase(),
        displayName: values.displayName.trim(),
        password: values.password,
        email: values.email.trim() || null,
        organizationName: values.organizationName.trim() || null,
        departmentName: values.departmentName.trim() || null,
        jobTitle: values.jobTitle.trim() || null,
      });
      setSuccess(true);
    } catch (error) {
      const fields = fieldErrorsFrom(error);
      setErrors(fields);
      if (error?.code === 'REGISTRATION_DISABLED') {
        setServerRegistrationDisabled(true);
      }
      setGlobalError(getUserErrorMessage(error));
      window.requestAnimationFrame(() => (
        Object.keys(fields).length
          ? focusFirstError(fields, 'signup')
          : errorRef.current?.focus()
      ));
    } finally {
      setSubmitting(false);
    }
  }
  return (
    <AuthPage mode="signup">
      <AuthCard
        title="첫 번째 검증 프로젝트를 시작하세요"
        description="계정을 만들고 검증 결과와 다음 행동을 안전하게 저장하세요."
      >
        <AuthError errorRef={errorRef} message={globalError} title="가입하지 못했습니다" />
        {policyLoading && (
          <div className="auth-policy-notice">
            <Alert title="정책 확인 중">서비스 운영 상태를 확인하고 있습니다.</Alert>
          </div>
        )}
        {policyError && (
          <div className="auth-policy-notice">
            <Alert tone="danger" title="운영 상태를 확인하지 못했습니다">
              <p>서비스 운영 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.</p>
              <Button type="button" variant="outline" size="small" onClick={retryPolicy}>
                다시 시도
              </Button>
            </Alert>
          </div>
        )}
        {registrationPaused && (
          <div className="auth-policy-notice">
            <Alert tone="warning" title="현재 신규 회원가입이 일시 중지되었습니다.">
              기존 계정은 로그인하여 서비스를 이용할 수 있습니다.
            </Alert>
          </div>
        )}
        {success ? (
          <AuthSuccess
            title="계정이 준비되었습니다"
            message="첫 번째 프로젝트를 시작할 수 있습니다."
          />
        ) : (
          <form className="auth-form" onSubmit={handleSubmit} noValidate>
            <TextInput
              id="signup-username"
              label="아이디"
              description="4~30자의 영문 소문자, 숫자, 마침표, 밑줄, 하이픈을 사용할 수 있습니다."
              placeholder="ventureuser"
              autoComplete="username"
              value={values.username}
              error={errors.username}
              onChange={update('username')}
              disabled={signupUnavailable}
              required
            />
            {errors.username === '이미 사용 중인 아이디입니다.' && (
              <p className="auth-inline-link">다른 아이디를 입력해 주세요.</p>
            )}
            <TextInput
              id="signup-display-name"
              label="이름 또는 닉네임"
              description="프로젝트 화면에 표시되는 이름입니다."
              autoComplete="name"
              value={values.displayName}
              error={errors.displayName}
              onChange={update('displayName')}
              disabled={signupUnavailable}
              required
            />
            <PasswordInput
              id="signup-password"
              label="비밀번호"
              autoComplete="new-password"
              value={values.password}
              error={errors.password}
              onChange={update('password')}
              onFocus={handleCapsLockFocus}
              onBlur={handleCapsLockBlur}
              onKeyUp={handleCapsLockKeyUp}
              onKeyDown={handleCapsLockKeyDown}
              disabled={signupUnavailable}
              required
            />
            <PasswordInput
              id="signup-password-confirm"
              label="비밀번호 확인"
              autoComplete="new-password"
              value={values.confirmPassword}
              error={errors.confirmPassword}
              onChange={update('confirmPassword')}
              onFocus={handleCapsLockFocus}
              onBlur={handleCapsLockBlur}
              onKeyUp={handleCapsLockKeyUp}
              onKeyDown={handleCapsLockKeyDown}
              disabled={signupUnavailable}
              required
            />
            <PasswordRequirements
              password={values.password}
              confirmPassword={values.confirmPassword}
              username={values.username}
              displayName={values.displayName}
            />
            <fieldset className="auth-optional-fields" disabled={signupUnavailable}>
              <legend>추가 정보 <span>선택</span></legend>
              <p>선택 사항이며 입력하지 않아도 가입할 수 있습니다.</p>
              <TextInput
                id="signup-email"
                label="이메일 (선택)"
                type="email"
                placeholder="name@example.com"
                autoComplete="email"
                description="계정 안내용 선택 정보입니다. 현재는 이메일 인증을 진행하지 않습니다."
                value={values.email}
                error={errors.email}
                onChange={update('email')}
              />
              <TextInput
                id="signup-organization"
                label="소속 또는 조직 (선택)"
                placeholder="회사, 학교, 기관 또는 팀 이름"
                value={values.organizationName}
                onChange={update('organizationName')}
              />
              <TextInput
                id="signup-department"
                label="부서 또는 팀 (선택)"
                placeholder="예: 신사업팀, AI 2조"
                value={values.departmentName}
                onChange={update('departmentName')}
              />
              <TextInput
                id="signup-job-title"
                label="직급 또는 역할 (선택)"
                placeholder="예: 팀원, 기획자, 대표, 연구원"
                value={values.jobTitle}
                onChange={update('jobTitle')}
              />
            </fieldset>
            {isCapsLockOn && (
              <p className="auth-caps-lock" aria-live="polite">Caps Lock이 켜져 있습니다.</p>
            )}
            <Button
              className="auth-form__submit"
              type="submit"
              size="large"
              loading={submitting}
              disabled={signupUnavailable}
            >
              {policyLoading
                ? '운영 상태 확인 중...'
                : submitting
                  ? '계정을 만들고 있습니다...'
                  : '무료 계정 만들기'}
            </Button>
          </form>
        )}
        <p className="auth-card__switch">
          이미 계정이 있나요?{' '}
          <Link
            to="/auth/login"
            state={{ authTransition: true, source: 'auth-switch', intent: 'login' }}
          >
            로그인
          </Link>
        </p>
      </AuthCard>
    </AuthPage>
  );
}
