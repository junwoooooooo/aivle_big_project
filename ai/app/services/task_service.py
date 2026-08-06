from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

from app.models.tasks import AiTaskRequest, AiTaskType
from app.models.tasks import AiTaskArtifactMetadata
from app.services.artifact_service import execute_artifact_smoke
from app.services.marketing_task_service import execute_marketing_banner


TaskHandler = Callable[
    [AiTaskRequest],
    tuple[dict[str, Any], list[AiTaskArtifactMetadata]],
]


@dataclass(frozen=True)
class TaskHandlerRegistration:
    name: str
    version: str
    execute: TaskHandler


@dataclass(frozen=True)
class TaskExecutionResult:
    result: dict[str, Any]
    handler: str
    handler_version: str
    artifacts: list[AiTaskArtifactMetadata]


def system_smoke_handler(
    task: AiTaskRequest,
) -> tuple[dict[str, Any], list[AiTaskArtifactMetadata]]:
    return (
        {
            "ok": True,
            "message": "SYSTEM_SMOKE_OK",
            "received_input": task.input,
        },
        [],
    )


def system_artifact_smoke_handler(
    task: AiTaskRequest,
) -> tuple[dict[str, Any], list[AiTaskArtifactMetadata]]:
    if len(task.artifacts) != 1 or len(task.output_targets) != 1:
        from fastapi import status

        from app.api.errors import ApiHttpException

        raise ApiHttpException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            code="INVALID_ARTIFACT_CONTRACT",
            message="Exactly one source and result target are required.",
        )
    result, artifact = execute_artifact_smoke(
        task.artifacts[0],
        task.output_targets[0],
    )
    return result, [artifact]


def marketing_banner_handler(
    task: AiTaskRequest,
) -> tuple[dict[str, Any], list[AiTaskArtifactMetadata]]:
    result, artifact = execute_marketing_banner(task)
    return result, [artifact]


TASK_HANDLERS: dict[AiTaskType, TaskHandlerRegistration] = {
    AiTaskType.SYSTEM_SMOKE_TEST: TaskHandlerRegistration(
        name="system-smoke",
        version="1.0",
        execute=system_smoke_handler,
    ),
    AiTaskType.SYSTEM_ARTIFACT_SMOKE_TEST: TaskHandlerRegistration(
        name="system-artifact-smoke",
        version="1.0",
        execute=system_artifact_smoke_handler,
    ),
    AiTaskType.MARKETING_BANNER_GENERATION: TaskHandlerRegistration(
        name="marketing-banner",
        version="1.0",
        execute=marketing_banner_handler,
    ),
}


def execute_task(task: AiTaskRequest) -> TaskExecutionResult:
    registration = TASK_HANDLERS[task.task_type]
    result, artifacts = registration.execute(task)
    return TaskExecutionResult(
        result=result,
        handler=registration.name,
        handler_version=registration.version,
        artifacts=artifacts,
    )
