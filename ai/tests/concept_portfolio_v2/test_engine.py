import asyncio
import json
from pathlib import Path

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway, ProviderMode
from app.concept_portfolio_v2.anchor_policy import assess_anchor, build_opportunity_anchor
from app.concept_portfolio_v2.models import (
    LegalReview, LegalRoute, PlanDraftPool, PortfolioPlanDraft,
    RunStage, SchemaCompatibilityItem, SchemaPreflightReport,
)
from app.concept_portfolio_v2.plan_fidelity import deterministic_plan_fidelity
from app.concept_portfolio_v2.providers import MockPortfolioProvider
from app.concept_portfolio_v2.mechanics import derive_candidate_descriptor
from app.concept_portfolio_v2.schema_preflight import inspect_strict_schema
from app.concept_portfolio_v2.snapshot_hash import production_compatible_snapshot_hash


FIXTURES = Path(__file__).resolve().parents[2] / "fixtures" / "concept_portfolio_v2"
README = Path(__file__).resolve().parents[2] / "notebooks" / "CONCEPT_PORTFOLIO_V2_LAB_README.md"


def fixture(name):
    return json.loads((FIXTURES / f"{name}.json").read_text(encoding="utf-8"))


def run(value):
    return asyncio.run(value)


def staged(name="food_minimal", *, gateway=None):
    engine = ConceptPortfolioEngine(gateway=gateway)
    seed = engine.seed_adapter.adapt(fixture(name))
    engine._reset()
    analysis = run(engine.analyze_seed(seed))
    plans = run(engine.plan_portfolio(seed, analysis))
    validated = run(engine.validate_plans(plans, analysis))
    candidates = run(engine.expand_plans(seed, validated.acceptedPlans))
    return engine, seed, analysis, plans, validated, candidates


def test_01_portfolio_plan_draft_schema_strict_preflight_passes():
    assert inspect_strict_schema(PortfolioPlanDraft.model_json_schema(), "PortfolioPlanDraft").status == "PASS"


def test_02_plan_draft_pool_schema_strict_preflight_passes():
    assert inspect_strict_schema(PlanDraftPool.model_json_schema(), "PlanDraftPool").status == "PASS"


def test_03_dynamic_map_provider_schema_fails_preflight():
    schema = {"type": "object", "properties": {"owned": {
        "type": "object", "additionalProperties": {"type": "string"}}},
        "required": ["owned"], "additionalProperties": False}
    result = inspect_strict_schema(schema, "DynamicMap")
    assert result.status == "FAIL"
    assert any(item["reason"] == "DYNAMIC_OBJECT_NOT_STRICT_COMPATIBLE" for item in result.failures)


def test_04_draft_normalization_owns_ids_anchors_and_locks():
    engine = ConceptPortfolioEngine()
    seed = engine.seed_adapter.adapt(fixture("food_partial_lock")); engine._reset()
    analysis = run(engine.analyze_seed(seed))
    drafts = run(engine.generate_plan_drafts(seed, analysis))
    plans = engine.normalize_plan_drafts(drafts, analysis)
    assert [item.planId for item in plans[:3]] == ["P1", "P2", "P3"]
    assert plans[0].preservedAnchors == analysis.semanticAnchors
    assert plans[0].preservedLocks == analysis.explicitBusinessLocks


def test_05_provider_wrong_lock_is_overwritten_by_user_authority():
    result = run(ConceptPortfolioEngine().run_full(fixture("provider_wrong_lock")))
    assert all(item.candidate.price == "월 19,900원" for item in result.concepts)
    assert all(item.candidate.channels == "모바일 앱" for item in result.concepts)


def test_06_explore_concept_definition_has_generated_provenance():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_minimal")))
    semantics = {item.fieldKey: item for item in result.concepts[0].candidate.valueSemantics}
    assert semantics["conceptDefinition"].source == "CONCEPT_GENERATED"
    assert semantics["conceptDefinition"].authority == "REVIEWABLE"


