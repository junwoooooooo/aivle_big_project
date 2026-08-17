import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import BusinessModelPage from './BusinessModelPage.jsx';

vi.mock('../hooks/useBusinessModel.js', () => ({ default: () => ({
  loading: false,
  marketSeed: { snapshotId: 'market-seed-1' },
  runs: [], busy: false, error: null, prepare: vi.fn(),
}) }));

describe('BusinessModelPage', () => {
  it('Market Seed 기반 BM 외부 모듈 경계만 표시한다', () => {
    render(<MemoryRouter initialEntries={['/app/projects/1/business-model']}><Routes>
      <Route path="/app/projects/:projectId/business-model" element={<BusinessModelPage />} />
    </Routes></MemoryRouter>);
    expect(screen.getByRole('heading', { name: '수익 구조 분석' })).toBeInTheDocument();
    expect(screen.getByText('확정된 시장 입력')).toBeInTheDocument();
    expect(screen.queryByText(/market-seed-1/)).not.toBeInTheDocument();
    expect(screen.getByText(/분석 기능을 준비하고 있습니다/)).toBeInTheDocument();
    expect(screen.queryByText(/최종 확정 기획/)).not.toBeInTheDocument();
    expect(screen.queryByText(/페르소나 응답/)).not.toBeInTheDocument();
  });
});
