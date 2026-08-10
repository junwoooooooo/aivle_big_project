import asyncio
import inspect
import json
from pathlib import Path

import pytest

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway, ProviderMode
from app.concept_portfolio_v2.adapters import CurrentLegalAdapter
from app.concept_portfolio_v2.anchor_policy import assess_anchor, build_opportunity_anchor
from app.concept_portfolio_v2.candidate_governance import candidate_result_to_draft, normalize_candidate_draft
from app.concept_portfolio_v2.distinctness import deterministic_distinctness
from app.concept_portfolio_v2.language_policy import (
    candidate_language_failures, is_governance_placeholder, plan_language_failures,
)
from app.concept_portfolio_v2.mechanics import GENERIC_CODE_SETS, derive_candidate_descriptor
from app.concept_portfolio_v2.models import (
    ExplorationBreadth, IdeaBriefLabContext, LegalRoute,
)
from app.concept_portfolio_v2.plan_policy import assess_plan_content
from app.concept_portfolio_v2.providers import MockPortfolioProvider, ReplayMiss
from app.tasks.concept_legal_review import service as legal_service
from app.tasks.concept_legal_review.models import ConceptLegalReviewProviderResult, OfficialEvidence


FIXTURES = Path(__file__).resolve().parents[2] / "fixtures" / "concept_portfolio_v2"


def fixture(name="food_minimal"):
    return json.loads((FIXTURES / f"{name}.json").read_text(encoding="utf-8"))


def run(value):
    return asyncio.run(value)


def staged(payload=None, *, gateway=None, breadth=None):
    engine = ConceptPortfolioEngine(gateway=gateway)
    seed = engine.seed_adapter.adapt(payload or fixture())
    engine._reset()
    run(engine.check_safety(seed))
    analysis = run(engine.analyze_seed(seed, breadth))
    plans = run(engine.plan_portfolio(seed, analysis))
    validated = run(engine.validate_plans(plans, analysis))
    candidates = run(engine.expand_plans(seed, validated.acceptedPlans))
    return engine, seed, analysis, plans, validated, candidates


def evidence(index=0):
    return OfficialEvidence(
        referenceIndex=index, sourceType="OFFICIAL_LAW", lawId="LAW-1",
        officialIdentifier="MST-1", lawName="개인정보 보호법", articleReference="제1조",
        title="목적", officialSourceUri="https://www.law.go.kr/법령/개인정보보호법",
        jurisdiction="KR", promulgationDate="2025-01-01", effectiveDate="2025-03-13",
        retrievedAt="2026-08-07T00:00:00Z", contentHash="sha256:" + "a" * 64,
        boundedProvisionSummary="공식 조문 요약", queryKey="sha256:" + "b" * 64,
        registryVersion="legal-registry-v1")


def legal_provider(index=0, text="필수 통제를 적용합니다"):
    return ConceptLegalReviewProviderResult.model_validate({
        "status": "IMPLEMENTABLE_WITH_CONTROLS", "reviewedActivities": ["거래 운영"],
        "requiredControls": [{"text": text, "evidenceReferenceIndexes": [index]}],
        "requiredPartnersAndQualifications": [], "requiredDisclosures": [],
        "prohibitedVariants": [], "evidenceReferenceIndexes": [index],
        "redesignRequirements": [], "unknownFacts": [], "expertReviewRecommended": True,
        "reviewBasisDate": "2026-08-07", "safeUserSummary": "통제를 적용하면 구현할 수 있습니다."})


def test_41_korean_plan_content():
    _, _, _, plans, _, _ = staged()
    assert all(not plan_language_failures(plan) for plan in plans)


def test_42_korean_candidate_content():
    _, _, _, _, _, candidates = staged()
    assert all(not candidate_language_failures(item.candidate) for item in candidates)


def test_43_machine_code_remains_stable_when_korean_label_changes():
    engine, _, _, plans, _, _ = staged()
    assert "mechanics" not in engine._last_plan_drafts[0].model_dump()
    architecture = plans[0].descriptor.architecture.model_dump()
    assert all(value in GENERIC_CODE_SETS[key] for key, value in architecture.items()
               if key in GENERIC_CODE_SETS)


