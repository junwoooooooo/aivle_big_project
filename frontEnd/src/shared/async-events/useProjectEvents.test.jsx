import { act, renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ApiClientProvider } from '../api/ApiClientProvider.jsx';
import { consumeAuthenticatedSse } from './authenticatedSseClient.js';
import { useProjectEvents } from './useProjectEvents.js';

vi.mock('./authenticatedSseClient.js', () => ({ consumeAuthenticatedSse: vi.fn() }));

describe('useProjectEvents cursor hardening', () => {
  it('increments revision only for a new global eventId', async () => {
    let subscription;
    consumeAuthenticatedSse.mockImplementation((options) => { subscription = options; options.onOpen?.(); return new Promise(() => {}); });
    const wrapper = ({ children }) => <ApiClientProvider client={{ get: vi.fn(), stream: vi.fn() }}>{children}</ApiClientProvider>;
    const { result } = renderHook(() => useProjectEvents('41'), { wrapper });
    await act(async () => {});
    act(() => subscription.onEvent({ eventId: '10' }));
    expect(result.current.revision).toBe(1);
    act(() => subscription.onEvent({ eventId: '10' }));
    act(() => subscription.onEvent({ eventId: '9' }));
    expect(result.current.revision).toBe(1);
    act(() => subscription.onEvent({ eventId: '11' }));
    expect(result.current.revision).toBe(2);
  });
});
