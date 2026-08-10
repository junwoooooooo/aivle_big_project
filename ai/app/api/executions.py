import json
import os
import re
import logging
from datetime import datetime, timezone
from typing import Any

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse

from pydantic import ValidationError

from app.models.executions import InternalExecutionRequestV1, InternalExecutionSuccessResponseV1
from app.providers import ProviderFailure
from app.canonical_json import canonical_input_hash


router = APIRouter(prefix="/internal/v1/ai", tags=["Internal AI Executions"])
logger = logging.getLogger(__name__)
TASK_TYPES = {
    "IDEA_BRIEF_DERIVATION",
    "CONCEPT_PORTFOLIO_V2_RUN",
    "CONCEPT_PORTFOLIO_V2_CONTINUE",
    "CONCEPT_PORTFOLIO_V2_SELECTION_ACTION",
    "CONCEPT_CANDIDATE", "CONCEPT_DISTINCTNESS_JUDGE",
    "CONCEPT_LEGAL_REVIEW",
    "CONCEPT_REDESIGN",
    "CONCEPT_HYPOTHESIS_ALTERNATIVE",
    "CONCEPT_DELTA_LEGAL_REVIEW",
    "TECH_OPS_PROPOSAL",
    "FINANCE_ESTIMATE",
    "MARKETING_CONTENT_GENERATION",
}


def internal_error(correlation_id: str, code: str, reason: str, status_code: int,
                   retryable: bool, task_run_id: str | None = None,
                   task_attempt_id: str | None = None,
                   validation_fields: list[dict[str, str]] | None = None,
                   retry_after_ms: int | None = None) -> JSONResponse:
    detail: dict[str, Any] = {"reason": reason}
    if validation_fields:
        detail["fields"] = validation_fields[:12]
    if retry_after_ms is not None:
        detail["retryAfterMs"] = retry_after_ms
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


