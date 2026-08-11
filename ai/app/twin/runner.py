"""트윈 러너 — OpenAI 호환 HTTP 팬아웃.

`build_body` / `parse_choice` / `fingerprint` 는 `combine_csv/_build/g3e/g3e_runner.py` 의
동명 함수를 **그대로** 옮긴 것이다. 0단계(계기 동등성 재측정)가 그 요청 본문으로 잰다.
**본문이 달라지면 그 시험의 결론이 이 파이프라인에 전이되지 않는다.**
`FINGERPRINT_FIELDS` 가 그 계약이고 테스트가 양쪽 지문 일치를 못박는다.

구조화 출력을 쓰지 않는다. 검증된 템플릿은 "이유 2~3문장 → 마지막 줄 `선택: A`" 형태고,
JSON 스키마 모드로 바꾸면 프롬프트가 달라져 계기가 또 바뀐다. `response_format` 없이
평문 chat 을 보낸다.
"""

import asyncio
import hashlib
import json
import logging
import os
import re
import time

import httpx

from app.providers import ProviderFailure          # ← 유일한 외부 결합
from app.twin.stimuli import DIRECTIONS, K_WAVE1, SYSTEM, build_prompt, needs_wave2, to_xy

logger = logging.getLogger(__name__)

CHOICE_RE = re.compile(r"^선택: (A|B|없음)$")

# 지문에 들어가는 것 = 계기를 정의하는 것. 여기 없는 값이 답을 바꾸면 지문이 거짓말을 한다.
FINGERPRINT_FIELDS = ("model", "temperature", "max_tokens", "system_sha256", "endpoint")

# temperature 0 이면 rep1 == rep2 라 적응식 k 가 죽는다. G2가 실측한 생성 분산
# (Δ_T 0.477)이 사라져 설계 자체가 무너진다. 1.0 은 검증 당시 CLI 기본 경로와 같은 자리다.
TEMPERATURE = 1.0
MAX_TOKENS = 1024               # 이유 2~3문장 + 선택 줄. 잘리면 마지막 줄이 안 맞는다.
RETRY_MAX = 3
BACKOFF0 = 5.0


def _env_int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, "").strip() or default)
    except ValueError:
        return default


def _configuration() -> tuple[str, str, str]:
    """`app.providers.structured._configuration` 과 같은 규칙. 같은 실패 코드를 낸다.

    복제한 이유: 이 모듈이 구조화 출력 경로를 타지 않으므로 그쪽 내부 함수에 매달리지
    않는다. 결합은 `ProviderFailure` 하나로 끝낸다.
    """
    provider = os.getenv("AI_PROVIDER", "").strip().lower()
    api_key = os.getenv("AI_API_KEY", "").strip()
    model = os.getenv("AI_MODEL", "").strip()
    base_url = os.getenv("AI_BASE_URL", "").strip().rstrip("/")
    if provider not in {"openai", "openai-compatible"} or not api_key or not model:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    if provider == "openai" and not base_url:
        base_url = "https://api.openai.com/v1"
    if not base_url.startswith(("http://", "https://")):
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    return api_key, model, base_url


def build_body(prompt_text: str, system: str, model: str,
               temperature: float, max_tokens: int) -> dict:
    """요청 본문 — **g3e 와 바이트 동일해야 한다.**

    `response_format` 없음, `stream` 없음, 도구 없음.
    """
    return {
        "model": model,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": prompt_text},
        ],
        "temperature": temperature,
        "max_tokens": max_tokens,
    }


