import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { expect, it, vi } from 'vitest';
import MarketingStrategyPanel from './MarketingStrategyPanel.jsx';

const result = {
  executiveSummary: '현재 사업안 기준 실행 전략', targetCustomers: ['지자체 운영 담당자'],
  positioning: '자전거 운영 데이터를 이해하기 쉬운 실행 정보로 전환',
  coreMessages: ['회수와 재배치 판단을 빠르게'], contentPillars: ['운영 효율', '데이터 근거'],
  channelStrategies: [{ channel: '공공 제안', objective: '검토 기회 확보', audience: '지자체 담당자',
    rationale: '조직 구매 절차에 맞는 채널', actions: ['운영 사례 제시'], kpis: ['제안 검토 수'] }],
  campaignRoadmap: [{ phase: '1단계', objective: '메시지 확인', actions: ['사례 정리'], kpis: ['검토 완료'] }],
  budgetGuidelines: ['확인된 예산 범위에서 채널별로 배분'], risks: ['실제 성과처럼 단정하지 않음'],
  evidenceRefs: ['CURRENT_CONCEPT:concept-1', 'MARKET:market-2'],
};

it('전략의 채널 실행·KPI·로드맵·예산·근거를 빠짐없이 표시한다', () => {
  const onNext = vi.fn();
  render(<MemoryRouter><MarketingStrategyPanel onNext={onNext} strategy={{ current: true, ready: true, active: false,
    downloading: false, generate: vi.fn(), download: vi.fn(),
    view: { ready: true, stale: false, sourceManifest: [{ type: 'CURRENT_CONCEPT' }, { type: 'MARKET' },
      { type: 'FINANCE' }, { type: 'FINANCE_REPORT' }], result } }} /></MemoryRouter>);
  expect(screen.getByText('지자체 운영 담당자')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '캠페인 로드맵' })).toBeInTheDocument();
  expect(screen.getByText('확인된 예산 범위에서 채널별로 배분')).toBeInTheDocument();
  fireEvent.click(screen.getByText('실행 항목·KPI 보기'));
  expect(screen.getByText('운영 사례 제시')).toBeInTheDocument();
  expect(screen.getAllByText('제안 검토 수').length).toBeGreaterThan(0);
  expect(screen.getAllByText('재무 분석')).toHaveLength(1);
  expect(screen.getByRole('link', { name: '보고서 보기' })).toHaveAttribute('href', '/report');
  fireEvent.click(screen.getByRole('button', { name: '이 전략으로 콘텐츠 만들기' }));
  expect(onNext).toHaveBeenCalledOnce();
});

it('재생성 중 실제 event stage를 3단계 rail로 표시하고 기존 결과를 유지한다', () => {
  render(<MemoryRouter><MarketingStrategyPanel onNext={vi.fn()} strategy={{ current: true, ready: true,
    active: true, generating: true, generate: vi.fn(), jobEvents: { events: [{ stage: 'ANALYZING' }] },
    view: { ready: true, stale: false, status: 'RUNNING', sourceManifest: [], result } }} /></MemoryRouter>);
  expect(screen.getByText('최신 자료로 전략을 다시 작성하고 있습니다.')).toBeInTheDocument();
  expect(screen.getByText('입력 자료 확인').closest('li')).toHaveAttribute('data-state', 'complete');
  expect(screen.getByText('전략 작성').closest('li')).toHaveAttribute('data-state', 'active');
  expect(screen.getByText('현재 사업안 기준 실행 전략')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '최신 전략 생성 중…' })).toBeDisabled();
});
