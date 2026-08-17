import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { ApiClientProvider } from '../../shared/api/ApiClientProvider.jsx';
import FinalReportPage from './FinalReportPage.jsx';

const report = {
  state: 'CURRENT', snapshotId: 'report-1', version: 1, generatedAt: '2026-08-13T00:00:00Z',
  sourceManifestHash: `sha256:${'1'.repeat(64)}`,
  sourceManifest: { schemaVersion: 2, sources: [{ type: 'MARKET', id: 'internal-market-id', resultHash: `sha256:${'2'.repeat(64)}` }] },
  report: { title: '사업 타당성 검토 보고서', metadata: { projectName: '스마트 이동', generatedAt: '2026-08-13T00:00:00Z', version: 1 }, sections: [], caveat: '시장 인터뷰와 트윈 패널 조사는 AI 가상 참여자를 활용한 탐색·시뮬레이션입니다.' },
  readiness: [], missingSources: [], blockingSources: [], omittedSources: [],
};

function renderPage(client) {
  return render(<MemoryRouter initialEntries={['/app/projects/41/final-report']}><ApiClientProvider client={client}><Routes><Route path="/app/projects/:projectId/final-report" element={<FinalReportPage />} /></Routes></ApiClientProvider></MemoryRouter>);
}

describe('final report page', () => {
  it('현재 snapshot을 사무 문서와 PDF 저장 action으로 표시한다', async () => {
    renderPage({ get: vi.fn(async () => ({ data: report })) });
    expect(await screen.findByRole('heading', { name: '사업 타당성 검토 보고서' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'PDF로 저장' })).toBeInTheDocument();
    expect(screen.getByText('최신 보고서')).toBeInTheDocument();
  });

  it('미완료 상태에서 생성 응답을 반영한다', async () => {
    const notReady = { ...report, state: 'NOT_READY', snapshotId: null, version: null,
      readiness: [{ journeyId: 'planning', label: '사업 기획', status: 'IN_PROGRESS' }], missingSources: ['MARKET'] };
    const client = { get: vi.fn(async () => ({ data: notReady })), post: vi.fn(async () => ({ data: report })) };
    renderPage(client);
    fireEvent.click(await screen.findByRole('button', { name: '최종 보고서 만들기' }));
    expect(await screen.findByText('최신 보고서')).toBeInTheDocument();
    expect(client.post).toHaveBeenCalledTimes(1);
  });

  it('6단계와 synthetic 한계를 표시하고 raw provenance를 숨긴다', async () => {
    renderPage({ get: vi.fn(async () => ({ data: report })) });
    expect(await screen.findByLabelText('6단계')).toBeInTheDocument();
    expect(screen.getByText(/AI 가상 참여자/)).toBeInTheDocument();
    expect(screen.queryByText('internal-market-id')).not.toBeInTheDocument();
    expect(screen.queryByText(`sha256:${'2'.repeat(64)}`)).not.toBeInTheDocument();
  });

  it('시장 인터뷰와 트윈 패널을 분리하고 legacy 명칭을 사용하지 않는다', async () => {
    const separated = { ...report, report: { ...report.report, sections: [
      { number: '4', title: '시장 인터뷰', sources: [{ type: 'MARKET_INTERVIEW', status: 'MISSING', label: '아직 시장 인터뷰를 진행하지 않았습니다.' }] },
      { number: '5', title: '트윈 패널 조사', sources: [{ type: 'TWIN_SURVEY', status: 'MISSING', label: '트윈 패널 조사를 진행하지 않았습니다.' }] },
    ] } };
    renderPage({ get: vi.fn(async () => ({ data: separated })) });
    expect(await screen.findByRole('heading', { name: '4. 시장 인터뷰' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '5. 트윈 패널 조사' })).toBeInTheDocument();
    expect(screen.queryByText('가상 인터뷰')).not.toBeInTheDocument();
  });

  it('모호한 generate 실패에서 POST를 반복하지 않고 current GET을 한 번만 복구 조회한다', async () => {
    const stale = { ...report, state: 'STALE', snapshotId: 'old', version: 1 };
    const current = { ...report, snapshotId: 'new', version: 2 };
    const client = { get: vi.fn().mockResolvedValueOnce({ data: stale }).mockResolvedValueOnce({ data: current }),
      post: vi.fn(async () => { throw new Error('network ambiguity'); }) };
    renderPage(client);
    fireEvent.click(await screen.findByRole('button', { name: '보고서 업데이트' }));
    expect(await screen.findByText('최신 보고서')).toBeInTheDocument();
    expect(client.post).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledTimes(2);
  });
});
