export async function consumeAuthenticatedSse({
  client,
  jobId,
  after = 0,
  signal,
  onOpen,
  onEvent,
}) {
  const response = await client.stream(
    `/api/v2/jobs/${encodeURIComponent(jobId)}/events`,
    {
      signal,
      headers: {
        Accept: 'text/event-stream',
        'Last-Event-ID': String(after),
      },
    },
  );
  onOpen?.();
  await parseSseStream(response.body, onEvent, signal);
}

export async function parseSseStream(body, onEvent, signal) {
  if (!body?.getReader) throw new Error('ReadableStream body is required.');
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  const abort = () => reader.cancel().catch(() => {});
  signal?.addEventListener('abort', abort, { once: true });
  try {
    while (true) {
      if (signal?.aborted) return;
      const { done, value } = await reader.read();
      buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });
      buffer = drainFrames(buffer, onEvent);
      if (done) break;
    }
    if (buffer.trim()) dispatchFrame(buffer, onEvent);
  } catch (error) {
    if (signal?.aborted) return;
    throw error;
  } finally {
    signal?.removeEventListener('abort', abort);
    reader.releaseLock();
  }
}

function drainFrames(buffer, onEvent) {
  let remaining = buffer;
  let delimiter = remaining.match(/\r?\n\r?\n/);
  while (delimiter) {
    const index = delimiter.index;
    dispatchFrame(remaining.slice(0, index), onEvent);
    remaining = remaining.slice(index + delimiter[0].length);
    delimiter = remaining.match(/\r?\n\r?\n/);
  }
  return remaining;
}

function dispatchFrame(frame, onEvent) {
  if (!frame) return;
  let id = null;
  let eventType = 'message';
  const data = [];
  for (const line of frame.split(/\r?\n/)) {
    if (!line || line.startsWith(':')) continue;
    const separator = line.indexOf(':');
    const field = separator < 0 ? line : line.slice(0, separator);
    let value = separator < 0 ? '' : line.slice(separator + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'id') id = value;
    if (field === 'event') eventType = value;
    if (field === 'data') data.push(value);
  }
  if (!data.length) return;
  const payload = JSON.parse(data.join('\n'));
  onEvent?.({ ...payload, sseId: id, sseEvent: eventType });
}