def test_44_target_region_open_placeholder_is_rejected():
    assert is_governance_placeholder("OPEN")


def test_45_open_target_region_becomes_korean_ai_hypothesis():
    engine, seed, analysis, _, validated, candidates = staged()
    draft = engine.gateway.provider
    candidate = candidates[0].candidate
    assert candidate.targetRegion == "대한민국"
    semantic = {item.fieldKey: item for item in candidate.valueSemantics}["targetRegion"]
    assert (semantic.source, semantic.authority, semantic.decision) == ("AI_HYPOTHESIS", "OPEN", "PROPOSED")


def test_45b_semantic_missing_target_region_uses_system_default():
    _, seed, analysis, _, _, candidates = staged()
    draft = candidate_result_to_draft(candidates[0].candidate).model_copy(
        update={"targetRegion": "대상 지역은 명시되지 않았습니다."})
    normalized = normalize_candidate_draft(draft, seed, analysis.explorationBreadth, 1)
    assert normalized.targetRegion == "대한민국"


def test_46_nonlocked_target_region_semantics_are_ai_hypothesis():
    candidate = staged()[-1][0].candidate
    item = {value.fieldKey: value for value in candidate.valueSemantics}["targetRegion"]
    assert item.source == "AI_HYPOTHESIS" and item.authority == "OPEN"


def test_47_nonlocked_revenue_model_semantics_are_ai_hypothesis():
    candidate = staged()[-1][0].candidate
    item = {value.fieldKey: value for value in candidate.valueSemantics}["revenueModel"]
    assert item.source == "AI_HYPOTHESIS" and item.decision == "PROPOSED"


def test_48_nonlocked_differentiators_semantics_are_ai_hypothesis():
    candidate = staged()[-1][0].candidate
    item = {value.fieldKey: value for value in candidate.valueSemantics}["differentiators"]
    assert item.source == "AI_HYPOTHESIS" and item.decision == "PROPOSED"


def test_49_live_like_idea_derivation_preserves_interpretation():
    class IdeaGateway(ProviderGateway):
        async def call(self, _stage, _operation, _payload, _fn, response_model=None, **_kwargs):
            raw = ConceptPortfolioEngine().seed_adapter.local_context(
                ConceptPortfolioEngine().seed_adapter.adapt(fixture())).model_dump(mode="json")
            raw["interpretation"]["conciseIdeaDefinition"] = "LIVE 유사 파생 결과"
            return response_model.model_validate(raw) if response_model else raw

    engine = ConceptPortfolioEngine(ProviderMode.LIVE, gateway=IdeaGateway(ProviderMode.LIVE))
    seed = engine.seed_adapter.adapt(fixture())
    context = run(engine.derive_idea_brief(seed))
    assert context.interpretation["conciseIdeaDefinition"] == "LIVE 유사 파생 결과"
    assert seed.interpretation == context.interpretation


def test_50_interpretation_reaches_market_seed():
    result = run(ConceptPortfolioEngine().run_full(fixture(), auto_confirm_hypotheses=True))
    assert result.handoff.marketAnalysisSeedSnapshot["aiInterpretation"]


def test_51_empty_interpretation_cannot_contract_pass():
    engine, seed, _, _, validated, candidates = staged()
    accepted, _ = run(engine.validate_candidates(seed, validated.acceptedPlans, candidates))
    legal = run(engine.review_legal(seed, accepted))
    seed.interpretation = {}
    hypotheses = engine.confirm_hypotheses(
        engine.build_or_load_current_hypothesis_contract(accepted[0]), confirm_all_proposed=True)
    handoff = engine.build_downstream_handoff(seed, accepted[0], hypotheses, legal)
    assert handoff.contractStatus == "CONTRACT_FAIL"
    assert any("interpretation" in error for error in handoff.validationErrors)


