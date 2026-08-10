import asyncio
import json
from pathlib import Path

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway
from app.concept_portfolio_v2.candidate_governance import candidate_result_to_draft
from app.concept_portfolio_v2.diagnostics.notebook_view import show_hypothesis_readiness
from app.concept_portfolio_v2.legal_fact_completeness import assess_role_semantics
from app.concept_portfolio_v2.models import (
    ArchitectureDimensionDiagnostic, BusinessRoleSemanticItem, FailureCode,
    LegalFactCompletionRequirement,
)
from app.concept_portfolio_v2.providers import MockPortfolioProvider

SCENARIOS = json.loads((Path(__file__).resolve().parents[2] / "fixtures" /
    "concept_portfolio_v2" / "live_scenarios.json").read_text(encoding="utf-8"))
SCENARIO_BY_ID = {item["scenarioId"]: item for item in SCENARIOS}


def scenario_payload(scenario_id):
    item = SCENARIO_BY_ID[scenario_id]
    return {key: item[key] for key in ("ideaOverview", "problem", "targetUsers")}


def run(value):
    return asyncio.run(value)


def prepared_candidates(provider=None, scenario_id="B2B_AI_SALES_ASSISTANT", count=3):
    gateway = ProviderGateway(provider=provider) if provider else None
    engine = ConceptPortfolioEngine(gateway=gateway)
    seed = engine.seed_adapter.adapt(scenario_payload(scenario_id))
    engine._reset()
    analysis = run(engine.analyze_seed(seed))
    plans = run(engine.prepare_portfolio_plans(seed, analysis, count))
    prepared = run(engine.prepare_candidate_portfolio(seed, plans, count))
    return engine, seed, prepared


class TrackingRoleProvider(MockPortfolioProvider):
    def __init__(self, decisions=("MATCH",)):
        self.decisions = list(decisions)
        self.role_batches = []

    async def classify_business_roles(self, items):
        self.role_batches.append(items)
        decision = self.decisions[min(len(self.role_batches) - 1, len(self.decisions) - 1)]
        return [BusinessRoleSemanticItem(
            candidateId=item["candidateId"], field=item["field"], decision=decision,
            safeReason="테스트 semantic 역할 판정") for item in items]


class CompletionRecheckProvider(TrackingRoleProvider):
    async def complete_legal_facts(self, seed, plan, candidate, requirements, candidate_index):
        patch = await MockPortfolioProvider.complete_legal_facts(
            self, seed, plan, candidate, requirements, candidate_index)
        role_fields = {item.field for item in requirements if item.reasonType == "ROLE_MISMATCH"}
        return {**patch, **{field: "해당 역할의 책임과 경계를 운영 정책에 따라 조율합니다"
                            for field in role_fields}}


class ExhaustedRoleProvider(CompletionRecheckProvider):
    def __init__(self):
        super().__init__(("UNKNOWN",))

    async def expand(self, seed, plan, candidate_index):
        draft = await super().expand(seed, plan, candidate_index)
        vague = "관련 업무 요청을 확인하고 지원합니다"
        return draft.model_copy(update={
            "platformRole": vague, "providerRole": vague,
            "sellerRole": vague, "intermediaryRole": vague,
        })


class PartialRoleProvider(CompletionRecheckProvider):
    def __init__(self):
        super().__init__(("UNKNOWN",))

    async def expand(self, seed, plan, candidate_index):
        draft = await super().expand(seed, plan, candidate_index)
        vague = "관련 업무 요청을 확인하고 지원합니다"
        return draft.model_copy(update={
            "platformRole": vague, "providerRole": vague,
            "sellerRole": vague, "intermediaryRole": vague,
        })

    async def classify_business_roles(self, items):
        self.role_batches.append(items)
        return [BusinessRoleSemanticItem(
            candidateId=item["candidateId"], field=item["field"],
            decision="MATCH" if item["candidateId"].split("-")[0] == "C1" else "UNKNOWN",
            safeReason="첫 Candidate만 역할 의미가 충분합니다.") for item in items]


