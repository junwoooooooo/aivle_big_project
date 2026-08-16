import { useEffect, useRef } from 'react';
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

export function PdfCanvasViewer({ blob, onError }) {
  const pagesRef = useRef(null);

  useEffect(() => {
    let disposed = false;
    let loadingTask;
    const renderTasks = [];
    const pages = pagesRef.current;
    pages.replaceChildren();

    void (async () => {
      try {
        const pdfjs = await import('pdfjs-dist');
        pdfjs.GlobalWorkerOptions.workerSrc = pdfWorkerUrl;
        loadingTask = pdfjs.getDocument({ data: await blob.arrayBuffer() });
        const document = await loadingTask.promise;
        for (let pageNumber = 1; pageNumber <= document.numPages; pageNumber += 1) {
          if (disposed) return;
          const page = await document.getPage(pageNumber);
          const viewport = page.getViewport({ scale: 1.35 });
          const canvas = window.document.createElement('canvas');
          const context = canvas.getContext('2d');
          canvas.width = Math.ceil(viewport.width);
          canvas.height = Math.ceil(viewport.height);
          canvas.setAttribute('aria-label', `${pageNumber}페이지`);
          pages.append(canvas);
          const task = page.render({ canvasContext: context, viewport });
          renderTasks.push(task);
          await task.promise;
        }
      } catch (error) {
        if (!disposed && error?.name !== 'RenderingCancelledException') onError(error);
      }
    })();

    return () => {
      disposed = true;
      renderTasks.forEach((task) => task.cancel());
      void loadingTask?.destroy();
      pages.replaceChildren();
    };
  }, [blob, onError]);

  return <div ref={pagesRef} className="launch-pdf-preview__pages" aria-label="PDF 보고서 페이지" />;
}
