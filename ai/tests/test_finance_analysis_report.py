import asyncio

from app.tasks.finance_analysis_report import service


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
