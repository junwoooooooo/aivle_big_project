import asyncio
import json
from pathlib import Path

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway
from app.concept_portfolio_v2.legal_fact_completeness import (
    assess_legal_fact_completeness, assess_legal_fact_dependency,
    validate_legal_fact_completion,
)
from app.concept_portfolio_v2.models import (
    FailureCode, LegalFactCompletionPatch, LegalFactCompletionRequirement,
    LegalFactDependencySemanticItem,
)
from app.concept_portfolio_v2.providers import MockPortfolioProvider
from app.concept_portfolio_v2.schema_preflight import v2_schema_preflight_report

from test_generic_role_semantic_recovery_round2 import (
    SCENARIOS, prepared_candidates, scenario_payload, vague_roles,
)


def run(value):
    return asyncio.run(value)


def _candidate_and_descriptor(scenario="AI_INTERVIEW_COACH"):
    _, _, prepared = prepared_candidates(scenario_id=scenario, count=1)
    envelope = prepared.candidates[0]
    return envelope.candidate, envelope.descriptor


def test_personal_data_dependency_distinguishes_required_absent_and_ambiguous():
    candidate, descriptor = _candidate_and_descriptor()
    required = candidate.model_copy(update={
        "personalDataUsage": [],
        "solutionMechanism": "회원 이메일과 답변 데이터를 저장해 반복 피드백을 제공",
    })
    absent = candidate.model_copy(update={
        "personalDataUsage": ["개인정보를 처리하지 않고 익명으로만 사용"],
        "solutionMechanism": "브라우저 안에서 익명으로 실행",
    })
    ambiguous = candidate.model_copy(update={
        "personalDataUsage": [], "solutionMechanism": "개인화 추천을 제공"})
    assert assess_legal_fact_dependency(required, "PERSONAL_DATA", descriptor).finalDecision == "REQUIRED"
    assert assess_legal_fact_dependency(absent, "PERSONAL_DATA", descriptor).finalDecision == "NOT_REQUIRED"
    result = assess_legal_fact_dependency(ambiguous, "PERSONAL_DATA", descriptor)
    assert result.deterministicDecision == "AMBIGUOUS"
    semantic = LegalFactDependencySemanticItem(
        candidateId="C1", dependencyType="PERSONAL_DATA", decision="NOT_REQUIRED",
        safeReason="개인 식별정보 처리가 없는 설정 기반 추천입니다.")
    assert assess_legal_fact_dependency(
        ambiguous, "PERSONAL_DATA", descriptor, semantic).finalDecision == "NOT_REQUIRED"


def test_physical_dependency_uses_actual_fulfillment_and_not_generic_digital_text():
    candidate, descriptor = _candidate_and_descriptor()
    physical = candidate.model_copy(update={
        "physicalActivities": [], "solutionMechanism": "장비를 방문 설치하고 회수하는 서비스"})
    digital = candidate.model_copy(update={
        "physicalActivities": ["물리적 이행 없음"], "solutionMechanism": "온라인 AI 분석만 제공"})
    assert assess_legal_fact_dependency(physical, "PHYSICAL_ACTIVITY", descriptor).finalDecision == "REQUIRED"
    assert assess_legal_fact_dependency(digital, "PHYSICAL_ACTIVITY", descriptor).finalDecision == "NOT_REQUIRED"


def test_p2p_participants_are_not_automatically_business_partners():
    candidate, descriptor = _candidate_and_descriptor("CAMPUS_SECONDHAND")
    architecture = descriptor.architecture.model_copy(update={
        "businessRole": "MARKETPLACE", "operatingModel": "PEER_TO_PEER",
        "partnerModel": "PEER_TO_PEER"})
    p2p_descriptor = descriptor.model_copy(update={"architecture": architecture})
    p2p = candidate.model_copy(update={
        "partnerRequirements": [], "partnerModel": "학생 판매자와 구매자가 직접 거래",
        "actorRoles": ["학생 판매자", "학생 구매자", "플랫폼 운영사"]})
    result = assess_legal_fact_dependency(p2p, "BUSINESS_PARTNER", p2p_descriptor)
    assert result.finalDecision == "NOT_REQUIRED"


