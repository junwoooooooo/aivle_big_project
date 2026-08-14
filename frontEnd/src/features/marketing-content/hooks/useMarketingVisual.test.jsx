import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiClientProvider } from '../../../shared/api/ApiClientProvider.jsx';
import useMarketingVisual from './useMarketingVisual.js';

let eventState;
vi.mock('../../../shared/async-events/index.js', () => ({ useJobEvents: () => eventState }));

describe('useMarketingVisual', () => {
  beforeEach(() => { eventState = { events: [], terminal: false }; global.URL.createObjectURL = vi.fn(() => 'blob:generated'); global.URL.revokeObjectURL = vi.fn(); });

  it('uses SSE terminal state to refresh the canonical visual REST result', async () => {
    const client = { get: vi.fn().mockResolvedValueOnce({ data: { taskRunId: 'task-1', state: 'RUNNING', activeJobId: 'task-1' } })
      .mockResolvedValueOnce({ data: { taskRunId: 'task-1', state: 'SUCCEEDED', result: { artifact: { downloadPath: '/artifact/download' } } } }),
      download: vi.fn(async () => ({ blob: new Blob(['image']) })) };
    const wrapper = ({ children }) => <ApiClientProvider client={client}>{children}</ApiClientProvider>;
    const hook = renderHook(() => useMarketingVisual('41', 'content-1'), { wrapper });
    await waitFor(() => expect(hook.result.current.run?.state).toBe('RUNNING'));
    eventState = { events: [{ sequence: 1, status: 'COMPLETED' }], terminal: true };
    await act(async () => { hook.rerender(); });
    await waitFor(() => expect(hook.result.current.run?.state).toBe('SUCCEEDED'));
    expect(client.get.mock.calls.at(-1)?.[0]).toBe('/api/v3/projects/41/marketing-visual-runs/task-1');
    expect(client.download).toHaveBeenCalledWith('/artifact/download', undefined);
  });
});