def test_07_as_is_candidate_one_preserves_original_authoritative_semantics():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_heavy_lock")))
    candidate = result.concepts[0].candidate
    semantics = {item.fieldKey: item for item in candidate.valueSemantics}
    assert candidate.originalCandidate is True
    assert candidate.conceptDefinition == fixture("food_heavy_lock")["ideaOverview"]
    assert semantics["conceptDefinition"].source == "USER_INPUT"
    assert semantics["conceptDefinition"].authority == "LOCKED"


def test_08_source_locks_and_business_locks_are_separate():
    engine = ConceptPortfolioEngine(); seed = engine.seed_adapter.adapt(fixture("food_partial_lock")); engine._reset()
    analysis = run(engine.analyze_seed(seed))
    assert "problem" in analysis.sourceLocks and "problem" not in analysis.explicitBusinessLocks
    assert analysis.explicitBusinessLocks["price"] == "월 19,900원"


def test_09_target_specialization_is_anchor_valid():
    seed = ConceptPortfolioEngine().seed_adapter.adapt(fixture("valid_specialization"))
    decision, _ = assess_anchor(build_opportunity_anchor(seed), seed.problem, "식재료 낭비가 많은 직장인 1인 가구")
    assert decision == "PASS"


def test_10_unrelated_target_and_problem_is_semantic_boundary_not_domain_rule():
    seed = ConceptPortfolioEngine().seed_adapter.adapt(fixture("anchor_drift"))
    decision, _ = assess_anchor(build_opportunity_anchor(seed), "대기업 급식 운영 효율화", "기업 구내식당 담당자")
    assert decision == "AMBIGUOUS"


def test_11_three_optional_locks_do_not_cap_valid_portfolio():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_partial_lock")))
    assert result.producedConceptCount == 5 and result.runStatus.value == "READY_FULL"


def test_12_only_three_unique_plans_returns_ready_limited_three():
    result = run(ConceptPortfolioEngine().run_full(fixture("limited_unique_plans")))
    assert result.runStatus.value == "READY_LIMITED" and result.producedConceptCount == 3


def test_13_near_duplicate_paraphrase_is_not_automatically_distinct():
    result = run(ConceptPortfolioEngine().run_full(fixture("near_duplicate_paraphrase")))
    assert any(item.reasonCode.value == "PLAN_DUPLICATE" for item in result.rejectedPlans)


def test_14_same_family_meaningful_plan_is_variant():
    engine, _, _, plans, _, _ = staged()
    assessment = engine.compare_plans(plans[0], plans[1])
    assert assessment.decision == "VARIANT" and assessment.familyA == assessment.familyB


def test_15_same_architecture_meaningful_thesis_is_variant_and_accepted():
    engine = ConceptPortfolioEngine(); seed = engine.seed_adapter.adapt(fixture("food_minimal")); engine._reset()
    analysis = run(engine.analyze_seed(seed)); plans = run(engine.plan_portfolio(seed, analysis))
    thesis = plans[0].descriptor.thesis.model_copy(update={
        "targetSegmentThesis": "도입 장벽이 높은 특정 우선 사용자",
        "useCaseThesis": "긴급하게 해결해야 하는 사용 상황"})
    changed = plans[0].descriptor.model_copy(update={"thesis": thesis})
    pair = [plans[0], plans[0].model_copy(update={
        "planId": "PX", "targetSegment": thesis.targetSegmentThesis,
        "useContext": thesis.useCaseThesis, "descriptor": changed})]
    result = run(engine.validate_plans(pair, analysis, max_concepts=2))
    assert result.diversity[0].decision == "VARIANT"
    assert len(result.acceptedPlans) == 2


def test_16_candidate_paraphrase_clone_is_duplicate():
    engine, _, _, _, _, candidates = staged()
    clone = candidates[0].model_copy(update={
        "candidateId": "CLONE",
        "candidate": candidates[0].candidate.model_copy(update={"conceptName": "같은 구조의 다른 표현"})})
    assert engine.compare_candidates(candidates[0], clone).decision == "DUPLICATE"


