import hashlib
import json
import os
import unicodedata

from fastapi.testclient import TestClient

from main import app, internal_json_limit_exceeded


client = TestClient(app)
TOKEN = "phase3-test-service-token"


def request_body(task_type="IDEA_INTERPRETATION"):
    text = "검증할 아이디어"
    chunk_hash = "sha256:" + hashlib.sha256(text.encode()).hexdigest()
    task_input = {
        "maxOpenQuestions": 5,
        "normalizationMode": "PRESERVE_CONSTRAINTS",
        "preserveSourceWording": True,
        "sourceReferences": [{"key": "source-1", "namespace": "INPUT", "resourceType": "SOURCE_EXTRACTION"}],
        "textContents": [{"contentKey": "source-1", "contentType": "TEXT", "language": "ko-KR",
            "totalCharacters": len(text), "contentHash": chunk_hash,
            "chunks": [{"index": 0, "text": text, "characterCount": len(text), "chunkHash": chunk_hash}]}],
    }
    body = {"contractVersion": "1.0", "taskType": task_type, "taskSchemaVersion": "1.0",
        "taskRunId": "run-1", "taskAttemptId": "attempt-1", "correlationId": "correlation-1",
        "deadlineAt": "2035-01-01T00:00:00Z", "locale": "ko-KR", "input": task_input}
    canonical = unicodedata.normalize("NFC", json.dumps({key: body[key] for key in
        ("contractVersion", "taskType", "taskSchemaVersion", "locale", "input")},
        ensure_ascii=False, sort_keys=True, separators=(",", ":")))
    body["canonicalInputHash"] = "sha256:" + hashlib.sha256(canonical.encode()).hexdigest()
    return body


def headers():
    return {"Authorization": f"Bearer {TOKEN}", "X-Correlation-Id": "correlation-1"}


def refresh_hash(body):
    canonical = unicodedata.normalize("NFC", json.dumps({key: body[key] for key in
        ("contractVersion", "taskType", "taskSchemaVersion", "locale", "input")},
        ensure_ascii=False, sort_keys=True, separators=(",", ":")))
    body["canonicalInputHash"] = "sha256:" + hashlib.sha256(canonical.encode()).hexdigest()
    return body


