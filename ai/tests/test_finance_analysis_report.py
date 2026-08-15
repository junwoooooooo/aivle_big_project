import asyncio

import pytest

from app.providers import ProviderFailure
from app.providers.schema_compatibility import strict_schema_failures
from app.tasks.finance_analysis_report import service
from app.tasks.finance_analysis_report.models import FinanceAnalysisReportResult


def _input():
    return {
        "snapshotId": "finance-1",
        "snapshotHash": "sha256:" + "a" * 64,
        "sourceMarketResearchVersionId": 101,
        "sourceBusinessModelVersionId": 201,
        "sourceTechOpsSnapshotId": "tech-1",
        "deterministicResult": {"calculation": {"scenarios": []}, "monteCarlo": {"seed": 7}},
    }


def test_report_uses_only_deterministic_result_and_preserves_strict_source(monkeypatch):
    seen = {}

    async def prompt(system, user, **kwargs):
        seen["system"] = system
        seen["user"] = user
        seen["task_type"] = kwargs["task_type"]
        return {"headline": "가정 기반 결과", "findings": ["계산 결과 확인"],
            "cautions": ["가정 변동 주의"], "recommendedActions": ["가격 검증"],
            "disclaimer": "추정치입니다.", "source": "AI_GENERATED_REPORT",
            "providerStatus": "SUCCEEDED", "safeFailureReason": None}

    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    result = asyncio.run(service.execute_finance_analysis_report(_input()))
    assert seen["task_type"] == "FINANCE_ANALYSIS_REPORT"
    assert "deterministic" in seen["system"]
    assert '"seed": 7' in seen["user"]
    assert result["source"] == "AI_GENERATED_REPORT"
    assert result["providerStatus"] == "SUCCEEDED"


def test_report_input_does_not_require_techops(monkeypatch):
    value = _input()
    value.pop("sourceTechOpsSnapshotId")

    async def prompt(*_args, **_kwargs):
        return {"headline": "가정 기반 결과", "findings": ["계산 결과 확인"],
            "cautions": ["가정 변동 주의"], "recommendedActions": ["가격 검증"],
            "disclaimer": "추정치입니다.", "source": "AI_GENERATED_REPORT",
            "providerStatus": "SUCCEEDED", "safeFailureReason": None}

    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    result = asyncio.run(service.execute_finance_analysis_report(value))
    assert result["providerStatus"] == "SUCCEEDED"


def test_finance_provider_schema_is_closed_fully_required_and_literal_bound() -> None:
    schema = FinanceAnalysisReportResult.model_json_schema()
    assert strict_schema_failures(schema) == []
    assert set(schema["required"]) == set(schema["properties"])
    assert schema["properties"]["source"]["const"] == "AI_GENERATED_REPORT"
    assert schema["properties"]["providerStatus"]["const"] == "SUCCEEDED"
    assert schema["properties"]["safeFailureReason"]["type"] == "null"
    assert "default" not in schema["properties"]["safeFailureReason"]


def test_report_rejects_malformed_provider_result(monkeypatch):
    async def malformed(*_args, **_kwargs):
        return {"headline": "필드가 부족합니다"}
    monkeypatch.setattr(service, "execute_structured_prompt", malformed)
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(service.execute_finance_analysis_report(_input()))
    assert raised.value.code == "RESULT_SCHEMA_INVALID"
    assert raised.value.reason == "AI_RESULT_INVALID"
