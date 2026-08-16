import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { BusinessValidationContent } from './BusinessValidationPage.jsx';

vi.mock('../../market/MarketResearchPage.jsx', () => ({
  MarketResultBody: () => <div>시장 결과 본문</div>,
}));
vi.mock('../../market/BmCanvasPage.jsx', () => ({
  BusinessModelResultBody: () => <div>비즈니스 모델 결과 본문</div>,
}));
vi.mock('../../market/useCellFocus.js', () => ({
  default: () => ({ active: null, jump: vi.fn() }),
}));

const stage = (state, result = null) => ({ state, result });
const view = (state, market = stage('WAITING'), businessModel = stage('WAITING')) => ({
  state, stale: state === 'STALE', market, businessModel, actions: [],
});
const api = {
  currentCompetitorSeeds: vi.fn().mockResolvedValue({ seeds: [] }),
  saveCompetitorSeeds: vi.fn(),
};

describe('BusinessValidationContent', () => {
  it('준비 정보와 하나의 사업 검증 시작 명령을 보여준다', async () => {
    render(<BusinessValidationContent current={view('NOT_STARTED')} plan={{ revision: 1 }} api={api} />);
    expect(screen.getByRole('button', { name: '사업 검증 시작' })).toBeInTheDocument();
    expect(screen.getByText('현재 검증 기준을 확인하세요')).toBeInTheDocument();
  });

  it('시장 분석 실행 중에는 BM을 대기로 표시한다', () => {
    render(<BusinessValidationContent current={view('MARKET_RUNNING', stage('RUNNING'))} api={api} />);
    expect(screen.getByText('사업 검증 진행 중')).toBeInTheDocument();
    expect(screen.getByText('대기')).toBeInTheDocument();
  });

  it('시장 완료 후 BM 실행 중에도 시장 결과를 보존한다', () => {
    render(<BusinessValidationContent current={view('BM_RUNNING',
      stage('SUCCEEDED', { market: {} }), stage('RUNNING'))} api={api} />);
    expect(screen.getByText('시장 결과 본문')).toBeInTheDocument();
    expect(screen.getByText('비즈니스 모델')).toBeInTheDocument();
    expect(screen.getAllByText('진행 중').length).toBeGreaterThan(0);
  });

  it('BM 실패 시 시장 결과와 BM 전용 재시도를 함께 보여준다', () => {
    render(<BusinessValidationContent current={view('BM_FAILED',
      stage('SUCCEEDED', { market: {} }), stage('FAILED'))} api={api} />);
    expect(screen.getByText('시장 결과 본문')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'BM 다시 시도' })).toBeInTheDocument();
    expect(screen.getByText(/같은 시장 결과로 비즈니스 모델만/)).toBeInTheDocument();
  });

  it('완료되면 시장과 BM 결과를 한 화면에 표시한다', () => {
    render(<BusinessValidationContent current={view('COMPLETED',
      stage('SUCCEEDED', { market: {} }), stage('SUCCEEDED', { bm: {} }))} api={api} />);
    expect(screen.getByText('시장 결과 본문')).toBeInTheDocument();
    expect(screen.getByText('비즈니스 모델 결과 본문')).toBeInTheDocument();
  });
});
