import hashlib
import json
import os
import unicodedata
import re
import logging
from datetime import datetime, timezone
from typing import Any

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse

from pydantic import ValidationError

from app.models.executions import (
    IdeaConversationTurnInputV1,
    InternalExecutionRequestV1,
    InternalExecutionSuccessResponseV1,
)
from app.services.journey_provider import ProviderFailure, execute_journey_task


router = APIRouter(prefix="/internal/v1/ai", tags=["Internal AI Executions"])
logger = logging.getLogger(__name__)
TASK_TYPES = {
    "IDEA_INTERPRETATION", "IDEA_CONVERSATION_TURN", "LEGAL_REVIEW", "CONCEPT_GENERATION", "QUICK_ASSESSMENT",
    "DETAILED_ANALYSIS", "PERSONA_CARD_GENERATION", "PERSONA_INTERVIEW",
    "INTERVIEW_SYNTHESIS", "MARKETING_GENERATION", "MARKETING_COMPARISON",
    "FINAL_REPORT_GENERATION",
    "IDEA_LEGAL_PRECHECK", "CONCEPT_LEGAL_VALIDATION", "REGULATORY_BOUNDARY_GENERATION",
    "CONCEPT_EXPLORATION",
}


def internal_error(correlation_id: str, code: str, reason: str, status_code: int,
                   retryable: bool, task_run_id: str | None = None,
                   task_attempt_id: str | None = None,
                   validation_fields: list[dict[str, str]] | None = None) -> JSONResponse:
    detail: dict[str, Any] = {"reason": reason}
    if validation_fields:
        detail["fields"] = validation_fields[:12]
    return JSONResponse(status_code=status_code, content={"error": {
        "code": code, "message": "Internal execution request could not be processed.",
        "correlationId": correlation_id, "taskRunId": task_run_id,
        "taskAttemptId": task_attempt_id, "retryable": retryable,
        "details": [detail],
    }})


def safe_validation_fields(failure: ValidationError, prefix: str = "input") -> list[dict[str, str]]:
    expected_types = {
        "missing": "required", "int_type": "integer", "int_parsing": "integer",
        "string_type": "string", "list_type": "array", "dict_type": "object",
        "model_type": "object", "literal_error": "allowed literal", "extra_forbidden": "no extra field",
        "bool_type": "boolean",
    }
    fields = []
    for issue in failure.errors()[:12]:
        location = ".".join(str(part) for part in issue.get("loc", ()))
        path = f"{prefix}.{location}" if location else prefix
        category = str(issue.get("type", "invalid"))[:80]
        fields.append({
            "path": path[:200],
            "expectedType": expected_types.get(category, "valid contract value"),
            "category": category,
        })
    return fields


def canonical_value(value: Any) -> Any:
    if isinstance(value, str):
        return unicodedata.normalize("NFC", value)
    if isinstance(value, list):
        return [canonical_value(item) for item in value]
    if isinstance(value, dict):
        normalized = {}
        for key, item in value.items():
            normalized_key = unicodedata.normalize("NFC", key)
            if normalized_key in normalized:
                raise ValueError("normalized key collision")
            normalized[normalized_key] = canonical_value(item)
        return normalized
    return value