def test_structured_requirements_are_additive_and_separate_from_user_inputs():
    candidate, descriptor = _candidate_and_descriptor("OFFICE_EQUIPMENT_SUBSCRIPTION")
    changed = candidate.model_copy(update={"personalDataUsage": []})
    report = assess_legal_fact_completeness(changed, descriptor=descriptor)
    fields = [item.field for item in report.structuredCompletionRequirements]
    assert len(fields) == len(set(fields))
    assert report.completionRequirements
    assert all(item.reasonType in {
        "MISSING_REQUIRED_FACT", "DEPENDENCY_UNKNOWN", "ROLE_MISMATCH",
        "TRANSACTION_INCOMPLETE", "PAYMENT_INCOMPLETE", "GENERAL_FACT_INCOMPLETE",
    } for item in report.structuredCompletionRequirements)


def test_completion_compliance_rejects_unchanged_required_field():
    candidate, descriptor = _candidate_and_descriptor()
    requirement = LegalFactCompletionRequirement(
        field="providerRole", reasonType="ROLE_MISMATCH", dependencyType=None,
        instruction="providerRole의 책임을 명시")
    report = assess_legal_fact_completeness(candidate, descriptor=descriptor)
    compliance = validate_legal_fact_completion(candidate, candidate, [requirement], report, "C1-F1")
    assert compliance.status == "FAIL"
    assert compliance.unchangedRequiredFields == ["providerRole"]


class ScopeViolationProvider(MockPortfolioProvider):
    async def complete_legal_facts(self, seed, plan, candidate, requirements, candidate_index):
        patch = await super().complete_legal_facts(seed, plan, candidate, requirements, candidate_index)
        return {**patch, "channels": "요청되지 않은 채널 변경"}


class NoncompliantProvider(MockPortfolioProvider):
    async def complete_legal_facts(self, seed, plan, candidate, requirements, candidate_index):
        return {item.field: getattr(candidate, item.field) for item in requirements}


def test_targeted_patch_scope_violation_is_candidate_scoped_prelegal_exclusion():
    provider = ScopeViolationProvider()
    engine, seed, prepared = prepared_candidates(provider, count=1)
    result = run(engine.prepare_legal_candidates(seed, [vague_roles(prepared.candidates[0])]))
    assert not result.candidates
    assert result.excludedCandidates[0]["reasonCode"] == (
        FailureCode.LEGAL_FACT_COMPLETION_SCOPE_VIOLATION.value)
    assert result.excludedCandidates[0]["patchChangedFields"]


def test_provider_noncompliance_has_specific_failure_and_no_required_input():
    provider = NoncompliantProvider()
    engine, seed, prepared = prepared_candidates(provider, count=1)
    result = run(engine.prepare_legal_candidates(seed, [vague_roles(prepared.candidates[0])]))
    assert not result.candidates
    assert result.excludedCandidates[0]["reasonCode"] == (
        FailureCode.LEGAL_FACT_COMPLETION_PROVIDER_NONCOMPLIANT.value)
    assert result.completionCompliance[0].status == "FAIL"


def test_mock_multi_domain_contract_keeps_legal_observability_and_final_resolutions():
    for scenario in SCENARIOS:
        result = run(ConceptPortfolioEngine().run_full(
            scenario_payload(scenario["scenarioId"]), max_concepts=2))
        assert result.runStatus.value in {"READY_FULL", "READY_LIMITED"}
        assert result.runSummary.totalLegalReviewEvents == len(result.legalSummaries)
        assert result.runSummary.legalInitialReviewed == result.runSummary.legalReady
        assert len(result.legalResolutions) == result.runSummary.legalReady
        assert all(item.finalResolution == "ACCEPTED" for item in result.legalResolutions)
        assert not result.requiredInputs


def test_strict_patch_schema_excludes_identity_and_notebook_shows_new_diagnostics():
    assert set(LegalFactCompletionPatch.model_fields) == {
        "platformRole", "providerRole", "sellerRole", "intermediaryRole",
        "transactionFlow", "paymentFlow", "personalDataUsage", "physicalActivities",
        "partnerRequirements", "targetRegion", "channels",
    }
    preflight = v2_schema_preflight_report()
    assert preflight.status == "PASS"
    statuses = {item.schemaName: item.status for item in preflight.schemas}
    assert statuses["LegalFactDependencySemanticBatch"] == "PASS"
    assert statuses["LegalFactCompletionPatch"] == "PASS"
    notebook_path = Path(__file__).resolve().parents[2] / "notebooks" / "concept_portfolio_v2_lab.ipynb"
    notebook = json.loads(notebook_path.read_text(encoding="utf-8"))
    source = "\n".join("".join(cell.get("source", [])) for cell in notebook["cells"])
    assert "dependencySemanticBatchCalls" in source
    assert "completionCompliance" in source
    assert "show_legal_resolutions(live_result)" in source
