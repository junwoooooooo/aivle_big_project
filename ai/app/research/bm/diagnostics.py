"""BM structured-output 검증 실패의 비밀정보 없는 내부 진단."""
from __future__ import annotations

import logging
from typing import Any

from pydantic import ValidationError


logger = logging.getLogger(__name__)

_SAFE_MESSAGES = {
    "missing": "Field required",
    "list_type": "Value must be an array",
    "string_type": "Value must be a string",
    "literal_error": "Value must match an allowed literal",
    "enum": "Value must match an allowed enum member",
    "too_short": "Collection does not meet the minimum length",
    "too_long": "Collection exceeds the maximum length",
    "value_error": "Value does not satisfy the schema validator",
}


def safe_validation_diagnostics(failure: ValidationError) -> list[dict[str, Any]]:
    """Pydantic 입력값·문서 URL·validator context를 버리고 위치와 분류만 남긴다."""
    diagnostics = []
    for issue in failure.errors(include_input=False, include_url=False)[:20]:
        error_type = str(issue.get("type") or "invalid")[:80]
        diagnostics.append({
            "loc": [str(part)[:80] for part in issue.get("loc", ())[:12]],
            "type": error_type,
            "msg": _SAFE_MESSAGES.get(error_type, "Invalid schema value"),
        })
    return diagnostics


def log_bm_validation_failure(
    failure: ValidationError,
    diagnostic_context: dict[str, str] | None,
) -> None:
    context = diagnostic_context or {}
    errors = safe_validation_diagnostics(failure)
    logger.warning(
        "BM schema validation failed taskRunId=%s taskAttemptId=%s correlationId=%s "
        "schemaName=BMAnalysisResult exceptionClass=%s validationErrorCount=%s errors=%s",
        str(context.get("taskRunId") or "")[:128],
        str(context.get("taskAttemptId") or "")[:128],
        str(context.get("correlationId") or "")[:128],
        type(failure).__name__,
        len(failure.errors(include_input=False, include_url=False)),
        errors,
    )
