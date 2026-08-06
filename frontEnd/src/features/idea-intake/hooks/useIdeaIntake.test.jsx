import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { useJobEvents } from '../../../shared/async-events/index.js';
import { IDEA_INTAKE_SCREEN_STATE } from '../model/ideaIntakeModel.js';
import useIdeaIntake from './useIdeaIntake.js';

vi.mock('../../../shared/api/ApiClientProvider.jsx', () => ({ useApiClient: vi.fn() }));
vi.mock('../../../shared/async-events/index.js', () => ({ useJobEvents: vi.fn() }));

describe('useIdeaIntake async recovery', () => {
  beforeEach(() => vi.clearAllMocks());

  it('restores an active job and re-queries the brief after a terminal replay event', async () => {
    let terminal = false;
    useJobEvents.mockImplementation(() => ({ terminal, events: [] }));
    const client = {
      get: vi.fn()
        .mockResolvedValueOnce({ data: response('DERIVING', 'job-1', []) })
        .mockResolvedValueOnce({ data: response('NEEDS_INPUT', null, [{
          questionId: 'q-1', targetFieldKey: 'problem', type: 'FREE_TEXT',
          prompt: '문제를 더 설명해 주세요.', optionsJson: '[]', answered: false, answerJson: null,
        }]) }),
      post: vi.fn(), patch: vi.fn(),
    };
    useApiClient.mockReturnValue(client);
    const { result, rerender } = renderHook(() => useIdeaIntake('42'));

    await waitFor(() => expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.RUNNING));
    expect(result.current.activeJobId).toBe('job-1');

    terminal = true;
    rerender();
    await waitFor(() => expect(result.current.screenState).toBe(IDEA_INTAKE_SCREEN_STATE.NEEDS_INPUT));
    expect(client.get).toHaveBeenCalledTimes(2);
    expect(result.current.questions[0].id).toBe('q-1');
  });
});

function response(status, activeJobId, questions) {
  return {
    briefId: 'brief-1', status, activeJobId, confirmedSnapshotId: null,
    fields: [], questions, readiness: { readyForConfirm: false }, updatedAt: '2026-08-06T12:00:00',
  };
}
