import { useEffect, useRef } from 'react';

import { renderPreview } from '../render/marketingRenderer.js';

export default function MarketingCanvas({ content, showSafeArea = true }) {
  const canvasRef = useRef(null);
  useEffect(() => {
    renderPreview(canvasRef.current, content);
  }, [content]);
  if (!content) return null;
  return (
    <section className="marketing-preview" aria-label="마케팅 콘텐츠 미리보기">
      <div className="marketing-preview__meta">
        <strong>{content.content.width} × {content.content.height}</strong>
        <span>화면 맞춤 미리보기 · 내보내기는 실제 규격</span>
      </div>
      <div className={`marketing-preview__stage ${showSafeArea ? 'marketing-preview__stage--safe' : ''}`}>
        <canvas ref={canvasRef} aria-label={`${content.content.title} 시안 미리보기`} />
      </div>
      {showSafeArea && <p>점선 안쪽에 핵심 메시지를 배치하면 SNS 잘림 위험을 줄일 수 있습니다.</p>}
    </section>
  );
}
