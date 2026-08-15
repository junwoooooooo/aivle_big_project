"""분석 실행과 분리된 bounded, best-effort JSONL progress writer."""

from __future__ import annotations

import io
import json
import logging
import queue
import threading


logger = logging.getLogger(__name__)
_STOP = object()


class SafeProgressJsonl:
    def __init__(self, path: str, *, queue_size: int = 128, truncate: bool = False):
        self.path = path
        self.queue: queue.Queue[dict | object] = queue.Queue(maxsize=queue_size)
        self.thread: threading.Thread | None = None
        if path:
            if truncate:
                try:
                    with io.open(path, "w", encoding="utf-8"):
                        pass
                except OSError:
                    return
            self.thread = threading.Thread(target=self._run, name="safe-progress-jsonl", daemon=True)
            self.thread.start()

    def emit(self, event: dict) -> None:
        if self.thread is None:
            return
        allowed = {key: event[key] for key in (
            "stage", "action", "status", "safeSummary", "reasonCode", "decision"
        ) if key in event}
        try:
            self.queue.put_nowait(allowed)
        except queue.Full:
            logger.warning("progress JSONL queue full")

    def close(self) -> None:
        if self.thread is None:
            return
        try:
            self.queue.put_nowait(_STOP)
        except queue.Full:
            return
        self.thread.join(timeout=1.0)
        self.thread = None

    def _run(self) -> None:
        while True:
            event = self.queue.get()
            if event is _STOP:
                return
            try:
                with io.open(self.path, "a", encoding="utf-8") as handle:
                    handle.write(json.dumps(event, ensure_ascii=False, separators=(",", ":")) + "\n")
            except OSError as failure:
                logger.warning("progress JSONL write failed exceptionType=%s", failure.__class__.__name__)
