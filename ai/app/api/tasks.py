from fastapi import APIRouter, Request, status

from app.api.errors import ApiHttpException
from app.models.tasks import (
    AiTaskExecution,
    AiTaskRequest,
    AiTaskResponse,
)
from app.request_context import REQUEST_ID_HEADER
from app.services.task_service import execute_task
from app.testing.e2e_faults import before_task, mutate_artifacts


SUPPORTED_SCHEMA_VERSION = "1.0"

router = APIRouter(
    prefix="/internal/v1",
    tags=["Internal AI Tasks"],
)


@router.post(
    "/tasks",
    response_model=AiTaskResponse,
)
async def run_task(
    request: Request,
    task: AiTaskRequest,
):
    header_request_id = request.headers.get(REQUEST_ID_HEADER)
    if (
        header_request_id is not None
        and header_request_id.strip()
        and header_request_id.strip() != task.request_id
    ):
        raise ApiHttpException(
            status_code=status.HTTP_400_BAD_REQUEST,
            code="INVALID_REQUEST",
            message=(
                "X-Request-Id와 task request_id가 "
                "일치하지 않습니다."
            ),
        )
    request.state.request_id = task.request_id

    if task.schema_version != SUPPORTED_SCHEMA_VERSION:
        raise ApiHttpException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            code="UNSUPPORTED_SCHEMA_VERSION",
            message="지원하지 않는 AI task schema version입니다.",
        )

    fault_response = await before_task()
    if fault_response is not None:
        return fault_response

    execution_result = execute_task(task)
    return AiTaskResponse(
        request_id=task.request_id,
        task_id=task.task_id,
        task_type=task.task_type,
        status="SUCCEEDED",
        schema_version=SUPPORTED_SCHEMA_VERSION,
        result=execution_result.result,
        warnings=[],
        execution=AiTaskExecution(
            handler=execution_result.handler,
            handler_version=execution_result.handler_version,
        ),
        error=None,
        artifacts=mutate_artifacts(execution_result.artifacts),
    )
