import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import useConceptSelection from './useConceptSelection.js';
import { useJobEvents } from '../../../shared/async-events/index.js';

const mocks = vi.hoisted(() => ({
  api: {
    currentRun: vi.fn(), concepts: vi.fn(), currentSelection: vi.fn(),
    currentMarketSeed: vi.fn(), decideHypothesis: vi.fn(),
  },
}));

vi.mock('../../../shared/api/ApiClientProvider.jsx', () => ({ useApiClient: () => ({}) }));
vi.mock('../../../shared/async-events/index.js', () => ({ useJobEvents: vi.fn() }));
vi.mock('../api/conceptSelectionApi.js', () => ({ createConceptSelectionApi: () => mocks.api }));

const selection = {
  selectionId: 9,
  activeActionTaskRunId: null,
  actionStatus: 'IDLE',
  hypotheses: [{ decisionId: 'revenue-1', hypothesisType: 'REVENUE_MODEL',
    proposedValue: '월 구독', proposalVersion: 1, decisionStatus: 'PROPOSED' }],
};

describe('useConceptSelection async actions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useJobEvents.mockReturnValue({ terminal: false, events: [] });
    mocks.api.currentRun.mockResolvedValue({ data: { status: 'COMPLETED' } });
    mocks.api.concepts.mockResolvedValue({ data: { concepts: [] } });
    mocks.api.currentSelection.mockResolvedValue({ data: selection });
    mocks.api.currentMarketSeed.mockRejectedValue({ status: 404 });
  });

  it('creates a fresh command key and restores pending state from a 202 response', async () => {
    mocks.api.decideHypothesis.mockResolvedValue({ data: {
      hypothesis: selection.hypotheses[0], decisionComplete: false,
      taskRunId: 'task-1', status: 'QUEUED', actionType: 'REQUEST_ALTERNATIVE',
      hypothesisType: 'REVENUE_MODEL', proposalVersion: 1,
    } });
    const { result } = renderHook(() => useConceptSelection('41'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(() => result.current.decideHypothesis('REVENUE_MODEL', 'REQUEST_ALTERNATIVE', 1));

    const options = mocks.api.decideHypothesis.mock.calls[0][3];
    expect(options.headers['Idempotency-Key']).toBeTruthy();
    expect(result.current.currentSelection).toMatchObject({
      activeActionTaskRunId: 'task-1', pendingActionType: 'REQUEST_ALTERNATIVE',
      pendingHypothesisType: 'REVENUE_MODEL', actionStatus: 'QUEUED',
    });
    expect(useJobEvents).toHaveBeenLastCalledWith('task-1');
  });

  it('refreshes the canonical query after a terminal SSE event', async () => {
    let terminal = false;
    mocks.api.currentSelection.mockResolvedValue({ data: { ...selection, activeActionTaskRunId: 'task-1',
      pendingActionType: 'REQUEST_ALTERNATIVE', pendingHypothesisType: 'REVENUE_MODEL', actionStatus: 'RUNNING' } });
    useJobEvents.mockImplementation(() => ({ terminal, events: [] }));
    const { result, rerender } = renderHook(() => useConceptSelection('41'));
    await waitFor(() => expect(result.current.currentSelection?.activeActionTaskRunId).toBe('task-1'));
    const beforeTerminal = mocks.api.currentSelection.mock.calls.length;

    terminal = true;
    rerender();

    await waitFor(() => expect(mocks.api.currentSelection.mock.calls.length).toBeGreaterThan(beforeTerminal));
  });
});