def canonical_hash(body: InternalExecutionRequestV1) -> str:
    value = {key: getattr(body, key) for key in (
        "contractVersion", "taskType", "taskSchemaVersion", "locale", "input"
    )}
    normalized = json.dumps(canonical_value(value), ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return "sha256:" + hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def validate_text_contents(task_input: dict[str, Any]) -> str | None:
    contents = task_input.get("textContents")
    if not isinstance(contents, list) or not 1 <= len(contents) <= 64:
        return "FIELD_CONSTRAINT_VIOLATION"
    total_chunks = 0
    for content in contents:
        if not isinstance(content, dict) or set(content) != {"contentKey", "contentType", "language", "totalCharacters", "contentHash", "chunks"}:
            return "UNKNOWN_FIELD"
        if content["contentType"] != "TEXT" or content["language"] != "ko-KR":
            return "FIELD_CONSTRAINT_VIOLATION"
        chunks = content["chunks"]
        if not isinstance(chunks, list) or not 1 <= len(chunks) <= 64:
            return "CHUNK_COUNT_EXCEEDED"
        total_chunks += len(chunks)
        joined = ""
        for expected, chunk in enumerate(chunks):
            if chunk.get("index") != expected:
                return "CHUNK_SEQUENCE_INVALID"
            text = chunk.get("text")
            if not isinstance(text, str) or not text or len(text) > 16384 or chunk.get("characterCount") != len(text):
                return "FIELD_CONSTRAINT_VIOLATION"
            if chunk.get("chunkHash") != "sha256:" + hashlib.sha256(text.encode("utf-8")).hexdigest():
                return "HASH_MISMATCH"
            joined += text
        if content.get("totalCharacters") != len(joined) or content.get("contentHash") != "sha256:" + hashlib.sha256(joined.encode("utf-8")).hexdigest():
            return "HASH_MISMATCH"
    return "CHUNK_COUNT_EXCEEDED" if total_chunks > 64 else None


@router.post("/executions", response_model=InternalExecutionSuccessResponseV1)
async def execute(request: Request, body: InternalExecutionRequestV1):
    correlation = request.headers.get("X-Correlation-Id") or body.correlationId
    token = os.getenv("AI_INTERNAL_SERVICE_TOKEN", "")
    authorization = request.headers.get("Authorization", "")
    if not authorization:
        return internal_error(correlation, "UNAUTHORIZED_INTERNAL_CALL", "SERVICE_TOKEN_MISSING", 401, False)
    if not token or authorization != f"Bearer {token}":
        return internal_error(correlation, "UNAUTHORIZED_INTERNAL_CALL", "SERVICE_TOKEN_INVALID", 401, False)
    if correlation != body.correlationId:
        return internal_error(correlation, "INVALID_REQUEST", "HEADER_BODY_CORRELATION_MISMATCH", 400, False,
                              body.taskRunId, body.taskAttemptId)
    if body.contractVersion != "1.0":
        return internal_error(correlation, "UNSUPPORTED_CONTRACT_VERSION", "CONTRACT_VERSION_UNSUPPORTED", 422, False,
                              body.taskRunId, body.taskAttemptId)
    if body.taskSchemaVersion != "1.0":
        return internal_error(correlation, "UNSUPPORTED_TASK_SCHEMA_VERSION", "TASK_SCHEMA_VERSION_UNSUPPORTED", 422, False,
                              body.taskRunId, body.taskAttemptId)
    if body.taskType not in TASK_TYPES:
        return internal_error(correlation, "UNSUPPORTED_TASK_TYPE", "TASK_TYPE_UNSUPPORTED", 422, False,
                              body.taskRunId, body.taskAttemptId)
    try:
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z", body.deadlineAt):
            raise ValueError
        deadline = datetime.fromisoformat(body.deadlineAt.replace("Z", "+00:00"))
        if deadline.tzinfo is None or deadline <= datetime.now(timezone.utc):
            raise ValueError
    except ValueError:
        return internal_error(correlation, "DEADLINE_EXCEEDED", "REQUEST_DEADLINE_EXCEEDED", 504, True,
                              body.taskRunId, body.taskAttemptId)
    try:
        calculated_hash = canonical_hash(body)
    except ValueError:
        return internal_error(correlation, "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False,
                              body.taskRunId, body.taskAttemptId)
    if calculated_hash != body.canonicalInputHash:
        return internal_error(correlation, "INVALID_REQUEST", "HASH_MISMATCH", 400, False,
                              body.taskRunId, body.taskAttemptId)
    if body.taskType not in {
        "IDEA_INTERPRETATION", "IDEA_CONVERSATION_TURN", "LEGAL_REVIEW", "CONCEPT_GENERATION",
        "QUICK_ASSESSMENT", "DETAILED_ANALYSIS", "PERSONA_CARD_GENERATION",
        "PERSONA_INTERVIEW", "INTERVIEW_SYNTHESIS",
        "MARKETING_GENERATION", "MARKETING_COMPARISON", "FINAL_REPORT_GENERATION",
        "IDEA_LEGAL_PRECHECK", "CONCEPT_LEGAL_VALIDATION", "REGULATORY_BOUNDARY_GENERATION",
        "CONCEPT_EXPLORATION",
    }:
        return internal_error(correlation, "DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", 503, True,
                              body.taskRunId, body.taskAttemptId)
    if body.taskType == "IDEA_CONVERSATION_TURN":
        try:
            conversation_input = IdeaConversationTurnInputV1.model_validate(body.input)
        except ValidationError as failure:
            fields = safe_validation_fields(failure)
            logger.warning(
                "Internal AI request rejected taskType=%s code=REQUEST_SCHEMA_INVALID fields=%s",
                body.taskType, fields,
            )
            return internal_error(correlation, "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                                  400, False, body.taskRunId, body.taskAttemptId, fields)
        text = json.dumps(conversation_input.model_dump(mode="json"), ensure_ascii=False,
                          sort_keys=True, separators=(",", ":"))
        source_keys = [f"message:{body.input['sourceMessageId']}"] + [
            f"attachment:{attachment['attachmentId']}" for attachment in body.input["attachments"]
        ]
    else:
        reason = validate_text_contents(body.input)
        if reason:
            return internal_error(correlation, "INVALID_REQUEST", reason, 400, False,
                                  body.taskRunId, body.taskAttemptId)
        text = "\n".join(chunk["text"] for content in body.input["textContents"] for chunk in content["chunks"])
        source_keys = [content["contentKey"] for content in body.input["textContents"]]
    generated_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    provenance = {"category": "AI_PROPOSAL", "statementKey": "interpretation-1", "sourceKeys": source_keys,
                  "externalSourceReferences": [], "generatedAt": generated_at, "verificationNeeded": True}
    execution_warnings = []
    try:
        if body.taskType == "CONCEPT_EXPLORATION":
            from app.services.concept_core import execute_concept_exploration
            result = await execute_concept_exploration(body.input)
        elif body.taskType == "REGULATORY_BOUNDARY_GENERATION":
            from app.legal.boundary import execute_regulatory_boundary
            result = await execute_regulatory_boundary(text, body.input)
        elif body.taskType == "CONCEPT_LEGAL_VALIDATION" and body.input.get("validationMode") == "GUARDRAIL_BATCH":
            from app.legal.concept_validation import execute_concept_legal_validation_batch
            result = await execute_concept_legal_validation_batch(body.input, text)
        elif body.taskType == "CONCEPT_LEGAL_VALIDATION" and body.input.get("validationMode") == "GUARDRAIL":
            from app.legal.concept_validation import execute_concept_legal_validation
            result = await execute_concept_legal_validation(body.input, text)
        elif body.taskType in {"IDEA_LEGAL_PRECHECK", "CONCEPT_LEGAL_VALIDATION"}:
            from app.legal.pipeline import execute_legal_source_pipeline
            result = await execute_legal_source_pipeline(body.taskType, text, body.input)
        elif body.taskType == "IDEA_CONVERSATION_TURN":
            def record_schema_repair(issue_count: int) -> None:
                execution_warnings.append({
                    "code": "RESULT_SCHEMA_REPAIRED",
                    "attemptPhase": "REPAIR",
                    "issueCount": issue_count,
                })

            result = await execute_journey_task(
                body.taskType, text, on_schema_repair=record_schema_repair
            )
        else:
            result = await execute_journey_task(body.taskType, text)
    except ProviderFailure as failure:
        return internal_error(correlation, failure.code, failure.reason, failure.status_code, failure.retryable,
                              body.taskRunId, body.taskAttemptId)
    return InternalExecutionSuccessResponseV1(contractVersion="1.0", taskType=body.taskType,
        taskSchemaVersion="1.0", taskRunId=body.taskRunId, taskAttemptId=body.taskAttemptId,
        correlationId=body.correlationId, canonicalInputHash=body.canonicalInputHash,
        resultSchemaVersion="1.0", result=result, warnings=execution_warnings,
        provenance=[provenance], usage=None)
