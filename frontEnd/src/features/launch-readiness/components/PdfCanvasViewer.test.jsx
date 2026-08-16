import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

const pdfMocks = vi.hoisted(() => ({
  renderPage: vi.fn(() => ({ promise: Promise.resolve(), cancel: vi.fn() })),
  destroy: vi.fn(),
  getDocument: vi.fn(),
}));

vi.mock('pdfjs-dist/build/pdf.worker.min.mjs?url', () => ({ default: '/pdf.worker.mjs' }));
vi.mock('pdfjs-dist', () => ({
  GlobalWorkerOptions: {},
  getDocument: pdfMocks.getDocument,
}));

import { PdfCanvasViewer } from './PdfCanvasViewer.jsx';

describe('PdfCanvasViewer', () => {
  afterEach(() => vi.restoreAllMocks());

  it('검증된 PDF bytes를 PDF.js canvas 최소 한 페이지로 렌더한다', async () => {
    pdfMocks.getDocument.mockReturnValue({
      promise: Promise.resolve({
        numPages: 1,
        getPage: vi.fn(async () => ({
          getViewport: () => ({ width: 595, height: 842 }),
          render: pdfMocks.renderPage,
        })),
      }),
      destroy: pdfMocks.destroy,
    });
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({});
    const onError = vi.fn();

    render(<PdfCanvasViewer
      blob={new Blob(['%PDF-1.4\n', 'x'.repeat(80)], { type: 'application/pdf' })}
      onError={onError}
    />);

    await waitFor(() => expect(screen.getByLabelText('1페이지')).toBeInTheDocument());
    expect(pdfMocks.getDocument).toHaveBeenCalledOnce();
    expect(pdfMocks.renderPage).toHaveBeenCalledOnce();
    expect(onError).not.toHaveBeenCalled();
  });
});
