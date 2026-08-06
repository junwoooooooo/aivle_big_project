import logging

from fastapi import HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.models.contracts import (
    AiServerErrorDetail,
    AiServerErrorResponse,
)
from app.request_context import REQUEST_ID_HEADER, current_request_id


logger = logging.getLogger(__name__)


class ApiHttpException(HTTPException):
    def __init__(
        self,
        *,
        status_code: int,
        code: str,
        message: str,
        retryable: bool = False,
    ):
        super().__init__(status_code=status_code, detail=message)
        self.code = code
        self.safe_message = message
        self.retryable = retryable


def error_response(
    request: Request,
    *,
    status_code: int,
    code: str,
    message: str,
    retryable: bool,
) -> JSONResponse:
    request_id = current_request_id(request)
    body = AiServerErrorResponse(
        request_id=request_id,
        error=AiServerErrorDetail(
            code=code,
            message=message,
            retryable=retryable,
        ),
    )
    return JSONResponse(
        status_code=status_code,
        content=body.model_dump(mode="json"),
        headers={REQUEST_ID_HEADER: request_id},
    )


async def api_http_exception_handler(
    request: Request,
    exception: ApiHttpException,
) -> JSONResponse:
    return error_response(
        request,
        status_code=exception.status_code,
        code=exception.code,
        message=exception.safe_message,
        retryable=exception.retryable,
    )


async def validation_exception_handler(
    request: Request,
    exception: RequestValidationError,
) -> JSONResponse:
    unknown_task_type = (
        request.url.path == "/internal/v1/tasks"
        and any(
            error.get("loc", ())[-1:] == ("task_type",)
            for error in exception.errors()
        )
    )
    return error_response(
        request,
        status_code=422,
        code=(
            "UNKNOWN_TASK_TYPE"
            if unknown_task_type
            else "INVALID_REQUEST"
        ),
        message=(
            "지원하지 않는 AI task type입니다."
            if unknown_task_type
            else "요청 값의 형식이 올바르지 않습니다."
        ),
        retryable=False,
    )


async def http_exception_handler(
    request: Request,
    exception: HTTPException,
) -> JSONResponse:
    message = (
        exception.detail
        if isinstance(exception.detail, str)
        else "요청을 처리할 수 없습니다."
    )
    return error_response(
        request,
        status_code=exception.status_code,
        code="INVALID_REQUEST",
        message=message,
        retryable=False,
    )


async def internal_exception_handler(
    request: Request,
    exception: Exception,
) -> JSONResponse:
    request_id = current_request_id(request)
    logger.exception(
        "Unhandled AI server error request_id=%s",
        request_id,
    )
    return error_response(
        request,
        status_code=500,
        code="AI_SERVER_INTERNAL_ERROR",
        message="AI 서버에서 요청을 처리하지 못했습니다.",
        retryable=True,
    )
