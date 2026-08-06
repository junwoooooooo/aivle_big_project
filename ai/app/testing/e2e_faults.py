import asyncio
import os

from fastapi.responses import Response

from app.models.tasks import AiTaskArtifactMetadata


def active_fault() -> str | None:
    if (
        os.getenv("APP_ENVIRONMENT", "").strip().upper() != "E2E"
        or os.getenv(
            "AI_E2E_FAULTS_ENABLED",
            "",
        ).strip().lower() != "true"
    ):
        return None
    mode = os.getenv("AI_E2E_FAULT_MODE", "").strip().lower()
    return mode if mode in {
        "malformed_response",
        "checksum_mismatch",
        "timeout",
    } else None


async def before_task() -> Response | None:
    mode = active_fault()
    if mode == "malformed_response":
        return Response(
            content='{"request_id":',
            status_code=200,
            media_type="application/json",
        )
    if mode == "timeout":
        delay = float(
            os.getenv("AI_E2E_FAULT_DELAY_SECONDS", "5")
        )
        await asyncio.sleep(min(max(delay, 0.1), 30.0))
    return None


def mutate_artifacts(
    artifacts: list[AiTaskArtifactMetadata],
) -> list[AiTaskArtifactMetadata]:
    if active_fault() != "checksum_mismatch" or not artifacts:
        return artifacts
    return [
        artifacts[0].model_copy(
            update={"checksum": "sha256:" + ("0" * 64)}
        ),
        *artifacts[1:],
    ]
