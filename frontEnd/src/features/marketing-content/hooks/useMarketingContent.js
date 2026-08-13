import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createMarketingContentApi } from '../api/marketingContentApi.js';
import { createMarketingSourceApi } from '../api/marketingSourceApi.js';
import useMarketingGeneration from './useMarketingGeneration.js';

export default function useMarketingContent(projectId) {
  const client = useApiClient();
  const api = useMemo(() => createMarketingContentApi(client), [client]);
  const sourceApi = useMemo(() => createMarketingSourceApi(client), [client]);
  const selectedId = useRef(null);
  const requestEpoch = useRef(0);
  const [state, setState] = useState({ loading: true, list: [], source: null, selected: null, error: null, saving: false, uploading: false });
  const updateSelected = useCallback((selected, options = {}) => {
    const content = selected?.content;
    if (!content?.contentId) return;
    const shouldSelect = options.forceSelect || selectedId.current === null
      || selectedId.current === content.contentId;
    if (shouldSelect) selectedId.current = content.contentId;
    setState((value) => {
      const index = value.list.findIndex((item) => item.contentId === content.contentId);
      const summary = index >= 0 ? { ...value.list[index], ...content } : content;
      const list = index >= 0
        ? value.list.map((item, itemIndex) => itemIndex === index ? summary : item)
        : [summary, ...value.list];
      return { ...value, list, selected: shouldSelect ? selected : value.selected };
    });
  }, []);
  const generation = useMarketingGeneration({ api, projectId, onUpdate: updateSelected });
  const restoreGeneration = generation.restore;

  const refresh = useCallback(async () => {
    const epoch = requestEpoch.current + 1;
    requestEpoch.current = epoch;
    const sourcePromise = sourceApi.current(projectId).catch(async (error) => {
      if ([404, 409, 422].includes(error?.status)) return sourceApi.finalize(projectId).catch(() => null);
      throw error;
    });
    const [contents, source] = await Promise.allSettled([api.list(projectId), sourcePromise]);
    const list = contents.status === 'fulfilled' ? contents.value.contents : [];
    const preferredId = selectedId.current && list.some((content) => content.contentId === selectedId.current)
      ? selectedId.current
      : list.find((content) => ['QUEUED', 'RUNNING'].includes(content.status))?.contentId ?? list[0]?.contentId;
    let selected = null;
    if (preferredId) {
      try { selected = await api.detail(projectId, preferredId); } catch { selected = null; }
    }
    if (epoch !== requestEpoch.current) return null;
    selectedId.current = selected?.content?.contentId ?? null;
    setState((value) => ({ ...value, loading: false, list,
      source: source.status === 'fulfilled' ? source.value : null,
      selected, error: contents.status === 'rejected' ? contents.reason : null }));
    if (selected) restoreGeneration(selected);
    return selected;
  }, [api, sourceApi, projectId, restoreGeneration]);
  useEffect(() => { const timer = setTimeout(() => void refresh(), 0); return () => clearTimeout(timer); }, [refresh]);

  const open = useCallback(async (contentId) => {
    const epoch = requestEpoch.current + 1;
    requestEpoch.current = epoch;
    selectedId.current = contentId;
    const detail = await api.detail(projectId, contentId);
    if (epoch !== requestEpoch.current) return detail;
    updateSelected(detail, { forceSelect: true });
    restoreGeneration(detail);
    return detail;
  }, [api, projectId, restoreGeneration, updateSelected]);
  const uploadReference = async (file) => {
    if (!['image/png', 'image/jpeg'].includes(file?.type) || file.size <= 0 || file.size > 20 * 1024 * 1024) {
      throw new Error('참고 이미지는 20MB 이하 PNG 또는 JPG 파일이어야 합니다.');
    }
    setState((value) => ({ ...value, uploading: true, error: null }));
    try {
      const artifact = await api.uploadReference(projectId, file, { timeoutMs: 30000 });
      setState((value) => ({ ...value, uploading: false })); return artifact;
    } catch (error) {
      setState((value) => ({ ...value, uploading: false, error })); throw error;
    }
  };
  const create = async (request) => { const detail = await generation.create(request); await refresh(); return detail; };
  const regenerate = async () => { if (!state.selected) return null; const detail = await generation.regenerate(state.selected.content.contentId); await refresh(); return detail; };
  const save = async (result, revisionType = 'USER_EDITED') => {
    if (!state.selected) return null; setState((value) => ({ ...value, saving: true, error: null }));
    try { const detail = await api.update(projectId, state.selected.content.contentId, { revisionType, result }); updateSelected(detail); await refresh(); setState((value) => ({ ...value, saving: false })); return detail; }
    catch (error) { setState((value) => ({ ...value, saving: false, error })); throw error; }
  };
  const finalize = async () => { if (!state.selected) return null; const detail = await api.finalize(projectId, state.selected.content.contentId); updateSelected(detail); await refresh(); return detail; };
  return { ...state, ...generation, refresh, open, create, regenerate, save, finalize, uploadReference };
}
