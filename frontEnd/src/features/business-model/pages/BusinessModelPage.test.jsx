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
    expect(screen.getByRole('heading', { name: 'BM 분석' })).toBeInTheDocument();
    expect(screen.getByText(/market-seed-1/)).toBeInTheDocument();
    expect(screen.getByText(/외부 BM 분석 알고리즘은 아직 연결되지 않았습니다/)).toBeInTheDocument();
    expect(screen.queryByText(/최종 확정 기획/)).not.toBeInTheDocument();
    expect(screen.queryByText(/페르소나 응답/)).not.toBeInTheDocument();
  });
});
