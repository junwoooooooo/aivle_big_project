import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PDF_PREVIEW_FAILURE, usePdfPreview } from './usePdfPreview.js';

const validPdf = () => new Blob(['%PDF-1.4\n', 'x'.repeat(80)], { type: 'application/pdf' });

describe('usePdfPreview', () => {
  afterEach(() => vi.restoreAllMocks());
  it('다운로드가 끝나기 전에 Dialog용 LOADING 상태를 즉시 연다', async () => {
    let resolve;
    const loader = vi.fn(() => new Promise((done) => { resolve = done; }));
    const { result } = renderHook(() => usePdfPreview());

    act(() => result.current.openPreview('기술 보고서', 'technology.pdf', loader));
    expect(result.current.preview).toMatchObject({ status: 'LOADING', blob: null });
    expect(loader).not.toHaveBeenCalled();

    await act(async () => { await Promise.resolve(); });
    expect(loader).toHaveBeenCalledOnce();
    await act(async () => { resolve(validPdf()); });
    await waitFor(() => expect(result.current.preview.status).toBe('READY'));
    expect(result.current.preview.failure).toBeNull();
  });

  it('유효하지 않은 PDF bytes를 fetch 실패와 구분한다', async () => {
    const { result } = renderHook(() => usePdfPreview());
    act(() => result.current.openPreview('재무 보고서', 'finance.pdf', async () => new Blob(['json'], { type: 'application/json' })));
    await waitFor(() => expect(result.current.preview.status).toBe('ERROR'));
    expect(result.current.preview.blob).toBeNull();
    expect(result.current.preview.failure).toBe(PDF_PREVIEW_FAILURE.INVALID_BYTES);
  });

  it('PDF fetch 실패를 별도 상태로 표시한다', async () => {
    const { result } = renderHook(() => usePdfPreview());
    act(() => result.current.openPreview('운영 보고서', 'operations.pdf', async () => {
      throw new Error('network unavailable');
    }));
    await waitFor(() => expect(result.current.preview.status).toBe('ERROR'));
    expect(result.current.preview.failure).toBe(PDF_PREVIEW_FAILURE.FETCH);
  });

  it('PDF.js render 실패는 검증된 blob을 다운로드용으로 유지한다', async () => {
    const { result } = renderHook(() => usePdfPreview());
    act(() => result.current.openPreview('통합 보고서', 'all.pdf', async () => validPdf()));
    await waitFor(() => expect(result.current.preview.status).toBe('READY'));
    act(() => result.current.markViewerError(new Error('canvas failed')));
    expect(result.current.preview).toMatchObject({
      status: 'ERROR', failure: PDF_PREVIEW_FAILURE.RENDER,
    });
    expect(result.current.preview.blob).toBeInstanceOf(Blob);
  });

  it('미리보기 요청만으로 anchor 또는 object URL 다운로드를 시작하지 않는다', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:unused');
    const { result } = renderHook(() => usePdfPreview());
    act(() => result.current.openPreview('기술 보고서', 'technology.pdf', async () => validPdf()));
    await waitFor(() => expect(result.current.preview.status).toBe('READY'));
    expect(click).not.toHaveBeenCalled();
    expect(createObjectURL).not.toHaveBeenCalled();
  });

  it('닫힌 요청이 완료되어도 Dialog를 다시 열지 않는다', async () => {
    let resolve;
    const { result } = renderHook(() => usePdfPreview());
    act(() => result.current.openPreview('통합 보고서', 'all.pdf', () => new Promise((done) => { resolve = done; })));
    await act(async () => { await Promise.resolve(); });
    act(() => result.current.closePreview());
    await act(async () => { resolve(validPdf()); });
    expect(result.current.preview).toBeNull();
  });
});