def test_17_candidate_with_primary_business_role_difference_is_distinct():
    engine, _, _, _, _, candidates = staged()
    changed_candidate = candidates[0].candidate.model_copy(update={
        "conceptName": "마켓플레이스 구조", "solutionMechanism": "다수 파트너를 매칭하는 마켓플레이스",
        "operatingModel": "파트너 네트워크 운영", "platformRole": "거래 마켓플레이스 중개",
        "partnerModel": "다수 제휴 파트너", "transactionFlow": ["사용자 요청", "파트너 매칭"],
        "revenueModel": "거래 수수료"})
    other = candidates[0].model_copy(update={"candidateId": "OTHER",
        "descriptor": derive_candidate_descriptor(changed_candidate), "candidate": changed_candidate})
    assert engine.compare_candidates(candidates[0], other).decision == "DISTINCT"


def test_18_plan_wording_difference_with_same_mechanics_passes_fidelity():
    _, _, _, plans, _, candidates = staged()
    paraphrased = plans[0].model_copy(update={"coreMechanism": "수요예측 방식의 정기 소분"})
    decision, matched, _ = deterministic_plan_fidelity(paraphrased, candidates[0].candidate)
    assert decision in {"PASS", "ADAPTED"} and "solutionThesis" in matched


def test_19_candidate_breaking_plan_mechanism_fails_fidelity():
    engine, seed, _, plans, validated, candidates = staged()
    broken = candidates[0].model_copy(update={"candidate": candidates[0].candidate.model_copy(update={
        "solutionMechanism": "광고 배너 판매", "operatingModel": "광고 게재",
        "partnerModel": "광고주", "transactionFlow": ["광고 노출"],
        "featureSet": ["광고 배너"], "conceptDefinition": "광고 배너를 판매하는 서비스",
        "revenueModel": "광고비", "physicalActivities": ["디지털 광고"]})})
    accepted, reports = run(engine.validate_candidates(seed, validated.acceptedPlans, [broken]))
    assert not accepted and reports[0].fidelityDecision == "FAIL"
    assert any(code.value == "PLAN_FIDELITY_FAILED" for code in reports[0].reasonCodes)


def test_20_two_lineages_each_receive_one_redesign():
    result = run(ConceptPortfolioEngine().run_full(fixture("two_legal_redesigns")))
    children = [item for item in result.concepts if item.redesignRound == 1]
    assert {(item.lineageId, item.redesignRound) for item in children} == {("L1", 1), ("L3", 1)}


def test_21_same_lineage_second_redesign_exhausts_budget():
    result = run(ConceptPortfolioEngine().run_full(fixture("second_redesign")))
    assert any(item.sourceStatus in {"REDESIGN_BUDGET", "REDESIGN_LOOP"} for item in result.legalSummaries)
    assert not any(item.redesignRound > 1 for item in result.concepts)


def test_22_redesign_parent_is_not_rejected_as_self_duplicate():
    result = run(ConceptPortfolioEngine().run_full(fixture("legal_redesign")))
    child = next(item for item in result.concepts if item.redesignRound == 1)
    assert child.lineageId == "L1" and child.redesignRound == 1


def test_23_redesign_clone_of_other_final_concept_routes_to_replan():
    class CloneProvider(MockPortfolioProvider):
        clone = None
        async def redesign(self, seed, plan, candidate, requirements, candidate_index):
            return self.clone

    provider = CloneProvider(); gateway = ProviderGateway(ProviderMode.MOCK, provider=provider)
    engine, seed, _, plans, validated, candidates = staged(gateway=gateway)
    target = candidates[0]
    other = target.model_copy(update={"candidateId": "C2", "lineageId": "L2"})
    provider.clone = other.candidate
    reviews = [LegalReview(candidateId="C2", route=LegalRoute.ACCEPT, sourceStatus="TEST", safeSummary="통과"),
               LegalReview(candidateId="C1", route=LegalRoute.REDESIGN_WITHIN_LINEAGE,
                           sourceStatus="TEST", safeSummary="재설계", redesignRequirements=["통제"])]
    _, all_reviews, _, _, _ = run(engine.resolve_legal(
        seed, validated.acceptedPlans, [other, target], reviews))
    assert any(item.candidateId == "C1-R1" and item.route == LegalRoute.REPLAN_REQUIRED for item in all_reviews)


