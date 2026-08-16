import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { usePdfPreview } from './usePdfPreview.js';

const validPdf = () => new Blob(['%PDF-1.4\n', 'x'.repeat(80)], { type: 'application/pdf' });

describe('usePdfPreview', () => {
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
  });

  it('유효하지 않은 PDF는 READY로 전환하지 않는다', async () => {
    const { result } = renderHook(() => usePdfPreview());
    act(() => result.current.openPreview('재무 보고서', 'finance.pdf', async () => new Blob(['json'], { type: 'application/json' })));
    await waitFor(() => expect(result.current.preview.status).toBe('ERROR'));
    expect(result.current.preview.blob).toBeNull();
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
