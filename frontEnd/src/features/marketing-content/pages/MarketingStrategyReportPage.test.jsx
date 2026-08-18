import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { expect, it, vi } from 'vitest';
import MarketingStrategyReportPage from './MarketingStrategyReportPage.jsx';
import useMarketingStrategy from '../hooks/useMarketingStrategy.js';

vi.mock('../hooks/useMarketingStrategy.js', () => ({ default: vi.fn() }));

it('현재 전략을 A4 보고서 구조와 사용자용 근거 label로 표시한다', () => {
  useMarketingStrategy.mockReturnValue({ loading: false, downloading: false, error: null,
    refresh: vi.fn(), download: vi.fn(), view: { generatedAt: '2026-08-18T00:00:00Z', projectName: '자전거 운영 혁신', result: {
      executiveSummary: '자전거 운영 조직을 위한 실행 전략', targetCustomers: ['지자체 운영 담당자'],
      positioning: '운영 데이터를 실행 정보로 전환', coreMessages: ['재배치 판단을 빠르게'],
      contentPillars: ['운영 효율'], channelStrategies: [], campaignRoadmap: [],
      budgetGuidelines: ['확인된 범위에서 배분'], risks: ['성과를 단정하지 않음'],
      evidenceRefs: ['FINANCE:finance-1', 'FINANCE_REPORT:report-1'],
    } } });
  render(<MemoryRouter initialEntries={['/app/projects/7/marketing/report']}><Routes>
    <Route path="/app/projects/:projectId/marketing/report" element={<MarketingStrategyReportPage />} />
  </Routes></MemoryRouter>);
  expect(screen.getByRole('heading', { name: '마케팅 전략 보고서' })).toBeInTheDocument();
  expect(screen.getAllByText('자전거 운영 혁신').length).toBeGreaterThan(0);
  expect(screen.getByRole('heading', { name: '5. 채널 전략' })).toBeInTheDocument();
  expect(screen.getAllByText('재무 분석')).toHaveLength(1);
  expect(screen.queryByText(/FINANCE:/)).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'PDF로 저장' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'PDF 다운로드' })).not.toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '목차' })).toBeInTheDocument();
  expect(screen.getByText('서명/날인')).toBeInTheDocument();
});
