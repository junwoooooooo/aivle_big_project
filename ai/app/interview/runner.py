"""1단계 응답 수집 — 응답자 1인당 셀 하나.

우열 조사와 세 가지가 다르다.

1. **자극이 하나다.** 좌우 제시가 없으니 방향(fwd/rev)도 적응식 k 도 없다.
   1인 1셀이라 n=20 이면 20 셀이다(우열형은 같은 n 에 477 셀이었다).
2. **구조화 출력을 쓴다.** 우열 조사가 평문을 고집한 것은 검증 계기(`g3e_runner`)와
   요청 본문을 바이트 동일하게 유지하기 위해서였다. 이 조사는 그 성적을 인용하지
   않으므로 그 제약을 승계하지 않는다 — 9칸을 평문에서 긁어내는 쪽이 오히려 위험하다.
3. **`max_completion_tokens` 를 보낸다.** `app/twin/runner.py` 는 아직 옛 이름
   `max_tokens` 를 쓴다. 새 모델(gpt-5.6-terra)이 옛 이름을 400 으로 거절하는 것이
   실측됐다(`TWIN_SURVEY_HANDOFF.md` §9) — 거기서 고치면 동결이 깨지므로 여기서만 바로 쓴다.

`temperature=1.0` 은 그대로다. 합성 응답자는 이미 실제보다 답이 균질한데
(2025~26 문헌의 일관된 관찰), 온도까지 낮추면 20명이 사실상 한 사람이 된다.
"""

import asyncio
import json
import logging
import os
import time

import httpx
from pydantic import ValidationError

from app.interview.models import InterviewAnswer
from app.interview.questions import SYSTEM, build_prompt
from app.providers import ProviderFailure
from app.twin.runner import BACKOFF0, RETRY_MAX, _configuration, _retry_after_ms

logger = logging.getLogger(__name__)

TEMPERATURE = 1.0
# 9문항 × 1~3문장 + 파고들기 3개(3·4·8번은 이유·비교·조건까지 답한다).
# ⚠ 낮게 잡으면 `finish_reason=="length"` → `truncated` → 그 사람이 분모에서 빠지고,
#   절반이 빠지면 `__init__` 의 게이트가 조사 전체를 죽인다. 7문항 시절 값은 1200 이었다.
MAX_COMPLETION_TOKENS = 2000
SCHEMA_NAME = "market_interview_answer_v1"


def build_body(prompt_text: str, model: str) -> dict:
    return {
        "model": model,
        "messages": [{"role": "system", "content": SYSTEM},
                     {"role": "user", "content": prompt_text}],
        "temperature": TEMPERATURE,
        "max_completion_tokens": MAX_COMPLETION_TOKENS,
        "response_format": {"type": "json_schema", "json_schema": {
            "name": SCHEMA_NAME, "strict": True,
            "schema": InterviewAnswer.model_json_schema()}},
    }


def parse_answer(text: str | None) -> dict | None:
    """9칸이 정확히 채워졌을 때만 채택. **위반은 재시도하지 않는다** — 측정치다.

    한 명이 형식을 어겨도 조사는 계속된다. 그 사람은 분모에서 빠지고 계측에 남는다.
    """
    try:
        return InterviewAnswer.model_validate(json.loads(text or "")).model_dump()
    except (ValueError, ValidationError):
        return None


class Runner:
    def __init__(self, base_url: str, api_key: str, model: str, timeout: int = 180):
        self.endpoint = f"{base_url.rstrip('/')}/chat/completions"
        self.api_key = api_key
        self.model = model
        self.timeout = timeout

    async def call(self, client: httpx.AsyncClient, prompt_text: str) -> dict:
        started = time.time()
        record: dict = {}
        try:
            response = await client.post(
                self.endpoint, json=build_body(prompt_text, self.model), timeout=self.timeout,
                headers={"Authorization": f"Bearer {self.api_key}",
                         "Content-Type": "application/json"})
        except httpx.TimeoutException:
            return {"ok": False, "kind": "timeout", "wall_s": round(time.time() - started, 1)}
        except httpx.HTTPError as failure:
            return {"ok": False, "kind": "transport", "detail": str(failure)[:500],
                    "wall_s": round(time.time() - started, 1)}

        record["wall_s"] = round(time.time() - started, 1)
        if response.status_code != 200:
            record.update(
                ok=False, kind="rate_limit" if response.status_code == 429 else "http_error",
                detail=response.text[:500], retry_after_ms=_retry_after_ms(response))
            return record
        try:
            envelope = response.json()
        except ValueError:
            record.update(ok=False, kind="bad_envelope", detail=response.text[:500])
            return record

        choices = envelope.get("choices") or []
        raw = (choices[0].get("message", {}).get("content") or "") if choices else ""
        finish = choices[0].get("finish_reason") if choices else None
        record.update(raw=raw, usage=envelope.get("usage") or {},
                      model_reported=envelope.get("model"))
        if finish == "length":
            record.update(ok=False, kind="truncated",
                          detail=f"max_completion_tokens={MAX_COMPLETION_TOKENS} 도달")
            return record
        if not raw.strip():
            record.update(ok=False, kind="empty_result")
            return record
        record["ok"] = True
        return record


