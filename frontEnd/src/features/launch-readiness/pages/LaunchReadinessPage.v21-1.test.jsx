import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ApiError } from '../../../shared/api/apiError.js';
import { PDF_PREVIEW_FAILURE } from '../model/usePdfPreview.js';
import { FinanceInputError, PdfPreviewDialog } from './LaunchReadinessPage.jsx';

describe('V21.1 Finance 문서 오류 UI', () => {
  it('안전한 제목과 필드별 한국어 오류를 함께 표시한다', () => {
    render(<FinanceInputError error={new ApiError({
      status: 422,
      code: 'FINANCIAL_INPUT_INVALID',
      fieldErrors: [
        { field: 'threeYearTargets', message: '1·2·3년차 값을 확인해 주세요.' },
        { field: 'shippingCost', message: '숫자를 입력하거나 비워 주세요.' },
      ],
    })} />);
    expect(screen.getByText('재무 입력 문서를 확인해 주세요.')).toBeInTheDocument();
    expect(screen.getByText('3개년 성장 목표')).toBeInTheDocument();
    expect(screen.getByText('1·2·3년차 값을 확인해 주세요.')).toBeInTheDocument();
    expect(screen.getByText('배송비')).toBeInTheDocument();
  });

  it('PDF 실패 원인별 문구와 render 실패 다운로드를 구분한다', () => {
    const base = { title: '보고서', filename: 'report.pdf', status: 'ERROR', error: new Error('failed') };
    const { rerender } = render(<PdfPreviewDialog preview={{ ...base, failure: PDF_PREVIEW_FAILURE.FETCH, blob: null }} onClose={() => {}} onViewerError={() => {}} />);
    expect(screen.getByText('보고서를 불러오지 못했습니다.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /PDF 다운로드/ })).not.toBeInTheDocument();

    rerender(<PdfPreviewDialog preview={{ ...base, failure: PDF_PREVIEW_FAILURE.INVALID_BYTES, blob: null }} onClose={() => {}} onViewerError={() => {}} />);
    expect(screen.getByText('생성된 보고서 형식을 확인할 수 없습니다.')).toBeInTheDocument();

    rerender(<PdfPreviewDialog preview={{ ...base, failure: PDF_PREVIEW_FAILURE.RENDER, blob: new Blob(['%PDF-', 'x'.repeat(80)], { type: 'application/pdf' }) }} onClose={() => {}} onViewerError={() => {}} />);
    expect(screen.getByText('보고서는 생성되었지만 미리보기를 표시하지 못했습니다.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /PDF 다운로드/ })).toBeInTheDocument();
  });
});
