import asyncio
import json
from pathlib import Path

import pytest

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway
from app.concept_portfolio_v2.legal_requirement_nature import (
    classify_legal_requirement_nature, normalize_legal_requirement_route,
)
from app.concept_portfolio_v2.models import (
    BusinessRoleSemanticItem, LegalFactDependencySemanticItem,
    LegalRequirementNature, LegalReview, LegalRoute,
)
from app.concept_portfolio_v2.providers import MockPortfolioProvider
from app.providers import ProviderFailure

from test_generic_role_semantic_recovery_round2 import prepared_candidates


def run(value):
    return asyncio.run(value)


class SafeExtraDependencyProvider(MockPortfolioProvider):
    async def classify_legal_fact_dependencies(self, items):
        requested = await super().classify_legal_fact_dependencies(items)
        return [
            *requested,
            LegalFactDependencySemanticItem(
                candidateId="C4", dependencyType="PERSONAL_DATA",
                decision="NOT_REQUIRED", safeReason="요청 외 정상 key"),
            LegalFactDependencySemanticItem(
                candidateId="C4", dependencyType="PHYSICAL_ACTIVITY",
                decision="NOT_REQUIRED", safeReason="요청 외 정상 key"),
        ]


class SafeExtraRoleProvider(MockPortfolioProvider):
    async def classify_business_roles(self, items):
        requested = await super().classify_business_roles(items)
        return [
            *requested,
            BusinessRoleSemanticItem(
                candidateId="C4", field="providerRole", decision="MATCH",
                safeReason="요청 외 정상 key"),
        ]


class UnsupportedDependencyProvider(MockPortfolioProvider):
    async def classify_legal_fact_dependencies(self, items):
        requested = await super().classify_legal_fact_dependencies(items)
        return [*requested, {
            "candidateId": "C4", "dependencyType": "UNKNOWN_DEPENDENCY",
            "decision": "NOT_REQUIRED", "safeReason": "지원하지 않는 key",
        }]


class ExtraHypothesisProvider(MockPortfolioProvider):
    async def classify_hypotheses(self, items):
        requested = await super().classify_hypotheses(items)
        return [*requested, requested[0].model_copy(update={"hypothesisType": "PRICE"})]


class ExtraArchitectureProvider(MockPortfolioProvider):
    async def classify_architectures(self, items):
        requested = await super().classify_architectures(items)
        return [*requested, requested[0].model_copy(update={"entityId": "C99"})]


def test_same_candidate_supported_dependency_extras_are_ignored_with_diagnostic():
    gateway = ProviderGateway(provider=SafeExtraDependencyProvider())
    requested = [{
        "candidateId": "C4", "dependencyType": "BUSINESS_PARTNER",
        "candidate": {}, "descriptor": {},
    }]
    result = run(gateway.classify_legal_fact_dependencies(requested))
    assert [(item.candidateId, item.dependencyType) for item in result] == [
        ("C4", "BUSINESS_PARTNER")]
    assert gateway.usage.batchDiagnostics == [{
        "action": "BATCH_EXTRA_RESULTS_IGNORED",
        "contract": "LEGAL_FACT_DEPENDENCY",
        "ignoredKeys": [["C4", "PERSONAL_DATA"], ["C4", "PHYSICAL_ACTIVITY"]],
        "safeSummary": "요청하지 않은 추가 의미판정 결과를 사용하지 않고 폐기했습니다.",
    }]


def test_same_candidate_supported_business_role_extra_is_ignored():
    gateway = ProviderGateway(provider=SafeExtraRoleProvider())
    result = run(gateway.classify_business_roles([{
        "candidateId": "C4", "field": "sellerRole", "value": "운영사가 판매",
    }]))
    assert [(item.candidateId, item.field) for item in result] == [("C4", "sellerRole")]
    assert gateway.usage.batchDiagnostics[-1]["ignoredKeys"] == [["C4", "providerRole"]]


def test_unsupported_dependency_kind_is_still_rejected():
    gateway = ProviderGateway(provider=UnsupportedDependencyProvider())
    with pytest.raises(ProviderFailure) as failure:
        run(gateway.classify_legal_fact_dependencies([{
            "candidateId": "C4", "dependencyType": "BUSINESS_PARTNER",
            "candidate": {}, "descriptor": {},
        }]))
    assert failure.value.reason == "LEGAL_FACT_DEPENDENCY_BATCH_ITEM_INVALID"


def test_hypothesis_and_architecture_extras_remain_contract_errors():
    with pytest.raises(ProviderFailure) as hypothesis_failure:
        run(ProviderGateway(provider=ExtraHypothesisProvider()).classify_hypotheses([
            {"hypothesisType": "CHANNELS", "value": "운영사 웹"},
        ]))
    assert "HYPOTHESIS_BATCH_IDENTITY_MISMATCH" in hypothesis_failure.value.reason

    _, _, prepared = prepared_candidates(count=1)
    envelope = prepared.candidates[0]
    with pytest.raises(ProviderFailure) as architecture_failure:
        run(ProviderGateway(provider=ExtraArchitectureProvider()).classify_architectures([{
            "entityId": envelope.candidateId,
            "businessText": envelope.candidate.operatingModel,
            "currentArchitecture": envelope.descriptor.architecture.model_dump(mode="json"),
        }]))
    assert "ARCHITECTURE_BATCH_IDENTITY_MISMATCH" in architecture_failure.value.reason