def fingerprint(system: str, model: str, temperature: float,
                max_tokens: int, endpoint: str) -> tuple[str, dict]:
    """CLI 시절 `argv_sha256` 자리를 대신한다. 시스템 프롬프트는 해시로 치환한다."""
    redacted = {
        "model": model,
        "temperature": temperature,
        "max_tokens": max_tokens,
        "system_sha256": hashlib.sha256(system.encode("utf-8")).hexdigest(),
        "endpoint": endpoint,
    }
    assert set(redacted) == set(FINGERPRINT_FIELDS), "지문 필드가 계약과 갈라졌다"
    blob = json.dumps(redacted, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(blob.encode("utf-8")).hexdigest(), redacted


def parse_choice(text: str | None) -> str | None:
    """마지막 비어있지 않은 줄이 정확히 일치할 때만 채택.

    뒤에 말이 붙으면 형식 위반이다 — 관대하게 읽으면 트윈이 규칙을 어긴 것을 측정치가
    아니라 파서가 덮는다. **위반은 재시도하지 않는다.**
    """
    for line in reversed((text or "").splitlines()):
        stripped = line.strip()
        if stripped:
            matched = CHOICE_RE.match(stripped)
            return matched.group(1) if matched else None
    return None


def _retry_after_ms(response: httpx.Response) -> int | None:
    value = response.headers.get("retry-after")
    if not value:
        return None
    try:
        return int(float(value) * 1000)
    except ValueError:
        return None


class Runner:
    """셀 하나가 보는 것은 ① system ② 프롬프트 1개, 그게 전부다."""

    def __init__(self, base_url: str, api_key: str, model: str, system: str = SYSTEM,
                 temperature: float = TEMPERATURE, max_tokens: int = MAX_TOKENS,
                 timeout: int = 180):
        self.endpoint = f"{base_url.rstrip('/')}/chat/completions"
        self.api_key = api_key
        self.model = model
        self.system = system
        self.temperature = temperature
        self.max_tokens = max_tokens
        self.timeout = timeout
        self.fingerprint, self.fingerprint_fields = fingerprint(
            system, model, temperature, max_tokens, self.endpoint)

    async def call(self, client: httpx.AsyncClient, prompt_text: str) -> dict:
        body = build_body(prompt_text, self.system, self.model,
                          self.temperature, self.max_tokens)
        started = time.time()
        record: dict = {"request_fingerprint": self.fingerprint}
        try:
            response = await client.post(
                self.endpoint, json=body, timeout=self.timeout,
                headers={"Authorization": f"Bearer {self.api_key}",
                         "Content-Type": "application/json"})
        except httpx.TimeoutException:
            record.update(ok=False, kind="timeout", detail=f"{self.timeout}s 초과",
                          wall_s=round(time.time() - started, 1))
            return record
        except httpx.HTTPError as failure:
            record.update(ok=False, kind="transport", detail=str(failure)[:500],
                          wall_s=round(time.time() - started, 1))
            return record

        record["wall_s"] = round(time.time() - started, 1)
        record["status_code"] = response.status_code
        if response.status_code != 200:
            record.update(
                ok=False,
                kind="rate_limit" if response.status_code == 429 else "http_error",
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
        record.update(raw=raw, usage=envelope.get("usage") or {}, finish_reason=finish,
                      model_reported=envelope.get("model"))
        if finish == "length":
            # 잘린 응답은 마지막 줄이 선택 줄이 아니다. 형식 위반과 구분해 남긴다.
            record.update(ok=False, kind="truncated",
                          detail=f"max_tokens={self.max_tokens} 도달")
            return record
        if not raw.strip():
            record.update(ok=False, kind="empty_result", detail="content 가 비었다")
            return record
        record["ok"] = True
        return record


async def call_retry(runner: Runner, client: httpx.AsyncClient, prompt: str,
                     semaphore: asyncio.Semaphore, stats: dict) -> tuple[dict, int]:
    """429/5xx/타임아웃만 재시도. **형식 위반은 재시도하지 않는다** — 그건 측정치다.

    한도 대기는 재시도 예산을 소모하지 않는다(G3B에서 잡은 러너 결함).
    """
    attempt = 0
    while True:
        async with semaphore:
            record = await runner.call(client, prompt)
        attempt += 1
        if record.get("ok"):
            return record, attempt
        kind = record.get("kind")
        if kind == "rate_limit":
            stats["rateLimited"] += 1
            wait = (record.get("retry_after_ms")
                    or BACKOFF0 * 1000 * (2 ** (attempt - 1))) / 1000
            stats["waitSeconds"] += wait
            await asyncio.sleep(wait)
            continue                                   # 재시도 예산 미소모
        if kind == "timeout":
            stats["timeouts"] += 1
        if kind not in ("timeout", "transport", "http_error", "bad_envelope") \
                or attempt >= RETRY_MAX:
            return record, attempt
        stats["retries"] += 1
        await asyncio.sleep(BACKOFF0 * (2 ** (attempt - 1)))


def _new_stats() -> dict:
    return {"cells": 0, "rateLimited": 0, "timeouts": 0, "retries": 0,
            "formatViolations": 0, "failures": 0, "truncated": 0, "waitSeconds": 0.0,
            "promptTokens": 0, "completionTokens": 0}


async def run_survey(cards: dict[str, str], pairs: list[dict], situation: str,
                     budget_seconds: float) -> tuple[list[dict], dict]:
    """양방향 전수 × 적응식 k 로 전 셀을 돌리고 원장과 계측을 돌려준다.

    1파: 전 셀 rep 1,2 → 2파: rep1 ≠ rep2 인 셀만 rep 3.
    예산이 마르면 **조용히 줄이지 않고** 잘린 사실을 계측에 남긴다.
    """
    api_key, model, base_url = _configuration()
    runner = Runner(base_url, api_key, model)
    concurrency = _env_int("TWIN_CONCURRENCY", 32)
    semaphore = asyncio.Semaphore(concurrency)
    stats = _new_stats()
    stats["model"] = model
    stats["requestFingerprint"] = runner.fingerprint
    stats["concurrency"] = concurrency
    started = time.time()
    rows: list[dict] = []

    def remaining() -> float:
        return budget_seconds - (time.time() - started)

    async def one(client, subject, pair, direction, rep):
        prompt = build_prompt(cards[subject], pair, direction, situation)
        record, attempts = await call_retry(runner, client, prompt, semaphore, stats)
        choice = parse_choice(record.get("raw")) if record.get("ok") else None
        usage = record.get("usage") or {}
        row = {"subject": subject, "pair_id": pair["pairId"], "direction": direction,
               "rep": rep, "ok": record.get("ok"), "kind": record.get("kind"),
               "choice": choice, "attempts": attempts, "raw": record.get("raw"),
               "model_reported": record.get("model_reported")}
        rows.append(row)
        stats["cells"] += 1
        stats["promptTokens"] += usage.get("prompt_tokens") or 0
        stats["completionTokens"] += usage.get("completion_tokens") or 0
        if record.get("ok"):
            if choice is None:
                stats["formatViolations"] += 1
        else:
            stats["failures"] += 1
            if record.get("kind") == "truncated":
                stats["truncated"] += 1
        return row

    limits = httpx.Limits(max_connections=concurrency + 8,
                          max_keepalive_connections=concurrency + 8)
    async with httpx.AsyncClient(limits=limits) as client:
        # ── 1파 ────────────────────────────────────────────────────────
        wave1 = [(s, p, d, k) for p in pairs for s in sorted(cards)
                 for d in DIRECTIONS for k in range(1, K_WAVE1 + 1)]
        if remaining() <= 0:
            raise ProviderFailure("DEADLINE_EXCEEDED", "REQUEST_DEADLINE_EXCEEDED", 504, True)
        await asyncio.gather(*(one(client, *job) for job in wave1))

        # ── 2파 — 불일치 셀만 ──────────────────────────────────────────
        seen: dict[tuple, dict] = {}
        for row in rows:
            if row["ok"]:
                key = (row["subject"], row["pair_id"], row["direction"])
                seen.setdefault(key, {})[row["rep"]] = to_xy(row["choice"], row["direction"])
        wave2 = [(subject, pair_id, direction)
                 for (subject, pair_id, direction), reps in seen.items()
                 if needs_wave2(reps)]
        pair_of = {p["pairId"]: p for p in pairs}
        stats["wave2Cells"] = len(wave2)
        if wave2 and remaining() > 0:
            await asyncio.gather(*(one(client, s, pair_of[p], d, 3) for s, p, d in wave2))
        elif wave2:
            # 예산이 말랐다. 불일치 셀은 미결정으로 남고 분모에서 빠진다 — 조용히 넘기지 않는다.
            stats["wave2Skipped"] = len(wave2)
            logger.warning("twin survey wave2 skipped by budget cells=%d", len(wave2))

    stats["seconds"] = round(time.time() - started, 1)
    stats["llmCalls"] = stats["cells"]
    return rows, stats
