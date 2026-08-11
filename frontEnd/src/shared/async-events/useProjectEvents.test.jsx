import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApiClientProvider } from '../api/ApiClientProvider.jsx';
import { consumeAuthenticatedSse } from './authenticatedSseClient.js';
import { useProjectEvents } from './useProjectEvents.js';

vi.mock('./authenticatedSseClient.js', () => ({ consumeAuthenticatedSse: vi.fn() }));

describe('useProjectEvents cursor hardening', () => {
  it('reconnects through SSE without issuing REST polling requests', async () => {
    vi.useFakeTimers();
    const client = { get: vi.fn(), stream: vi.fn() };
    consumeAuthenticatedSse
      .mockRejectedValueOnce(new Error('temporary disconnect'))
      .mockImplementationOnce(() => new Promise(() => {}));
    const wrapper = ({ children }) => <ApiClientProvider client={client}>{children}</ApiClientProvider>;
    const { result } = renderHook(() => useProjectEvents('41'), { wrapper });
    await act(async () => {});
    expect(result.current.transport).toBe('reconnecting');
    await act(async () => { vi.advanceTimersByTime(1500); });
    expect(consumeAuthenticatedSse).toHaveBeenCalledTimes(2);
    expect(client.get).not.toHaveBeenCalled();
    vi.useRealTimers();
  });
  it('increments revision only for a new global eventId', async () => {
    vi.useFakeTimers();
    let subscription;
    consumeAuthenticatedSse.mockImplementation((options) => { subscription = options; options.onOpen?.(); return new Promise(() => {}); });
    const wrapper = ({ children }) => <ApiClientProvider client={{ get: vi.fn(), stream: vi.fn() }}>{children}</ApiClientProvider>;
    const { result } = renderHook(() => useProjectEvents('41'), { wrapper });
    await act(async () => {});
    act(() => subscription.onEvent({ eventId: '10' }));
    act(() => vi.advanceTimersByTime(180));
    expect(result.current.revision).toBe(1);
    act(() => subscription.onEvent({ eventId: '10' }));
    act(() => subscription.onEvent({ eventId: '9' }));
    expect(result.current.revision).toBe(1);
    act(() => subscription.onEvent({ eventId: '11' }));
    act(() => vi.advanceTimersByTime(180));
    expect(result.current.revision).toBe(2);
    vi.useRealTimers();
  });
  it('coalesces a burst into one canonical refresh revision', async () => {
    vi.useFakeTimers();
    let subscription;
    consumeAuthenticatedSse.mockImplementation((options) => { subscription = options; options.onOpen?.(); return new Promise(() => {}); });
    const wrapper = ({ children }) => <ApiClientProvider client={{ get: vi.fn(), stream: vi.fn() }}>{children}</ApiClientProvider>;
    const { result } = renderHook(() => useProjectEvents('41'), { wrapper });
    await act(async () => {});
    act(() => { for (let id = 20; id <= 24; id += 1) subscription.onEvent({ eventId: String(id) }); });
    act(() => vi.advanceTimersByTime(180));
    expect(result.current.revision).toBe(1);
    vi.useRealTimers();
  });
});
