import hashlib
from pathlib import Path

from fastapi import status

from app.api.errors import ApiHttpException
from app.models.marketing import (
    AdvertisingMood,
    BannerFormat,
    MarketingBannerRequest,
)
from app.models.tasks import (
    AiTaskArtifactInput,
    AiTaskArtifactMetadata,
    AiTaskOutputTarget,
    AiTaskRequest,
)
from app.services.artifact_service import (
    download_artifact,
    upload_artifact,
)
from app.services.prompt_service import build_banner_prompt
from app.utils.image_validator import (
    ALLOWED_IMAGE_TYPES,
    MAX_IMAGE_SIZE,
    validate_image_bytes,
)


def execute_marketing_banner(
    task: AiTaskRequest,
) -> tuple[dict[str, object], AiTaskArtifactMetadata]:
    if len(task.artifacts) != 1 or len(task.output_targets) != 1:
        raise ApiHttpException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            code="INVALID_ARTIFACT_CONTRACT",
            message="Exactly one source and result target are required.",
        )
    source = task.artifacts[0]
    target = task.output_targets[0]
    _validate_artifacts(source, target)
    request = _request(task)
    image = validate_image_bytes(
        source.object_key,
        source.content_type,
        download_artifact(source, MAX_IMAGE_SIZE),
    )
    upload_artifact(target, image)
    metadata = AiTaskArtifactMetadata(
        role="RESULT",
        object_key=target.object_key,
        content_type=target.content_type,
        size=len(image),
        checksum=f"sha256:{hashlib.sha256(image).hexdigest()}",
    )
    return (
        {
            "normalized_input": request.model_dump(mode="json"),
            "prompt_preview": build_banner_prompt(request),
            "provider": {"name": "mock-copy", "mock": True},
            "generated_artifact": metadata.model_dump(mode="json"),
        },
        metadata,
    )


def _request(task: AiTaskRequest) -> MarketingBannerRequest:
    try:
        mood = AdvertisingMood[str(task.input["mood"])]
        banner_format = BannerFormat[str(task.input["banner_format"])]
        return MarketingBannerRequest(
            promotion_name=task.input["promotion_name"],
            main_banner=task.input["main_banner"],
            supporting_copy=task.input["supporting_copy"],
            mood=mood,
            banner_format=banner_format,
            emphasis_keywords=task.input.get("emphasis_keywords", []),
        )
    except (KeyError, TypeError, ValueError) as exception:
        raise ApiHttpException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            code="INVALID_REQUEST",
            message="Marketing task input is invalid.",
        ) from exception


def _validate_artifacts(
    source: AiTaskArtifactInput,
    target: AiTaskOutputTarget,
) -> None:
    extension = Path(source.object_key).suffix.lower()
    allowed = ALLOWED_IMAGE_TYPES.get(extension)
    if (
        allowed is None
        or source.content_type not in allowed
        or target.content_type != source.content_type
        or Path(target.object_key).suffix.lower() != extension
    ):
        raise ApiHttpException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            code="UNSUPPORTED_IMAGE_TYPE",
            message="Marketing artifact type is not supported.",
        )
    if source.size > MAX_IMAGE_SIZE:
        raise ApiHttpException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            code="IMAGE_TOO_LARGE",
            message="Image exceeds the 10MB limit.",
        )