def canonical_hash(body: InternalExecutionRequestV1) -> str:
    return canonical_input_hash(
        contract_version=body.contractVersion,
        task_type=body.taskType,
        task_schema_version=body.taskSchemaVersion,
        locale=body.locale,
        input_value=body.input,
    )


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
    if body.taskType == "CONCEPT_PORTFOLIO_V2_RUN":
        from app.tasks.concept_portfolio_v2.models import ConceptPortfolioProductionInput
        try:
            portfolio_input = ConceptPortfolioProductionInput.model_validate(body.input)
        except ValidationError as failure:
            return internal_error(correlation, "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                                  400, False, body.taskRunId, body.taskAttemptId,
                                  safe_validation_fields(failure))
        text = json.dumps(portfolio_input.model_dump(mode="json"), ensure_ascii=False,
                          sort_keys=True, separators=(",", ":"))
        source_keys = ["concept-portfolio-v2-input"]
    elif body.taskType == "CONCEPT_PORTFOLIO_V2_CONTINUE":
        from app.tasks.concept_portfolio_v2 import ConceptPortfolioContinuationInput
        try:
            continuation_input = ConceptPortfolioContinuationInput.model_validate(body.input)
        except ValidationError as failure:
            return internal_error(correlation, "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                                  400, False, body.taskRunId, body.taskAttemptId,
                                  safe_validation_fields(failure))
        text = json.dumps(continuation_input.model_dump(mode="json"), ensure_ascii=False,
                          sort_keys=True, separators=(",", ":"))
        source_keys = ["concept-portfolio-v2-continuation-input"]
    elif body.taskType == "CONCEPT_PORTFOLIO_V2_SELECTION_ACTION":
        from app.tasks.concept_portfolio_v2 import ConceptPortfolioSelectionActionInput
        try:
            selection_input = ConceptPortfolioSelectionActionInput.model_validate(body.input)
        except ValidationError as failure:
            return internal_error(correlation, "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                                  400, False, body.taskRunId, body.taskAttemptId,
                                  safe_validation_fields(failure))
        text = json.dumps(selection_input.model_dump(mode="json"), ensure_ascii=False,
                          sort_keys=True, separators=(",", ":"))
        source_keys = ["concept-portfolio-v2-selection-action-input"]
    elif body.taskType in {"CONCEPT_CANDIDATE", "CONCEPT_DISTINCTNESS_JUDGE", "CONCEPT_LEGAL_REVIEW", "CONCEPT_REDESIGN", "CONCEPT_HYPOTHESIS_ALTERNATIVE", "CONCEPT_DELTA_LEGAL_REVIEW"}:
        text = json.dumps(body.input, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        source_keys = ["concept-factory-input"]
    elif body.taskType == "MARKETING_CONTENT_GENERATION":
        text = json.dumps(body.input, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        source_hash = body.input.get("source", {}).get("hash", body.input.get("source", {}).get("sourceSnapshotHash", "unknown"))
        source_keys = [f"finalized-planning:{source_hash}"]
    elif body.taskType == "IDEA_BRIEF_DERIVATION":
        from app.tasks.idea_brief.models import IdeaBriefDerivationInput
        try:
            idea_brief_input = IdeaBriefDerivationInput.model_validate(body.input)
        except ValidationError as failure:
            return internal_error(correlation, "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                                  400, False, body.taskRunId, body.taskAttemptId,
                                  safe_validation_fields(failure))
        text = json.dumps(idea_brief_input.model_dump(mode="json"), ensure_ascii=False,
                          sort_keys=True, separators=(",", ":"))
        source_keys = ["idea-brief-input"]
    else:
        text = json.dumps(body.input, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        source_keys = ["pipeline-input"]
    generated_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    provenance = {"category": "AI_PROPOSAL", "statementKey": "interpretation-1", "sourceKeys": source_keys,
                  "externalSourceReferences": [], "generatedAt": generated_at, "verificationNeeded": True}
    execution_warnings: list[dict[str, Any]] = []
    try:
        if body.taskType == "CONCEPT_PORTFOLIO_V2_RUN":
            from app.tasks.concept_portfolio_v2 import (
                ConceptPortfolioProductionContractError,
                execute_concept_portfolio_v2,
            )
            from app.tasks.concept_portfolio_v2.progress_sender import (
                progress_sender_from_environment,
            )
            try:
                async with progress_sender_from_environment(
                    task_run_id=body.taskRunId,
                    task_attempt_id=body.taskAttemptId,
                    correlation_id=correlation,
                ) as progress:
                    result = await execute_concept_portfolio_v2(
                        body.input,
                        event_sink=progress.emit if progress.enabled else None,
                    )
            except ConceptPortfolioProductionContractError as failure:
                logger.warning(
                    "CPV2 production result contract invalid taskRunId=%s taskAttemptId=%s correlationId=%s",
                    body.taskRunId,
                    body.taskAttemptId,
                    correlation,
                    exc_info=True,
                )
                return internal_error(
                    correlation, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False,
                    body.taskRunId, body.taskAttemptId, failure.validation_fields,
                )
        elif body.taskType == "CONCEPT_PORTFOLIO_V2_CONTINUE":
            from app.tasks.concept_portfolio_v2 import (
                ConceptPortfolioProductionContractError,
                execute_concept_portfolio_v2_continuation,
            )
            try:
                result = await execute_concept_portfolio_v2_continuation(body.input)
            except ConceptPortfolioProductionContractError as failure:
                logger.warning(
                    "CPV2 continuation result contract invalid taskRunId=%s taskAttemptId=%s correlationId=%s",
                    body.taskRunId, body.taskAttemptId, correlation, exc_info=True,
                )
                return internal_error(
                    correlation, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False,
                    body.taskRunId, body.taskAttemptId, failure.validation_fields,
                )
        elif body.taskType == "CONCEPT_PORTFOLIO_V2_SELECTION_ACTION":
            from app.tasks.concept_portfolio_v2 import (
                ConceptPortfolioProductionContractError,
                execute_concept_portfolio_v2_selection_action,
            )
            try:
                result = await execute_concept_portfolio_v2_selection_action(body.input)
            except ConceptPortfolioProductionContractError as failure:
                logger.warning(
                    "CPV2 selection action result contract invalid taskRunId=%s taskAttemptId=%s correlationId=%s",
                    body.taskRunId, body.taskAttemptId, correlation, exc_info=True,
                )
                return internal_error(
                    correlation, "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False,
                    body.taskRunId, body.taskAttemptId, failure.validation_fields,
                )
        elif body.taskType == "CONCEPT_CANDIDATE":
            from app.tasks.concept_candidate import execute_concept_candidate
            result = await execute_concept_candidate(body.input)
        elif body.taskType == "CONCEPT_DISTINCTNESS_JUDGE":
            from app.tasks.concept_distinctness_judge import execute_concept_distinctness_judge
            result = await execute_concept_distinctness_judge(body.input)
        elif body.taskType == "CONCEPT_LEGAL_REVIEW":
            from app.tasks.concept_legal_review import execute_concept_legal_review
            result = await execute_concept_legal_review(body.input)
        elif body.taskType == "CONCEPT_REDESIGN":
            from app.tasks.concept_redesign import execute_concept_redesign
            result = await execute_concept_redesign(body.input)
        elif body.taskType == "CONCEPT_HYPOTHESIS_ALTERNATIVE":
            from app.tasks.concept_hypothesis_alternative import execute_concept_hypothesis_alternative
            result = await execute_concept_hypothesis_alternative({
                "hypothesisType": body.input.get("hypothesisType"),
                "rejectedValue": body.input.get("rejectedValue"),
                "proposalVersion": body.input.get("proposalVersion"),
                "candidate": body.input.get("candidate"),
            })
        elif body.taskType == "CONCEPT_DELTA_LEGAL_REVIEW":
            from app.tasks.concept_legal_review import execute_concept_legal_review
            result = await execute_concept_legal_review({
                "legalFactPattern": body.input.get("legalFactPattern"),
                "factPatternHash": body.input.get("factPatternHash"),
                "externalFactContext": body.input.get("externalFactContext"),
            })
        elif body.taskType == "TECH_OPS_PROPOSAL":
            from app.tasks.tech_ops_proposal import execute_tech_ops_proposal
            result = await execute_tech_ops_proposal({
                "contextJson": body.input.get("contextJson"),
                "proposalVersion": body.input.get("proposalVersion"),
                "rejectedProposalJson": body.input.get("rejectedProposalJson", ""),
            })
        elif body.taskType == "FINANCE_ESTIMATE":
            from app.tasks.finance_estimate import execute_finance_estimate
            result = await execute_finance_estimate({
                "contextJson": body.input.get("contextJson"),
                "fieldKey": body.input.get("fieldKey"),
                "proposalVersion": body.input.get("proposalVersion"),
                "rejectedProposalJson": body.input.get("rejectedProposalJson", ""),
            })
        elif body.taskType == "MARKETING_CONTENT_GENERATION":
            from app.tasks.marketing_content import execute_marketing_content
            result = await execute_marketing_content(body.input)
        elif body.taskType == "IDEA_BRIEF_DERIVATION":
            from app.tasks.idea_brief import execute_idea_brief_derivation
            result = await execute_idea_brief_derivation(body.input)
        else:
            return internal_error(correlation, "UNSUPPORTED_TASK_TYPE", "TASK_TYPE_UNSUPPORTED", 422, False,
                                  body.taskRunId, body.taskAttemptId)
    except ProviderFailure as failure:
        logger.warning(
            "AI execution failed taskType=%s taskRunId=%s taskAttemptId=%s correlationId=%s "
            "code=%s reason=%s retryable=%s schemaName=%s upstreamStatus=%s "
            "providerErrorType=%s providerErrorParam=%s retryAfterMs=%s validationFields=%s",
            body.taskType, body.taskRunId, body.taskAttemptId, correlation,
            failure.code, failure.reason, failure.retryable, failure.schema_name,
            failure.upstream_status, failure.provider_error_type, failure.provider_error_param,
            failure.retry_after_ms,
            failure.validation_fields,
        )
        return internal_error(correlation, failure.code, failure.reason, failure.status_code, failure.retryable,
                              body.taskRunId, body.taskAttemptId, failure.validation_fields,
                              failure.retry_after_ms)
    return InternalExecutionSuccessResponseV1(contractVersion="1.0", taskType=body.taskType,
        taskSchemaVersion="1.0", taskRunId=body.taskRunId, taskAttemptId=body.taskAttemptId,
        correlationId=body.correlationId, canonicalInputHash=body.canonicalInputHash,
        resultSchemaVersion="1.0", result=result, warnings=execution_warnings,
        provenance=[provenance], usage=None)
