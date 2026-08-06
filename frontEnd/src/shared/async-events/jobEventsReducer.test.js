import { describe, expect, it } from 'vitest';

import { initialJobEventsState, jobEventsReducer } from './jobEventsReducer.js';

describe('job events reducer', () => {
  it('orders events and removes replay duplicates by sequence', () => {
    const first = jobEventsReducer(initialJobEventsState, {
      type: 'APPEND',
      events: [{ sequence: 2, eventType: 'SECOND' }, { sequence: 1, eventType: 'FIRST' }],
    });
    const replayed = jobEventsReducer(first, {
      type: 'APPEND',
      events: [{ sequence: 2, eventType: 'SECOND' }, { sequence: 3, eventType: 'THIRD' }],
    });

    expect(replayed.events.map((event) => event.eventType)).toEqual(['FIRST', 'SECOND', 'THIRD']);
    expect(replayed.lastSequence).toBe(3);
  });

  it('marks the latest completed event as terminal', () => {
    const state = jobEventsReducer(initialJobEventsState, {
      type: 'APPEND',
      events: [{ sequence: 1, status: 'COMPLETED' }],
    });

    expect(state.terminal).toBe(true);
    expect(state.connectionState).toBe('terminal');
  });

  it('treats a blocked regulatory boundary as terminal', () => {
    const state = jobEventsReducer(initialJobEventsState, {
      type: 'APPEND', events: [{ sequence: 1, status: 'BLOCKED' }],
    });
    expect(state.terminal).toBe(true);
  });
});
