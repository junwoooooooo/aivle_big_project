import asyncio
import json
from pathlib import Path

import pytest

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway
from app.concept_portfolio_v2.fact_consistency import assess_concept_fact_consistency
from app.concept_portfolio_v2.models import (
    FailureCode, LegalFactCompletionRequirement, LegalFactDependencySemanticItem,
)
from app.concept_portfolio_v2.providers import (
    MockPortfolioProvider, legal_fact_completion_schema,
)
from app.concept_portfolio_v2.schema_preflight import inspect_strict_schema
from app.providers import ProviderFailure

from test_generic_role_semantic_recovery_round2 import prepared_candidates, scenario_payload, vague_roles


def run(value):
    return asyncio.run(value)


DEPENDENCY_ITEMS = [
    {"candidateId": "C1", "dependencyType": "PERSONAL_DATA", "candidate": {}, "descriptor": {}},
    {"candidateId": "C1", "dependencyType": "PHYSICAL_ACTIVITY", "candidate": {}, "descriptor": {}},
    {"candidateId": "C2", "dependencyType": "PERSONAL_DATA", "candidate": {}, "descriptor": {}},
]


class ReorderedBatchProvider(MockPortfolioProvider):
    async def classify_legal_fact_dependencies(self, items):
        return list(reversed(await super().classify_legal_fact_dependencies(items)))

    async def classify_business_roles(self, items):
        return list(reversed(await super().classify_business_roles(items)))

    async def classify_hypotheses(self, items):
        return list(reversed(await super().classify_hypotheses(items)))

    async def classify_architectures(self, items):
        return list(reversed(await super().classify_architectures(items)))


class DuplicateDependencyProvider(MockPortfolioProvider):
    async def classify_legal_fact_dependencies(self, items):
        result = await super().classify_legal_fact_dependencies(items)
        return [result[0], *result[:-1]]


class MissingDependencyProvider(MockPortfolioProvider):
    async def classify_legal_fact_dependencies(self, items):
        return (await super().classify_legal_fact_dependencies(items))[:-1]


class ExtraDependencyProvider(MockPortfolioProvider):
    async def classify_legal_fact_dependencies(self, items):
        result = await super().classify_legal_fact_dependencies(items)
        return [*result, LegalFactDependencySemanticItem(
            candidateId="C9", dependencyType="PERSONAL_DATA", decision="NOT_REQUIRED",
            safeReason="요청하지 않은 결과")]


class WrongCandidateProvider(MockPortfolioProvider):
    async def classify_legal_fact_dependencies(self, items):
        result = await super().classify_legal_fact_dependencies(items)
        result[0] = result[0].model_copy(update={"candidateId": "WRONG"})
        return result


def test_reordered_keyed_batches_are_accepted_and_canonicalized():
    gateway = ProviderGateway(provider=ReorderedBatchProvider())
    dependencies = run(gateway.classify_legal_fact_dependencies(DEPENDENCY_ITEMS))
    assert [(item.candidateId, item.dependencyType) for item in dependencies] == [
        (item["candidateId"], item["dependencyType"]) for item in DEPENDENCY_ITEMS]

    roles_input = [
        {"candidateId": "C1", "field": "sellerRole", "value": "운영사가 판매"},
        {"candidateId": "C2", "field": "providerRole", "value": "파트너가 제공"},
    ]
    roles = run(gateway.classify_business_roles(roles_input))
    assert [(item.candidateId, item.field) for item in roles] == [
        (item["candidateId"], item["field"]) for item in roles_input]

    hypotheses_input = [
        {"hypothesisType": "CHANNELS", "value": "운영사 웹"},
        {"hypothesisType": "PRICE", "value": "월 3만원"},
    ]
    hypotheses = run(gateway.classify_hypotheses(hypotheses_input))
    assert [item.hypothesisType for item in hypotheses] == ["CHANNELS", "PRICE"]

    _, _, prepared = prepared_candidates(count=2)
    architecture_input = [{
        "entityId": item.candidateId, "businessText": item.candidate.operatingModel,
        "currentArchitecture": item.descriptor.architecture.model_dump(mode="json"),
    } for item in prepared.candidates]
    architectures = run(gateway.classify_architectures(architecture_input))
    assert [item.entityId for item in architectures] == [item["entityId"] for item in architecture_input]


