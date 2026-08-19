"""Shared OpenAI-compatible structured-output transport for new pipeline tasks."""

import hashlib
import json
import logging
import os
import re
from email.utils import parsedate_to_datetime
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import httpx

from app.providers.schema_compatibility import strict_schema_failures


logger = logging.getLogger(__name__)


class ProviderFailure(Exception):
    def __init__(self, code: str, reason: str, status_code: int, retryable: bool, *,
                 upstream_status: int | None = None, provider_error_type: str | None = None,
                 provider_error_param: str | None = None, schema_name: str | None = None,
                 validation_fields: list[dict[str, str]] | None = None,
                 retry_after_ms: int | None = None, safe_provider_message: str | None = None,
                 safe_diagnostics: dict[str, Any] | None = None):
        super().__init__(reason)
        self.code = code
        self.reason = reason
        self.status_code = status_code
        self.retryable = retryable
        self.upstream_status = upstream_status
        self.provider_error_type = provider_error_type
        self.provider_error_param = provider_error_param
        self.schema_name = schema_name
        self.validation_fields = list(validation_fields or [])[:12]
        self.retry_after_ms = retry_after_ms
        self.safe_provider_message = safe_provider_message
        self.safe_diagnostics = dict(safe_diagnostics or {})


def _configuration(model_override: str | None = None) -> tuple[str, str, str]:
    provider = os.getenv("AI_PROVIDER", "").strip().lower()
    api_key = os.getenv("AI_API_KEY", "").strip()
    model = (model_override or "").strip() or os.getenv("AI_MODEL", "").strip()
    base_url = os.getenv("AI_BASE_URL", "").strip().rstrip("/")
    if provider not in {"openai", "openai-compatible"} or not api_key or not model:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    if provider == "openai" and not base_url:
        base_url = "https://api.openai.com/v1"
    if not base_url.startswith(("http://", "https://")):
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    return api_key, model, base_url


#: 구조화 호출의 온도. 낮게 두는 이유는 같은 입력에 같은 답을 받기 위해서다 —
#: 안 주면 SDK 기본값으로 돌아 스키마를 못 맞추는 일이 잦아진다(`research/bm/analyze.py`
#: 실측: 시도 6회 중 3회 실패). **단 이 값을 못 받는 모델이 있다** — 아래 참조.
TEMPERATURE = 0.1

#: 모델별 «보내는 방식». **손으로 채우지 않고 실행 중에 배운다** — 모델 목록은 반드시 낡는다.
#: 프로세스 안에서만 산다(재기동하면 모델당 400 을 한 번 더 겪는다). 그 값이 목록을
#: 관리하는 값보다 싸다.
#:
#:   "plain"       온도만 보낸다 — gpt-4.x 계열의 기본
#:   "effort-none" 추론을 끄고 온도를 보낸다 — gpt-5.x 계열에서 옛 동작을 그대로 지키는 길
#:   "no-temp"     온도를 못 쓴다 (추론이 켜진 채로 돈다 · 실효 온도 1.0)
_MODEL_MODE: dict[str, str] = {}

#: 추론 모델에서 **온도를 되찾는** 열쇠. 2026-08-15 실측:
#:   effort=none + temperature=0.1 → 200 (추론 토큰 0)
#:   effort=low  + temperature=0.1 → 400
#: 즉 gpt-5.x 는 「추론을 끄면」 옛 샘플링 인자를 다시 받는다.
_EFFORT_NONE = "none"


#: 모델마다 받아 주는 «샘플링 인자»가 다르다. 이 둘 중 하나 때문에 400 이면 다음 방식으로
#: 내려간다. 다른 400(프롬프트가 길다 · 스키마가 틀렸다)까지 재시도하면 엉뚱한 데 돈을 쓴다.
_SAMPLING_PARAMS = ("temperature", "reasoning_effort")


def _sampling_rejected(response) -> bool:
    """400 이 «샘플링 인자 때문»인가."""
    if response.status_code != 400:
        return False
    try:
        error = (response.json() or {}).get("error") or {}
    except ValueError:
        return False
    if error.get("param") in _SAMPLING_PARAMS:
        return True
    message = (error.get("message") or "").lower()
    return any(name in message for name in _SAMPLING_PARAMS)