def test_24_replan_reenters_full_candidate_validation():
    engine = ConceptPortfolioEngine(); seen = []
    original = engine.validate_candidates
    async def wrapped(seed, plans, candidates, **kwargs):
        seen.extend(item.candidateId for item in candidates)
        return await original(seed, plans, candidates, **kwargs)
    engine.validate_candidates = wrapped
    result = run(engine.run_full(fixture("legal_replan")))
    assert any("REPLAN" in item for item in seen)
    assert any("REPLAN" in item.candidateId for item in result.concepts)


def test_25_replan_lock_violation_skips_legal_call():
    payload = fixture("food_partial_lock"); payload["fixtureName"] = "legal_replan"
    engine = ConceptPortfolioEngine(); original = engine.expand_plan
    async def corrupt(*args, **kwargs):
        envelope = await original(*args, **kwargs)
        if "REPLAN" in envelope.candidateId:
            envelope = envelope.model_copy(update={"candidate": envelope.candidate.model_copy(update={"price": "LOCK 위반"})})
        return envelope
    engine.expand_plan = corrupt
    result = run(engine.run_full(payload))
    assert not any(item.candidateId == "C1-REPLAN" for item in result.legalSummaries)
    assert result.producedConceptCount == 4


def test_26_replan_anchor_drift_skips_legal_call():
    result = run(ConceptPortfolioEngine().run_full(fixture("replan_anchor_drift")))
    assert not any(item.candidateId == "C1-REPLAN" for item in result.legalSummaries)
    assert result.producedConceptCount == 4


def test_27_legal_lock_conflict_needs_input_without_regeneration():
    result = run(ConceptPortfolioEngine().run_full(fixture("lock_legal_conflict")))
    assert result.runStatus.value == "NEEDS_INPUT"
    assert result.requiredInputs[0]["conflictingLock"] == "channels"
    assert result.runSummary is not None and result.runSummary.replanned == 0


def test_28_snapshot_hash_matches_fixed_cross_contract_fixture():
    contract = fixture("snapshot_hash_cross_contract")
    assert production_compatible_snapshot_hash(contract["payload"]) == contract["expectedHash"]


def test_29_unicode_nfc_produces_same_hash():
    assert production_compatible_snapshot_hash({"text": "é"}) == production_compatible_snapshot_hash({"text": "e\u0301"})


def test_30_java_equivalent_float_and_integer_hash_match():
    assert production_compatible_snapshot_hash({"value": 1.0}) == production_compatible_snapshot_hash({"value": 1})


def test_31_market_legal_result_includes_partner_qualifications():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_minimal"), auto_confirm_hypotheses=True))
    contract = fixture("downstream_contract_cross_contract")["marketAnalysisSeedSnapshot"]
    legal = result.handoff.marketAnalysisSeedSnapshot["legalResult"]
    assert set(contract["requiredLegalResult"]) <= legal.keys()


def test_32_market_analysis_seed_required_shape_passes():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_minimal"), auto_confirm_hypotheses=True))
    handoff = result.handoff
    contract = fixture("downstream_contract_cross_contract")["marketAnalysisSeedSnapshot"]
    assert handoff.structureStatus == "STRUCTURE_PASS"
    assert handoff.marketAnalysisSeedSnapshot["contract"] == contract["contract"]
    assert set(contract["requiredTopLevel"]) <= handoff.marketAnalysisSeedSnapshot.keys()


