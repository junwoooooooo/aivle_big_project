from pathlib import Path
import hashlib

import httpx
import pytest
from fastapi.testclient import TestClient

from app.models.marketing import AdvertisingMood, BannerFormat
from app.api import marketing as marketing_api
from app.api import tasks as task_api
from app.models.tasks import AiTaskType
from app.services import banner_service
from app.services import artifact_service
from app.testing import e2e_faults
from app.utils.image_validator import MAX_IMAGE_SIZE
from main import app


client = TestClient(app)
safe_client = TestClient(app, raise_server_exceptions=False)


def banner_form(**overrides):
    values = {
        "promotion_name": " 여름 프로모션 ",
        "main_banner": " 지금 시작하세요 ",
        "supporting_copy": " 특별 혜택을 확인하세요 ",
        "mood": AdvertisingMood.TRUSTWORTHY.value,
        "banner_format": BannerFormat.LANDSCAPE.value,
        "emphasis_keywords": " 혜택, 신규, 혜택,  ",
    }
    values.update(overrides)
    return values


def upload(
    tmp_path: Path,
    *,
    test_client: TestClient = client,
    headers: dict[str, str] | None = None,
    filename: str = "product.png",
    content: bytes = b"AIdev mock image bytes",
    content_type: str = "image/png",
    **form_overrides,
):
    original_output = banner_service.OUTPUT_DIRECTORY
    banner_service.OUTPUT_DIRECTORY = tmp_path
    try:
        return test_client.post(
            "/api/v1/marketing/banners/generate",
            data=banner_form(**form_overrides),
            files={"image": (filename, content, content_type)},
            headers=headers,
        )
    finally:
        banner_service.OUTPUT_DIRECTORY = original_output


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert response.json()["service"] == "ai-server"
    assert response.json()["request_id"]
    assert response.headers["X-Request-Id"] == response.json()["request_id"]


def test_live_and_ready_health(tmp_path, monkeypatch):
    monkeypatch.setattr(
        banner_service,
        "OUTPUT_DIRECTORY",
        tmp_path / "outputs",
    )

    live = client.get("/health/live")
    ready = client.get("/health/ready")

    assert live.status_code == 200
    assert live.json()["status"] == "live"
    assert ready.status_code == 200
    assert ready.json()["status"] == "ready"
    assert (tmp_path / "outputs").is_dir()


def test_request_id_is_propagated_or_generated():
    supplied = "phase2-request-id"
    propagated = client.get(
        "/health",
        headers={"X-Request-Id": supplied},
    )
    generated = client.get("/health")

    assert propagated.json()["request_id"] == supplied
    assert propagated.headers["X-Request-Id"] == supplied
    assert generated.json()["request_id"]
    assert generated.json()["request_id"] != supplied


def test_echo():
    response = client.post("/api/v1/test", json={"message": "연결 확인"})
    assert response.status_code == 200
    assert response.json()["received_message"] == "연결 확인"
    assert "연결 확인" in response.json()["reply"]