def _extract_json(content: str) -> dict[str, Any]:
    fenced = re.search(r"```(?:json)?\s*([\s\S]*?)```", content, flags=re.IGNORECASE)
    candidate = fenced.group(1).strip() if fenced else content.strip()
    start = candidate.find("{")
    if start < 0:
        raise ValueError("JSON object not found")
    value, end = json.JSONDecoder().raw_decode(candidate[start:])
    if not isinstance(value, dict) or candidate[start + end:].strip():
        raise ValueError("Provider result is not one JSON object")
    return value


def _safe_provider_error(response) -> tuple[str | None, str | None, str | None]:
    try:
        payload = response.json()
        error = payload.get("error") if isinstance(payload, dict) else None
        if not isinstance(error, dict):
            return None, None, None
        error_type = error.get("type")
        error_param = error.get("param")
        message = error.get("message")
        safe_message = None
        if isinstance(message, str):
            safe_message = re.sub(r"(?i)(bearer\s+|sk-)[a-z0-9._-]+", r"\1[REDACTED]", message.strip())[:500]
        return (error_type if error_type == "invalid_request_error" else None,
                error_param if error_param == "response_format" else None, safe_message)
    except (TypeError, ValueError, AttributeError):
        return None, None, None


def _retry_after_ms(response) -> int | None:
    raw = response.headers.get("Retry-After", "").strip()
    if not raw:
        return None
    try:
        seconds = float(raw)
    except ValueError:
        try:
            retry_at = parsedate_to_datetime(raw)
            if retry_at.tzinfo is None:
                retry_at = retry_at.replace(tzinfo=timezone.utc)
            seconds = (retry_at - datetime.now(timezone.utc)).total_seconds()
        except (TypeError, ValueError, OverflowError):
            return None
    if seconds <= 0:
        return None
    return min(15_000, max(1_000, int(seconds * 1000)))


def _replay_settings() -> tuple[Path | None, str]:
    """녹화/재생 설정. `AI_REPLAY_DIR` 이 비어 있으면 통째로 꺼진다(기본값).

    이 경로를 타는 모듈은 재무·시장 인터뷰(코딩·타깃)·법률 3종·여정이다.
    `concept_portfolio_v2` 는 자기 `ProviderGateway` 에 이미 녹화가 있어 여기 오지 않는다 —
    **두 곳이 따로**인 것은 의도이고, 합치려면 봉투 모양부터 맞춰야 한다.

    - `auto`(기본): 있으면 재생, 없으면 실제로 호출하고 **성공한 것만** 녹화한다
    - `replay`: 없으면 호출하지 않고 실패한다. 결정론이 필요한 자리(테스트·시연)용

    ⚠ 재생이어도 `AI_PROVIDER`/`AI_API_KEY`/`AI_MODEL` 은 여전히 있어야 한다 —
      모델 이름이 녹화 키의 일부라 설정 없이는 무엇을 재생할지 정할 수 없다.
    """
    raw = os.getenv("AI_REPLAY_DIR", "").strip()
    if not raw:
        return None, "off"
    mode = os.getenv("AI_REPLAY_MODE", "auto").strip().lower() or "auto"
    if mode not in {"auto", "replay"}:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    return Path(raw), mode


