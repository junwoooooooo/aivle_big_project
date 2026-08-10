"""Shared OpenAI-compatible structured-output transport for new pipeline tasks."""

import json
import logging
import os
import re
from email.utils import parsedate_to_datetime
from datetime import datetime, timezone
from typing import Any

import httpx


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


async def execute_structured_prompt(system: str, user: str, model_override: str | None = None,
                                    response_schema: dict[str, Any] | None = None,
                                    schema_name: str | None = None,
                                    task_type: str | None = None) -> dict[str, Any]:
    api_key, model, base_url = _configuration(model_override)
    try:
        timeout_seconds = float(os.getenv("AI_PROVIDER_TIMEOUT_SECONDS", "60"))
        if timeout_seconds <= 0:
            raise ValueError
    except ValueError as failure:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False) from failure
    response_format: dict[str, Any] = {"type": "json_object"}
    if response_schema is not None:
        response_format = {"type": "json_schema", "json_schema": {
            "name": schema_name or "structured_result", "strict": True, "schema": response_schema}}
    body = {"model": model, "messages": [{"role": "system", "content": system},
            {"role": "user", "content": user}], "temperature": 0.1,
            "response_format": response_format}
    try:
        async with httpx.AsyncClient(timeout=timeout_seconds) as client:
            response = await client.post(f"{base_url}/chat/completions",
                                         headers={"Authorization": f"Bearer {api_key}",
                                                  "Content-Type": "application/json"}, json=body)
    except (httpx.TimeoutException, httpx.NetworkError) as failure:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", 503, True,
                              schema_name=schema_name) from failure
    if response.status_code in (401, 403):
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    if response.status_code == 429:
        raise ProviderFailure("RATE_LIMITED", "DEPENDENCY_RATE_LIMITED", 429, True,
                              upstream_status=429, schema_name=schema_name,
                              retry_after_ms=_retry_after_ms(response))
    if response.status_code >= 500:
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
        return _extract_json(content)
    except (KeyError, IndexError, TypeError, AttributeError, ValueError, json.JSONDecodeError) as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "PROVIDER_JSON_INVALID", 502, False,
                              upstream_status=response.status_code, schema_name=schema_name) from failure
