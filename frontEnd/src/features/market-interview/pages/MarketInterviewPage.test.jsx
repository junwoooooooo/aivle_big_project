import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiClientProvider } from '../../../shared/api/ApiClientProvider.jsx';
import MarketInterviewPage from './MarketInterviewPage.jsx';

const result = {
  contract: 'market-interview-result-v2', schemaVersion: '2.0', synthetic: true,
  usableInterviewCount: 19, codedInterviewCount: 19, codingFailureCount: 0,
  targeting: { criteriaText: '서울 조건 교집합 80명', requestedSampleSize: 20, drawnSampleSize: 20,
    attemptedCount: 20, usableCount: 19, failedCount: 1, targetCount: 15, nonTargetCount: 4 },
  participants: [{ participantId: 'R001', label: '가상 참여자 A', profile: '소규모 매장 운영자', context: '도입 전 비교', needs: ['간단한 설정'], group: 'TARGET' }],
  interviews: [{ participantId: 'R001', questions: [{ question: '무엇이 걱정되나요?', answer: '도입 시간이 걱정됩니다.', uncertainty: '실제 현장 확인 필요' }] }],
  themes: [{ axis: 'CONCERN', title: '도입 부담', description: '설정과 지원을 먼저 확인하려는 관점', participantIds: ['R001'], mentionCount: 1, quote: '도입 시간이 걱정됩니다.' }],
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
    expect(await screen.findByRole('button', { name: '가상 고객 인터뷰 시작' })).toBeInTheDocument();
    expect(screen.getByText(/RESEARCH MISSION/)).toBeInTheDocument();
    for (const step of ['표집', '가상 인터뷰', '응답 코딩', '반복 패턴', '실제 고객 질문']) expect(screen.getByText(step)).toBeInTheDocument();
    expect(screen.getByText(/실제 고객에게 조사한 결과는 아닙니다/)).toBeInTheDocument();
    expect(client.post).not.toHaveBeenCalled();
  });

  it('starts only after the user clicks', async () => {
    const client = { get: vi.fn().mockResolvedValue({ data: current('NOT_STARTED') }),
      post: vi.fn().mockResolvedValue({ data: current('RUNNING') }) };
    renderPage(client);
    fireEvent.click(await screen.findByRole('button', { name: '가상 고객 인터뷰 시작' }));
    await waitFor(() => expect(client.post).toHaveBeenCalledTimes(1));
    expect(client.post.mock.calls[0][0]).toMatch(/\/market-interview$/);
    expect(client.post.mock.calls[0][1]).toEqual({ sampleSize: 20 });
    expect(await screen.findByText(/실제 실행 상태를 기다리고/)).toBeInTheDocument();
  });

  it('offers only the 20, 40 and 80 profile-bank sample contract', async () => {
    const client = { get: vi.fn().mockResolvedValue({ data: current('NOT_STARTED') }), post: vi.fn() };
    renderPage(client);
    expect(await screen.findByRole('radio', { name: /20명/ })).toBeChecked();
    expect(screen.getByRole('radio', { name: /40명/ })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /80명/ })).toBeInTheDocument();
  });

  it('renders RUNNING without implying contact with real customers', async () => {
    renderPage({ get: vi.fn().mockResolvedValue({ data: current('RUNNING') }), post: vi.fn() });
    expect(await screen.findByText(/실제 고객에게 연락하거나 조사하는 과정은 아닙니다/)).toBeInTheDocument();
  });

  it('renders structured participants, themes and follow-up questions', async () => {
    renderPage({ get: vi.fn().mockResolvedValue({ data: current('SUCCEEDED', { result }) }), post: vi.fn() });
    expect(await screen.findByRole('heading', { name: 'Respondent Explorer' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '이번 탐색에서 먼저 볼 인사이트' })).toBeInTheDocument();
    expect(screen.getAllByText('가상 참여자 A')).toHaveLength(2);
    expect(screen.getAllByText('도입 부담')).toHaveLength(2);
    expect(screen.getByText(/응답 생성 실패 1명은 모든 코딩과 집계에서 제외/)).toBeInTheDocument();
    expect(screen.getByText('15명')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '실제 고객에게 확인할 질문' })).toBeInTheDocument();
  });

  it('shows stale history and requires an explicit current-source restart', async () => {
    const client = { get: vi.fn().mockResolvedValue({ data: current('STALE', { result }) }), post: vi.fn() };
    renderPage(client);
    expect(await screen.findByText(/이전 결과를 current로 표시하지 않습니다/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '현재 사업안으로 다시 인터뷰' })).toBeInTheDocument();
    expect(client.post).not.toHaveBeenCalled();
  });

  it('offers retry only when the backend allows it', async () => {
    const client = { get: vi.fn().mockResolvedValue({ data: current('FAILED', { retryAllowed: true, restartAllowed: true, failure: '잠시 후 다시 시도해 주세요.' }) }),
      post: vi.fn().mockResolvedValue({ data: current('RUNNING', { attempt: 2 }) }) };
    renderPage(client);
    fireEvent.click(await screen.findByRole('button', { name: '실패한 실행 다시 시도' }));
    await waitFor(() => expect(client.post.mock.calls[0][0]).toMatch(/\/market-interview\/retry$/));
  });

  it('starts a new attempt-one run when retry is exhausted', async () => {
    const exhausted = current('FAILED', { attempt: 3, requestedSampleSize: 40,
      retryAllowed: false, restartAllowed: true });
    const client = { get: vi.fn().mockResolvedValue({ data: exhausted }),
      post: vi.fn().mockResolvedValue({ data: current('RUNNING', { attempt: 1, requestedSampleSize: 40 }) }) };
    renderPage(client);
    fireEvent.click(await screen.findByRole('button', { name: '현재 사업안으로 새 인터뷰 시작' }));
    await waitFor(() => expect(client.post).toHaveBeenCalled());
    expect(client.post.mock.calls[0][0]).toMatch(/\/market-interview$/);
    expect(client.post.mock.calls[0][1]).toEqual({ sampleSize: 40 });
  });

  it('refreshes a raced 409 and explains the restart path', async () => {
    const conflict = Object.assign(new Error('conflict'), { status: 409, code: 'JOB_RETRY_NOT_ALLOWED' });
    const client = { get: vi.fn()
      .mockResolvedValueOnce({ data: current('FAILED', { retryAllowed: true, restartAllowed: true }) })
      .mockResolvedValueOnce({ data: current('FAILED', { attempt: 3, requestedSampleSize: 20,
        retryAllowed: false, restartAllowed: true }) }),
    post: vi.fn().mockRejectedValue(conflict) };
    renderPage(client);
    fireEvent.click(await screen.findByRole('button', { name: '실패한 실행 다시 시도' }));
    expect(await screen.findByText(/재시도 횟수를 모두 사용했습니다/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '현재 사업안으로 새 인터뷰 시작' })).toBeInTheDocument();
    expect(client.get).toHaveBeenCalledTimes(2);
  });

  it('recovers ambiguous POST with exactly one current GET and never resends the mutation', async () => {
    const client = { get: vi.fn()
      .mockResolvedValueOnce({ data: current('NOT_STARTED') })
      .mockResolvedValueOnce({ data: current('RUNNING') }),
    post: vi.fn().mockRejectedValue(new Error('network ambiguity')) };
    renderPage(client);
    fireEvent.click(await screen.findByRole('button', { name: '가상 고객 인터뷰 시작' }));
    await screen.findByText(/실제 실행 상태를 기다리고/);
    expect(client.post).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledTimes(2);
  });

  it('B2B organization concept를 전체 개인 패널 TARGET으로 미리 표시하지 않는다', async () => {
    const concept = { identity: { conceptName: '스마트 킥포인트 - 데이터 분석 서비스',
      conceptDefinition: 'AI 카메라 데이터로 자전거 대여 운영 효율을 높입니다.',
      targetUsers: ['자전거 대여 운영 조직', '지자체'] } };
    renderPage({ get: vi.fn().mockResolvedValue({ data: current('NOT_STARTED', {
      concept, targetingPreview: { customerUnit: 'ORGANIZATION' },
    }) }), post: vi.fn() });
    expect(await screen.findByText('스마트 킥포인트 - 데이터 분석 서비스')).toBeInTheDocument();
    expect(screen.getAllByText('직접 타겟 표현 불가 · 탐색 표본')).toHaveLength(2);
    expect(screen.queryByText(/패널 전체가 타겟/)).not.toBeInTheDocument();
  });

  it('never presents synthetic output as market evidence or a population statistic', async () => {
    renderPage({ get: vi.fn().mockResolvedValue({ data: current('SUCCEEDED', { result }) }), post: vi.fn() });
    expect(await screen.findByText(/시장 근거나 통계로 인용하지 말고/)).toBeInTheDocument();
    expect(screen.queryByText(/시장 점유율|구매 전환율|표본 대표성/)).not.toBeInTheDocument();
  });
});
