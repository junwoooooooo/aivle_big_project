import { describe, expect, it, vi } from 'vitest';

import { consumeAuthenticatedSse, parseSseStream } from './authenticatedSseClient.js';

describe('authenticated SSE client', () => {
  it('parses split frames, multiline data, ids, and heartbeat comments', async () => {
    const encoder = new TextEncoder();
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode(': heartbeat\r\n\r\nid: 2\r\nevent: job-event\r\ndata: {"sequence":2,\r\n'));
        controller.enqueue(encoder.encode('data: "status":"RUNNING"}\r\n\r\n'));
        controller.close();
      },
    });
    const received = [];

    await parseSseStream(body, (event) => received.push(event));

    expect(received).toEqual([expect.objectContaining({
      sequence: 2,
      status: 'RUNNING',
      sseId: '2',
      sseEvent: 'job-event',
    })]);
  });

  it('sends the cursor as Last-Event-ID through the authenticated stream API', async () => {
    const encoder = new TextEncoder();
    const client = {
      stream: vi.fn(async () => new Response(new ReadableStream({
        start(controller) {
          controller.enqueue(encoder.encode('id: 4\ndata: {"sequence":4}\n\n'));
          controller.close();
        },
      }), { headers: { 'Content-Type': 'text/event-stream' } })),
    };
    const onEvent = vi.fn();

    await consumeAuthenticatedSse({ client, jobId: 'job / one', after: 3, onEvent });

    expect(client.stream).toHaveBeenCalledWith(
      '/api/v2/jobs/job%20%2F%20one/events',
      expect.objectContaining({ headers: expect.objectContaining({ 'Last-Event-ID': '3' }) }),
    );
    expect(onEvent).toHaveBeenCalledWith(expect.objectContaining({ sequence: 4 }));
  });

  it('cancels the stream reader when its AbortController is aborted', async () => {
    const cancel = vi.fn();
    const body = new ReadableStream({
      pull() {},
      cancel,
    });
    const controller = new AbortController();
    const parsing = parseSseStream(body, vi.fn(), controller.signal);

    controller.abort();
    await parsing;

    expect(cancel).toHaveBeenCalledOnce();
  });

  it('treats an AbortError from a disconnected reader as cleanup', async () => {
    const controller = new AbortController();
    const reader = {
      read: vi.fn(() => new Promise((resolve, reject) => controller.signal.addEventListener(
        'abort', () => reject(new DOMException('aborted', 'AbortError')), { once: true },
      ))),
      cancel: vi.fn(async () => {}),
      releaseLock: vi.fn(),
    };
    const parsing = parseSseStream({ getReader: () => reader }, vi.fn(), controller.signal);

    controller.abort();

    await expect(parsing).resolves.toBeUndefined();
    expect(reader.releaseLock).toHaveBeenCalledOnce();
  });

  it('ignores a comment line without dropping data in the same frame', async () => {
    const encoder = new TextEncoder();
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode(
          ': server note\nid: 9\nevent: job-event\ndata: {"sequence":9}\n\n',
        ));
        controller.close();
      },
    });
    const received = [];

    await parseSseStream(body, (event) => received.push(event));

    expect(received).toEqual([expect.objectContaining({ sequence: 9, sseId: '9' })]);
  });
});