def test_52_refine_intent_drift_is_semantic_boundary_without_domain_keywords():
    seed = ConceptPortfolioEngine().seed_adapter.adapt(fixture())
    decision, _ = assess_anchor(build_opportunity_anchor(seed), seed.problem, seed.targetUsers,
                                "남은 재료의 레시피만 보여주는 앱", ExplorationBreadth.REFINE)
    assert decision == "AMBIGUOUS"


def test_53_explore_allows_broader_solution():
    seed = ConceptPortfolioEngine().seed_adapter.adapt(fixture())
    decision, _ = assess_anchor(build_opportunity_anchor(seed), seed.problem, seed.targetUsers,
                                "남은 재료의 레시피를 추천하는 앱", ExplorationBreadth.EXPLORE)
    assert decision == "PASS"


def test_54_as_is_strongly_preserves_intent():
    seed = ConceptPortfolioEngine().seed_adapter.adapt(fixture())
    decision, _ = assess_anchor(build_opportunity_anchor(seed), seed.problem, seed.targetUsers,
                                "남은 재료의 레시피만 보여주는 앱", ExplorationBreadth.AS_IS)
    assert decision == "OUT_OF_SCOPE"


def test_55_explicit_plan_thesis_not_metadata_is_used_for_scope_validation():
    _, _, analysis, plans, _, _ = staged(breadth=ExplorationBreadth.REFINE)
    updates = {"problemFocus": "원 문제와 표면상 다른 표현",
               "targetSegment": "새로운 하위 대상 표현",
               "solutionThesis": "별도 해결 방식"}
    decision, _ = assess_plan_content(plans[0].model_copy(update=updates), analysis)
    assert decision == "AMBIGUOUS"


def test_56_locked_commercial_or_channel_conflict_is_rejected():
    _, _, analysis, plans, _, _ = staged(fixture("food_partial_lock"))
    conflict = plans[0].model_copy(update={"commercialApproach": "완전 무료 광고형",
                                          "customerInteraction": "오프라인 전용 방문 접점"})
    decision, reasons = assess_plan_content(conflict, analysis)
    assert decision == "FAIL" and any("LOCK" in reason for reason in reasons)


def test_57_candidate_descriptor_is_derived_from_candidate_not_plan_identity():
    _, _, _, _, validated, candidates = staged()
    assert candidates[0].descriptor is not validated.acceptedPlans[0].descriptor
    assert candidates[0].descriptor == derive_candidate_descriptor(candidates[0].candidate)


def test_58_changing_actual_candidate_mechanics_changes_descriptor():
    candidate = staged()[-1][0].candidate
    changed = candidate.model_copy(update={"solutionMechanism": "광고 배너만 제공",
                                           "featureSet": ["광고 배너"]})
    assert derive_candidate_descriptor(candidate) != derive_candidate_descriptor(changed)


def test_59_candidate_pair_result_can_differ_from_plan_pair_result():
    engine, _, _, plans, _, candidates = staged()
    clone = candidates[0].model_copy(update={"candidateId": "CLONE", "planId": plans[1].planId,
        "lineageId": "LX", "candidate": candidates[0].candidate.model_copy(update={"conceptName": "복제 사업"})})
    assert engine.compare_plans(plans[0], plans[1]).decision == "VARIANT"
    assert engine.compare_candidates(candidates[0], clone).decision == "DUPLICATE"


def test_60_requested_seven_returned_five_reports_reserve_shortfall():
    payload = fixture(); payload["fixtureName"] = "plan_pool_shortfall"
    engine, *_ = staged(payload)
    status = engine._last_plan_pool_status
    assert (status.requestedPoolSize, status.returnedPoolSize, status.status) == (7, 5, "RESERVE_SHORTFALL")


def test_61_no_reserve_legal_replan_invokes_targeted_replacement_generation():
    class CountingProvider(MockPortfolioProvider):
        replacements = 0

        async def replacement_plans(self, *args, **kwargs):
            self.replacements += 1
            return await super().replacement_plans(*args, **kwargs)

    provider = CountingProvider()
    gateway = ProviderGateway(provider=provider)
    payload = fixture(); payload["fixtureName"] = "no_reserve_legal_replan"
    result = run(ConceptPortfolioEngine(gateway=gateway).run_full(payload))
    assert provider.replacements == 1
    assert any("REPLAN" in item.candidateId for item in result.concepts)


