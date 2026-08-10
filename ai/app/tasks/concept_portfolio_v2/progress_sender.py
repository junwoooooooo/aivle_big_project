"""CPV2 trace를 Backend internal callback으로 비차단 전달한다."""

from __future__ import annotations

import asyncio
import logging
import os
from collections.abc import Awaitable, Callable
from typing import Any

import httpx

from .models import ProductionTraceEvent


logger = logging.getLogger(__name__)
ProgressPost = Callable[[dict[str, Any]], Awaitable[None]]


class ProductionProgressSender:
    def __init__(self, *, task_run_id: str, task_attempt_id: str, correlation_id: str,
                 base_url: str | None = None, token: str | None = None,
                 post: ProgressPost | None = None, queue_size: int = 256,
                 flush_seconds: float = 2.0, client: httpx.AsyncClient | None = None):
        self.task_run_id = task_run_id
        self.task_attempt_id = task_attempt_id
        self.correlation_id = correlation_id
        self.base_url = (base_url or "").rstrip("/")
        self.token = token or ""
        self.post = post
        self.flush_seconds = flush_seconds
        self.queue: asyncio.Queue[dict[str, Any] | None] = asyncio.Queue(maxsize=queue_size)
        self.task: asyncio.Task[None] | None = None
        self.client = client
        self._owns_client = False

    @property
    def enabled(self) -> bool:
        return self.post is not None or bool(self.base_url and self.token)

    async def __aenter__(self) -> "ProductionProgressSender":
        if self.enabled:
            if self.post is None and self.client is None:
                self.client = httpx.AsyncClient(timeout=httpx.Timeout(2.0))
                self._owns_client = True
            self.task = asyncio.create_task(self._run())
        return self

    async def __aexit__(self, exc_type, exc, traceback) -> None:
        if self.task is not None:
            try:
                self.queue.put_nowait(None)
            except asyncio.QueueFull:
                pass
            try:
                await asyncio.wait_for(self.task, timeout=self.flush_seconds)
            except (TimeoutError, asyncio.CancelledError):
                self.task.cancel()
        if self.client is not None and self._owns_client:
            await self.client.aclose()

    def emit(self, event: ProductionTraceEvent) -> None:
        if not self.enabled:
            return
        payload = event.model_dump(mode="json")
        payload.update({
            "taskRunId": self.task_run_id,
            "taskAttemptId": self.task_attempt_id,
            "correlationId": self.correlation_id,
        })
        try:
            self.queue.put_nowait(payload)
        except asyncio.QueueFull:
            logger.warning("CPV2 progress queue full taskRunId=%s", self.task_run_id)

    async def _run(self) -> None:
        while True:
            payload = await self.queue.get()
            if payload is None:
                return
            try:
                if self.post is not None:
                    await self.post(payload)
                else:
                    response = await self.client.post(
                        f"{self.base_url}/internal/v1/ai/task-progress",
                        json=payload,
                        headers={"X-AI-Internal-Token": self.token},
                    )
                    response.raise_for_status()
            except httpx.HTTPStatusError as failure:
                logger.warning(
                    "CPV2 progress callback rejected taskRunId=%s taskAttemptId=%s sequence=%s status=%s",
                    self.task_run_id, self.task_attempt_id, payload.get("sequence"),
                    failure.response.status_code,
                )
            except Exception as failure:
                logger.warning(
                    "CPV2 progress callback failed taskRunId=%s taskAttemptId=%s sequence=%s exceptionType=%s",
                    self.task_run_id, self.task_attempt_id, payload.get("sequence"),
                    failure.__class__.__name__,
                )


def progress_sender_from_environment(*, task_run_id: str, task_attempt_id: str,
                                     correlation_id: str) -> ProductionProgressSender:
    return ProductionProgressSender(
        task_run_id=task_run_id,
        task_attempt_id=task_attempt_id,
        correlation_id=correlation_id,
        base_url=os.getenv("BACKEND_INTERNAL_BASE_URL", ""),
        token=os.getenv("AI_INTERNAL_SERVICE_TOKEN", ""),
    )