async def _call_retry(runner: Runner, client: httpx.AsyncClient, prompt: str,
                      semaphore: asyncio.Semaphore, stats: dict) -> dict:
    """429/5xx/타임아웃만 재시도. 한도 대기는 재시도 예산을 쓰지 않는다."""
    attempt = 0
    while True:
        async with semaphore:
            record = await runner.call(client, prompt)
        attempt += 1
        if record.get("ok"):
            return record
        kind = record.get("kind")
        if kind == "rate_limit":
            stats["rateLimited"] += 1
            wait = (record.get("retry_after_ms")
                    or BACKOFF0 * 1000 * (2 ** (attempt - 1))) / 1000
            stats["waitSeconds"] += wait
            await asyncio.sleep(wait)
            continue
        if kind == "timeout":
            stats["timeouts"] += 1
        if kind not in ("timeout", "transport", "http_error", "bad_envelope") \
                or attempt >= RETRY_MAX:
            return record
        stats["retries"] += 1
        await asyncio.sleep(BACKOFF0 * (2 ** (attempt - 1)))


def _env_int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, "").strip() or default)
    except ValueError:
        return default


async def run_interviews(cards: dict[str, str], board_text: str,
                         budget_seconds: float) -> tuple[list[dict], dict]:
    """`cards` 의 전원에게 같은 컨셉보드를 보이고 9문항을 받는다.

    돌려주는 행: `{subject, ok, kind, answers}`. `subject` 는 `pid_hash` 이고
    **결과 봉투에는 싣지 않는다** — 여기서는 대표 응답자를 고르는 열쇠로만 쓴다.
    """
    api_key, model, base_url = _configuration()
    runner = Runner(base_url, api_key, model)
    concurrency = _env_int("TWIN_CONCURRENCY", 32)
    semaphore = asyncio.Semaphore(concurrency)
    stats = {"cells": 0, "rateLimited": 0, "timeouts": 0, "retries": 0,
             "formatViolations": 0, "failures": 0, "truncated": 0, "waitSeconds": 0.0,
             "promptTokens": 0, "completionTokens": 0,
             "model": model, "concurrency": concurrency}
    started = time.time()
    rows: list[dict] = []

    async def one(client, subject):
        record = await _call_retry(
            runner, client, build_prompt(cards[subject], board_text), semaphore, stats)
        answers = parse_answer(record.get("raw")) if record.get("ok") else None
        usage = record.get("usage") or {}
        stats["cells"] += 1
        stats["promptTokens"] += usage.get("prompt_tokens") or 0
        stats["completionTokens"] += usage.get("completion_tokens") or 0
        if record.get("ok"):
            if answers is None:
                stats["formatViolations"] += 1
        else:
            stats["failures"] += 1
            if record.get("kind") == "truncated":
                stats["truncated"] += 1
        rows.append({"subject": subject, "ok": bool(answers), "kind": record.get("kind"),
                     "answers": answers})

    if budget_seconds <= 0:
        raise ProviderFailure("DEADLINE_EXCEEDED", "REQUEST_DEADLINE_EXCEEDED", 504, True)

    limits = httpx.Limits(max_connections=concurrency + 8,
                          max_keepalive_connections=concurrency + 8)
    async with httpx.AsyncClient(limits=limits) as client:
        await asyncio.gather(*(one(client, subject) for subject in sorted(cards)))

    stats["seconds"] = round(time.time() - started, 1)
    stats["llmCalls"] = stats["cells"]
    rows.sort(key=lambda row: row["subject"])          # 뒤 단계가 전부 결정론적이어야 한다
    return rows, stats
