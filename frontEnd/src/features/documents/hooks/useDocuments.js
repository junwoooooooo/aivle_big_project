import { useCallback, useEffect, useRef, useState } from 'react';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { useServicePolicy } from '../../service-policy/useServicePolicy.js';
import { isServicePolicyError } from '../../service-policy/servicePolicyRestrictions.js';
import { createDocumentApi } from '../api/documentApi.js';

function createIdempotencyKey() {
  return globalThis.crypto?.randomUUID?.()
    ?? `upload-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function useDocuments(projectId) {
  const client = useApiClient();
  const [state, setState] = useState({
    status: 'loading',
    documents: [],
    versions: {},
    error: null,
  });

  const fetchDocuments = useCallback(async (signal) => {
    const api = createDocumentApi(client);
    const documents = await api.list(projectId, { signal });
    const versionEntries = await Promise.all(documents.map(async (document) => {
      if (!document.latestVersionId) return [document.documentId, null];
      const version = await api.getVersion(
        document.documentId,
        document.latestVersionId,
        { signal },
      );
      return [document.documentId, version];
    }));
    return {
      documents,
      versions: Object.fromEntries(versionEntries),
    };
  }, [client, projectId]);

  const load = useCallback(async () => {
    setState((current) => ({ ...current, status: 'loading', error: null }));
    try {
      const result = await fetchDocuments();
      setState({
        status: 'success',
        ...result,
        error: null,
      });
    } catch (error) {
      if (error.code !== 'REQUEST_ABORTED') {
        setState({ status: 'error', documents: [], versions: {}, error });
      }
    }
  }, [fetchDocuments]);

  useEffect(() => {
    const controller = new AbortController();
    fetchDocuments(controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) {
          setState({ status: 'success', ...result, error: null });
        }
      })
      .catch((error) => {
        if (error.code !== 'REQUEST_ABORTED') {
          setState({ status: 'error', documents: [], versions: {}, error });
        }
      });
    return () => controller.abort();
  }, [fetchDocuments]);

  return { ...state, retry: load };
}

export function useDocumentUpload(projectId, onSuccess) {
  const client = useApiClient();
  const { refresh: refreshPolicy } = useServicePolicy();
  const [file, setFileState] = useState(null);
  const [error, setError] = useState(null);
  const [uploading, setUploading] = useState(false);
  const idempotencyKey = useRef(null);

  const setFile = useCallback((nextFile) => {
    setFileState(nextFile);
    setError(null);
    idempotencyKey.current = null;
  }, []);

  const upload = useCallback(async () => {
    if (!file || uploading) return null;
    if (!idempotencyKey.current) {
      idempotencyKey.current = createIdempotencyKey();
    }
    setUploading(true);
    setError(null);
    try {
      const result = await createDocumentApi(client).upload(
        projectId,
        file,
        idempotencyKey.current,
      );
      idempotencyKey.current = null;
      onSuccess?.(result);
      return result;
    } catch (nextError) {
      setError(nextError);
      if (isServicePolicyError(nextError)) {
        void refreshPolicy().catch(() => undefined);
      }
      return null;
    } finally {
      setUploading(false);
    }
  }, [client, file, onSuccess, projectId, refreshPolicy, uploading]);

  return { file, setFile, upload, uploading, error };
}