def test_valid_multipart_normalizes_keywords_and_creates_mock(tmp_path):
    response = upload(
        tmp_path,
        headers={"X-Request-Id": "marketing-request-id"},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "completed"
    assert payload["data"]["promotion_name"] == "여름 프로모션"
    assert payload["data"]["emphasis_keywords"] == ["혜택", "신규"]
    assert payload["banner"]["mock"] is True
    assert payload["request_id"] == "marketing-request-id"
    assert payload["banner"]["preview_url"].startswith(
        "http://testserver/outputs/banner_"
    )
    generated = list(tmp_path.glob("banner_*.png"))
    assert len(generated) == 1
    assert generated[0].read_bytes() == b"AIdev mock image bytes"


def test_invalid_enum_returns_422(tmp_path):
    response = upload(tmp_path, mood="존재하지 않는 분위기")
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "INVALID_REQUEST"
    assert response.json()["request_id"]


@pytest.mark.parametrize("filename", ["product.gif", "product.bmp"])
def test_unsupported_extension_returns_415(tmp_path, filename):
    response = upload(
        tmp_path,
        filename=filename,
        content_type="image/gif",
    )
    assert response.status_code == 415
    assert response.json()["error"]["code"] == "UNSUPPORTED_IMAGE_TYPE"
    assert response.json()["error"]["retryable"] is False


def test_extension_and_mime_mismatch_returns_415(tmp_path):
    response = upload(
        tmp_path,
        filename="product.png",
        content_type="image/jpeg",
    )
    assert response.status_code == 415
    assert response.json()["error"]["code"] == "UNSUPPORTED_IMAGE_TYPE"


def test_empty_file_returns_400(tmp_path):
    response = upload(tmp_path, content=b"")
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "EMPTY_IMAGE"


def test_file_over_10mb_returns_413(tmp_path):
    response = upload(
        tmp_path,
        content=b"x" * (MAX_IMAGE_SIZE + 1),
    )
    assert response.status_code == 413
    assert response.json()["error"]["code"] == "IMAGE_TOO_LARGE"


def test_response_models_are_declared_in_openapi():
    schema = client.get("/openapi.json").json()
    responses = schema["paths"][
        "/api/v1/marketing/banners/generate"
    ]["post"]["responses"]

    assert "MarketingBannerResult" in str(responses["200"])
    assert "HealthResponse" in str(
        schema["paths"]["/health"]["get"]["responses"]["200"]
    )


def test_internal_error_is_safe_and_does_not_expose_trace(
    tmp_path,
    monkeypatch,
):
    def fail_mock_banner(**kwargs):
        raise RuntimeError("sensitive provider stack detail")

    monkeypatch.setattr(
        marketing_api,
        "create_mock_banner",
        fail_mock_banner,
    )
    response = upload(
        tmp_path,
        test_client=safe_client,
        headers={"X-Request-Id": "internal-error-id"},
    )

    assert response.status_code == 500
    assert response.json() == {
        "request_id": "internal-error-id",
        "error": {
            "code": "AI_SERVER_INTERNAL_ERROR",
            "message": "AI 서버에서 요청을 처리하지 못했습니다.",
            "retryable": True,
        },
    }
    assert "sensitive provider stack detail" not in response.text


def task_payload(**overrides):
    payload = {
        "request_id": "task-request-id",
        "task_id": "101",
        "task_type": AiTaskType.SYSTEM_SMOKE_TEST.value,
        "schema_version": "1.0",
        "input": {"probe": "phase-3"},
        "context": {},
        "options": {},
    }
    payload.update(overrides)
    return payload


def test_system_smoke_task_preserves_contract_ids():
    response = client.post(
        "/internal/v1/tasks",
        json=task_payload(),
        headers={"X-Request-Id": "task-request-id"},
    )

    assert response.status_code == 200
    assert response.headers["X-Request-Id"] == "task-request-id"
    assert response.json() == {
        "request_id": "task-request-id",
        "task_id": "101",
        "task_type": "SYSTEM_SMOKE_TEST",
        "status": "SUCCEEDED",
        "schema_version": "1.0",
        "result": {
            "ok": True,
            "message": "SYSTEM_SMOKE_OK",
            "received_input": {"probe": "phase-3"},
        },
        "warnings": [],
        "execution": {
            "handler": "system-smoke",
            "handler_version": "1.0",
        },
        "error": None,
        "artifacts": [],
    }


def test_task_schema_version_is_rejected():
    response = client.post(
        "/internal/v1/tasks",
        json=task_payload(schema_version="2.0"),
        headers={"X-Request-Id": "task-request-id"},
    )

    assert response.status_code == 422
    assert (
        response.json()["error"]["code"]
        == "UNSUPPORTED_SCHEMA_VERSION"
    )


def test_unknown_task_type_is_rejected():
    response = client.post(
        "/internal/v1/tasks",
        json=task_payload(task_type="AUTONOMOUS_AGENT"),
        headers={"X-Request-Id": "task-request-id"},
    )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "UNKNOWN_TASK_TYPE"


def test_task_request_id_mismatch_is_rejected():
    response = client.post(
        "/internal/v1/tasks",
        json=task_payload(),
        headers={"X-Request-Id": "different-request-id"},
    )

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "INVALID_REQUEST"
    assert response.json()["request_id"] == "different-request-id"


def test_task_handler_error_uses_safe_envelope(monkeypatch):
    def fail_handler(task):
        raise RuntimeError("private handler stack")

    monkeypatch.setattr(
        task_api,
        "execute_task",
        fail_handler,
    )
    response = safe_client.post(
        "/internal/v1/tasks",
        json=task_payload(),
        headers={"X-Request-Id": "task-request-id"},
    )

    assert response.status_code == 500
    assert (
        response.json()["error"]["code"]
        == "AI_SERVER_INTERNAL_ERROR"
    )
    assert "private handler stack" not in response.text


def enable_e2e_fault(monkeypatch, mode):
    monkeypatch.setenv("APP_ENVIRONMENT", "E2E")
    monkeypatch.setenv("AI_E2E_FAULTS_ENABLED", "true")
    monkeypatch.setenv("AI_E2E_FAULT_MODE", mode)


def test_e2e_faults_are_disabled_outside_e2e(monkeypatch):
    monkeypatch.setenv("APP_ENVIRONMENT", "PROD")
    monkeypatch.setenv("AI_E2E_FAULTS_ENABLED", "true")
    monkeypatch.setenv("AI_E2E_FAULT_MODE", "malformed_response")

    response = client.post(
        "/internal/v1/tasks",
        json=task_payload(),
        headers={"X-Request-Id": "task-request-id"},
    )

    assert response.status_code == 200
    assert response.json()["status"] == "SUCCEEDED"


def test_e2e_malformed_response_is_explicitly_gated(monkeypatch):
    enable_e2e_fault(monkeypatch, "malformed_response")

    response = client.post(
        "/internal/v1/tasks",
        json=task_payload(),
        headers={"X-Request-Id": "task-request-id"},
    )

    assert response.status_code == 200
    assert response.text == '{"request_id":'
    assert "traceback" not in response.text.lower()


def test_e2e_timeout_delay_is_explicitly_gated(monkeypatch):
    delays = []
    enable_e2e_fault(monkeypatch, "timeout")
    monkeypatch.setenv("AI_E2E_FAULT_DELAY_SECONDS", "7")
    async def record_delay(seconds):
        delays.append(seconds)
    monkeypatch.setattr(
        e2e_faults.asyncio,
        "sleep",
        record_delay,
    )

    response = client.post(
        "/internal/v1/tasks",
        json=task_payload(),
        headers={"X-Request-Id": "task-request-id"},
    )

    assert response.status_code == 200
    assert delays == [7.0]


def artifact_task_payload(
    source: bytes = b'{"message":"artifact-smoke"}',
    **overrides,
):
    payload = task_payload(
        task_type=AiTaskType.SYSTEM_ARTIFACT_SMOKE_TEST.value,
        artifacts=[
            {
                "artifact_id": "source-1",
                "role": "SOURCE",
                "object_key": "ai-artifacts/source.json",
                "download_url": (
                    "http://127.0.0.1:9000/bucket/source"
                ),
                "content_type": "application/json",
                "size": len(source),
                "checksum": (
                    "sha256:" + hashlib.sha256(source).hexdigest()
                ),
            }
        ],
        output_targets=[
            {
                "role": "RESULT",
                "object_key": "ai-artifacts/result.json",
                "upload_url": (
                    "http://127.0.0.1:9000/bucket/result"
                ),
                "content_type": "application/json",
            }
        ],
    )
    payload.update(overrides)
    return payload


def install_artifact_transport(monkeypatch, handler):
    real_client = httpx.Client
    transport = httpx.MockTransport(handler)
    monkeypatch.setattr(
        artifact_service.httpx,
        "Client",
        lambda **kwargs: real_client(
            transport=transport,
            **kwargs,
        ),
    )


def test_artifact_smoke_downloads_and_uploads(monkeypatch):
    source = b'{"message":"artifact-smoke"}'
    uploaded = {}

    def handler(request):
        if request.method == "GET":
            return httpx.Response(
                200,
                content=source,
                headers={"Content-Type": "application/json"},
            )
        uploaded["content"] = request.content
        uploaded["content_type"] = request.headers["Content-Type"]
        return httpx.Response(200)

    install_artifact_transport(monkeypatch, handler)
    response = client.post(
        "/internal/v1/tasks",
        json=artifact_task_payload(source),
        headers={"X-Request-Id": "task-request-id"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["task_id"] == "101"
    assert body["request_id"] == "task-request-id"
    assert body["execution"] == {
        "handler": "system-artifact-smoke",
        "handler_version": "1.0",
    }
    assert body["artifacts"][0]["object_key"] == (
        "ai-artifacts/result.json"
    )
    assert body["artifacts"][0]["checksum"].startswith("sha256:")
    assert body["artifacts"][0]["size"] == len(uploaded["content"])
    assert uploaded["content_type"] == "application/json"


def test_e2e_checksum_mismatch_mutates_only_result_metadata(
    monkeypatch,
):
    source = b'{"message":"artifact-smoke"}'
    enable_e2e_fault(monkeypatch, "checksum_mismatch")

    def handler(request):
        if request.method == "GET":
            return httpx.Response(
                200,
                content=source,
                headers={"Content-Type": "application/json"},
            )
        return httpx.Response(200)

    install_artifact_transport(monkeypatch, handler)
    response = client.post(
        "/internal/v1/tasks",
        json=artifact_task_payload(source),
        headers={"X-Request-Id": "task-request-id"},
    )

    assert response.status_code == 200
    assert response.json()["artifacts"][0]["checksum"] == (
        "sha256:" + ("0" * 64)
    )


def test_artifact_checksum_mismatch_is_rejected(monkeypatch):
    source = b'{"message":"artifact-smoke"}'
    payload = artifact_task_payload(source)
    payload["artifacts"][0]["checksum"] = "sha256:" + ("0" * 64)

    install_artifact_transport(
        monkeypatch,
        lambda request: httpx.Response(
            200,
            content=source,
            headers={"Content-Type": "application/json"},
        ),
    )
    response = client.post(
        "/internal/v1/tasks",
        json=payload,
        headers={"X-Request-Id": "task-request-id"},
    )
    assert response.status_code == 422
    assert response.json()["error"]["code"] == (
        "ARTIFACT_CHECKSUM_MISMATCH"
    )


def test_artifact_content_type_mismatch_is_rejected(monkeypatch):
    source = b'{"message":"artifact-smoke"}'
    install_artifact_transport(
        monkeypatch,
        lambda request: httpx.Response(
            200,
            content=source,
            headers={"Content-Type": "text/plain"},
        ),
    )
    response = client.post(
        "/internal/v1/tasks",
        json=artifact_task_payload(source),
        headers={"X-Request-Id": "task-request-id"},
    )
    assert response.status_code == 422
    assert response.json()["error"]["code"] == (
        "ARTIFACT_CONTENT_TYPE_MISMATCH"
    )


def test_artifact_size_limit_is_enforced(monkeypatch):
    monkeypatch.setenv("AI_ARTIFACT_MAX_BYTES", "4")
    response = client.post(
        "/internal/v1/tasks",
        json=artifact_task_payload(),
        headers={"X-Request-Id": "task-request-id"},
    )
    assert response.status_code == 413
    assert response.json()["error"]["code"] == "ARTIFACT_TOO_LARGE"


def test_artifact_disallowed_host_is_rejected():
    payload = artifact_task_payload()
    payload["artifacts"][0]["download_url"] = (
        "http://metadata.internal/source"
    )
    response = client.post(
        "/internal/v1/tasks",
        json=payload,
        headers={"X-Request-Id": "task-request-id"},
    )
    assert response.status_code == 400
    assert response.json()["error"]["code"] == (
        "ARTIFACT_URL_NOT_ALLOWED"
    )


def test_artifact_redirect_is_not_followed(monkeypatch):
    install_artifact_transport(
        monkeypatch,
        lambda request: httpx.Response(
            307,
            headers={"Location": "http://127.0.0.1/other"},
        ),
    )
    response = client.post(
        "/internal/v1/tasks",
        json=artifact_task_payload(),
        headers={"X-Request-Id": "task-request-id"},
    )
    assert response.status_code == 502
    assert response.json()["error"]["code"] == (
        "ARTIFACT_REDIRECT_REJECTED"
    )


def test_artifact_download_timeout_is_safe(monkeypatch):
    def handler(request):
        raise httpx.ReadTimeout("private timeout detail")

    install_artifact_transport(monkeypatch, handler)
    response = client.post(
        "/internal/v1/tasks",
        json=artifact_task_payload(),
        headers={"X-Request-Id": "task-request-id"},
    )
    assert response.status_code == 504
    assert response.json()["error"]["code"] == (
        "ARTIFACT_DOWNLOAD_TIMEOUT"
    )
    assert "private timeout detail" not in response.text


def test_artifact_upload_failure_is_safe(monkeypatch):
    source = b'{"message":"artifact-smoke"}'

    def handler(request):
        if request.method == "GET":
            return httpx.Response(
                200,
                content=source,
                headers={"Content-Type": "application/json"},
            )
        return httpx.Response(503)

    install_artifact_transport(monkeypatch, handler)
    response = client.post(
        "/internal/v1/tasks",
        json=artifact_task_payload(source),
        headers={"X-Request-Id": "task-request-id"},
    )
    assert response.status_code == 502
    assert response.json()["error"]["code"] == (
        "ARTIFACT_UPLOAD_FAILED"
    )


def test_artifact_download_failure_is_safe(monkeypatch):
    install_artifact_transport(
        monkeypatch,
        lambda request: httpx.Response(403),
    )
    response = client.post(
        "/internal/v1/tasks",
        json=artifact_task_payload(),
        headers={"X-Request-Id": "task-request-id"},
    )
    assert response.status_code == 502
    assert response.json()["error"]["code"] == (
        "ARTIFACT_DOWNLOAD_FAILED"
    )


def marketing_task_payload(source: bytes = b"mock-image"):
    payload = task_payload(
        task_type=AiTaskType.MARKETING_BANNER_GENERATION.value,
        input={
            "promotion_name": "Summer Sale",
            "main_banner": "Save now",
            "supporting_copy": "Limited offer",
            "mood": "PROFESSIONAL",
            "banner_format": "LANDSCAPE",
            "emphasis_keywords": ["sale", " sale ", "limited"],
        },
        artifacts=[{
            "artifact_id": "marketing-source",
            "role": "SOURCE",
            "object_key": "ai-artifacts/source.png",
            "download_url": "http://127.0.0.1:9000/bucket/source",
            "content_type": "image/png",
            "size": len(source),
            "checksum": "sha256:" + hashlib.sha256(source).hexdigest(),
        }],
        output_targets=[{
            "role": "RESULT",
            "object_key": "ai-artifacts/result.png",
            "upload_url": "http://127.0.0.1:9000/bucket/result",
            "content_type": "image/png",
        }],
    )
    return payload


def test_marketing_task_reuses_prompt_and_mock_artifact_semantics(monkeypatch):
    source = b"mock-image"
    uploaded = {}

    def handler(request):
        if request.method == "GET":
            return httpx.Response(
                200,
                content=source,
                headers={"Content-Type": "image/png"},
            )
        uploaded["content"] = request.content
        return httpx.Response(200)

    install_artifact_transport(monkeypatch, handler)
    response = client.post(
        "/internal/v1/tasks",
        json=marketing_task_payload(source),
        headers={"X-Request-Id": "task-request-id"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["execution"] == {
        "handler": "marketing-banner",
        "handler_version": "1.0",
    }
    assert body["result"]["provider"] == {
        "name": "mock-copy",
        "mock": True,
    }
    assert body["result"]["normalized_input"]["emphasis_keywords"] == [
        "sale",
        "limited",
    ]
    assert "Summer Sale" in body["result"]["prompt_preview"]
    assert "Save now" in body["result"]["prompt_preview"]
    assert uploaded["content"] == source
    assert body["artifacts"][0]["checksum"] == (
        "sha256:" + hashlib.sha256(source).hexdigest()
    )


def test_marketing_task_rejects_invalid_enum():
    payload = marketing_task_payload()
    payload["input"]["mood"] = "AUTONOMOUS"
    response = client.post(
        "/internal/v1/tasks",
        json=payload,
        headers={"X-Request-Id": "task-request-id"},
    )
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "INVALID_REQUEST"


def test_marketing_task_rejects_mime_and_extension_mismatch():
    payload = marketing_task_payload()
    payload["artifacts"][0]["content_type"] = "image/jpeg"
    response = client.post(
        "/internal/v1/tasks",
        json=payload,
        headers={"X-Request-Id": "task-request-id"},
    )
    assert response.status_code == 415
    assert response.json()["error"]["code"] == "UNSUPPORTED_IMAGE_TYPE"


def test_marketing_task_rejects_oversized_source():
    payload = marketing_task_payload()
    payload["artifacts"][0]["size"] = 10 * 1024 * 1024 + 1
    response = client.post(
        "/internal/v1/tasks",
        json=payload,
        headers={"X-Request-Id": "task-request-id"},
    )
    assert response.status_code == 413
    assert response.json()["error"]["code"] == "IMAGE_TOO_LARGE"
