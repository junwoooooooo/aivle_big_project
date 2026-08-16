import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiClientProvider } from '../../../shared/api/ApiClientProvider.jsx';
import MarketInterviewPage from './MarketInterviewPage.jsx';

const result = {
  contract: 'market-interview-result-v1', schemaVersion: '1.0', synthetic: true,
  participants: [{ participantId: 'P1', label: '가상 참여자 A', profile: '소규모 매장 운영자', context: '도입 전 비교', needs: ['간단한 설정'] }],
  interviews: [{ participantId: 'P1', questions: [{ question: '무엇이 걱정되나요?', answer: '도입 시간이 걱정됩니다.', uncertainty: '실제 현장 확인 필요' }] }],
  themes: [{ title: '도입 부담', description: '설정과 지원을 먼저 확인하려는 관점', participantIds: ['P1'] }],
  objections: ['지원 범위'], unmetNeeds: ['초기 교육'], purchaseTriggers: ['간단한 설정'],
  followUpQuestions: ['현재 어떻게 해결하나요?'], limitations: ['실제 고객 조사 결과가 아닙니다.'],
};
const current = (state, extra = {}) => ({ state, stale: state === 'STALE', attempt: 1, ...extra });

function renderPage(client) {
  return render(<MemoryRouter initialEntries={['/app/projects/41/market-interview']}>
    <ApiClientProvider client={client}><Routes>
      <Route path="/app/projects/:projectId/market-interview" element={<MarketInterviewPage />} />
    </Routes></ApiClientProvider>
  </MemoryRouter>);
}

describe('MarketInterviewPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('shows the explicit NOT_STARTED CTA and synthetic disclaimer without auto-starting', async () => {
    const client = { get: vi.fn().mockResolvedValue({ data: current('NOT_STARTED') }), post: vi.fn() };
    renderPage(client);
    expect(await screen.findByRole('button', { name: '시장 인터뷰 시작' })).toBeInTheDocument();
    expect(screen.getByText(/실제 고객에게 조사한 결과는 아닙니다/)).toBeInTheDocument();
    expect(client.post).not.toHaveBeenCalled();
  });

  it('starts only after the user clicks', async () => {
    const client = { get: vi.fn().mockResolvedValue({ data: current('NOT_STARTED') }),
      post: vi.fn().mockResolvedValue({ data: current('RUNNING') }) };
    renderPage(client);
    fireEvent.click(await screen.findByRole('button', { name: '시장 인터뷰 시작' }));
    await waitFor(() => expect(client.post).toHaveBeenCalledTimes(1));
    expect(client.post.mock.calls[0][0]).toMatch(/\/market-interview$/);
    expect(await screen.findByText(/가상 고객 관점에서 사업안을 검토/)).toBeInTheDocument();
  });

  it('renders RUNNING without implying contact with real customers', async () => {
    renderPage({ get: vi.fn().mockResolvedValue({ data: current('RUNNING') }), post: vi.fn() });
    expect(await screen.findByText(/실제 고객에게 연락하거나 조사하는 과정은 아닙니다/)).toBeInTheDocument();
  });

  it('renders structured participants, themes and follow-up questions', async () => {
    renderPage({ get: vi.fn().mockResolvedValue({ data: current('SUCCEEDED', { result }) }), post: vi.fn() });
    expect(await screen.findByRole('heading', { name: '가상 참여자' })).toBeInTheDocument();
    expect(screen.getByText('가상 참여자 A')).toBeInTheDocument();
    expect(screen.getByText('도입 부담')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '실제 고객에게 확인할 질문' })).toBeInTheDocument();
  });

  it('shows stale history and requires an explicit current-source restart', async () => {
    const client = { get: vi.fn().mockResolvedValue({ data: current('STALE', { result }) }), post: vi.fn() };
    renderPage(client);
    expect(await screen.findByText(/이전 버전 기준/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '현재 사업안으로 다시 인터뷰' })).toBeInTheDocument();
    expect(client.post).not.toHaveBeenCalled();
  });

  it('offers retry only for a current FAILED run', async () => {
    const client = { get: vi.fn().mockResolvedValue({ data: current('FAILED', { failure: '잠시 후 다시 시도해 주세요.' }) }),
      post: vi.fn().mockResolvedValue({ data: current('RUNNING', { attempt: 2 }) }) };
    renderPage(client);
    fireEvent.click(await screen.findByRole('button', { name: '다시 시도' }));
    await waitFor(() => expect(client.post.mock.calls[0][0]).toMatch(/\/market-interview\/retry$/));
  });

  it('recovers ambiguous POST with exactly one current GET and never resends the mutation', async () => {
    const client = { get: vi.fn()
      .mockResolvedValueOnce({ data: current('NOT_STARTED') })
      .mockResolvedValueOnce({ data: current('RUNNING') }),
    post: vi.fn().mockRejectedValue(new Error('network ambiguity')) };
    renderPage(client);
    fireEvent.click(await screen.findByRole('button', { name: '시장 인터뷰 시작' }));
    await screen.findByText(/가상 고객 관점에서 사업안을 검토/);
    expect(client.post).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledTimes(2);
  });

  it('never presents synthetic output as market evidence or a population statistic', async () => {
    renderPage({ get: vi.fn().mockResolvedValue({ data: current('SUCCEEDED', { result }) }), post: vi.fn() });
    expect(await screen.findByText(/시장 근거나 통계로 인용하지 말고/)).toBeInTheDocument();
    expect(screen.queryByText(/시장 점유율|구매 전환율|표본 대표성/)).not.toBeInTheDocument();
  });
});
