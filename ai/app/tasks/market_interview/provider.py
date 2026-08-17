"""Market Interview-specific model and sampling controls over the shared transport."""

import os
from typing import Any

from app.providers import ProviderFailure, execute_structured_prompt


RESPONDENT_WORKLOAD = "RESPONDENT"
CLASSIFICATION_TEMPERATURE = 0.1
DEFAULT_RESPONDENT_TEMPERATURE = 1.0
DEFAULT_CONCURRENCY = 4
MAX_CONCURRENCY = 16


def interview_concurrency() -> int:
    try:
        value = int(os.getenv("MARKET_INTERVIEW_CONCURRENCY", str(DEFAULT_CONCURRENCY)))
    except ValueError as failure:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False) from failure
    if value < 1 or value > MAX_CONCURRENCY:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    return value


def _temperature() -> float:
    try:
        value = float(os.getenv("MARKET_INTERVIEW_TEMPERATURE", str(DEFAULT_RESPONDENT_TEMPERATURE)))
    except ValueError as failure:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False) from failure
    if value < 0 or value > 2:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    return value


def _reasoning_effort() -> str | None:
    return os.getenv("MARKET_INTERVIEW_REASONING_EFFORT", "").strip() or None


def _model() -> str | None:
    return os.getenv("MARKET_INTERVIEW_MODEL", "").strip() or None


def _supports_temperature(model: str | None, reasoning_effort: str | None) -> bool:
    if reasoning_effort:
        return False
    normalized = (model or os.getenv("AI_MODEL", "")).strip().lower()
    return not normalized.startswith(("o1", "o3", "o4"))


async def execute_market_interview_prompt(system: str, user: str, *, workload: str,
                                          response_schema: dict[str, Any] | None = None,
                                          schema_name: str | None = None,
                                          task_type: str | None = None) -> dict[str, Any]:
    model = _model()
    effort = _reasoning_effort()
    temperature = (_temperature() if workload == RESPONDENT_WORKLOAD
                   else CLASSIFICATION_TEMPERATURE)
    if not _supports_temperature(model, effort):
        temperature = None
    return await execute_structured_prompt(
        system,
        user,
        model_override=model,
        response_schema=response_schema,
        schema_name=schema_name,
        task_type=task_type,
        temperature_override=temperature,
        reasoning_effort_override=effort,
    )