def test_62_runtime_legal_schema_only_permits_actual_refs():
    schema = legal_service._runtime_provider_schema([0, 2])
    assert schema["properties"]["evidenceReferenceIndexes"]["items"]["enum"] == [0, 2]


def test_63_nested_finding_refs_are_constrained_and_nonempty():
    schema = legal_service._runtime_provider_schema([0, 1, 2])
    refs = schema["$defs"]["EvidenceBackedFinding"]["properties"]["evidenceReferenceIndexes"]
    assert refs["items"]["enum"] == [0, 1, 2] and refs["minItems"] == 1


def test_64_evidence_binding_diagnostic_targets_invalid_reference():
    diagnostic = legal_service._binding_diagnostic(legal_provider(1), [evidence(0)])
    assert diagnostic["invalidIndexes"] == [1] and diagnostic["findingType"] == "requiredControls"


def test_65_citation_only_repair_preserves_judgment():
    before, after = legal_provider(1), legal_provider(0)
    assert legal_service._judgment_without_references(before) == legal_service._judgment_without_references(after)


def test_66_citation_repair_cannot_introduce_new_finding_text():
    before, mutated = legal_provider(1), legal_provider(0, "새로운 법률 판단")
    assert legal_service._judgment_without_references(before) != legal_service._judgment_without_references(mutated)


def test_67_locked_target_region_enters_external_fact_context():
    payload = fixture("food_partial_lock")
    payload["targetRegion"] = {"value": "대한민국", "decisionState": "LOCKED", "source": "USER_CONFIRMED"}
    engine, seed, _, _, _, candidates = staged(payload)
    facts = CurrentLegalAdapter().task_input(candidates[0].candidate, seed)["externalFactContext"]["facts"]
    region = seed.by_key()["targetRegion"]
    assert region.decisionState == "LOCKED"
    assert facts == [{"factKey": "fixedJurisdiction", "value": region.value,
                      "source": "USER_INPUT", "authority": "LOCKED"}]


def test_68_candidate_scoped_needs_input_yields_ready_limited_four():
    payload = fixture(); payload["fixtureName"] = "candidate_needs_input"
    result = run(ConceptPortfolioEngine().run_full(payload))
    assert result.runStatus.value == "READY_LIMITED" and result.producedConceptCount == 4
    assert result.unresolvedCandidates[0]["scope"] == "CANDIDATE"


def test_69_global_missing_input_yields_needs_input():
    result = run(ConceptPortfolioEngine().run_full(fixture("lock_legal_conflict")))
    assert result.runStatus.value == "NEEDS_INPUT"
    assert result.requiredInputs[0]["scope"] == "GLOBAL"


@pytest.mark.parametrize(("candidate_id", "lineage_id"), [("C1-R1", "LR"), ("C1-REPLAN", "LX")])
def test_70_71_recovery_candidate_is_compared_against_final_context(candidate_id, lineage_id):
    engine, seed, _, _, validated, candidates = staged()
    base = candidates[0]
    changed_candidate = base.candidate.model_copy(update={"price": "광고 기반 무료 요금"})
    changed = base.model_copy(update={"candidateId": candidate_id, "lineageId": lineage_id,
                                      "candidate": changed_candidate,
                                      "descriptor": derive_candidate_descriptor(changed_candidate)})
    _, reports = run(engine.validate_candidates(seed, validated.acceptedPlans, [changed],
                                                comparison_context=[base]))
    assert reports[0].candidateId == candidate_id and not reports[0].accepted


def test_72_replay_hash_includes_prompt_version():
    payload = {"value": 1}
    assert ProviderGateway.request_hash("PLAN_POOL", payload, prompt_version="v1") != \
           ProviderGateway.request_hash("PLAN_POOL", payload, prompt_version="v2")


