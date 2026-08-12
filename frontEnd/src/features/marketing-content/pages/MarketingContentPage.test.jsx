import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import MarketingContentPage from './MarketingContentPage.jsx';

const refresh = vi.fn();
let contentState;

vi.mock('../hooks/useMarketingContent.js', () => ({ default: () => contentState }));
vi.mock('../hooks/useMarketingVisual.js', () => ({
  default: () => ({
    run: null,
    error: null,
    previewUrl: null,
    busy: false,
    events: { events: [] },
    create: vi.fn(),
    retry: vi.fn(),
    cancel: vi.fn(),
    download: vi.fn(),
  }),
}));

describe('MarketingContentPage empty selection', () => {
  beforeEach(() => {
    contentState = {
      loading: false,
      list: [],
      source: { snapshotId: 'source-1', snapshot: { conceptName: 'Current Concept' } },
      selected: null,
      error: null,
      saving: false,
      active: false,
      status: 'IDLE',
      jobEvents: { events: [] },
      refresh,
      open: vi.fn(),
      create: vi.fn(),
      regenerate: vi.fn(),
      save: vi.fn(),
      finalize: vi.fn(),
    };
  });

  it('keeps the Marketing Source and Visual empty state visible before content selection', () => {
    expect(() => render(
      <MemoryRouter initialEntries={['/app/projects/2/marketing']}>
        <Routes>
          <Route path="/app/projects/:projectId/marketing" element={<MarketingContentPage />} />
        </Routes>
      </MemoryRouter>,
    )).not.toThrow();

    expect(screen.getByText('Marketing Source Snapshot')).toBeInTheDocument();
    expect(screen.getByText('Marketing Visual')).toBeInTheDocument();
    expect(screen.getByText('배너 결과가 아직 없습니다.')).toBeInTheDocument();
    expect(screen.getByText('먼저 마케팅 콘텐츠와 revision을 선택해 주세요.')).toBeInTheDocument();
  });
});
