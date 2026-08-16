import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiClientProvider } from '../../shared/api/ApiClientProvider.jsx';
import TwinSurveyPage from './TwinSurveyPage.jsx';

const draft = {
  situation: '가게에서 두 상품 중 하나를 고릅니다.', dropped: [],
  pairs: [{ pairId: 'P1', axis: '형태', rationale: '형태만 비교합니다.',
    X: { label: '신선형', attrs: { 형태: '신선' }, priceKrw: 10000 },
    Y: { label: '냉동형', attrs: { 형태: '냉동' }, priceKrw: 10000 } }],
};
const result = {
  synthetic: true, situation: draft.situation, sampleSize: 100,
  sampling: { requested: 100, drawn: 100, strata: {}, shortCells: {} },
  pairs: [], telemetry: {}, notes: ['실제 소비자 설문조사 결과가 아닙니다.'],
};
const current = (state = null, extra = {}) => ({
  run: state ? { state, taskState: state === 'RUNNING' ? 'RUNNING' : state, retryable: state === 'FAILED', errorCode: state === 'FAILED' ? 'EXECUTION_FAILED' : null } : null,
  version: extra.result ? { result: extra.result } : null, stale: state === 'STALE', ...extra,
});

function clientFor(survey, options = {}) {
  return {
    get: vi.fn(async (url) => url.endsWith('/stimulus-draft/current')
      ? { data: options.draft === false ? null : { state: 'SUCCEEDED', result: draft } }
      : { data: survey }),
    post: vi.fn(options.post ?? (async (url) => ({ data: url.endsWith('/retry')
      ? current('RUNNING') : { state: 'QUEUED', taskState: 'QUEUED' } }))),
  };
}

function renderPage(client) {
  return render(<MemoryRouter initialEntries={['/app/projects/41/twin-survey']}>
    <ApiClientProvider client={client}><Routes>
      <Route path="/app/projects/:projectId/twin-survey" element={<TwinSurveyPage />} />
    </Routes></ApiClientProvider>
  </MemoryRouter>);
}

async function chooseDraft() {
  fireEvent.click(await screen.findByRole('button', { name: /고른 1쌍으로 계속/ }));
}

describe('TwinSurveyPage alignment', () => {
  beforeEach(() => vi.clearAllMocks());

  it('uses the Twin Panel name, visible synthetic disclaimer, and no legacy virtual-interview wording', async () => {
    const client = clientFor(current()); renderPage(client);
    expect(await screen.findByRole('heading', { name: '트윈 패널 조사' })).toBeInTheDocument();
    expect(screen.getByText(/실제 소비자 설문조사 결과는 아닙니다/)).toBeInTheDocument();
    expect(screen.queryByText(/가상 인터뷰/)).not.toBeInTheDocument();
    expect(client.post).not.toHaveBeenCalled();
  });

  it('offers only 50, 100, 300 with 100 as the default and an explicit start CTA', async () => {
    renderPage(clientFor(current())); await chooseDraft();
    const slider = screen.getByRole('slider', { name: '가상 패널 규모' });
    expect(slider).toHaveAttribute('aria-valuetext', '100명');
    expect(screen.getByText('50')).toBeInTheDocument();
    expect(screen.getByText('100')).toBeInTheDocument();
    expect(screen.getByText('300')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '트윈 패널 조사 시작' })).toBeEnabled();
  });

  it('starts only on explicit click and renders RUNNING as a simulation', async () => {
    const client = clientFor(current()); renderPage(client); await chooseDraft();
    fireEvent.click(screen.getByRole('button', { name: '트윈 패널 조사 시작' }));
    await waitFor(() => expect(client.post).toHaveBeenCalledTimes(1));
    expect(client.post.mock.calls[0][0]).toMatch(/\/twin-survey$/);
  });

  it('renders a succeeded result as a virtual-panel result, not a population claim', async () => {
    renderPage(clientFor(current('SUCCEEDED', { result }), { draft: false }));
    expect(await screen.findByRole('heading', { name: '가상 패널 시뮬레이션 결과' })).toBeInTheDocument();
    expect(screen.getByText(/이 가상 패널 안에서의 비교 결과/)).toBeInTheDocument();
  });

  it('offers friendly FAILED retry through the retry endpoint', async () => {
    const client = clientFor(current('FAILED'), { draft: false }); renderPage(client);
    fireEvent.click(await screen.findByRole('button', { name: '다시 시도' }));
    await waitFor(() => expect(client.post.mock.calls[0][0]).toMatch(/\/twin-survey\/retry$/));
    expect(screen.queryByText('EXECUTION_FAILED')).not.toBeInTheDocument();
  });

  it('keeps stale history and requires an explicit current-concept rerun', async () => {
    const client = clientFor(current('STALE', { result }), { draft: false }); renderPage(client);
    expect(await screen.findByText(/이전 사업안 기준의 결과/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '현재 사업안으로 다시 조사' })).toBeInTheDocument();
    expect(client.post).not.toHaveBeenCalled();
  });

  it('recovers an ambiguous mutation with exactly one current GET and no mutation replay', async () => {
    let currentReads = 0;
    const client = clientFor(current(), { post: async () => { throw new Error('network ambiguity'); } });
    client.get.mockImplementation(async (url) => {
      if (url.endsWith('/stimulus-draft/current')) return { data: { state: 'SUCCEEDED', result: draft } };
      currentReads += 1;
      return { data: current(currentReads === 1 ? null : 'RUNNING') };
    });
    renderPage(client); await chooseDraft();
    fireEvent.click(screen.getByRole('button', { name: '트윈 패널 조사 시작' }));
    await screen.findByText(/가상 패널 응답을 시뮬레이션/);
    expect(client.post).toHaveBeenCalledTimes(1);
    expect(currentReads).toBe(2);
  });

  it('renders only the Twin Panel product on its canonical route', async () => {
    renderPage(clientFor(current(), { draft: false }));
    expect(await screen.findByRole('heading', { name: '트윈 패널 조사' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '시장 인터뷰' })).not.toBeInTheDocument();
  });
});
