from __future__ import annotations

import asyncio

import httpx
import pytest

from app.concept_portfolio_v2.models import (
    BusinessRoleSemanticBatch, LegalFactDependencySemanticBatch, PlanDraftPool,
    SemanticArchitectureBatch, SemanticDistinctnessResult, SemanticFidelityResult,
    SemanticHypothesisBatch,
)
from app.models.legal_source import RoutingResult, ScreeningProviderResult
from app.providers import ProviderFailure
from app.providers import structured
from app.providers.schema_compatibility import strict_schema_failures
from app.tasks.concept_candidate.models import ConceptCandidateDraft
from app.tasks.concept_distinctness_judge.models import ConceptDistinctnessJudgeResult
from app.tasks.concept_hypothesis_alternative.models import ConceptHypothesisAlternativeResult
from app.tasks.concept_legal_review.models import (
    ConceptLegalReviewProviderResult, LegalQuestionClassificationBatch,
)
from app.tasks.concept_legal_review.service import _runtime_provider_schema
from app.tasks.finance_analysis_report.models import FinanceAnalysisReportResult
from app.tasks.finance_estimate.models import FinanceEstimateResult
from app.tasks.idea_brief.models import IdeaBriefProviderResult
from app.tasks.marketing_content.models import MarketingContentResult
from app.tasks.tech_ops_proposal.models import TechOpsProposalResult
from app.twin.stimulus_draft import DraftProviderResult


SCHEMAS = {
    model.__name__: model.model_json_schema() for model in (
        BusinessRoleSemanticBatch, ConceptCandidateDraft, LegalFactDependencySemanticBatch,
        PlanDraftPool, SemanticArchitectureBatch, SemanticDistinctnessResult,
        SemanticFidelityResult, SemanticHypothesisBatch, RoutingResult,
        ScreeningProviderResult, DraftProviderResult, ConceptDistinctnessJudgeResult,
        ConceptHypothesisAlternativeResult, ConceptLegalReviewProviderResult,
        LegalQuestionClassificationBatch, FinanceAnalysisReportResult,
        FinanceEstimateResult, IdeaBriefProviderResult, MarketingContentResult,
        TechOpsProposalResult,
    )
}
SCHEMAS["ConceptLegalReviewRuntime"] = _runtime_provider_schema([1, 2])


@pytest.mark.parametrize("schema_name,schema", SCHEMAS.items())
def test_all_structured_provider_schemas_are_offline_strict_compatible(schema_name, schema):
    assert strict_schema_failures(schema) == [], schema_name


def _configure(monkeypatch) -> None:
    monkeypatch.setenv("AI_PROVIDER", "openai")
    monkeypatch.setenv("AI_API_KEY", "test-only")
    monkeypatch.setenv("AI_MODEL", "test-model")
    monkeypatch.setenv("AI_BASE_URL", "https://provider.invalid/v1")


def _client(monkeypatch, response: httpx.Response, captured: list[dict]) -> None:
    class Client:
        async def __aenter__(self): return self
        async def __aexit__(self, *_args): return None
        async def post(self, _url, **kwargs):
            captured.append(kwargs["json"])
            return response
    monkeypatch.setattr(structured.httpx, "AsyncClient", lambda **_kwargs: Client())


def _valid_response() -> dict:
    return {"choices": [{"message": {"content": '{"value":"ok"}'}}]}


def test_provider_receives_accepted_strict_schema_and_valid_result(monkeypatch):
    _configure(monkeypatch)
    captured = []
    response = httpx.Response(200, json=_valid_response(), request=httpx.Request("POST", "https://provider.invalid"))
    _client(monkeypatch, response, captured)
    schema = {"type": "object", "properties": {"value": {"type": "string"}},
              "required": ["value"], "additionalProperties": False}
    result = asyncio.run(structured.execute_structured_prompt(
        "system", "user", response_schema=schema, schema_name="accepted_v1"))
    assert result == {"value": "ok"}
    assert captured[0]["response_format"]["json_schema"] == {
        "name": "accepted_v1", "strict": True, "schema": schema,
    }


def test_provider_response_format_rejection_is_safely_classified(monkeypatch):
    _configure(monkeypatch)
    captured = []
    response = httpx.Response(400, json={"error": {
        "type": "invalid_request_error", "param": "response_format",
        "message": "Invalid schema; bearer secret-must-not-leak",
    }}, request=httpx.Request("POST", "https://provider.invalid"))
    _client(monkeypatch, response, captured)
    schema = {"type": "object", "properties": {"value": {"type": "string"}},
              "required": ["value"], "additionalProperties": False}
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(structured.execute_structured_prompt(
            "system", "user", response_schema=schema, schema_name="rejected_v1"))
    assert raised.value.reason == "PROVIDER_RESPONSE_SCHEMA_REJECTED"
    assert raised.value.provider_error_param == "response_format"
    assert "secret-must-not-leak" not in (raised.value.safe_provider_message or "")


def test_malformed_provider_json_is_rejected(monkeypatch):
    _configure(monkeypatch)
    captured = []
    response = httpx.Response(200, json={"choices": [{"message": {"content": "not-json"}}]},
                              request=httpx.Request("POST", "https://provider.invalid"))
    _client(monkeypatch, response, captured)
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(structured.execute_structured_prompt("system", "user"))
    assert raised.value.reason == "PROVIDER_JSON_INVALID"


def test_incompatible_schema_fails_before_provider_http(monkeypatch):
    _configure(monkeypatch)
    called = []
    invalid = {"type": "object", "properties": {"optional": {
        "type": "null", "default": None}}, "required": [], "additionalProperties": False}
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(structured.execute_structured_prompt(
            "system", "user", response_schema=invalid, schema_name="invalid_v1"))
    assert raised.value.reason == "PROVIDER_RESPONSE_SCHEMA_REJECTED"
    assert raised.value.safe_diagnostics == {"stage": "OFFLINE_SCHEMA_PREFLIGHT"}
    assert called == []


def test_unsupported_one_of_is_rejected_offline():
    schema = {
        "type": "object",
        "properties": {
            "value": {
                "oneOf": [{"type": "string"}, {"type": "integer"}],
            },
        },
        "required": ["value"],
        "additionalProperties": False,
    }

    assert strict_schema_failures(schema) == [{
        "path": "$.properties.value.oneOf",
        "reason": "UNSUPPORTED_SCHEMA_KEYWORD",
    }]