@pytest.mark.parametrize("provider, marker", [
    (DuplicateDependencyProvider(), "duplicate="),
    (MissingDependencyProvider(), "missing="),
    (ExtraDependencyProvider(), "extra="),
    (WrongCandidateProvider(), "missing="),
])
def test_dependency_batch_identity_violations_are_rejected(provider, marker):
    with pytest.raises(ProviderFailure) as failure:
        run(ProviderGateway(provider=provider).classify_legal_fact_dependencies(DEPENDENCY_ITEMS))
    assert "LEGAL_FACT_DEPENDENCY_BATCH_IDENTITY_MISMATCH" in failure.value.reason
    assert marker in failure.value.reason


def test_dynamic_completion_schema_contains_only_requested_fields():
    requirements = [
        LegalFactCompletionRequirement(
            field="sellerRole", reasonType="ROLE_MISMATCH", dependencyType=None,
            instruction="판매 책임을 명시"),
        LegalFactCompletionRequirement(
            field="personalDataUsage", reasonType="MISSING_REQUIRED_FACT",
            dependencyType="PERSONAL_DATA", instruction="개인정보 항목과 목적을 명시"),
    ]
    schema = legal_fact_completion_schema(requirements)
    assert set(schema["properties"]) == {"sellerRole", "personalDataUsage"}
    assert schema["required"] == ["sellerRole", "personalDataUsage"]
    assert schema["additionalProperties"] is False
    assert inspect_strict_schema(schema, "dynamic_completion_test").status == "PASS"
    assert not ({"conceptName", "targetUsers", "solutionMechanism", "price", "revenueModel"}
                & set(schema["properties"]))


def test_system_merge_preserves_candidate_identity_and_locks():
    provider = MockPortfolioProvider()
    engine, seed, prepared = prepared_candidates(provider, count=1)
    parent = vague_roles(prepared.candidates[0])
    identity_fields = (
        "conceptName", "problemScenario", "targetUsers", "coreValue", "solutionMechanism",
        "featureSet", "differentiators", "price", "revenueModel",
    )
    result = run(engine.prepare_legal_candidates(seed, [parent]))
    assert result.candidates
    child = result.candidates[0]
    assert all(getattr(child.candidate, field) == getattr(parent.candidate, field)
               for field in identity_fields)
    for field in ("targetRegion", "channels"):
        locked = seed.by_key().get(field)
        if locked and locked.decisionState == "LOCKED" and locked.value.strip():
            assert getattr(child.candidate, field) == locked.value


def test_digital_service_with_false_physical_fact_is_invalid_and_repaired_once():
    engine, seed, prepared = prepared_candidates(scenario_id="AI_INTERVIEW_COACH", count=1)
    envelope = prepared.candidates[0]
    architecture = envelope.descriptor.architecture.model_copy(update={
        "deliveryModel": "DIGITAL", "physicalDependency": "NONE"})
    descriptor = envelope.descriptor.model_copy(update={"architecture": architecture})
    candidate = envelope.candidate.model_copy(update={
        "solutionMechanism": envelope.candidate.solutionMechanism + " 온라인 AI 분석과 디지털 결과만 제공",
        "channels": "운영사 웹", "physicalActivities": ["배송", "현장 방문", "설치"],
    })
    invalid = envelope.model_copy(update={"candidate": candidate, "descriptor": descriptor})
    report = assess_concept_fact_consistency(candidate, descriptor, envelope.candidateId)
    assert report.status == "INVALID_FACT"
    assert {item.field for item in report.issues} == {"physicalActivities"}
    prepared_legal = run(engine.prepare_legal_candidates(seed, [invalid]))
    assert prepared_legal.consistencyRepairAttempted == 1
    assert prepared_legal.consistencyRepairAccepted == 1
    assert prepared_legal.candidates
    assert all("배송" not in item and "설치" not in item
               for item in prepared_legal.candidates[0].candidate.physicalActivities)


