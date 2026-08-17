import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import MarketingContentPage from './MarketingContentPage.jsx';
import useMarketingContent from '../hooks/useMarketingContent.js';
import useMarketingStrategy from '../hooks/useMarketingStrategy.js';

vi.mock('../hooks/useMarketingContent.js', () => ({ default: vi.fn() }));
vi.mock('../hooks/useMarketingStrategy.js', () => ({ default: vi.fn() }));

const reportId = 'a'.repeat(64);
function contentHook(overrides = {}) { return { loading: false, list: [], source: { snapshotId: 'source-1', snapshot: {
  conceptName: '자전거 운영 분석', targetSegment: '운영 조직', valueProposition: '관리 효율', prohibitedClaims: [], requiredDisclosures: [],
} }, selected: null, error: null, saving: false, uploading: false, active: false, status: 'IDLE', jobEvents: { events: [] },
refresh: vi.fn(), open: vi.fn(), uploadReference: vi.fn(), create: vi.fn().mockResolvedValue({ content: { contentId: 'c1' } }),
save: vi.fn(), finalize: vi.fn(), regenerate: vi.fn(), retry: vi.fn(), ...overrides }; }
function strategyHook(overrides = {}) { return { loading: false, active: false, current: true, ready: true,
  generating: false, downloading: false, error: null, refresh: vi.fn(), generate: vi.fn(), download: vi.fn(),
  view: { reportId, status: 'SUCCEEDED', sourceManifest: [], result: { executiveSummary: '전략 요약', targetCustomers: ['운영 조직'], positioning: '운영 효율', coreMessages: ['효율'], channelStrategies: [], contentPillars: [], campaignRoadmap: [], budgetGuidelines: [], risks: [], evidenceRefs: [] } }, ...overrides }; }
function renderPage() { return render(<MemoryRouter initialEntries={['/app/projects/1/marketing']}><Routes><Route path="/app/projects/:projectId/marketing" element={<MarketingContentPage />} /></Routes></MemoryRouter>); }
function openContentWorkspace() { fireEvent.click(screen.getByRole('button', { name: /콘텐츠 제작/ })); }
function fillRequired() { fireEvent.change(screen.getByLabelText('채널'), { target: { value: 'B2B 제안' } }); fireEvent.change(screen.getByLabelText('목적'), { target: { value: '상담 확보' } }); }

describe('Marketing workspace', () => {
  beforeEach(() => { vi.clearAllMocks(); useMarketingContent.mockReturnValue(contentHook()); useMarketingStrategy.mockReturnValue(strategyHook()); });

  it('강제 wizard 대신 전략과 콘텐츠 독립 workspace를 제공한다', () => {
    renderPage();
    expect(screen.getByRole('heading', { name: '마케팅 전략' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /마케팅 전략/ })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByText('AI가 현재 확정된 컨셉을 바탕으로 만든 초안입니다.')).not.toBeInTheDocument();
    openContentWorkspace();
    expect(screen.getByRole('heading', { name: '만들 콘텐츠를 설정하세요' })).toBeInTheDocument();
    expect(screen.getByText('생성 이력 · 0개')).toBeInTheDocument();
  });

  it('전략 없이 현재 사업안 기준으로 콘텐츠를 생성한다', async () => {
    const create = vi.fn().mockResolvedValue({ content: { contentId: 'c1' } });
    useMarketingContent.mockReturnValue(contentHook({ create }));
    useMarketingStrategy.mockReturnValue(strategyHook({ current: false, view: null }));
    renderPage(); openContentWorkspace(); fillRequired();
    fireEvent.click(screen.getByRole('button', { name: '마케팅 초안 만들기' }));
    await waitFor(() => expect(create).toHaveBeenCalled());
    expect(create.mock.calls[0][0].marketingStrategyReportId).toBeNull();
  });

  it('사용자가 최신 전략 적용을 선택한 경우에만 report id를 전송한다', async () => {
    const create = vi.fn().mockResolvedValue({ content: { contentId: 'c1' } });
    useMarketingContent.mockReturnValue(contentHook({ create }));
    renderPage(); openContentWorkspace();
    fireEvent.click(screen.getByRole('radio', { name: /최신 마케팅 전략 적용/ })); fillRequired();
    fireEvent.click(screen.getByRole('button', { name: '마케팅 초안 만들기' }));
    await waitFor(() => expect(create).toHaveBeenCalled());
    expect(create.mock.calls[0][0].marketingStrategyReportId).toBe(reportId);
  });
});
