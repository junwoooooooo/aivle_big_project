import json
import logging
import os

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import Response

from app.api.errors import (
    ApiHttpException,
    api_http_exception_handler,
    http_exception_handler,
    internal_exception_handler,
    validation_exception_handler,
)
from app.api.executions import router as execution_router, internal_error
from app.api.financial import router as financial_router
from app.legal.registry import LegalRegistry, RegistryError
from app.models.contracts import HealthResponse
from app.request_context import (
    REQUEST_ID_HEADER,
    current_request_id,
    resolve_request_id,
)


app = FastAPI(title="New Pipeline AI Server", version="1.0.0")
logger = logging.getLogger(__name__)
INTERNAL_JSON_MAX_BYTES = 2 * 1024 * 1024


def internal_json_limit_exceeded(raw: bytes) -> bool:
    return len(raw) > INTERNAL_JSON_MAX_BYTES


app.add_exception_handler(ApiHttpException, api_http_exception_handler)


async def routed_validation_exception_handler(request: Request, exception: RequestValidationError):
    if request.url.path == "/internal/v1/ai/executions":
        error_types = {error.get("type") for error in exception.errors()}
        reason = "JSON_PARSE_FAILED" if "json_invalid" in error_types else (
            "UNKNOWN_FIELD" if "extra_forbidden" in error_types else "FIELD_CONSTRAINT_VIOLATION")
        correlation = request.headers.get("X-Correlation-Id") or resolve_request_id(None)
        fields = safe_validation_fields(exception, "request")
        logger.warning("Internal AI request rejected taskType=%s code=REQUEST_SCHEMA_INVALID fields=%s",
                       getattr(request.state, "internal_task_type", None), fields)
        return internal_error(correlation, "INVALID_REQUEST", reason, 400, False,
                              validation_fields=fields)
    return await validation_exception_handler(request, exception)


app.add_exception_handler(RequestValidationError, routed_validation_exception_handler)
app.add_exception_handler(HTTPException, http_exception_handler)
app.add_exception_handler(Exception, internal_exception_handler)


@app.middleware("http")
async def request_id_middleware(request: Request, call_next):
    if request.url.path == "/internal/v1/ai/executions":
        raw = await request.body()
        if internal_json_limit_exceeded(raw):
            correlation = request.headers.get("X-Correlation-Id") or resolve_request_id(None)
            return internal_error(correlation, "PAYLOAD_TOO_LARGE", "REQUEST_BYTES_EXCEEDED", 413, False)
        try:
            def reject_duplicate_keys(pairs):
                result = {}
                for key, value in pairs:
                    if key in result:
                        raise ValueError("duplicate key")
                    result[key] = value
                return result
            parsed = json.loads(raw, object_pairs_hook=reject_duplicate_keys)
            request.state.internal_task_run_id = parsed.get("taskRunId") if isinstance(parsed, dict) else None
            request.state.internal_task_attempt_id = parsed.get("taskAttemptId") if isinstance(parsed, dict) else None
            request.state.internal_task_type = parsed.get("taskType") if isinstance(parsed, dict) else None
        except (json.JSONDecodeError, UnicodeDecodeError, ValueError):
            correlation = request.headers.get("X-Correlation-Id") or resolve_request_id(None)
            return internal_error(correlation, "INVALID_REQUEST", "JSON_PARSE_FAILED", 400, False)
    request.state.request_id = resolve_request_id(request.headers.get(REQUEST_ID_HEADER))
    response = await call_next(request)
    if request.url.path == "/internal/v1/ai/executions":
        body = b"".join([chunk async for chunk in response.body_iterator])
        if internal_json_limit_exceeded(body):
            correlation = request.headers.get("X-Correlation-Id") or resolve_request_id(None)
            return internal_error(correlation, "PAYLOAD_TOO_LARGE", "RESPONSE_BYTES_EXCEEDED", 500, False,
                                  getattr(request.state, "internal_task_run_id", None),
                                  getattr(request.state, "internal_task_attempt_id", None))
        headers = dict(response.headers)
        headers.pop("content-length", None)
        response = Response(content=body, status_code=response.status_code,
                            headers=headers, media_type=response.media_type)
    response.headers[REQUEST_ID_HEADER] = current_request_id(request)
    return response


app.include_router(execution_router)
app.include_router(financial_router)


def health_payload(request: Request, health_status: str) -> HealthResponse:
    return HealthResponse(status=health_status, service="ai-server",
                          request_id=current_request_id(request))


@app.get("/health", response_model=HealthResponse)
def health_check(request: Request):
    return health_payload(request, "ok")


@app.get("/health/live", response_model=HealthResponse)
def health_live(request: Request):
    return health_payload(request, "live")


@app.get("/health/ready", response_model=HealthResponse)
def health_ready(request: Request):
    return health_payload(request, "ready")


@app.get("/health/capabilities/concept-factory")
def concept_factory_capability():
    ai_configured = (
        os.getenv("AI_PROVIDER", "").strip().lower() in {"openai", "openai-compatible"}
        and bool(os.getenv("AI_API_KEY", "").strip())
        and bool(os.getenv("AI_MODEL", "").strip())
    )
    internal_token = bool(os.getenv("AI_INTERNAL_SERVICE_TOKEN", "").strip())
    moleg_configured = bool(os.getenv("MOLEG_API_KEY", "").strip())
    try:
        registry = LegalRegistry()
        registry_status = {"available": True, "version": registry.version}
    except RegistryError:
        registry_status = {"available": False, "version": None}
    available = ai_configured and internal_token and moleg_configured and registry_status["available"]
    return {
        "capability": "CONCEPT_FACTORY",
        "available": available,
        "checks": {
            "aiProviderConfig": ai_configured,
            "internalServiceToken": internal_token,
            "legalRegistry": registry_status,
            "molegConfiguration": moleg_configured,
        },
    }