def test_73_changed_prompt_version_causes_replay_miss(tmp_path):
    payload = {"value": 1}
    old_hash = ProviderGateway.request_hash("PLAN_POOL", payload, prompt_version="old")
    (tmp_path / f"{old_hash}.json").write_text(json.dumps({"canonicalRequestHash": old_hash}), encoding="utf-8")
    gateway = ProviderGateway(ProviderMode.REPLAY, recordings_dir=tmp_path)
    with pytest.raises(ReplayMiss):
        run(gateway.call("PLANNING", "PLAN_POOL", payload, lambda: None, prompt_version="new"))


def test_74_idea_brief_context_replay_works_without_external_operation(tmp_path):
    recording_gateway = ProviderGateway(recordings_dir=tmp_path, record_mock_fixtures=True)
    first_engine = ConceptPortfolioEngine(gateway=recording_gateway)
    first_seed = first_engine.seed_adapter.adapt(fixture())
    expected = run(first_engine.derive_idea_brief(first_seed))
    replay_gateway = ProviderGateway(ProviderMode.REPLAY, recordings_dir=tmp_path)
    replay_engine = ConceptPortfolioEngine(ProviderMode.REPLAY, gateway=replay_gateway)
    replay_seed = replay_engine.seed_adapter.adapt(fixture())
    actual = run(replay_engine.derive_idea_brief(replay_seed))
    assert actual == expected and replay_gateway.usage.topLevelExternalOperations == 0


def test_75_run_full_default_does_not_auto_confirm():
    assert inspect.signature(ConceptPortfolioEngine.run_full).parameters["auto_confirm_hypotheses"].default is False
    result = run(ConceptPortfolioEngine().run_full(fixture()))
    assert result.handoff is None and result.downstreamReadiness == "PENDING_HYPOTHESIS_CONFIRMATION"


def test_76_manual_confirm_all_accepts_all_seven():
    candidate = staged()[-1][0]
    engine = ConceptPortfolioEngine()
    confirmed = engine.confirm_hypotheses(
        engine.build_or_load_current_hypothesis_contract(candidate), confirm_all_proposed=True)
    assert len(confirmed) == 7 and all(item.accepted for item in confirmed)


def test_77_edited_legal_sensitive_hypothesis_runs_delta_legal():
    engine, seed, _, _, validated, candidates = staged()
    accepted, _ = run(engine.validate_candidates(seed, validated.acceptedPlans, candidates))
    hypotheses = engine.confirm_hypotheses(
        engine.build_or_load_current_hypothesis_contract(accepted[0]),
        {"PRICE": "월 17,900원"}, confirm_all_proposed=True)
    delta = run(engine.review_delta_legal(seed, accepted[0], hypotheses))
    reviewed = engine.mark_delta_legal_reviewed(hypotheses, delta)
    price = next(item for item in reviewed if item.hypothesisType == "PRICE")
    assert delta.approved and price.legalReviewStatus == "IMPLEMENTABLE_WITH_CONTROLS"


def test_78_delta_legal_cannot_be_marked_passed_without_result_object():
    candidate = staged()[-1][0]
    engine = ConceptPortfolioEngine()
    hypotheses = engine.build_or_load_current_hypothesis_contract(candidate)
    with pytest.raises(ValueError):
        engine.mark_delta_legal_reviewed(hypotheses, {"PRICE"})


def test_79_final_live_like_mock_pipeline_is_contract_complete():
    result = run(ConceptPortfolioEngine().run_full(fixture(), auto_confirm_hypotheses=True))
    assert result.handoff.contractStatus == "CONTRACT_PASS"
    assert result.handoff.marketAnalysisSeedSnapshot["aiInterpretation"]
    assert len(result.handoff.marketAnalysisSeedSnapshot["finalHypotheses"]) == 7
    assert all(not candidate_language_failures(item.candidate) for item in result.concepts)
    assert all(item.route == LegalRoute.ACCEPT for item in result.legalSummaries)
