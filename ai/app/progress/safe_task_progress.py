"""Market/Twin의 관측 이벤트를 Backend에 비차단 방식으로 전달한다.

이 채널은 분석 결과의 정본이 아니다. 큐 포화, 네트워크 오류, 수신 거부는
로그만 남기며 분석 실행과 결과 계약을 변경하지 않는다.
"""

from __future__ import annotations

import asyncio
import logging
import os
from collections.abc import Awaitable, Callable
from datetime import datetime, timezone
from typing import Any

import httpx


logger = logging.getLogger(__name__)
ProgressPost = Callable[[dict[str, Any]], Awaitable[None]]


class SafeTaskProgressSender:
    def __init__(self, *, task_run_id: str, task_attempt_id: str, correlation_id: str,
                 base_url: str | None = None, token: str | None = None,
                 post: ProgressPost | None = None, queue_size: int = 128,
                 flush_seconds: float = 1.0, callback_timeout_seconds: float = 1.0,
                 client: httpx.AsyncClient | None = None):
        self.task_run_id = task_run_id
        self.task_attempt_id = task_attempt_id
        self.correlation_id = correlation_id
        self.base_url = (base_url or "").rstrip("/")
        self.token = token or ""
        self.post = post
        self.flush_seconds = flush_seconds
        self.callback_timeout_seconds = callback_timeout_seconds
        self.queue: asyncio.Queue[dict[str, Any] | None] = asyncio.Queue(maxsize=queue_size)
        self.task: asyncio.Task[None] | None = None
        self.client = client
        self._owns_client = False
        self._sequence = 0

    @property
    def enabled(self) -> bool:
        return self.post is not None or bool(self.base_url and self.token)

    async def __aenter__(self) -> "SafeTaskProgressSender":
        if self.enabled:
            if self.post is None and self.client is None:
                self.client = httpx.AsyncClient(timeout=httpx.Timeout(self.callback_timeout_seconds))
                self._owns_client = True
            self.task = asyncio.create_task(self._run())
        return self

    async def __aexit__(self, exc_type, exc, traceback) -> None:
        if self.task is not None:
            try:
                self.queue.put_nowait(None)
            except asyncio.QueueFull:
                self.task.cancel()
            try:
                await asyncio.wait_for(self.task, timeout=self.flush_seconds)
            except (TimeoutError, asyncio.CancelledError):
                self.task.cancel()
        if self.client is not None and self._owns_client:
            await self.client.aclose()

    def emit(self, event: dict[str, Any]) -> None:
        if not self.enabled:
            return
        self._sequence += 1
        payload: dict[str, Any] = {
            "taskRunId": self.task_run_id,
            "taskAttemptId": self.task_attempt_id,
            "correlationId": self.correlation_id,
            "sequence": self._sequence,
            "stage": str(event.get("stage") or "PROGRESS")[:40],
            "action": str(event.get("action") or "UPDATED")[:80],
            "status": str(event.get("status") or "RUNNING")[:40],
            "safeSummary": str(event.get("safeSummary") or "진행 상태를 갱신했습니다.")[:500],
            "occurredAt": event.get("occurredAt")
                or datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        }
        for optional in ("reasonCode", "decision"):
            if event.get(optional) is not None:
                payload[optional] = str(event[optional])[:80]
        try:
            self.queue.put_nowait(payload)
        except asyncio.QueueFull:
            logger.warning("safe progress queue full taskRunId=%s", self.task_run_id)

    async def _run(self) -> None:
        while True:
            payload = await self.queue.get()
            if payload is None:
                return
            try:
                if self.post is not None:
                    await asyncio.wait_for(self.post(payload), timeout=self.callback_timeout_seconds)
                else:
                    response = await self.client.post(
                        f"{self.base_url}/internal/v1/ai/task-progress",
                        json=payload,
                        headers={"X-AI-Internal-Token": self.token},
                    )
                    response.raise_for_status()
            except Exception as failure:
                logger.warning(
                    "safe progress callback failed taskRunId=%s taskAttemptId=%s sequence=%s exceptionType=%s",
                    self.task_run_id, self.task_attempt_id, payload.get("sequence"),
                    failure.__class__.__name__,
                )


def progress_sender_from_environment(*, task_run_id: str, task_attempt_id: str,
                                     correlation_id: str) -> SafeTaskProgressSender:
    return SafeTaskProgressSender(
        task_run_id=task_run_id,
        task_attempt_id=task_attempt_id,
        correlation_id=correlation_id,
        base_url=os.getenv("BACKEND_INTERNAL_BASE_URL", ""),
        token=os.getenv("AI_INTERNAL_SERVICE_TOKEN", ""),
    )
