import { afterEach, describe, expect, it, vi } from 'vitest';
import { downloadPdfBlob, InvalidPdfError, validatePdfBlob } from './pdfBlob.js';

const validPdf = () => new Blob(['%PDF-1.4\n', 'x'.repeat(80)], { type: 'application/pdf' });

describe('launch readiness PDF byte contract', () => {
  afterEach(() => vi.restoreAllMocks());

  it('최소 크기와 %PDF- magic을 확인하며 MIME은 참고값으로만 사용한다', async () => {
    await expect(validatePdfBlob(validPdf())).resolves.toBeInstanceOf(Blob);
    await expect(validatePdfBlob(new Blob(['%PDF-1.4\n', 'x'.repeat(80)], { type: 'application/octet-stream' })))
      .resolves.toBeInstanceOf(Blob);
    await expect(validatePdfBlob(new Blob(['%PDF-'], { type: 'application/pdf' })))
      .rejects.toBeInstanceOf(InvalidPdfError);
    await expect(validatePdfBlob(new Blob(['not-pdf'.repeat(20)], { type: 'application/pdf' })))
      .rejects.toBeInstanceOf(InvalidPdfError);
    await expect(validatePdfBlob(new Blob(['<html>', 'x'.repeat(80)], { type: 'application/pdf' })))
      .rejects.toBeInstanceOf(InvalidPdfError);
  });

  it('검증된 PDF만 명시적 다운로드하며 anchor를 DOM에서 정리한다', async () => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:pdf');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    await downloadPdfBlob(validPdf(), 'report.pdf');
    expect(click).toHaveBeenCalledOnce();
    expect(document.querySelector('a[download="report.pdf"]')).toBeNull();
  });
});
