import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiClientProvider } from '../../../shared/api/ApiClientProvider.jsx';
import { ApiError } from '../../../shared/api/apiError.js';
import { useJobRecovery } from './useJobRecovery.js';

afterEach(() => {
  vi.useRealTimers();
});

describe('job recovery polling', () => {
  it('recovers the latest job, polls to terminal, loads the plan, and aborts on unmount', async () => {
    vi.useFakeTimers();
    let jobReads = 0;
    const client = {
      get: vi.fn(async (path) => {
        if (path.includes('/jobs/latest')) {
          return { data: { jobId: 7, status: 'QUEUED', progress: 0 } };
        }
        if (path === '/jobs/7') {
          jobReads += 1;
          return {
            data: jobReads === 1
              ? { jobId: 7, status: 'RUNNING', progress: 30 }
              : { jobId: 7, status: 'SUCCEEDED', progress: 100 },
          };
        }
        if (path.includes('/structured-plans/latest')) {
          return {
            data: {
              provider: 'mock',
              completionRate: 100,
              sections: [],
              missingFields: [],
            },
          };
        }
        throw new Error(`unexpected path ${path}`);
      }),
    };
    const wrapper = ({ children }) => (
      <ApiClientProvider client={client}>{children}</ApiClientProvider>
    );
    const hook = renderHook(() => useJobRecovery('1'), { wrapper });

    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
      await vi.runOnlyPendingTimersAsync();
    });
    expect(jobReads).toBe(1);
    expect(hook.result.current.job.status).toBe('RUNNING');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(jobReads).toBe(2);
    expect(hook.result.current.status).toBe('result');
    expect(hook.result.current.plan.completionRate).toBe(100);

    hook.unmount();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(60000);
    });
    expect(jobReads).toBe(2);
  });

  it('stops polling when a recovered job is no longer visible', async () => {
    vi.useFakeTimers();
    let jobReads = 0;
    const client = {
      get: vi.fn(async (path) => {
        if (path.includes('/jobs/latest')) {
          return { data: { jobId: 11, status: 'QUEUED', progress: 0 } };
        }
        jobReads += 1;
        throw new ApiError({
          status: 404,
          code: 'JOB_NOT_FOUND',
          retryable: false,
        });
      }),
    };
    const wrapper = ({ children }) => (
      <ApiClientProvider client={client}>{children}</ApiClientProvider>
    );
    const hook = renderHook(() => useJobRecovery('1'), { wrapper });

    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
      await vi.runOnlyPendingTimersAsync();
    });
    expect(hook.result.current.status).toBe('error');
    expect(jobReads).toBe(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60000);
    });
    expect(jobReads).toBe(1);
  });
});