def test_idea_interpretation_returns_validated_echo(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    async def provider_result(task_type, text):
        return {"originalSourceSummary": text, "normalizedDescription": text,
            "facts": [], "assumptions": [], "constraints": [], "openQuestions": [],
            "readiness": "APPROPRIATE", "warnings": [], "evidenceNeeds": [],
            "originDraft": {"productServiceDescription": text, "problem": [text],
                "target": {"customerTypes": [], "segment": text, "situation": None, "needs": []},
                "solution": [text], "coreValue": [text], "primaryCategory": "기타",
                "targetRegion": "KR", "fixedValues": [{"field": "origin", "value": text}],
                "confirmedValues": {}, "assumptions": [], "pricingIntent": None,
                "revenueModelIntent": None, "salesChannelIntent": None, "knownUnitCost": None,
                "alternatives": [], "knownCompetitors": [], "differentiationIntent": None,
                "internalConstraints": []},
            "fieldMetadata": [], "clarificationQuestions": []}
    monkeypatch.setattr("app.api.executions.execute_journey_task", provider_result)
    body = request_body()
    response = client.post("/internal/v1/ai/executions", json=body, headers=headers())
    assert response.status_code == 200
    payload = response.json()
    assert payload["taskRunId"] == body["taskRunId"]
    assert payload["taskAttemptId"] == body["taskAttemptId"]
    assert payload["canonicalInputHash"] == body["canonicalInputHash"]
    assert payload["result"]["readiness"] == "APPROPRIATE"
    assert "storage" not in response.text.lower()


def test_internal_execution_requires_service_token(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    response = client.post("/internal/v1/ai/executions", json=request_body())
    assert response.status_code == 401
    assert response.json()["error"]["taskRunId"] is None
    assert response.json()["error"]["details"][0]["reason"] == "SERVICE_TOKEN_MISSING"


def test_internal_execution_rejects_invalid_service_token(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    response = client.post("/internal/v1/ai/executions", json=request_body(), headers={
        "Authorization": "Bearer wrong-token", "X-Correlation-Id": "correlation-1"})
    assert response.status_code == 401
    assert response.json()["error"]["details"][0]["reason"] == "SERVICE_TOKEN_INVALID"


def test_plain_text_content_type_is_rejected(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    body = request_body()
    body["input"]["textContents"][0]["contentType"] = "PLAIN_TEXT"
    response = client.post("/internal/v1/ai/executions", json=refresh_hash(body), headers=headers())
    assert response.status_code == 400
    assert response.json()["error"]["details"][0]["reason"] == "FIELD_CONSTRAINT_VIOLATION"


def test_non_korean_locale_is_rejected(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    body = request_body()
    body["locale"] = "en-US"
    response = client.post("/internal/v1/ai/executions", json=refresh_hash(body), headers=headers())
    assert response.status_code == 400
    assert response.json()["error"]["details"][0]["reason"] == "FIELD_CONSTRAINT_VIOLATION"


def test_non_korean_text_language_is_rejected(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    body = request_body()
    body["input"]["textContents"][0]["language"] = "en-US"
    response = client.post("/internal/v1/ai/executions", json=refresh_hash(body), headers=headers())
    assert response.status_code == 400
    assert response.json()["error"]["details"][0]["reason"] == "FIELD_CONSTRAINT_VIOLATION"


def test_hash_mismatch_is_not_retryable(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    body = request_body(); body["canonicalInputHash"] = "sha256:" + "0" * 64
    response = client.post("/internal/v1/ai/executions", json=body, headers=headers())
    assert response.status_code == 400
    assert response.json()["error"]["details"][0]["reason"] == "HASH_MISMATCH"


def test_legal_review_uses_provider_and_keeps_source_unverified(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    async def provider_result(task_type, text):
        return {"status": "PASS_WITH_CONDITIONS", "summary": "사전 검토",
            "issues": [], "conditions": ["전문가 확인"], "prohibitedElements": [],
            "researchNeeds": [], "sourceVerified": False, "disclaimer": "공식 법률 자문 아님"}
    monkeypatch.setattr("app.api.executions.execute_journey_task", provider_result)
    response = client.post("/internal/v1/ai/executions", json=request_body("LEGAL_REVIEW"), headers=headers())
    assert response.status_code == 200
    assert response.json()["result"]["sourceVerified"] is False


def test_legal_source_task_forwards_incremental_contract(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    async def legal_result(task_type, text, task_input):
        assert task_input["mode"] == "INCREMENTAL"
        assert task_input["rerunCategories"] == ["PRIVACY_AND_DATA"]
        assert task_input["confirmedFacts"] == [{"key": "data", "value": "email"}]
        return {"taskType": task_type, "sourceStatus": "SOURCE_COMPLETE",
            "registryVersion": "legal-registry-v1", "routes": [], "findings": [],
            "evidence": [], "requiredUserInputs": [], "sourceWarnings": []}
    monkeypatch.setattr("app.legal.pipeline.execute_legal_source_pipeline", legal_result)
    body = request_body("IDEA_LEGAL_PRECHECK")
    body["input"].update({"mode": "INCREMENTAL", "rerunCategories": ["PRIVACY_AND_DATA"],
        "confirmedFacts": [{"key": "data", "value": "email"}],
        "registryVersion": "legal-registry-v1"})
    canonical = unicodedata.normalize("NFC", json.dumps({key: body[key] for key in
        ("contractVersion", "taskType", "taskSchemaVersion", "locale", "input")},
        ensure_ascii=False, sort_keys=True, separators=(",", ":")))
    body["canonicalInputHash"] = "sha256:" + hashlib.sha256(canonical.encode()).hexdigest()
    response = client.post("/internal/v1/ai/executions", json=body, headers=headers())
    assert response.status_code == 200
    assert response.json()["result"]["taskType"] == "IDEA_LEGAL_PRECHECK"


def test_regulatory_boundary_task_uses_boundary_pipeline(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    async def boundary_result(text, task_input):
        assert task_input["confirmedBriefVersionId"] == 7
        return {"taskType": "REGULATORY_BOUNDARY_GENERATION", "sourceStatus": "COMPLETE",
            "registryVersion": "legal-registry-v1", "routes": [], "evidence": [], "rules": [],
            "questions": [], "conflicts": [], "status": "NEEDS_INPUT",
            "userActionOptions": [], "sourceWarnings": ["NO_EVIDENCE"]}
    monkeypatch.setattr("app.legal.boundary.execute_regulatory_boundary", boundary_result)
    body = request_body("REGULATORY_BOUNDARY_GENERATION")
    body["input"].update({"confirmedBriefVersionId": 7, "confirmedBriefHash": "sha256:" + "a" * 64,
        "briefFields": [], "mode": "FULL", "rerunCategories": [], "confirmedFacts": [],
        "registryVersion": "legal-registry-v1"})
    canonical = unicodedata.normalize("NFC", json.dumps({key: body[key] for key in
        ("contractVersion", "taskType", "taskSchemaVersion", "locale", "input")},
        ensure_ascii=False, sort_keys=True, separators=(",", ":")))
    body["canonicalInputHash"] = "sha256:" + hashlib.sha256(canonical.encode()).hexdigest()
    response = client.post("/internal/v1/ai/executions", json=body, headers=headers())
    assert response.status_code == 200
    assert response.json()["result"]["status"] == "NEEDS_INPUT"


def test_concept_exploration_task_uses_isolated_slot_pipeline(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    async def concept_result(task_input):
        assert task_input["confirmedBriefVersionId"] == 7
        assert task_input["regulatoryBoundaryVersionId"] == 8
        return {"slots": [{"slotIndex": index, "variationFocus": "TARGET_AND_USER_EXPERIENCE",
            "attempts": [{"attemptNumber": 1, "phase": "INITIAL", "outcome": "VALID",
                "candidate": None, "safeFailureType": None, "duplicateStatus": "UNIQUE",
                "negativeConstraint": {}}], "accepted": True} for index in range(3)],
            "acceptedSlotIndices": [0, 1, 2], "eligibleCandidateCount": 3, "exhausted": False}
    monkeypatch.setattr("app.services.concept_core.execute_concept_exploration", concept_result)
    body = request_body("CONCEPT_EXPLORATION")
    body["input"].update({"confirmedBriefVersionId": 7, "confirmedBriefHash": "sha256:" + "a" * 64,
        "regulatoryBoundaryVersionId": 8, "regulatoryBoundaryHash": "sha256:" + "b" * 64,
        "briefFields": [], "boundaryRules": [], "desiredCount": 3,
        "maxInspectedCandidates": 9, "maxReplacementRounds": 2, "negativeConstraints": []})
    canonical = unicodedata.normalize("NFC", json.dumps({key: body[key] for key in
        ("contractVersion", "taskType", "taskSchemaVersion", "locale", "input")},
        ensure_ascii=False, sort_keys=True, separators=(",", ":")))
    body["canonicalInputHash"] = "sha256:" + hashlib.sha256(canonical.encode()).hexdigest()
    response = client.post("/internal/v1/ai/executions", json=body, headers=headers())
    assert response.status_code == 200
    assert response.json()["result"]["eligibleCandidateCount"] == 3


def test_unknown_field_rejected(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    body = request_body(); body["unexpected"] = True
    response = client.post("/internal/v1/ai/executions", json=body, headers=headers())
    assert response.status_code == 400
    assert response.json()["error"]["details"][0]["reason"] == "UNKNOWN_FIELD"


def test_invalid_json_uses_internal_error_without_identifier_echo(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    response = client.post("/internal/v1/ai/executions", content=b'{"taskRunId":"untrusted",',
        headers={**headers(), "Content-Type": "application/json"})
    assert response.status_code == 400
    error = response.json()["error"]
    assert error["details"][0]["reason"] == "JSON_PARSE_FAILED"
    assert error["taskRunId"] is None and error["taskAttemptId"] is None


def test_duplicate_json_key_is_rejected_before_model_validation(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    response = client.post("/internal/v1/ai/executions", content=b'{"contractVersion":"1.0","contractVersion":"1.0"}',
        headers={**headers(), "Content-Type": "application/json"})
    assert response.status_code == 400
    assert response.json()["error"]["details"][0]["reason"] == "JSON_PARSE_FAILED"


def test_deadline_requires_rfc3339_utc_z(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    body = request_body(); body["deadlineAt"] = "2035-01-01T09:00:00+09:00"
    response = client.post("/internal/v1/ai/executions", json=body, headers=headers())
    assert response.status_code == 504
    assert response.json()["error"]["details"][0]["reason"] == "REQUEST_DEADLINE_EXCEEDED"
    assert response.json()["error"]["retryable"] is True


def test_raw_request_over_two_mib_is_rejected_before_identity_echo(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    raw = b'{"padding":"' + (b"x" * (2 * 1024 * 1024)) + b'"}'
    response = client.post("/internal/v1/ai/executions", content=raw,
        headers={**headers(), "Content-Type": "application/json"})
    assert response.status_code == 413
    error = response.json()["error"]
    assert error["details"][0]["reason"] == "REQUEST_BYTES_EXCEEDED"
    assert error["taskRunId"] is None and error["taskAttemptId"] is None


def test_raw_response_limit_uses_encoded_bytes():
    assert internal_json_limit_exceeded(b"x" * (2 * 1024 * 1024)) is False
    assert internal_json_limit_exceeded(b"x" * (2 * 1024 * 1024 + 1)) is True
    assert internal_json_limit_exceeded("가".encode("utf-8") * 699051) is True