def _replay_key(body: dict[str, Any]) -> str:
    """요청 본문 전체가 키다 — 모델·온도·스키마·프롬프트가 하나라도 다르면 다른 녹화다.

    ⚠ `body` 에는 API 키가 들어 있지 않다(헤더에만 있다). 녹화 파일에 그대로 적으므로
      **여기에 비밀을 얹지 말 것.**
    """
    canonical = json.dumps(body, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


async def execute_structured_prompt(system: str, user: str, model_override: str | None = None,
                                    response_schema: dict[str, Any] | None = None,
                                    schema_name: str | None = None,
                                    task_type: str | None = None,
                                    timeout_seconds_override: float | None = None) -> dict[str, Any]:
    if response_schema is not None:
        schema_failures = strict_schema_failures(response_schema)
        if schema_failures:
            raise ProviderFailure(
                "RESULT_SCHEMA_INVALID", "PROVIDER_RESPONSE_SCHEMA_REJECTED", 502, False,
                schema_name=schema_name or "structured_result",
                validation_fields=schema_failures,
                safe_diagnostics={"stage": "OFFLINE_SCHEMA_PREFLIGHT"},
            )
    api_key, model, base_url = _configuration(model_override)
    try:
        timeout_seconds = (float(timeout_seconds_override) if timeout_seconds_override is not None
                           else float(os.getenv("AI_PROVIDER_TIMEOUT_SECONDS", "60")))
        if timeout_seconds <= 0:
            raise ValueError
    except ValueError as failure:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False) from failure
    response_format: dict[str, Any] = {"type": "json_object"}
    if response_schema is not None:
        response_format = {"type": "json_schema", "json_schema": {
            "name": schema_name or "structured_result", "strict": True, "schema": response_schema}}
    def _build(mode: str) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "model": model,
            "messages": [{"role": "system", "content": system},
                         {"role": "user", "content": user}],
            "response_format": response_format}
        if mode != "no-temp":
            payload["temperature"] = TEMPERATURE
        if mode == "effort-none":
            payload["reasoning_effort"] = _EFFORT_NONE
        return payload

    mode = _MODEL_MODE.get(model, "plain")
    body = _build(mode)

    replay_dir, replay_mode = _replay_settings()
    replay_path = replay_dir / f"{_replay_key(body)}.json" if replay_dir is not None else None
    if replay_path is not None and replay_path.is_file():
        try:
            cached = json.loads(replay_path.read_text(encoding="utf-8"))["result"]
        except (OSError, KeyError, ValueError) as failure:
            # 깨진 녹화는 **조용히 실제 호출로 넘어가지 않는다** — 그러면 재생 중인 줄 알고
            # 돈을 쓰게 된다. 파일을 지우라고 시끄럽게 말한다.
            raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False) from failure
        logger.info("Structured prompt replayed taskType=%s model=%s key=%s",
                    task_type or "STRUCTURED_TASK", model, replay_path.stem)
        return cached
    if replay_mode == "replay":
        logger.error("Structured prompt replay miss taskType=%s model=%s key=%s dir=%s",
                     task_type or "STRUCTURED_TASK", model,
                     replay_path.stem if replay_path else "-", replay_dir)
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False,
                              schema_name=schema_name)

    async def _post(payload: dict[str, Any]):
        try:
            async with httpx.AsyncClient(timeout=timeout_seconds) as client:
                return await client.post(f"{base_url}/chat/completions",
                                         headers={"Authorization": f"Bearer {api_key}",
                                                  "Content-Type": "application/json"}, json=payload)
        except (httpx.TimeoutException, httpx.NetworkError) as failure:
            raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", 503, True,
                                  schema_name=schema_name) from failure

    response = await _post(body)

    # ── 온도를 거절하는 모델이면 **한 번 배우고 다시 보낸다.**
    #
    # 추론 모델(gpt-5.x 계열)은 `temperature` 를 기본값 1 로 고정하고 다른 값을 400 으로
    # 거절한다(2026-08-15 실측: 「Only the default (1) value is supported」). 이 함수는
    # 제품의 **모든** 구조화 호출이 지나는 자리라, 여기서 막히면 모델을 바꾸는 순간
    # 컨셉·BM·법률·마케팅·인터뷰가 **한꺼번에** 죽는다.
    #
    # ★ 그렇다고 온도를 그냥 버리지 않는다. **추론을 끄면 온도가 돌아온다** —
    #   `reasoning_effort="none"` + `temperature=0.1` 은 200 이고, `low` 와 함께면 400 이다.
    #   온도를 버리면 실효 온도가 1.0 이 되어 **모델을 바꾼 것 이상이 바뀐다**(같은 입력에
    #   같은 답이 안 온다). 옛 동작을 지키는 쪽을 먼저 시도하고, 그것도 안 되면 그때 버린다.
    #
    # 모델 이름 목록을 손으로 관리하지 않는 이유는 그 목록이 반드시 낡기 때문이다.
    # 대신 거절을 겪고 기억한다 — 프로세스당 모델당 400 한두 번이 값이다.
    for attempt in ("effort-none", "no-temp"):
        if not _sampling_rejected(response):
            break
        logger.warning("Provider rejects sampling args — retrying as %s model=%s taskType=%s",
                       attempt, model, task_type or "STRUCTURED_TASK")
        body = _build(attempt)
        # 녹화 열쇠는 **실제로 보낸 본문**에서 나와야 한다. 안 그러면 다음 판이 보내지도
        # 않을 본문의 이름으로 저장된 답을 재생한다.
        replay_path = replay_dir / f"{_replay_key(body)}.json" if replay_dir is not None else None
        if replay_path is not None and replay_path.is_file():
            try:
                return json.loads(replay_path.read_text(encoding="utf-8"))["result"]
            except (OSError, KeyError, ValueError) as failure:
                raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID",
                                      503, False) from failure
        response = await _post(body)
        # **통한 방식만 기억한다.** 통하지 않은 것을 기억하면 다음 호출이 같은 400 을
        # 겪고도 더 내려갈 곳이 없다고 판단한다.
        if not _sampling_rejected(response):
            _MODEL_MODE[model] = attempt

    if response.status_code in (401, 403):
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    if response.status_code == 429:
        raise ProviderFailure("RATE_LIMITED", "DEPENDENCY_RATE_LIMITED", 429, True,
                              upstream_status=429, schema_name=schema_name,
                              retry_after_ms=_retry_after_ms(response))
    if response.status_code >= 500:
        logger.error("Provider server error taskType=%s model=%s status=%s body=%s",
                     task_type or "STRUCTURED_TASK", model, response.status_code,
                     re.sub(r"(?i)(bearer\\s+|sk-)[a-z0-9._-]+", r"\1[REDACTED]", response.text)[:800])
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", 503, True,
                              upstream_status=response.status_code, schema_name=schema_name)
    if response.status_code == 400 and response_schema is not None:
        error_type, error_param, safe_message = _safe_provider_error(response)
        if error_type == "invalid_request_error" and error_param == "response_format":
            logger.warning("Provider response schema rejected taskType=%s model=%s schemaName=%s",
                           task_type or "STRUCTURED_TASK", model, schema_name or "structured_result")
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "PROVIDER_RESPONSE_SCHEMA_REJECTED", 502, False,
                                  upstream_status=400, provider_error_type=error_type,
                                  provider_error_param=error_param,
                                  schema_name=schema_name or "structured_result",
                                  safe_provider_message=safe_message)
    if response.status_code >= 400:
        raise ProviderFailure("EXECUTION_FAILED", "PERMANENT_EXECUTION_FAILURE", 500, False)
    if len(response.content) > 2 * 1024 * 1024:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "PROVIDER_JSON_INVALID", 502, False,
                              upstream_status=response.status_code, schema_name=schema_name)
    try:
        payload = response.json()
        content = payload["choices"][0]["message"]["content"]
        if isinstance(content, list):
            content = "".join(part.get("text", "") for part in content if isinstance(part, dict))
        result = _extract_json(content)
    except (KeyError, IndexError, TypeError, AttributeError, ValueError, json.JSONDecodeError) as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "PROVIDER_JSON_INVALID", 502, False,
                              upstream_status=response.status_code, schema_name=schema_name) from failure

    # **성공한 것만 녹화한다.** 실패를 녹화하면 다음 판이 그 실패를 공짜로 재생하고,
    # 고친 뒤에도 계속 실패한다.
    if replay_path is not None:
        try:
            replay_dir.mkdir(parents=True, exist_ok=True)
            replay_path.write_text(json.dumps(
                {"taskType": task_type or "STRUCTURED_TASK", "schemaName": schema_name,
                 "request": body, "result": result}, ensure_ascii=False, indent=2), encoding="utf-8")
            logger.info("Structured prompt recorded taskType=%s model=%s key=%s",
                        task_type or "STRUCTURED_TASK", model, replay_path.stem)
        except OSError:
            # 녹화 실패로 **실제 결과를 버리지 않는다.** 이건 편의 기능이다.
            logger.warning("Structured prompt recording failed taskType=%s dir=%s",
                           task_type or "STRUCTURED_TASK", replay_dir)
    return result
