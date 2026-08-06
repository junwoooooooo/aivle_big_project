from enum import Enum
from typing import Any, Literal

from pydantic import BaseModel, Field

from app.models.contracts import AiServerErrorDetail


class AiTaskType(str, Enum):
    SYSTEM_SMOKE_TEST = "SYSTEM_SMOKE_TEST"
    SYSTEM_ARTIFACT_SMOKE_TEST = "SYSTEM_ARTIFACT_SMOKE_TEST"
    MARKETING_BANNER_GENERATION = "MARKETING_BANNER_GENERATION"


class AiTaskArtifactInput(BaseModel):
    artifact_id: str = Field(min_length=1, max_length=100)
    role: Literal["SOURCE"]
    object_key: str = Field(min_length=1, max_length=500)
    download_url: str = Field(min_length=1, max_length=4096)
    content_type: str = Field(min_length=1, max_length=150)
    size: int = Field(gt=0)
    checksum: str = Field(
        pattern=r"^sha256:[0-9a-f]{64}$",
    )


class AiTaskOutputTarget(BaseModel):
    role: Literal["RESULT"]
    object_key: str = Field(min_length=1, max_length=500)
    upload_url: str = Field(min_length=1, max_length=4096)
    content_type: str = Field(min_length=1, max_length=150)


class AiTaskArtifactMetadata(BaseModel):
    role: Literal["RESULT"]
    object_key: str
    content_type: str
    size: int
    checksum: str


class AiTaskRequest(BaseModel):
    request_id: str = Field(min_length=1, max_length=100)
    task_id: str = Field(min_length=1, max_length=100)
    task_type: AiTaskType
    schema_version: str = Field(min_length=1, max_length=20)
    input: dict[str, Any] = Field(default_factory=dict)
    context: dict[str, Any] = Field(default_factory=dict)
    options: dict[str, Any] = Field(default_factory=dict)
    artifacts: list[AiTaskArtifactInput] = Field(
        default_factory=list,
    )
    output_targets: list[AiTaskOutputTarget] = Field(
        default_factory=list,
    )


class AiTaskExecution(BaseModel):
    handler: str
    handler_version: str


class AiTaskResponse(BaseModel):
    request_id: str
    task_id: str
    task_type: AiTaskType
    status: Literal["SUCCEEDED"]
    schema_version: str
    result: dict[str, Any]
    warnings: list[str] = Field(default_factory=list)
    execution: AiTaskExecution
    error: AiServerErrorDetail | None = None
    artifacts: list[AiTaskArtifactMetadata] = Field(
        default_factory=list,
    )
