import { renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import useTwinSurveyLiveState from './useTwinSurveyPolling.js';

describe('useTwinSurveyLiveState SSE refresh seam', () => {
  it('reloads canonical current state when the project live revision changes', async () => {
    const load = vi.fn().mockResolvedValue({ run: null, version: null });
    const start = vi.fn();
    const { rerender } = renderHook(
      ({ revision }) => useTwinSurveyLiveState(load, start, revision),
      { initialProps: { revision: 0 } },
    );

    await waitFor(() => expect(load).toHaveBeenCalledTimes(1));
    rerender({ revision: 1 });
    await waitFor(() => expect(load).toHaveBeenCalledTimes(2));
  });
});
