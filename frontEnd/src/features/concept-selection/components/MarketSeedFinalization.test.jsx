import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import MarketSeedFinalization from './MarketSeedFinalization.jsx';

describe('MarketSeedFinalization', () => {
  it('모든 가설 결정 전에는 Snapshot 확정을 차단한다', () => {
    render(<MemoryRouter><MarketSeedFinalization projectId="7" selection={{ decisionComplete: false }}
      snapshot={null} finalizing={false} onFinalize={vi.fn()} /></MemoryRouter>);
    expect(screen.getByRole('button', { name: '시장 분석 입력 저장' })).toBeDisabled();
  });

  it('완료된 결정은 확정 동작을 허용하고 immutable 식별정보를 표시한다', () => {
    const onFinalize = vi.fn();
    const { rerender } = render(<MemoryRouter><MarketSeedFinalization projectId="7" selection={{ decisionComplete: true }}
      snapshot={null} finalizing={false} onFinalize={onFinalize} /></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: '시장 분석 입력 저장' }));
    expect(onFinalize).toHaveBeenCalledOnce();

    rerender(<MemoryRouter><MarketSeedFinalization projectId="7" selection={{ decisionComplete: true }}
      snapshot={{ snapshotId: 'seed-1', schemaVersion: '2.0', snapshotHash: `sha256:${'a'.repeat(64)}`, createdAt: '2026-08-08T00:00:00Z' }}
      finalizing={false} onFinalize={onFinalize} /></MemoryRouter>);
    expect(screen.getByText('seed-1')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '시장분석으로 이동' })).toHaveAttribute('href', '/app/projects/7/market');
  });
});
