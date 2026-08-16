import { useState } from 'react';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { AppIcon, Dialog, useBodyScrollLock } from '../../../shared/ui/index.js';
import { downloadPdfBlob } from '../model/pdfBlob.js';
import { FinanceReportPreviewDocument } from './FinanceReportPreviewDocument.jsx';
import { LaunchReadinessReportPreviewDocument } from './LaunchReadinessReportPreviewDocument.jsx';

export function ReportPreviewDialog({ preview, onClose }) {
  useBodyScrollLock(Boolean(preview));
  const [download, setDownload] = useState({ busy: false, error: null });

  const downloadReport = async () => {
    if (!preview?.loadPdf || download.busy) return;
    setDownload({ busy: true, error: null });
    try {
      const blob = await preview.loadPdf();
      await downloadPdfBlob(blob, preview.filename);
      setDownload({ busy: false, error: null });
    } catch (error) {
      setDownload({ busy: false, error });
    }
  };

  const documents = preview?.documents ?? [];
  return <Dialog open={Boolean(preview)} onClose={onClose} title={preview?.title ?? '출시 준비 보고서 미리보기'} variant="report-preview">
    <div className="launch-report-preview">
      <div className="launch-report-preview__body">
        {documents.length > 1 && <header className="launch-preview-bundle-cover"><p>출시 준비 통합 보고서</p><h2>{documents.length}개 분석 결과를 함께 확인하세요</h2><span>각 보고서는 현재 저장된 결과를 같은 순서로 보여줍니다.</span></header>}
        {documents.map((document) => document.module === 'finance'
          ? <FinanceReportPreviewDocument key={document.module} current={document.current} />
          : <LaunchReadinessReportPreviewDocument key={document.module} module={document.module} current={document.current} />)}
      </div>
      <footer>
        {download.error && <p role="alert">{getUserErrorMessage(download.error) || 'PDF 보고서를 내려받지 못했습니다.'}</p>}
        <button type="button" className="launch-button is-primary" disabled={download.busy || !preview?.loadPdf} onClick={downloadReport}>
          <AppIcon name="download" size={16} />{download.busy ? 'PDF를 준비하고 있습니다…' : 'PDF 다운로드'}
        </button>
      </footer>
    </div>
  </Dialog>;
}