def vague_roles(envelope):
    vague = "이용자의 요청을 확인하고 관련 업무를 책임 있게 조율합니다"
    candidate = envelope.candidate.model_copy(update={
        "platformRole": vague, "providerRole": vague,
        "sellerRole": vague, "intermediaryRole": vague,
    })
    return envelope.model_copy(update={"candidate": candidate})


def test_ambiguous_paraphrase_uses_batched_semantic_checks():
    provider = TrackingRoleProvider(("MATCH",))
    engine, seed, prepared = prepared_candidates(provider)
    candidates = [vague_roles(item) for item in prepared.candidates]
    result = run(engine.prepare_legal_candidates(seed, candidates))
    assert assess_role_semantics("providerRole", candidates[0].candidate.providerRole) == "AMBIGUOUS"
    assert 1 <= len(provider.role_batches) <= 2
    assert len(provider.role_batches[0]) == len(candidates) * 4
    assert result.roleSemanticBatchCalls == len(provider.role_batches)
    assert len(result.candidates) == len(candidates)
    assert all(role["semanticUsed"] and role["finalStatus"] == "MATCH"
               for report in result.reports for role in report.roleSemantics)


def test_fact_completion_children_are_semantically_rechecked_in_one_batch():
    provider = CompletionRecheckProvider(("UNKNOWN", "MATCH"))
    engine, seed, prepared = prepared_candidates(provider, count=2)
    candidates = [vague_roles(item) for item in prepared.candidates]
    result = run(engine.prepare_legal_candidates(seed, candidates))
    assert len(provider.role_batches) == 2
    assert result.roleSemanticBatchCalls == 2
    assert result.completionAttempted == len(candidates)
    assert result.completionValidated == len(candidates)
    assert result.completionAccepted == len(candidates)
    assert all(item.candidateId.endswith("-F1") for item in result.candidates)


def test_architecture_role_conflict_is_diagnostic_only():
    engine, seed, prepared = prepared_candidates(count=1)
    envelope = prepared.candidates[0]
    architecture = envelope.descriptor.architecture.model_copy(update={"businessRole": "INTERMEDIARY"})
    diagnostics = dict(envelope.descriptor.architectureDiagnostics)
    diagnostics["businessRole"] = ArchitectureDimensionDiagnostic(
        code="INTERMEDIARY", confidence="HIGH", source="RULE")
    descriptor = envelope.descriptor.model_copy(update={
        "architecture": architecture, "architectureDiagnostics": diagnostics})
    candidate = envelope.candidate.model_copy(update={"intermediaryRole": "제3자 거래를 중개하지 않음"})
    changed = envelope.model_copy(update={"descriptor": descriptor, "candidate": candidate})
    reports, role_calls, dependency_calls = run(engine._assess_legal_fact_batch([changed]))
    assert role_calls >= 0 and dependency_calls >= 0
    assert reports[0].architectureRoleConsistency["status"] == "POTENTIAL_CONFLICT"
    assert reports[0].status in {"COMPLETE", "COMPLETABLE"}


def test_all_prelegal_exhaustion_has_specific_failure_and_no_required_input_pollution():
    provider = ExhaustedRoleProvider()
    engine = ConceptPortfolioEngine(gateway=ProviderGateway(provider=provider))
    result = run(engine.run_full(scenario_payload("AI_INTERVIEW_COACH"), max_concepts=3))
    assert result.runStatus.value == "FAILED"
    assert result.failureDiagnostics.failedStage == "LEGAL_RECOVERING"
    assert result.failureDiagnostics.failureCode == FailureCode.NO_LEGAL_READY_CANDIDATES.value
    assert result.requiredInputs == [] and result.unresolvedCandidates == []
    assert result.preLegalExclusions
    assert all(item["scope"] == "PRE_LEGAL_EXCLUSION" for item in result.preLegalExclusions)
    assert result.runSummary.legalReady == 0


