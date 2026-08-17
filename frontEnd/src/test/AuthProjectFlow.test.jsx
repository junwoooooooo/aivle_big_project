import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import App from '../app/App.jsx';
import AppProviders from '../app/providers/AppProviders.jsx';
import { AUTH_STATUS } from '../features/auth/authSession.js';

const user = {
  id: 1,
  username: 'ventureuser',
  email: 'user@example.com',
  displayName: '통합 사용자',
};

const project = {
  id: 21,
  title: '통합 프로젝트',
  description: '계약 기반 통합 테스트',
  industryCategory: 'SaaS',
  stage: 'DOCUMENT',
  status: 'DRAFT',
  createdAt: '2026-07-24T00:00:00Z',
  updatedAt: '2026-07-24T00:00:00Z',
  version: 0,
};

function renderFlow(path, { session, client, initialSnapshot }) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppProviders
        authSession={session}
        apiClient={client}
        authProps={{ initialSnapshot }}
      >
        <App />
      </AppProviders>
    </MemoryRouter>,
  );
}

describe('auth and project integration flow', () => {
  it('logs in, shows the project hub, and creates a project without automatic analysis', async () => {
    const session = {
      login: vi.fn(async () => user),
      subscribe: vi.fn(),
    };
    const client = {
      get: vi.fn(async (path) => (
        path === '/projects' ? { data: [] } : { data: project }
      )),
      post: vi.fn(async () => ({ data: project })),
    };
    renderFlow('/auth/login', {
      session,
      client,
      initialSnapshot: { status: AUTH_STATUS.UNAUTHENTICATED, user: null },
    });

    fireEvent.change(document.getElementById('login-username'), {
      target: { value: 'ventureuser' },
    });
    fireEvent.change(document.getElementById('login-password'), {
      target: { value: 'safe-password' },
    });
    fireEvent.submit(screen.getByRole('button', { name: '로그인' }).closest('form'));

    fireEvent.click(await screen.findByRole('link', { name: '프로젝트' }));
    fireEvent.click(await screen.findByRole('link', { name: '프로젝트 만들기' }));
    fireEvent.change(document.getElementById('project-title'), {
      target: { value: '통합 프로젝트' },
    });
    fireEvent.submit(screen.getByRole('button', { name: '프로젝트 만들기' }).closest('form'));

    expect(await screen.findByRole('heading', { name: '통합 프로젝트' }))
      .toBeInTheDocument();
    expect(document.getElementById('project-overview-title')).toHaveTextContent('프로젝트 개요');
    expect(client.post).toHaveBeenCalledWith('/projects', expect.objectContaining({
      title: '통합 프로젝트',
    }));
  });

  it('restores authentication and a direct project route during bootstrap', async () => {
    const session = {
      bootstrap: vi.fn(async () => ({ status: AUTH_STATUS.AUTHENTICATED, user })),
      subscribe: vi.fn(),
    };
    const client = {
      get: vi.fn(async () => ({ data: project })),
    };
    renderFlow('/app/projects/21', {
      session,
      client,
      initialSnapshot: { status: AUTH_STATUS.UNKNOWN, user: null },
    });

    expect(await screen.findByRole('heading', { name: '통합 프로젝트' }))
      .toBeInTheDocument();
    expect(session.bootstrap).toHaveBeenCalledOnce();
    expect(client.get).toHaveBeenCalledWith('/projects/21');
  });
});
