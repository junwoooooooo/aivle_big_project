import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { ApiClientProvider } from '../../shared/api/ApiClientProvider.jsx';
import FinalReportPage from './FinalReportPage.jsx';

const status = { state: 'READY', currentVersion: null, generatedAt: null, stale: false,
  taskRunId: null, blockingSources: [], availableSources: ['PROJECT', 'CURRENT_CONCEPT',
    'BUSINESS_VALIDATION_SESSION', 'MARKET', 'BUSINESS_MODEL', 'MARKETING_STRATEGY', 'MARKETING',
    'LAUNCH_TECHNOLOGY'], omittedSources: [], sourceStates: { MARKET_INTERVIEW: 'FAILED',
      MARKETING_STRATEGY: 'AVAILABLE', MARKETING: 'AVAILABLE_DRAFT', LAUNCH_TECHNOLOGY: 'AVAILABLE',
      LAUNCH_OPERATIONS: 'NOT_RUN', FINANCE: 'CURRENT_RESULT_UNAVAILABLE' } };
const proposal = { contract: 'final-business-proposal-result-v1', cover: { documentName: '사업기획서',
  businessName: '자전거 운영 분석', createdOn: '2026-08-18', version: 'v1', documentStatus: '검토용', approvalPlaceholder: '결재 / 검토' },
executiveDecisionSummary: { businessDefinition: '자전거 운영 데이터를 분석합니다.', purpose: '운영 효율 개선',
  targetCustomers: ['대여 운영 조직'], coreValue: '관리 근거 제공', marketEvidence: ['관측 근거'], financialHighlights: [],
  keyRisks: ['실제 고객 확인 필요'], approvalRequest: '파일럿 승인', evidenceRefs: ['CURRENT_CONCEPT:concept-1'] },
sections: [{ number: 1, title: '사업 추진 배경 및 목적', summary: '운영 문제를 해결합니다.', narratives: [{ heading: '문제', body: '관리 효율이 필요합니다.' }], keyPoints: ['운영 조건 확인'], tables: [], evidenceRefs: ['CURRENT_CONCEPT:concept-1'] }],
decisionRequest: { approvalRequests: ['파일럿'], conditionalApprovals: [], requiredChecks: [], nextActions: ['고객 확인'] },
appendix: { assumptions: [], omittedAnalyses: [], sourceVersions: ['현재 사업안'] } };
const view = { state: 'CURRENT', snapshotId: 'snapshot-1', version: 1, generatedAt: '2026-08-18T00:00:00Z',
  sourceManifest: { sources: [{ type: 'CURRENT_CONCEPT', id: 'concept-1' }] }, report: proposal };

function clientFor(currentStatus = status) {
  return { get: vi.fn(async (url) => {
    if (url.endsWith('/status')) return { data: currentStatus };
    if (url.endsWith('/review')) return { data: { status: 'NOT_STARTED', result: null } };
    return { data: view };
  }), post: vi.fn(async () => ({ data: { taskRunId: 'task-1', status: 'QUEUED' } })) };
}
function renderPage(client) { return render(<MemoryRouter initialEntries={['/app/projects/41/final-report']}><ApiClientProvider client={client}><Routes><Route path="/app/projects/:projectId/final-report" element={<FinalReportPage />} /></Routes></ApiClientProvider></MemoryRouter>); }

describe('Final business proposal workspace', () => {
  it('status endpoint만 먼저 사용하고 선택 가능한 source 준비 화면을 제공한다', async () => {
    const client = clientFor(); renderPage(client);
    expect(await screen.findByRole('heading', { name: '사업기획서 작성' })).toBeInTheDocument();
    expect(screen.getByText('현재 확정 사업안')).toBeInTheDocument();
    expect(screen.getByText('마케팅 전략')).toBeInTheDocument();
    expect(screen.getByText('초안 있음 · 검토 전')).toBeInTheDocument();
    expect(screen.getByText('최근 실행 실패 · 포함할 결과 없음')).toBeInTheDocument();
    expect(screen.getByText('실행 안 함')).toBeInTheDocument();
    expect(screen.queryByText(/트윈 패널/)).not.toBeInTheDocument();
    expect(client.get).toHaveBeenCalledTimes(1);
    expect(client.get.mock.calls[0][0]).toMatch(/\/status$/);
  });

  it('page-level 회색 직사각형 배경을 만들지 않는다', () => {
    const css = readFileSync('src/features/final-report/final-report.css', 'utf8');
    const pageRule = css.match(/\.final-report-page\{[^}]+\}/)?.[0] ?? '';
    expect(pageRule).not.toContain('background:#edf1f0');
    expect(pageRule).not.toContain('margin:-.5rem');
  });

  it('선택 source를 generation request에 전달한다', async () => {
    const client = clientFor(); renderPage(client);
    fireEvent.click(await screen.findByRole('button', { name: '사업기획서 만들기' }));
    await waitFor(() => expect(client.post).toHaveBeenCalled());
    expect(client.post.mock.calls[0][1].includedOptionalSources).toContain('MARKETING_STRATEGY');
  });

  it('structured proposal과 PDF/DOCX 및 독립 AI 검토 action을 표시한다', async () => {
    const client = clientFor({ ...status, state: 'CURRENT', currentVersion: 1 }); renderPage(client);
    expect(await screen.findByRole('heading', { name: '사업기획서' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '의사결정 요약' })).toBeInTheDocument();
    expect(screen.getByText('PDF 다운로드')).toHaveAttribute('href', expect.stringMatching(/\/pdf$/));
    expect(screen.getByText('DOCX 다운로드')).toHaveAttribute('href', expect.stringMatching(/\/docx$/));
    fireEvent.click(screen.getByRole('button', { name: 'AI 사업기획서 검토' }));
    await waitFor(() => expect(client.post).toHaveBeenCalledWith(expect.stringMatching(/\/review$/), {}, expect.anything()));
  });
});