def test_partial_prelegal_exclusions_do_not_become_user_required_inputs():
    engine = ConceptPortfolioEngine(gateway=ProviderGateway(provider=PartialRoleProvider()))
    result = run(engine.run_full(scenario_payload("B2B_AI_SALES_ASSISTANT"), max_concepts=3))
    assert result.runStatus.value == "READY_LIMITED"
    assert result.producedConceptCount == 1
    assert result.preLegalExclusions
    assert result.requiredInputs == [] and result.unresolvedCandidates == []
    assert result.runSummary.legalReady == 1 and result.runSummary.legalReviewed == 1


def test_empty_hypothesis_readiness_is_explicitly_not_ready():
    result = show_hypothesis_readiness([])
    assert result == {
        "All Values Semantically Valid": False,
        "All Decisions Confirmed": False,
        "Ready For Handoff": False,
        "status": "NOT_READY",
        "reason": "NO_SELECTED_CONCEPT_OR_HYPOTHESES",
        "unresolvedHypotheses": [],
    }


def test_mock_completion_does_not_invent_physical_activity_for_digital_candidate():
    provider = MockPortfolioProvider()
    engine, seed, prepared = prepared_candidates(provider, "AI_INTERVIEW_COACH", 1)
    envelope = prepared.candidates[0]
    candidate = envelope.candidate.model_copy(update={"physicalActivities": []})
    plan = prepared.usedPlans[0]
    completed = run(provider.complete_legal_facts(
        seed, plan, candidate, [LegalFactCompletionRequirement(
            field="providerRole", reasonType="ROLE_MISMATCH", dependencyType=None,
            instruction="providerRole을 명시")], candidate.candidateIndex))
    assert "physicalActivities" not in completed


def test_digital_feedback_text_is_not_reported_as_regulated_physical_activity():
    engine, _, prepared = prepared_candidates(scenario_id="AI_INTERVIEW_COACH", count=1)
    envelope = prepared.candidates[0]
    digital = envelope.candidate.model_copy(update={
        "physicalActivities": ["모의면접 진행", "AI 분석 결과에 따른 피드백 제공"]})
    assert engine.legal_precheck(envelope.model_copy(update={"candidate": digital})).regulatedPhysicalActivity is False


def test_notebook_one_click_and_core_execution_guards_are_source_visible():
    path = Path(__file__).resolve().parents[2] / "notebooks" / "concept_portfolio_v2_lab.ipynb"
    notebook = json.loads(path.read_text(encoding="utf-8"))
    source = "\n".join("".join(cell.get("source", [])) for cell in notebook["cells"])
    assert "RUN_STAGED_CORE = LIVE_TEST_LEVEL in {'CORE', 'LEGAL_C1', 'FULL_E2E'}" in source
    assert "RUN_STAGED_LEGAL = LIVE_TEST_LEVEL in {'LEGAL_C1', 'FULL_E2E'}" in source
    assert "RUN_REMAINING_LEGAL = RUN_STAGED_FULL" in source
    assert "if RUN_ONE_CLICK_LIVE and LIVE_TEST_LEVEL == 'ONE_CLICK':" in source
    assert "if LIVE_TEST_LEVEL == 'FULL_E2E' and MODE != 'LIVE':" in source
    assert "'Ready For Handoff'" in source
    by_id = {cell.get("id"): "".join(cell.get("source", [])) for cell in notebook["cells"]}
    assert "if RUN_STAGED_CORE:" in by_id["1c2c20f5"]
    assert "if RUN_STAGED_CORE and selected_plans" in by_id["df69cf1a"]
    assert "if RUN_STAGED_CORE:" in by_id["f30b070f"]
    assert "if RUN_STAGED_CORE and plan_validation" in by_id["149853f2"]
    assert "if RUN_STAGED_LEGAL and candidates" in by_id["022ea2cf"]


def test_role_semantic_contract_covers_all_seven_generic_domains():
    for scenario in SCENARIOS:
        result = run(ConceptPortfolioEngine().run_full(scenario_payload(scenario["scenarioId"]), max_concepts=2))
        assert result.runStatus.value in {"READY_FULL", "READY_LIMITED"}
        assert result.runSummary.legalReady >= 1
        assert result.failureDiagnostics is None
