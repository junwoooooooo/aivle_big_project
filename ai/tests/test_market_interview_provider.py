import asyncio

from app.providers.structured import _request_body
from app.tasks.market_interview import provider


def test_respondent_workload_uses_interview_specific_model_and_diversity(monkeypatch):
    captured = {}

    async def shared(*args, **kwargs):
        captured.update(kwargs)
        return {"ok": True}

    monkeypatch.setenv("MARKET_INTERVIEW_MODEL", "interview-model")
    monkeypatch.setenv("MARKET_INTERVIEW_TEMPERATURE", "1.0")
    monkeypatch.delenv("MARKET_INTERVIEW_REASONING_EFFORT", raising=False)
    monkeypatch.setattr(provider, "execute_structured_prompt", shared)
    asyncio.run(provider.execute_market_interview_prompt(
        "system", "user", workload=provider.RESPONDENT_WORKLOAD,
        response_schema={"type": "object"}, schema_name="answer", task_type="MARKET_INTERVIEW"))
    assert captured["model_override"] == "interview-model"
    assert captured["temperature_override"] == 1.0
    assert captured["reasoning_effort_override"] is None


def test_classification_uses_low_temperature(monkeypatch):
    captured = {}

    async def shared(*args, **kwargs):
        captured.update(kwargs)
        return {"ok": True}

    monkeypatch.setenv("MARKET_INTERVIEW_MODEL", "interview-model")
    monkeypatch.delenv("MARKET_INTERVIEW_REASONING_EFFORT", raising=False)
    monkeypatch.setattr(provider, "execute_structured_prompt", shared)
    asyncio.run(provider.execute_market_interview_prompt(
        "system", "user", workload="CLASSIFICATION"))
    assert captured["temperature_override"] == 0.1


def test_reasoning_model_omits_unsupported_temperature(monkeypatch):
    captured = {}

    async def shared(*args, **kwargs):
        captured.update(kwargs)
        return {"ok": True}

    monkeypatch.setenv("MARKET_INTERVIEW_MODEL", "reasoning-model")
    monkeypatch.setenv("MARKET_INTERVIEW_REASONING_EFFORT", "medium")
    monkeypatch.setattr(provider, "execute_structured_prompt", shared)
    asyncio.run(provider.execute_market_interview_prompt(
        "system", "user", workload=provider.RESPONDENT_WORKLOAD))
    assert captured["temperature_override"] is None
    assert captured["reasoning_effort_override"] == "medium"


def test_shared_structured_provider_default_temperature_is_unchanged():
    body = _request_body("model", "system", "user", {"type": "json_object"})
    assert body["temperature"] == 0.1
    assert "reasoning_effort" not in body
