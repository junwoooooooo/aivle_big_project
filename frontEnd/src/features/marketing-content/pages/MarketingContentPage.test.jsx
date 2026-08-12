import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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
      uploadReference: vi.fn(async () => ({ artifactId: '00000000-0000-4000-8000-000000000001' })),
      uploading: false,
    };
  });

  it('keeps the Marketing Source and integrated canvas empty state visible before content selection', () => {
    expect(() => render(
      <MemoryRouter initialEntries={['/app/projects/2/marketing']}>
        <Routes>
          <Route path="/app/projects/:projectId/marketing" element={<MarketingContentPage />} />
        </Routes>
      </MemoryRouter>,
    )).not.toThrow();

    expect(screen.getByText('Marketing Source Snapshot')).toBeInTheDocument();
    expect(screen.getByText('생성된 콘텐츠가 이곳에 표시됩니다.')).toBeInTheDocument();
    expect(screen.queryByText('Marketing Visual')).not.toBeInTheDocument();
  });

  it('uploads a selected reference and sends its artifact id in the create request', async () => {
    render(<MemoryRouter initialEntries={['/app/projects/2/marketing']}><Routes>
      <Route path="/app/projects/:projectId/marketing" element={<MarketingContentPage />} />
    </Routes></MemoryRouter>);
    const file = new File([new Uint8Array([0xff, 0xd8, 0xff])], 'product.jpg', { type: 'image/jpeg' });
    fireEvent.change(screen.getByLabelText(/^참고 상품 이미지 \(선택\)/), { target: { files: [file] } });
    fireEvent.change(screen.getByLabelText('채널'), { target: { value: 'Instagram' } });
    fireEvent.change(screen.getByLabelText('목적'), { target: { value: '출시' } });
    fireEvent.click(screen.getByRole('button', { name: '콘텐츠 생성' }));
    await waitFor(() => expect(contentState.uploadReference).toHaveBeenCalledWith(file));
    expect(contentState.create).toHaveBeenCalledWith(expect.objectContaining({
      referenceArtifactId: '00000000-0000-4000-8000-000000000001',
    }));
  });
});