def test_33_marketing_source_required_shape_passes():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_minimal"), auto_confirm_hypotheses=True))
    contract = fixture("downstream_contract_cross_contract")["marketingSourceSnapshot"]
    assert result.handoff.marketingSourceSnapshot["contract"] == contract["contract"]
    assert set(contract["requiredTopLevel"]) <= result.handoff.marketingSourceSnapshot.keys()
    assert result.handoff.contractStatus == "CONTRACT_PASS"


def test_34_incomplete_seven_hypotheses_fail_downstream_contract():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_minimal")))
    selected = result.concepts[0]
    engine = ConceptPortfolioEngine(); seed = engine.seed_adapter.adapt(fixture("food_minimal"))
    hypotheses = engine.confirm_hypotheses(
        engine.build_or_load_current_hypothesis_contract(selected), confirm_all_proposed=True)[:-1]
    legal = next(item for item in result.legalSummaries if item.candidateId == selected.candidateId)
    handoff = engine.downstream_adapter.build(seed, selected.candidateId, selected.candidate, hypotheses, legal)
    assert handoff.contractStatus == "CONTRACT_FAIL"
    assert any("확정되지 않은 hypothesis" in item for item in handoff.validationErrors)


def test_35_manual_legal_sensitive_edit_requires_delta_legal():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_minimal"), auto_confirm_hypotheses=True))
    engine = ConceptPortfolioEngine()
    hypotheses = engine.build_or_load_current_hypothesis_contract(result.concepts[0])
    edited = engine.confirm_hypotheses(hypotheses, {"CHANNELS": "오프라인 방문판매"})
    channel = next(item for item in edited if item.hypothesisType == "CHANNELS")
    assert channel.deltaLegalRequired and channel.legalReviewStatus == "PENDING"


def test_36_auto_confirm_is_documented_as_lab_shortcut():
    text = README.read_text(encoding="utf-8")
    assert "Lab shortcut" in text and "auto_confirm_hypotheses" in text


def test_37_provider_recording_redacts_secrets_recursively():
    raw = {"AI_API_KEY": "sk-secret", "nested": {"Authorization": "Bearer secret"},
           "safe": "visible", "items": [{"moleg_key": "secret"}]}
    redacted = ProviderGateway.redact_secrets(raw)
    assert redacted["AI_API_KEY"] == "[REDACTED]"
    assert redacted["nested"]["Authorization"] == "[REDACTED]"
    assert redacted["items"][0]["moleg_key"] == "[REDACTED]" and redacted["safe"] == "visible"


def test_38_schema_failure_is_terminal_with_zero_external_calls():
    engine = ConceptPortfolioEngine()
    engine.schema_preflight_report = lambda: SchemaPreflightReport(status="FAIL", providerCalls=0, schemas=[
        SchemaCompatibilityItem(schemaName="PlanDraftPool", status="FAIL",
                                failures=[{"path": "$.properties.x", "reason": "TEST_FAILURE"}])])
    result = run(engine.run_full(fixture("food_minimal")))
    assert result.runStatus.value == "FAILED" and result.runtimeStage == RunStage.FAILED
    assert result.providerUsage.externalProviderCalls == 0
    assert result.trace[-1].action == "SCHEMA_PREFLIGHT_FAILED"


def test_39_full_mock_normal_reaches_contract_pass():
    result = run(ConceptPortfolioEngine().run_full(
        fixture("food_minimal"), auto_confirm_hypotheses=True))
    assert result.runStatus.value in {"READY_FULL", "READY_LIMITED"}
    assert result.handoff.contractStatus == "CONTRACT_PASS"
    assert result.providerUsage.logicalOperations > 0 and result.providerUsage.externalProviderCalls == 0


def test_40_full_mock_heavy_lock_has_no_arbitrary_lock_count_cap():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_heavy_lock")))
    assert result.runStatus.value == "READY_FULL" and result.producedConceptCount == 5