class FirstCandidateLegalFailureProvider(MockPortfolioProvider):
    def __init__(self):
        self.reviewed = []

    async def review_legal(self, candidate_id, candidate, seed):
        self.reviewed.append(candidate_id)
        if candidate_id == "C1":
            raise ProviderFailure(
                "RESULT_SCHEMA_INVALID", "C1_LEGAL_RESULT_CONTRACT_ERROR",
                502, False, schema_name="concept_legal_review_result_v2")
        return await super().review_legal(candidate_id, candidate, seed)


class LegalSourceUnavailableProvider(MockPortfolioProvider):
    async def review_legal(self, candidate_id, candidate, seed):
        raise ProviderFailure(
            "DEPENDENCY_UNAVAILABLE", "LEGAL_SOURCE_EVIDENCE_UNAVAILABLE",
            503, False)


class FactQuestionAsRedesignProvider(MockPortfolioProvider):
    async def review_legal(self, candidate_id, candidate, seed):
        requirement = "판매 주체 확인 필요"
        return LegalReview(
            candidateId=candidate_id, route=LegalRoute.REDESIGN_WITHIN_LINEAGE,
            productionStatus="REDESIGNABLE", sourceStatus="OFFICIAL_EVIDENCE",
            safeSummary=requirement, redesignRequirements=[requirement],
        )


def test_first_candidate_legal_failure_does_not_stop_remaining_candidates():
    provider = FirstCandidateLegalFailureProvider()
    engine, seed, prepared = prepared_candidates(provider, count=4)
    reviews = run(engine.review_legal(seed, prepared.candidates))
    assert len(reviews) == 4
    assert reviews[0].candidateId == "C1" and reviews[0].route == LegalRoute.SYSTEM_FAILURE
    assert provider.reviewed == ["C1", "C2", "C3", "C4"]
    assert all(item.route != LegalRoute.SYSTEM_FAILURE for item in reviews[1:])


def test_global_legal_source_failure_still_stops_batch():
    provider = LegalSourceUnavailableProvider()
    engine, seed, prepared = prepared_candidates(provider, count=4)
    with pytest.raises(ProviderFailure) as failure:
        run(engine.review_legal(seed, prepared.candidates))
    assert failure.value.code == "DEPENDENCY_UNAVAILABLE"


def test_engine_routes_legal_fact_question_to_candidate_scoped_input():
    provider = FactQuestionAsRedesignProvider()
    engine, seed, prepared = prepared_candidates(provider, count=1)
    review = run(engine.review_legal_candidate(seed, prepared.candidates[0]))
    assert review.route == LegalRoute.NEEDS_INPUT
    assert review.inputScope == "CANDIDATE"
    assert review.recoveryResolution == "LEGAL_FACT_REQUIRED_NOT_REDESIGN"


@pytest.mark.parametrize("requirement", [
    "구체적인 판매 방식에 대한 정보가 필요합니다.",
    "플랫폼 판매자에게 특정 자격이 필요한지 확인해야 합니다.",
    "판매 주체 확인 필요",
])
def test_fact_questions_are_needs_input_and_never_trigger_redesign(requirement):
    _, _, prepared = prepared_candidates(count=1)
    review = LegalReview(
        candidateId="C1", route=LegalRoute.REDESIGN_WITHIN_LINEAGE,
        productionStatus="REDESIGNABLE", sourceStatus="OFFICIAL_EVIDENCE",
        safeSummary=requirement, redesignRequirements=[requirement],
    )
    assessment = classify_legal_requirement_nature(review, prepared.candidates[0].candidate)
    normalized = normalize_legal_requirement_route(review, prepared.candidates[0].candidate)
    assert assessment.nature == LegalRequirementNature.FACT_REQUIRED
    assert normalized.route == LegalRoute.NEEDS_INPUT
    assert normalized.unknownFacts == [requirement]
    assert normalized.evidenceDiagnostics["factQuestion"]


def test_explicit_before_and_required_structure_allows_redesign():
    _, _, prepared = prepared_candidates(count=1)
    requirement = (
        "플랫폼 직접 판매 구조를 제거하고 자격 보유 외부 판매자가 "
        "판매 계약 주체가 되도록 변경해야 합니다.")
    review = LegalReview(
        candidateId="C1", route=LegalRoute.REDESIGN_WITHIN_LINEAGE,
        productionStatus="REDESIGNABLE", sourceStatus="OFFICIAL_EVIDENCE",
        safeSummary=requirement, redesignRequirements=[requirement],
    )
    assessment = classify_legal_requirement_nature(review, prepared.candidates[0].candidate)
    normalized = normalize_legal_requirement_route(review, prepared.candidates[0].candidate)
    assert assessment.nature == LegalRequirementNature.STRUCTURAL_CHANGE
    assert assessment.beforeSummary and assessment.requiredStructure
    assert normalized.route == LegalRoute.REDESIGN_WITHIN_LINEAGE


def test_accept_route_is_never_reclassified():
    _, _, prepared = prepared_candidates(count=1)
    review = LegalReview(
        candidateId="C1", route=LegalRoute.ACCEPT, sourceStatus="OFFICIAL_EVIDENCE",
        safeSummary="구현 가능합니다.",
    )
    assert normalize_legal_requirement_route(
        review, prepared.candidates[0].candidate) == review


def test_notebook_uses_candidate_safe_legal_batch_for_c1_and_keeps_remaining_stage():
    path = Path(__file__).resolve().parents[2] / "notebooks" / "concept_portfolio_v2_lab.ipynb"
    notebook = json.loads(path.read_text(encoding="utf-8"))
    source = "\n".join("".join(cell.get("source", [])) for cell in notebook["cells"])
    assert "legal_one = (await engine.review_legal(seed, candidates[:1]))[0]" in source
    assert "legal_remaining = await engine.review_legal(seed, candidates[1:])" in source
    assert "c1_terminal = bool(" in source
