import json
import os
import logging
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import Response
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from app.api.errors import (
    ApiHttpException,
    api_http_exception_handler,
    http_exception_handler,
    internal_exception_handler,
    validation_exception_handler,
)
from app.api.marketing import router as marketing_router
from app.api.tasks import router as task_router
from app.api.executions import router as execution_router, internal_error, safe_validation_fields
from app.models.contracts import EchoResponse, HealthResponse
from app.request_context import (
    REQUEST_ID_HEADER,
    current_request_id,
    resolve_request_id,
)
from app.services import banner_service


app = FastAPI(
    title="AIVLE Test AI Server",
    version="0.2.0",
)
logger = logging.getLogger(__name__)
INTERNAL_JSON_MAX_BYTES = 2 * 1024 * 1024


def internal_json_limit_exceeded(raw: bytes) -> bool:
    return len(raw) > INTERNAL_JSON_MAX_BYTES
app.add_exception_handler(
    ApiHttpException,
    api_http_exception_handler,
)
async def routed_validation_exception_handler(request: Request, exception: RequestValidationError):
    if request.url.path == "/internal/v1/ai/executions":
        error_types = {error.get("type") for error in exception.errors()}
        reason = "JSON_PARSE_FAILED" if "json_invalid" in error_types else (
            "UNKNOWN_FIELD" if "extra_forbidden" in error_types else "FIELD_CONSTRAINT_VIOLATION"
        )
        correlation = request.headers.get("X-Correlation-Id") or resolve_request_id(None)
        fields = safe_validation_fields(exception, "request")
        logger.warning(
            "Internal AI request rejected taskType=%s code=REQUEST_SCHEMA_INVALID fields=%s",
            getattr(request.state, "internal_task_type", None), fields,
        )
        return internal_error(correlation, "INVALID_REQUEST", reason, 400, False,
                              validation_fields=fields)
    return await validation_exception_handler(request, exception)


app.add_exception_handler(RequestValidationError, routed_validation_exception_handler)
app.add_exception_handler(
    HTTPException,
    http_exception_handler,
)
app.add_exception_handler(
    Exception,
    internal_exception_handler,
)


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
    request_id = resolve_request_id(
        request.headers.get(REQUEST_ID_HEADER)
    )
    request.state.request_id = request_id
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
    response.headers[REQUEST_ID_HEADER] = current_request_id(
        request
    )
    return response


output_directory = Path(__file__).resolve().parent / "outputs"

# Mock-only static output serving. A later MinIO phase replaces this boundary.
app.mount(
    "/outputs",
    StaticFiles(
        directory=str(output_directory),
        check_dir=False,
    ),
    name="outputs",
)
app.include_router(marketing_router)
app.include_router(task_router)
app.include_router(execution_router)


def health_payload(
    request: Request,
    health_status: str,
) -> HealthResponse:
    return HealthResponse(
        status=health_status,
        service="ai-server",
        request_id=current_request_id(request),
    )


@app.get("/health", response_model=HealthResponse)
def health_check(request: Request):
    return health_payload(request, "ok")


@app.get("/health/live", response_model=HealthResponse)
def health_live(request: Request):
    return health_payload(request, "live")


@app.get("/health/ready", response_model=HealthResponse)
def health_ready(request: Request):
    marketing_route_ready = any(
        getattr(route, "path", None)
        == "/api/v1/marketing/banners/generate"
        for route in app.routes
    )
    try:
        banner_service.OUTPUT_DIRECTORY.mkdir(
            parents=True,
            exist_ok=True,
        )
        output_ready = (
            banner_service.OUTPUT_DIRECTORY.is_dir()
            and os.access(
                banner_service.OUTPUT_DIRECTORY,
                os.W_OK,
            )
        )
    except OSError:
        output_ready = False

    if not marketing_route_ready or not output_ready:
        raise ApiHttpException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            code="AI_SERVER_INTERNAL_ERROR",
            message="AI 서버가 요청을 처리할 준비가 되지 않았습니다.",
            retryable=True,
        )
    return health_payload(request, "ready")


class TestRequest(BaseModel):
    message: str


@app.post("/api/v1/test", response_model=EchoResponse)
def connection_test(request: Request, body: TestRequest):
    return EchoResponse(
        success=True,
        received_message=body.message,
        reply=f"AI 서버가 '{body.message}'를 정상적으로 받았습니다.",
        request_id=current_request_id(request),
    )
