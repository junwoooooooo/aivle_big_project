import { Button, Dialog } from '../../../shared/ui/index.js';

export default function MarketingExportDialog({
  open,
  content,
  filename,
  warnings = [],
  checking,
  exporting,
  onClose,
  onExport,
}) {
  const width = content?.content.width;
  const height = content?.content.height;
  return (
    <Dialog open={open} onClose={onClose} title="PNG 내보내기">
      <dl className="marketing-export-summary">
        <div><dt>파일명</dt><dd>{filename || 'marketing-content'}.png</dd></div>
        <div><dt>출력 크기</dt><dd>{width} × {height}px</dd></div>
        <div><dt>형식</dt><dd>PNG</dd></div>
        <div><dt>예상 비율</dt><dd>{width && height ? `${(width / height).toFixed(2)} : 1` : '-'}</dd></div>
        <div><dt>배경</dt><dd>포함</dd></div>
      </dl>
      {checking && <p role="status">폰트와 텍스트 영역을 확인하고 있습니다.</p>}
      {warnings.length > 0 && (
        <div className="marketing-export-warning" role="alert">
          <strong>내보내기 전에 문구를 조정해 주세요.</strong>
          <ul>{warnings.map((warning) => <li key={warning}>{warning}</li>)}</ul>
        </div>
      )}
      <p>브라우저에서 직접 렌더링하므로 서버 감사 로그에는 내보내기 이벤트가 기록되지 않습니다.</p>
      <div className="marketing-dialog-actions">
        <Button variant="ghost" onClick={onClose}>취소</Button>
        <Button loading={exporting} disabled={checking || warnings.length > 0} onClick={onExport}>PNG 다운로드</Button>
      </div>
    </Dialog>
  );
}
