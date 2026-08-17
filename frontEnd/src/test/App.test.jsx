import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import App from '../app/App.jsx';
import AppProviders from '../app/providers/AppProviders.jsx';
import { AUTH_STATUS } from '../features/auth/authSession.js';

const authenticated = {
  status: AUTH_STATUS.AUTHENTICATED,
  user: { id: 7, email: 'user@example.com', displayName: '사용자' },
};

const apiClient = {
  get: vi.fn(async (path) => {
    if (path === '/projects') return { data: [] };
    const projectId = path.split('/').at(-1);
    return {
      data: {
        id: projectId,
        title: `테스트 프로젝트 ${projectId}`,
        description: '테스트 설명',
        industryCategory: 'SaaS',
        stage: 'DOCUMENT',
        status: 'DRAFT',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-02T00:00:00Z',
        version: 0,
      },
    };
  }),
};

function renderApp(
  path,
  snapshot = { status: AUTH_STATUS.UNAUTHENTICATED, user: null },
  session,
) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppProviders
        apiClient={apiClient}
        authSession={session}
        authProps={{ initialSnapshot: snapshot }}
      >
        <App />
      </AppProviders>
    </MemoryRouter>,
  );
}

describe('application routing', () => {
  it('renders the public route', () => {
    renderApp('/');
    expect(screen.getByRole('heading', {
      name: '아이디어에서, 실행 판단을 위한 보고서까지.',
    })).toBeInTheDocument();
  });

  it('renders the auth route under the documented path', () => {
    renderApp('/auth/login');
    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument();
  });

  it('redirects an authenticated user away from public auth routes', async () => {
    renderApp('/auth/login', authenticated);
    expect(await screen.findByRole('link', { name: '프로젝트' })).toBeInTheDocument();
  });

  it('keeps a protected route pending while auth is unknown', () => {
    const session = {
      bootstrap: vi.fn(() => new Promise(() => {})),
    };
    renderApp('/app/projects', { status: AUTH_STATUS.UNKNOWN, user: null }, session);
    expect(screen.getAllByText('로그인 상태를 확인하고 있습니다')).toHaveLength(2);
  });

  it('redirects unauthenticated users to the canonical login route', () => {
    renderApp('/app/projects');
    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument();
  });

  it('renders protected content for authenticated users', async () => {
    renderApp('/app/projects', authenticated);
    expect(await screen.findByRole('heading', { name: '프로젝트' })).toBeInTheDocument();
  });

  it('renders not found state', () => {
    renderApp('/unknown-route');
    expect(screen.getByRole('heading', { name: '페이지를 찾을 수 없습니다' })).toBeInTheDocument();
  });

  it('supports direct project route entry', async () => {
    renderApp('/app/projects/42/idea', authenticated);
    expect((await screen.findAllByText('테스트 프로젝트 42')).length).toBeGreaterThan(0);
    expect(await screen.findByLabelText('프로젝트 도구')).toBeInTheDocument();
  });

  it('retains the project route parameter after rendering again', async () => {
    const { unmount } = renderApp('/app/projects/p-100/overview', authenticated);
    expect((await screen.findAllByText('테스트 프로젝트 p-100')).length).toBeGreaterThan(0);
    unmount();
    renderApp('/app/projects/p-100/overview', authenticated);
    expect((await screen.findAllByText('테스트 프로젝트 p-100')).length).toBeGreaterThan(0);
  });

  it('renders the application header and main navigation', () => {
    renderApp('/app', authenticated);
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: '주요 메뉴' })).toBeInTheDocument();
  });

  it('provides a mobile navigation trigger and drawer', () => {
    renderApp('/app', authenticated);
    fireEvent.click(document.querySelector('.app-mobile-menu'));
    expect(screen.getByRole('dialog', { name: '메뉴' })).toBeInTheDocument();
  });

  it('provides skip navigation', () => {
    renderApp('/app', authenticated);
    expect(screen.getByRole('link', { name: '본문으로 바로가기' }))
      .toHaveAttribute('href', '#main-content');
  });

  it('renders project context navigation', async () => {
    renderApp('/app/projects/77/overview', authenticated);
    fireEvent.click(await screen.findByRole('button', { name: '단계' }));
    expect(screen.getByLabelText('프로젝트 단계 탐색')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '전체 단계 보기' }));
    expect(screen.getByRole('navigation', { name: '프로젝트 전체 단계' })).toBeInTheDocument();
  });

  it('provides the main landmark', () => {
    renderApp('/app', authenticated);
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main-content');
  });

  it('uses a single level-one heading in a protected page', async () => {
    renderApp('/app', authenticated);
    await screen.findByRole('link', { name: '프로젝트' });
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('logs out from the current user menu and returns to login', async () => {
    const session = {
      logout: vi.fn(async () => {}),
      subscribe: vi.fn(),
    };
    renderApp('/app', authenticated, session);
    fireEvent.click(screen.getByRole('button', { name: '계정 메뉴' }));
    fireEvent.click(screen.getByRole('menuitem', { name: '로그아웃' }));
    expect(await screen.findByRole('heading', { name: '로그인' })).toBeInTheDocument();
    expect(session.logout).toHaveBeenCalledOnce();
  });
});
