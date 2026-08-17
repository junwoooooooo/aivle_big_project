import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { ApiClientProvider } from '../../shared/api/ApiClientProvider.jsx';
import WorkspaceHomePage from './WorkspaceHomePage.jsx';

vi.mock('../auth/AuthProvider.jsx', () => ({ useAuth: () => ({ user: { displayName: '민준' } }) }));
vi.mock('../service-policy/useServicePolicy.js', () => ({
  useServicePolicy: () => ({ loading: false, policy: { maintenanceMode: false, registrationEnabled: true }, error: null }),
}));

describe('workspace home', () => {
  it('프로젝트가 없으면 canonical 6단계 onboarding rail을 표시한다', async () => {
    render(<MemoryRouter><ApiClientProvider client={{ get: vi.fn(async () => ({ data: [] })) }}><WorkspaceHomePage /></ApiClientProvider></MemoryRouter>);
    expect(await screen.findByText('6단계 사업 여정')).toBeInTheDocument();
    expect(document.querySelectorAll('.getting-started-rail li')).toHaveLength(6);
    expect(screen.queryByText('8단계 사업 여정')).not.toBeInTheDocument();
  });

  it('프로젝트가 있으면 전체 폭에서 이어서 할 일·확인 항목·최근 프로젝트를 보여준다', async () => {
    const project = { id: 5, title: '스마트 킥포인트', industryCategory: '모빌리티', status: 'DRAFT',
      presentationState: 'NEEDS_ATTENTION', attentionCount: 1, attentionReason: '재무 입력을 확인해 주세요.',
      currentJourneyLabel: '출시 준비', completedJourneyCount: 2,
      createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-13T00:00:00Z' };
    render(<MemoryRouter><ApiClientProvider client={{ get: vi.fn(async () => ({ data: [project] })) }}><WorkspaceHomePage /></ApiClientProvider></MemoryRouter>);
    expect((await screen.findAllByRole('heading', { name: '스마트 킥포인트' })).length).toBeGreaterThanOrEqual(2);
    expect(screen.getByRole('heading', { name: '지금 살펴볼 프로젝트' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '최근 수정한 프로젝트' })).toBeInTheDocument();
    expect(document.querySelector('.workspace-home__layout--single')).toBeInTheDocument();
    expect(screen.getAllByText('확인 필요').length).toBeGreaterThanOrEqual(1);
  });
});
