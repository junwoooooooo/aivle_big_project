import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useJobEvents } from '../../../shared/async-events/index.js';
import useMarketingGeneration from './useMarketingGeneration.js';

vi.mock('../../../shared/async-events/index.js', () => ({ useJobEvents: vi.fn() }));

describe('useMarketingGeneration', () => {
  beforeEach(() => vi.clearAllMocks());

  it('restores a running detail and connects event replay with activeJobId', async () => {
    useJobEvents.mockReturnValue({ terminal: false, events: [], transport: 'SSE' });
    const api = { detail: vi.fn(), create: vi.fn(), regenerate: vi.fn() };
    const { result } = renderHook(() => useMarketingGeneration({ api, projectId: '7' }));
    act(() => result.current.restore({ content: { contentId: 'content-1', status: 'RUNNING', activeJobId: 'job-1' } }));
    expect(result.current.active).toBe(true);
    expect(useJobEvents).toHaveBeenLastCalledWith('job-1');
  });

  it('re-queries detail after terminal event and shows completed result', async () => {
    let terminal = false;
    useJobEvents.mockImplementation(() => ({ terminal, events: terminal ? [{ sequence: 2, status: 'COMPLETED' }] : [] }));
    const api = { detail: vi.fn().mockResolvedValue({ content: { contentId: 'content-1', status: 'COMPLETED', activeJobId: null }, revisions: [] }), create: vi.fn(), regenerate: vi.fn() };
    const { result, rerender } = renderHook(() => useMarketingGeneration({ api, projectId: '7' }));
    act(() => result.current.restore({ content: { contentId: 'content-1', status: 'RUNNING', activeJobId: 'job-1' } }));
    terminal = true; rerender();
    await waitFor(() => expect(result.current.status).toBe('COMPLETED'));
    expect(api.detail).toHaveBeenCalledWith('7', 'content-1');
  });
});
