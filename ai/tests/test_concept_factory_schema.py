from typing import Any, get_type_hints

import asyncio
import pytest

from app.providers import ProviderFailure
from app.tasks.concept_candidate.models import ConceptCandidateInput, ConceptCandidateResult
from app.tasks.concept_candidate import service as candidate_service
from app.tasks.concept_legal_review.models import ConceptLegalReviewProviderResult
from app.tasks.concept_legal_review.service import execute_concept_legal_review
from app.tasks.concept_hypothesis_alternative.models import ConceptHypothesisAlternativeResult
from app.tasks.concept_redesign.models import ConceptRedesignResult
from concept_candidate_v2_fixture import valid_candidate


MODELS = [ConceptCandidateResult, ConceptLegalReviewProviderResult, ConceptRedesignResult,
          ConceptHypothesisAlternativeResult]


def _assert_closed(schema: dict, root: dict) -> None:
    if "$ref" in schema:
        target = root
        for part in schema["$ref"].removeprefix("#/").split("/"):
            target = target[part]
        _assert_closed(target, root)
        return
    if schema.get("type") == "object":
        assert schema.get("additionalProperties") is False
        assert schema.get("properties")
        for value in schema["properties"].values():
            _assert_closed(value, root)
    if schema.get("type") == "array":
        assert schema.get("items")
        _assert_closed(schema["items"], root)
    for branch in schema.get("anyOf", []):
        _assert_closed(branch, root)


def test_all_provider_result_schemas_are_closed_and_typed():
    for model in MODELS:
        schema = model.model_json_schema()
        _assert_closed(schema, schema)
        assert Any not in get_type_hints(model).values()


def test_legal_review_refuses_incomplete_fact_pattern():
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(execute_concept_legal_review({
            "legalFactPattern": {}, "factPatternHash": "sha256:" + "a" * 64,
            "externalFactContext": {},
        }))
    assert raised.value.retryable is False


def test_minimal_seed_can_start_explore_generation():
    value = ConceptCandidateInput.model_validate({
        "ideaBriefSnapshotId": "brief-1", "generationStrategy": "EXPLORE", "candidateIndex": 1,
        "originalCandidate": False, "diversityFocus": "CUSTOMER_EXPERIENCE",
        "fields": [
            {"fieldKey": "ideaOverview", "value": "동네 재고 연결", "source": "USER_INPUT", "authority": "LOCKED"},
            {"fieldKey": "problem", "value": "재고 폐기", "source": "USER_INPUT", "authority": "LOCKED"},
            {"fieldKey": "targetUsers", "value": "동네 가게", "source": "USER_INPUT", "authority": "LOCKED"},
        ],
        "acceptedConceptFingerprints": [],
    })
    assert value.generationStrategy == "EXPLORE"


def test_missing_revenue_is_a_proposed_ai_hypothesis():
    value = ConceptCandidateResult.model_validate(valid_candidate())
    semantics = {item.fieldKey: item for item in value.valueSemantics}
    assert semantics["revenueModel"].source == "AI_HYPOTHESIS"
    assert semantics["revenueModel"].decision == "PROPOSED"


def test_open_target_region_is_a_kr_ai_hypothesis(monkeypatch):
    async def prompt(*_args, **_kwargs):
        return valid_candidate()
    monkeypatch.setattr(candidate_service, "execute_structured_prompt", prompt)
    result = asyncio.run(candidate_service.execute_concept_candidate({
        "ideaBriefSnapshotId": "brief-1", "generationStrategy": "EXPLORE", "candidateIndex": 1,
        "originalCandidate": False, "diversityFocus": "CUSTOMER_EXPERIENCE",
        "fields": [
            {"fieldKey": "ideaOverview", "value": "예약 자동화", "source": "USER_INPUT", "authority": "LOCKED"},
            {"fieldKey": "problem", "value": "확인 반복", "source": "USER_INPUT", "authority": "LOCKED"},
            {"fieldKey": "targetUsers", "value": "소형 매장", "source": "USER_INPUT", "authority": "LOCKED"},
        ], "acceptedConceptFingerprints": [],
    }))
    semantics = {item["fieldKey"]: item for item in result["valueSemantics"]}
    assert result["targetRegion"] == "대한민국"
    assert semantics["targetRegion"] == {
        "fieldKey": "targetRegion", "source": "AI_HYPOTHESIS", "authority": "OPEN", "decision": "PROPOSED"}


def test_locked_user_confirmed_target_region_must_keep_exact_semantics(monkeypatch):
    candidate = valid_candidate("REFINE", 1)
    for item in candidate["valueSemantics"]:
        if item["fieldKey"] == "targetRegion":
            item.update(source="USER_CONFIRMED", authority="LOCKED", decision="ACCEPTED")
    async def prompt(*_args, **_kwargs):
        return candidate
    monkeypatch.setattr(candidate_service, "execute_structured_prompt", prompt)
    result = asyncio.run(candidate_service.execute_concept_candidate({
        "ideaBriefSnapshotId": "brief-1", "generationStrategy": "REFINE", "candidateIndex": 1,
        "originalCandidate": False, "diversityFocus": "CUSTOMER_EXPERIENCE",
        "fields": [
            {"fieldKey": "ideaOverview", "value": "예약 자동화", "source": "USER_INPUT", "authority": "LOCKED"},
            {"fieldKey": "problem", "value": "확인 반복", "source": "USER_INPUT", "authority": "LOCKED"},
            {"fieldKey": "targetUsers", "value": "소형 매장", "source": "USER_INPUT", "authority": "LOCKED"},
            {"fieldKey": "targetRegion", "value": "대한민국", "source": "USER_CONFIRMED", "authority": "LOCKED"},
        ], "acceptedConceptFingerprints": [],
    }))
    semantics = {item["fieldKey"]: item for item in result["valueSemantics"]}
    assert semantics["targetRegion"]["source"] == "USER_CONFIRMED"
    assert semantics["targetRegion"]["authority"] == "LOCKED"


def test_pre_market_som_is_never_labeled_as_analysis_result():
    candidate = valid_candidate()
    for item in candidate["valueSemantics"]:
        if item["fieldKey"] == "preMarketSomHypothesis":
            item.update(source="ANALYSIS_RESULT", authority="REVIEWABLE", decision="ACCEPTED")
    with pytest.raises(Exception):
        ConceptCandidateResult.model_validate(candidate)


def test_as_is_original_is_candidate_one_only():
    candidate = valid_candidate("AS_IS", 1)
    assert ConceptCandidateResult.model_validate(candidate).originalCandidate is True
    candidate["candidateIndex"] = 2
    with pytest.raises(Exception):
        ConceptCandidateResult.model_validate(candidate)
