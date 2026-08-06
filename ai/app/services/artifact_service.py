import hashlib
import json
import os
from urllib.parse import urlsplit

import httpx
from fastapi import status

from app.api.errors import ApiHttpException
from app.models.tasks import (
    AiTaskArtifactInput,
    AiTaskArtifactMetadata,
    AiTaskOutputTarget,
)


DEFAULT_MAX_BYTES = 1024 * 1024
CHUNK_SIZE = 8192


def execute_artifact_smoke(
    source: AiTaskArtifactInput,
    target: AiTaskOutputTarget,
) -> tuple[dict[str, object], AiTaskArtifactMetadata]:
    _validate_contract(source, target)
    content = download_artifact(source)
    try:
        source_json = json.loads(content.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exception:
        raise ApiHttpException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            code="ARTIFACT_CONTENT_INVALID",
            message="Artifact JSON content is invalid.",
        ) from exception
    result_content = json.dumps(
        {"status": "processed", "source": source_json},
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")
    if len(result_content) > _max_bytes():
        raise _too_large()
    upload_artifact(target, result_content)
    checksum = hashlib.sha256(result_content).hexdigest()
    metadata = AiTaskArtifactMetadata(
        role="RESULT",
        object_key=target.object_key,
        content_type=target.content_type,
        size=len(result_content),
        checksum=f"sha256:{checksum}",
    )
    return (
        {
            "ok": True,
            "message": "SYSTEM_ARTIFACT_SMOKE_OK",
            "artifact": metadata.model_dump(mode="json"),
        },
        metadata,
    )


def _validate_contract(
    source: AiTaskArtifactInput,
    target: AiTaskOutputTarget,
) -> None:
    if (
        source.content_type != "application/json"
        or target.content_type != "application/json"
    ):
        raise ApiHttpException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            code="ARTIFACT_CONTENT_TYPE_MISMATCH",
            message="Artifact content type is not allowed.",
        )
    if source.size > _max_bytes():
        raise _too_large()
    _validate_url(source.download_url)
    _validate_url(target.upload_url)


def _validate_url(url: str) -> None:
    parsed = urlsplit(url)
    allowed_origins = {
        value.strip().lower()
        for value in os.getenv(
            "AI_ARTIFACT_ALLOWED_ORIGINS",
            (
                "http://127.0.0.1:9000,"
                "http://localhost:9000,"
                "http://minio:9000"
            ),
        ).split(",")
        if value.strip()
    }
    default_port = 443 if parsed.scheme == "https" else 80
    origin = (
        f"{parsed.scheme}://{parsed.hostname.lower()}:"
        f"{parsed.port or default_port}"
    ) if parsed.hostname else ""
    if (
        parsed.scheme not in {"http", "https"}
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or origin not in allowed_origins
    ):
        raise ApiHttpException(
            status_code=status.HTTP_400_BAD_REQUEST,
            code="ARTIFACT_URL_NOT_ALLOWED",
            message="Artifact URL is not allowed.",
        )


def download_artifact(
    source: AiTaskArtifactInput,
    max_bytes: int | None = None,
) -> bytes:
    digest = hashlib.sha256()
    content = bytearray()
    try:
        with httpx.Client(
            timeout=_timeout(),
            follow_redirects=False,
        ) as client:
            with client.stream(
                "GET",
                source.download_url,
            ) as response:
                if response.is_redirect:
                    raise ApiHttpException(
                        status_code=status.HTTP_502_BAD_GATEWAY,
                        code="ARTIFACT_REDIRECT_REJECTED",
                        message="Artifact redirect is not allowed.",
                    )
                if response.status_code != 200:
                    raise _download_failed()
                if not _same_content_type(
                    source.content_type,
                    response.headers.get("content-type"),
                ):
                    raise ApiHttpException(
                        status_code=422,
                        code="ARTIFACT_CONTENT_TYPE_MISMATCH",
                        message="Artifact content type does not match.",
                    )
                for chunk in response.iter_bytes(CHUNK_SIZE):
                    content.extend(chunk)
                    digest.update(chunk)
                    if len(content) > (max_bytes or _max_bytes()):
                        raise _too_large()
    except ApiHttpException:
        raise
    except httpx.TimeoutException as exception:
        raise ApiHttpException(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            code="ARTIFACT_DOWNLOAD_TIMEOUT",
            message="Artifact download timed out.",
            retryable=True,
        ) from exception
    except httpx.HTTPError as exception:
        raise _download_failed() from exception

    if len(content) != source.size:
        raise ApiHttpException(
            status_code=422,
            code="ARTIFACT_SIZE_MISMATCH",
            message="Artifact size does not match.",
        )
    if f"sha256:{digest.hexdigest()}" != source.checksum:
        raise ApiHttpException(
            status_code=422,
            code="ARTIFACT_CHECKSUM_MISMATCH",
            message="Artifact checksum does not match.",
        )
    return bytes(content)


def upload_artifact(
    target: AiTaskOutputTarget,
    content: bytes,
) -> None:
    try:
        with httpx.Client(
            timeout=_timeout(),
            follow_redirects=False,
        ) as client:
            response = client.put(
                target.upload_url,
                content=content,
                headers={"Content-Type": target.content_type},
            )
            if response.is_redirect:
                raise ApiHttpException(
                    status_code=status.HTTP_502_BAD_GATEWAY,
                    code="ARTIFACT_REDIRECT_REJECTED",
                    message="Artifact redirect is not allowed.",
                )
            if response.status_code < 200 or response.status_code >= 300:
                raise _upload_failed()
    except ApiHttpException:
        raise
    except httpx.TimeoutException as exception:
        raise ApiHttpException(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            code="ARTIFACT_UPLOAD_TIMEOUT",
            message="Artifact upload timed out.",
            retryable=True,
        ) from exception
    except httpx.HTTPError as exception:
        raise _upload_failed() from exception


def _same_content_type(
    expected: str,
    actual: str | None,
) -> bool:
    return (
        actual is not None
        and actual.split(";", 1)[0].strip().lower()
        == expected.lower()
    )


def _max_bytes() -> int:
    return int(
        os.getenv(
            "AI_ARTIFACT_MAX_BYTES",
            str(DEFAULT_MAX_BYTES),
        )
    )


def _timeout() -> float:
    return float(
        os.getenv("AI_ARTIFACT_HTTP_TIMEOUT_SECONDS", "5")
    )


def _too_large() -> ApiHttpException:
    return ApiHttpException(
        status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
        code="ARTIFACT_TOO_LARGE",
        message="Artifact exceeds the configured size limit.",
    )


def _download_failed() -> ApiHttpException:
    return ApiHttpException(
        status_code=status.HTTP_502_BAD_GATEWAY,
        code="ARTIFACT_DOWNLOAD_FAILED",
        message="Artifact could not be downloaded.",
        retryable=True,
    )


def _upload_failed() -> ApiHttpException:
    return ApiHttpException(
        status_code=status.HTTP_502_BAD_GATEWAY,
        code="ARTIFACT_UPLOAD_FAILED",
        message="Artifact could not be uploaded.",
        retryable=True,
    )
