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
  const [state, setState] = useState({ loading: true, list: [], source: null, selected: null, error: null, saving: false });
  const updateSelected = useCallback((selected) => {
    selectedId.current = selected?.content?.contentId ?? null;
    setState((value) => ({ ...value, selected }));
  }, []);
  const generation = useMarketingGeneration({ api, projectId, onUpdate: updateSelected });
  const restoreGeneration = generation.restore;

  const refresh = useCallback(async () => {
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
    selectedId.current = selected?.content?.contentId ?? null;
    setState((value) => ({ ...value, loading: false, list,
      source: source.status === 'fulfilled' ? source.value : null,
      selected, error: contents.status === 'rejected' ? contents.reason : null }));
    if (selected) restoreGeneration(selected);
  }, [api, sourceApi, projectId, restoreGeneration]);
  useEffect(() => { const timer = setTimeout(() => void refresh(), 0); return () => clearTimeout(timer); }, [refresh]);

  const open = useCallback(async (contentId) => {
    const detail = await api.detail(projectId, contentId); updateSelected(detail); restoreGeneration(detail); return detail;
  }, [api, projectId, restoreGeneration, updateSelected]);
  const create = async (request) => { const detail = await generation.create(request); await refresh(); return detail; };
  const regenerate = async () => { if (!state.selected) return null; const detail = await generation.regenerate(state.selected.content.contentId); await refresh(); return detail; };
  const save = async (result, revisionType = 'USER_EDITED') => {
    if (!state.selected) return null; setState((value) => ({ ...value, saving: true, error: null }));
    try { const detail = await api.update(projectId, state.selected.content.contentId, { revisionType, result }); updateSelected(detail); await refresh(); setState((value) => ({ ...value, saving: false })); return detail; }
    catch (error) { setState((value) => ({ ...value, saving: false, error })); throw error; }
  };
  const finalize = async () => { if (!state.selected) return null; const detail = await api.finalize(projectId, state.selected.content.contentId); updateSelected(detail); await refresh(); return detail; };
  return { ...state, ...generation, refresh, open, create, regenerate, save, finalize };
}
