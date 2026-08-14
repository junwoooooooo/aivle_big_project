import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { ApiClientProvider } from '../../shared/api/ApiClientProvider.jsx';
import FinalReportPage from './FinalReportPage.jsx';

const report = {
  state: 'CURRENT', snapshotId: 'report-1', version: 1, generatedAt: '2026-08-13T00:00:00Z',
  sourceManifestHash: `sha256:${'1'.repeat(64)}`,
  sourceManifest: [{ type: 'MARKET', id: '10', version: 2, resultHash: `sha256:${'2'.repeat(64)}` }],
  report: { title: '사업 타당성 검토 보고서', metadata: { projectName: '스마트 이동', generatedAt: '2026-08-13T00:00:00Z', version: 1 }, sections: [] },
  readiness: [], missingSources: [],
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
    expect(client.post).toHaveBeenCalledWith('/api/v3/projects/41/final-report/generate', {}, undefined);
  });
});
