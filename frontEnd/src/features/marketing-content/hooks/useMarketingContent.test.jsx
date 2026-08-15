import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import useMarketingGeneration from './useMarketingGeneration.js';
import useMarketingContent from './useMarketingContent.js';

vi.mock('../../../shared/api/ApiClientProvider.jsx', () => ({ useApiClient: vi.fn() }));
vi.mock('./useMarketingGeneration.js', () => ({ default: vi.fn() }));

const generationState = (restore = vi.fn()) => ({
  active: false,
  status: 'IDLE',
  activeJobId: null,
  contentId: null,
  error: null,
  jobEvents: { events: [] },
  restore,
  create: vi.fn(),
  regenerate: vi.fn(),
});

const deferred = () => {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
};

describe('useMarketingContent canonical reconciliation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useMarketingGeneration.mockReturnValue(generationState());
  });

  it('queries running content detail and restores its active job on page refresh', async () => {
    const restore = vi.fn();
    useMarketingGeneration.mockReturnValue(generationState(restore));
    const detail = { content: { contentId: 'content-1', status: 'RUNNING', activeJobId: 'job-1' }, revisions: [] };
    const client = {
      get: vi.fn(async (path) => {
        if (path.endsWith('/marketing-contents')) return { data: { contents: [detail.content] } };
        if (path.endsWith('/marketing-source-snapshots/current')) return { data: { snapshotId: 'source-1', snapshot: {} } };
        if (path.endsWith('/marketing-contents/content-1')) return { data: detail };
        throw new Error(`unexpected path ${path}`);
      }),
      post: vi.fn(), patch: vi.fn(),
    };
    useApiClient.mockReturnValue(client);

    const { result } = renderHook(() => useMarketingContent('7'));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(client.get).toHaveBeenCalledWith('/api/v3/projects/7/marketing-contents/content-1', undefined);
    expect(restore).toHaveBeenCalledWith(detail);
    expect(result.current.selected).toEqual(detail);
  });

  it('reconciles a terminal detail into both selected detail and the content list', async () => {
    const running = { content: { contentId: 'content-1', status: 'RUNNING', activeJobId: 'job-1' }, revisions: [] };
    const completed = { content: { contentId: 'content-1', status: 'COMPLETED', activeJobId: null, currentRevisionNumber: 1 }, revisions: [{ revisionNumber: 1 }] };
    const client = {
      get: vi.fn(async (path) => {
        if (path.endsWith('/marketing-contents')) return { data: { contents: [running.content] } };
        if (path.endsWith('/marketing-source-snapshots/current')) return { data: { snapshotId: 'source-1', snapshot: {} } };
        if (path.endsWith('/marketing-contents/content-1')) return { data: running };
        throw new Error(`unexpected path ${path}`);
      }),
      post: vi.fn(), patch: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useMarketingContent('7'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    const onUpdate = useMarketingGeneration.mock.calls.at(-1)[0].onUpdate;
    act(() => onUpdate(completed));

    expect(result.current.selected).toEqual(completed);
    expect(result.current.list[0]).toEqual(expect.objectContaining({
      contentId: 'content-1', status: 'COMPLETED', currentRevisionNumber: 1,
    }));
  });

  it('does not let an older open request overwrite the newly selected content', async () => {
    const oldRequest = deferred();
    const newRequest = deferred();
    const client = {
      get: vi.fn((path) => {
        if (path.endsWith('/marketing-contents')) return Promise.resolve({ data: { contents: [] } });
        if (path.endsWith('/marketing-source-snapshots/current')) return Promise.resolve({ data: { snapshotId: 'source-1', snapshot: {} } });
        if (path.endsWith('/marketing-contents/old-content')) return oldRequest.promise.then((data) => ({ data }));
        if (path.endsWith('/marketing-contents/new-content')) return newRequest.promise.then((data) => ({ data }));
        throw new Error(`unexpected path ${path}`);
      }),
      post: vi.fn(), patch: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result } = renderHook(() => useMarketingContent('7'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    let oldOpen;
    let newOpen;
    act(() => {
      oldOpen = result.current.open('old-content');
      newOpen = result.current.open('new-content');
    });
    const newDetail = { content: { contentId: 'new-content', status: 'COMPLETED' }, revisions: [] };
    const oldDetail = { content: { contentId: 'old-content', status: 'COMPLETED' }, revisions: [] };
    await act(async () => { newRequest.resolve(newDetail); await newOpen; });
    await act(async () => { oldRequest.resolve(oldDetail); await oldOpen; });

    expect(result.current.selected).toEqual(newDetail);
  });
});