def test_equipment_physical_facts_are_consistent_and_partner_text_is_not_intermediary():
    _, _, office = prepared_candidates(scenario_id="OFFICE_EQUIPMENT_SUBSCRIPTION", count=1)
    envelope = office.candidates[0]
    physical = envelope.candidate.model_copy(update={
        "solutionMechanism": envelope.candidate.solutionMechanism + " 장비 배송·설치·회수",
        "physicalActivities": ["장비 배송", "설치", "계약 종료 후 회수"],
    })
    assert assess_concept_fact_consistency(
        physical, envelope.descriptor, envelope.candidateId).status != "INVALID_FACT"

    false_intermediary = envelope.candidate.model_copy(update={"intermediaryRole": "CRM 업체와 협력"})
    report = assess_concept_fact_consistency(
        false_intermediary, envelope.descriptor, envelope.candidateId)
    assert report.status == "INVALID_FACT"
    assert any(item.field == "intermediaryRole" for item in report.issues)


class UnchangedConsistencyRepairProvider(MockPortfolioProvider):
    async def complete_legal_facts(self, seed, plan, candidate, requirements, candidate_index):
        if any(item.reasonType == "FACT_CONSISTENCY_REPAIR" for item in requirements):
            return {item.field: getattr(candidate, item.field) for item in requirements}
        return await super().complete_legal_facts(seed, plan, candidate, requirements, candidate_index)


def test_one_invalid_candidate_does_not_stop_other_candidate():
    provider = UnchangedConsistencyRepairProvider()
    engine, seed, prepared = prepared_candidates(provider, "AI_INTERVIEW_COACH", 2)
    first = prepared.candidates[0]
    architecture = first.descriptor.architecture.model_copy(update={
        "deliveryModel": "DIGITAL", "physicalDependency": "NONE"})
    invalid = first.model_copy(update={
        "descriptor": first.descriptor.model_copy(update={"architecture": architecture}),
        "candidate": first.candidate.model_copy(update={
            "solutionMechanism": first.candidate.solutionMechanism + " 온라인 AI 분석만 제공",
            "physicalActivities": ["배송", "현장 방문", "설치"],
        }),
    })
    result = run(engine.prepare_legal_candidates(seed, [invalid, prepared.candidates[1]]))
    assert result.consistencyRepairExhausted == 1
    assert any(item["reasonCode"] == FailureCode.CONCEPT_FACT_CONSISTENCY_REPAIR_FAILED.value
               for item in result.excludedCandidates)
    assert any(item.lineageId == prepared.candidates[1].lineageId for item in result.candidates)


@pytest.mark.parametrize("scenario_id", [
    "B2B_AI_SALES_ASSISTANT", "WEEKEND_TRIP_PLANNER", "FOOD_PHYSICAL_COMMERCE",
    "OFFICE_EQUIPMENT_SUBSCRIPTION", "CAMPUS_SECONDHAND",
])
def test_five_cutover_scenarios_mock_run_full_and_handoff(scenario_id):
    result = run(ConceptPortfolioEngine().run_full(
        scenario_payload(scenario_id), max_concepts=2, auto_confirm_hypotheses=True))
    assert result.runStatus.value in {"READY_FULL", "READY_LIMITED"}
    assert result.producedConceptCount >= 1
    assert result.handoff and result.handoff.contractStatus == "CONTRACT_PASS"
    assert all(item.finalResolution in {
        "ACCEPTED", "NEEDS_INPUT", "EXCLUDED_LEGAL", "SYSTEM_FAILURE"}
        for item in result.legalResolutions)


def test_notebook_exposes_fact_consistency_and_final_legal_resolution():
    path = Path(__file__).resolve().parents[2] / "notebooks" / "concept_portfolio_v2_lab.ipynb"
    notebook = json.loads(path.read_text(encoding="utf-8"))
    source = "\n".join("".join(cell.get("source", [])) for cell in notebook["cells"])
    assert "factConsistency" in source
    assert "consistencyRepairAttempted" in source
    assert "Consistency Repair Accepted" in source
    assert "show_legal_resolutions(live_result)" in source
