import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/apiError.js';
import { AuthProvider } from './AuthProvider.jsx';
import { LoginPage, SignupPage } from './AuthPages.jsx';
import { AUTH_STATUS } from './authSession.js';

function renderAuthPage(path, session) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider
        session={session}
        initialSnapshot={{ status: AUTH_STATUS.UNAUTHENTICATED, user: null }}
      >
        <Routes>
          <Route path="/auth/login" element={<LoginPage />} />
          <Route path="/auth/signup" element={<SignupPage />} />
          <Route path="/app" element={<h1>프로젝트 허브</h1>} />
          <Route path="/app/projects" element={<h1>프로젝트 허브</h1>} />
          <Route path="/projects" element={<h1>프로젝트 도착</h1>} />
          <Route path="/projects/:id/overview" element={<h1>원래 화면 도착</h1>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function fillLogin() {
  fireEvent.change(document.getElementById('login-username'), {
    target: { value: 'ventureuser' },
  });
  fireEvent.change(document.getElementById('login-password'), {
    target: { value: 'safe-password' },
  });
}

function LandingProbe() {
  const location = useLocation();
  return <p>인트로 건너뛰기: {String(location.state?.skipLandingIntro === true)}</p>;
}

describe('auth pages', () => {
  afterEach(() => { vi.useRealTimers(); window.sessionStorage.clear(); });
  it('submits login with accessible fields and returns to an internal route', async () => {
    const session = {
      login: vi.fn(async () => ({ id: 1, displayName: 'User' })),
      subscribe: vi.fn(),
    };
    renderAuthPage({
      pathname: '/auth/login',
      state: { returnTo: '/projects/3/overview' },
    }, session);
    fillLogin();
    fireEvent.submit(screen.getByRole('button', { name: '로그인' }).closest('form'));
    expect(await screen.findByRole('heading', { name: '원래 화면 도착' })).toBeInTheDocument();
    expect(session.login).toHaveBeenCalledWith({
      username: 'ventureuser',
      password: 'safe-password',
    });
  });

  it('blocks an external return route', async () => {
    const session = {
      login: vi.fn(async () => ({ id: 1 })),
      subscribe: vi.fn(),
    };
    renderAuthPage({
      pathname: '/auth/login',
      state: { returnTo: '//evil.example/path' },
    }, session);
    fillLogin();
    fireEvent.submit(screen.getByRole('button', { name: '로그인' }).closest('form'));
    expect(await screen.findByRole('heading', { name: '프로젝트 허브' })).toBeInTheDocument();
  });

  it('shows a non-enumerating login error and moves focus to it', async () => {
    const session = {
      login: vi.fn(async () => {
        throw new ApiError({ status: 401, code: 'INVALID_CREDENTIALS' });
      }),
      subscribe: vi.fn(),
    };
    renderAuthPage('/auth/login', session);
    fillLogin();
    fireEvent.submit(screen.getByRole('button', { name: '로그인' }).closest('form'));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('아이디 또는 비밀번호를 확인해 주세요.');
    await waitFor(() => expect(alert.parentElement).toHaveFocus());
  });

  it('shows the server-provided final warning without treating a 401 as a rate limit', async () => {
    const session = {
      login: vi.fn(async () => {
        throw new ApiError({
          status: 401,
          code: 'INVALID_CREDENTIALS',
          loginAttempt: { warningLevel: 'FINAL_WARNING', remainingAttempts: 1 },
        });
      }),
      subscribe: vi.fn(),
    };
    renderAuthPage('/auth/login', session);
    fillLogin();
    fireEvent.submit(screen.getByRole('button', { name: '로그인' }).closest('form'));
    expect(await screen.findByText('마지막 로그인 시도 전 안내')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로그인' })).toBeEnabled();
    expect(window.sessionStorage.getItem('authLoginRetryAt')).toBeNull();
  });

  it('disables login while a server-provided retry period is active', async () => {
    const session = {
      login: vi.fn(async () => { throw new ApiError({ status: 429, code: 'LOGIN_RATE_LIMITED', retryAfterSeconds: 120 }); }),
      subscribe: vi.fn(),
    };
    renderAuthPage('/auth/login', session);
    fillLogin();
    fireEvent.submit(screen.getByRole('button', { name: '로그인' }).closest('form'));
    expect(await screen.findByText(/로그인 시도가 반복되어 잠시 제한되었습니다/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /후 다시 시도/ })).toBeDisabled();
  });

  it('prevents duplicate login submission while pending', () => {
    const session = {
      login: vi.fn(() => new Promise(() => {})),
      subscribe: vi.fn(),
    };
    renderAuthPage('/auth/login', session);
    fillLogin();
    const form = screen.getByRole('button', { name: '로그인' }).closest('form');
    fireEvent.submit(form);
    fireEvent.submit(form);
    expect(session.login).toHaveBeenCalledOnce();
    expect(screen.getByRole('button', { name: /로그인/ })).toBeDisabled();
  });

  it('validates an invalid username and focuses the first invalid field', async () => {
    const session = { login: vi.fn(), subscribe: vi.fn() };
    renderAuthPage('/auth/login', session);
    fireEvent.change(document.getElementById('login-username'), { target: { value: 'user@' } });
    fireEvent.submit(screen.getByRole('button', { name: '로그인' }).closest('form'));
    expect(screen.getByText('사용할 수 없는 문자가 포함되어 있습니다.')).toBeInTheDocument();
    await waitFor(() => expect(document.getElementById('login-username')).toHaveFocus());
    expect(session.login).not.toHaveBeenCalled();
  });

  it('shows a caps lock hint without treating it as an error', () => {
    const session = { login: vi.fn(), subscribe: vi.fn() };
    renderAuthPage('/auth/login', session);
    fireEvent.keyUp(document.getElementById('login-password'), { key: 'CapsLock' });
    expect(screen.getByText('Caps Lock이 켜져 있습니다.')).toBeInTheDocument();
  });

  it('keeps every password requirement neutral until password input exists', () => {
    const session = { signup: vi.fn(), subscribe: vi.fn() };
    renderAuthPage('/auth/signup', session);
    expect(screen.getByText('15자 이상')).toBeInTheDocument();
    expect(screen.getByText('비밀번호 확인과 일치')).toBeInTheDocument();
    fireEvent.change(document.getElementById('signup-password'), { target: { value: 'abcdefghijklmno' } });
    expect(screen.getByText('15자 이상')).toBeInTheDocument();
    fireEvent.change(document.getElementById('signup-password-confirm'), { target: { value: 'abcdefghijklmno' } });
    expect(screen.getByText('비밀번호 확인과 일치')).toBeInTheDocument();
  });

  it('makes the brand link a clear internal return to the landing page', () => {
    const session = { login: vi.fn(), subscribe: vi.fn() };
    render(<MemoryRouter initialEntries={['/auth/login']}><AuthProvider session={session} initialSnapshot={{ status: AUTH_STATUS.UNAUTHENTICATED, user: null }}><Routes><Route path="/auth/login" element={<LoginPage />} /><Route path="/" element={<LandingProbe />} /></Routes></AuthProvider></MemoryRouter>);
    const brand = screen.getByRole('link', { name: 'Venture Verify 서비스 소개로 돌아가기' });
    expect(brand).toHaveAttribute('href', '/');
    fireEvent.click(brand);
    expect(screen.getByText('인트로 건너뛰기: true')).toBeInTheDocument();
  });

  it('cycles the brand preview independently and pauses while the preview is hovered', async () => {
    vi.useFakeTimers();
    const session = { login: vi.fn(), subscribe: vi.fn() };
    renderAuthPage('/auth/login', session);
    expect(document.querySelector('.auth-preview__header b')).toHaveTextContent('문서 구조화');
    await act(async () => { vi.advanceTimersByTime(7500); });
    expect(document.querySelector('.auth-preview__header b')).toHaveTextContent('근거와 위험 확인');
    const preview = document.querySelector('.auth-preview');
    fireEvent.mouseEnter(preview);
    await act(async () => { vi.advanceTimersByTime(6000); });
    expect(document.querySelector('.auth-preview__header b')).toHaveTextContent('근거와 위험 확인');
  });

  it('validates signup password confirmation without calling the API', () => {
    const session = { signup: vi.fn(), subscribe: vi.fn() };
    renderAuthPage('/auth/signup', session);
    fireEvent.change(document.getElementById('signup-username'), { target: { value: 'newuser' } });
    fireEvent.change(document.getElementById('signup-display-name'), { target: { value: '새 사용자' } });
    fireEvent.change(document.getElementById('signup-password'), { target: { value: 'a long unique password' } });
    fireEvent.change(document.getElementById('signup-password-confirm'), { target: { value: 'a different long passphrase' } });
    fireEvent.submit(screen.getByRole('button', { name: '무료 계정 만들기' }).closest('form'));
    expect(screen.getByText('비밀번호가 일치하지 않습니다.')).toBeInTheDocument();
    expect(session.signup).not.toHaveBeenCalled();
  });

  it('signs up without sending password confirmation', async () => {
    vi.useFakeTimers();
    const session = {
      signup: vi.fn(async () => ({ id: 2 })),
      subscribe: vi.fn(),
    };
    renderAuthPage('/auth/signup', session);
    fireEvent.change(document.getElementById('signup-username'), { target: { value: 'newuser' } });
    fireEvent.change(document.getElementById('signup-display-name'), { target: { value: '새 사용자' } });
    fireEvent.change(document.getElementById('signup-password'), { target: { value: 'a long unique password' } });
    fireEvent.change(document.getElementById('signup-password-confirm'), { target: { value: 'a long unique password' } });
    fireEvent.submit(screen.getByRole('button', { name: '무료 계정 만들기' }).closest('form'));
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    expect(session.signup).toHaveBeenCalledOnce();
    expect(screen.getByText('계정이 준비되었습니다')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '로그인하러 가기' })).toHaveAttribute('href', '/auth/login');
    expect(session.signup).toHaveBeenCalledWith({
      username: 'newuser',
      displayName: '새 사용자',
      password: 'a long unique password',
      email: null,
      organizationName: null,
      departmentName: null,
      jobTitle: null,
    });
  });

  it('maps a duplicate signup username to its field', async () => {
    const session = { signup: vi.fn(async () => { throw new ApiError({ status: 409, code: 'USERNAME_ALREADY_EXISTS' }); }), subscribe: vi.fn() };
    renderAuthPage('/auth/signup', session);
    fireEvent.change(document.getElementById('signup-username'), { target: { value: 'newuser' } });
    fireEvent.change(document.getElementById('signup-display-name'), { target: { value: '새 사용자' } });
    fireEvent.change(document.getElementById('signup-password'), { target: { value: 'a long unique password' } });
    fireEvent.change(document.getElementById('signup-password-confirm'), { target: { value: 'a long unique password' } });
    fireEvent.submit(screen.getByRole('button', { name: '무료 계정 만들기' }).closest('form'));
    await waitFor(() => expect(document.getElementById('signup-username-error')).toHaveTextContent('이미 사용 중인 아이디입니다.'));
  });
});
