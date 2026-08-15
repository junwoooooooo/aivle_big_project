export const initialJobEventsState = {
  events: [],
  lastSequence: 0,
  connectionState: 'idle',
  transport: null,
  error: null,
  terminal: false,
};

export const TERMINAL_JOB_STATUSES = new Set(['COMPLETED', 'FAILED', 'NEEDS_INPUT', 'BLOCKED']);

export function isTerminalJobEvent(event) {
  return TERMINAL_JOB_STATUSES.has(event?.status);
}

export function jobEventsReducer(state, action) {
  switch (action.type) {
    case 'CONNECTING':
      return { ...state, connectionState: 'connecting', transport: 'SSE', error: null };
    case 'CONNECTED':
      return { ...state, connectionState: 'live', transport: 'SSE', error: null };
    case 'RECONNECTING':
      return {
        ...state,
        connectionState: 'connecting',
        transport: 'SSE',
        error: action.error ?? null,
      };
    case 'APPEND': {
      const bySequence = new Map(state.events.map((event) => [event.sequence, event]));
      for (const event of action.events ?? []) {
        if (Number.isSafeInteger(event?.sequence) && event.sequence > 0) {
          bySequence.set(event.sequence, event);
        }
      }
      const events = [...bySequence.values()].sort((left, right) => left.sequence - right.sequence);
      const terminal = isTerminalJobEvent(events.at(-1));
      return {
        ...state,
        events,
        lastSequence: events.at(-1)?.sequence ?? state.lastSequence,
        connectionState: terminal ? 'terminal' : state.connectionState,
        terminal,
        error: null,
      };
    }
    case 'ERROR':
      return { ...state, connectionState: 'error', error: action.error };
    case 'STOPPED':
      return { ...state, connectionState: 'stopped' };
    case 'RESET':
      return { ...initialJobEventsState };
    default:
      return state;
  }
}
