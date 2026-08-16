import { useCallback, useRef, useState } from 'react';
import { validatePdfBlob } from './pdfBlob.js';

export function usePdfPreview() {
  const [preview, setPreview] = useState(null);
  const requestRef = useRef({ id: 0, controller: null });

  const openPreview = useCallback((title, filename, loader) => {
    requestRef.current.controller?.abort();
    const id = requestRef.current.id + 1;
    const controller = new AbortController();
    requestRef.current = { id, controller };
    setPreview({ title, filename, status: 'LOADING', blob: null, error: null });

    Promise.resolve()
      .then(() => loader(controller.signal))
      .then(validatePdfBlob)
      .then((blob) => {
        if (requestRef.current.id !== id || controller.signal.aborted) return;
        setPreview({ title, filename, status: 'READY', blob, error: null });
      })
      .catch((error) => {
        if (requestRef.current.id !== id || controller.signal.aborted) return;
        setPreview({ title, filename, status: 'ERROR', blob: null, error });
      });
  }, []);

  const closePreview = useCallback(() => {
    requestRef.current.controller?.abort();
    requestRef.current = { id: requestRef.current.id + 1, controller: null };
    setPreview(null);
  }, []);

  const markViewerError = useCallback((error) => {
    setPreview((current) => current
      ? { ...current, status: 'ERROR', error }
      : current);
  }, []);

  return { preview, openPreview, closePreview, markViewerError };
}
