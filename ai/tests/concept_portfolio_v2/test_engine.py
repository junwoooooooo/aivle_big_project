import asyncio
import json
from pathlib import Path

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway, ProviderMode
from app.concept_portfolio_v2.adapters import CurrentIdeaBriefAdapter
from app.concept_portfolio_v2.models import CandidateEnvelope, RunStage
from app.concept_portfolio_v2.providers import MockPortfolioProvider


FIXTURES = Path(__file__).resolve().parents[2] / "fixtures" / "concept_portfolio_v2"


def fixture(name):
    return json.loads((FIXTURES / f"{name}.json").read_text(encoding="utf-8"))


def run(value):
    return asyncio.run(value)


def test_current_seed_adapter_uses_authoritative_field_contract():
    adapter = CurrentIdeaBriefAdapter()
    seed = adapter.adapt(fixture("food_partial_lock"))
    current = adapter.current_payload(seed)
    assert {item["fieldKey"] for item in current["fields"]} >= {"ideaOverview", "problem", "targetUsers", "price"}
    assert next(item for item in current["fields"] if item["fieldKey"] == "price")["decisionState"] == "LOCKED"


def test_hard_lock_classification():
    engine = ConceptPortfolioEngine()
    seed = engine.seed_adapter.adapt(fixture("food_partial_lock"))
    engine._reset()
    analysis = run(engine.analyze_seed(seed))
    assert analysis.hardLocks["price"] == "월 19,900원"


def test_anchor_and_open_design_are_separate():
    engine = ConceptPortfolioEngine()
    seed = engine.seed_adapter.adapt(fixture("food_minimal"))
    engine._reset()
    analysis = run(engine.analyze_seed(seed))
    assert analysis.semanticAnchors["problem"] == seed.problem
    assert "solutionMechanism" in analysis.openDimensions


def test_max_concepts_never_exceeds_five():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_minimal"), max_concepts=5))
    assert result.producedConceptCount <= 5


def test_dynamic_plans_do_not_require_fixed_lens():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_minimal")))
    assert result.producedConceptCount == 5
    assert all(not hasattr(item, "diversityFocus") for item in result.concepts)


def test_plan_diversity_uses_business_mechanics():
    engine = ConceptPortfolioEngine()
    seed = engine.seed_adapter.adapt(fixture("food_minimal")); engine._reset()
    analysis = run(engine.analyze_seed(seed)); plans = run(engine.plan_portfolio(seed, analysis))
    assert engine.compare_plans(plans[0], plans[1]).decision == "DISTINCT"


def test_plan_clone_is_rejected():
    result = run(ConceptPortfolioEngine().run_full(fixture("duplicate_plans")))
    assert any(item.reasonCode.value == "PLAN_DUPLICATE" for item in result.rejectedPlans)


def test_same_problem_and_target_are_allowed_for_distinct_candidates():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_minimal")))
    engine = ConceptPortfolioEngine()
    comparison = engine.compare_candidates(result.concepts[0], result.concepts[1])
    assert result.concepts[0].candidate.problemScenario == result.concepts[1].candidate.problemScenario
    assert comparison.decision == "DISTINCT"


def test_candidate_plan_fidelity():
    engine = ConceptPortfolioEngine(); seed = engine.seed_adapter.adapt(fixture("food_minimal")); engine._reset()
    analysis = run(engine.analyze_seed(seed)); pool = run(engine.plan_portfolio(seed, analysis))
    plans = run(engine.validate_plans(pool, analysis)).acceptedPlans
    candidates = run(engine.expand_plans(seed, plans)); _, reports = run(engine.validate_candidates(seed, plans, candidates))
    assert all(item.planFidelity for item in reports)


def test_candidate_preserves_lock():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_partial_lock")))
    assert all(item.candidate.price == "월 19,900원" and item.candidate.channels == "모바일 앱" for item in result.concepts)


def test_candidate_mechanics_clone_is_duplicate():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_minimal")))
    clone = result.concepts[0].model_copy(update={"candidateId": "CLONE", "lineageId": "CLONE"})
    assert ConceptPortfolioEngine().compare_candidates(result.concepts[0], clone).decision == "DUPLICATE"


def test_legal_redesign_stays_in_same_lineage():
    result = run(ConceptPortfolioEngine().run_full(fixture("legal_redesign")))
    child = next(item for item in result.concepts if item.parentCandidateId == "C1")
    assert child.lineageId == "L1" and child.redesignRound == 1


def test_redesign_parent_is_not_self_duplicate_rejected():
    result = run(ConceptPortfolioEngine().run_full(fixture("legal_redesign")))
    assert any(item.parentCandidateId == "C1" for item in result.concepts)


def test_redesign_is_checked_against_other_portfolio_candidate():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_minimal")))
    other = result.concepts[1]
    redesign = CandidateEnvelope(candidateId="C1-R1", planId=result.concepts[0].planId,
                                 lineageId=result.concepts[0].lineageId, parentCandidateId="C1",
                                 redesignRound=1, candidate=other.candidate)
    assert ConceptPortfolioEngine().compare_candidates(other, redesign).decision == "DUPLICATE"


def test_legal_replan_uses_replacement_plan():
    result = run(ConceptPortfolioEngine().run_full(fixture("legal_replan")))
    replacement = next(item for item in result.concepts if "REPLAN" in item.candidateId)
    assert replacement.planId not in {"P1", "P2", "P3", "P4", "P5"}


def test_legal_lock_conflict_needs_input():
    result = run(ConceptPortfolioEngine().run_full(fixture("lock_legal_conflict")))
    assert result.runStatus.value == "NEEDS_INPUT"
    assert result.requiredInputs[0]["conflictingLock"] == "channels"


def test_ready_limited_portfolio():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_heavy_lock")))
    assert result.runStatus.value == "READY_LIMITED"
    assert 1 <= result.producedConceptCount < 5


def test_ready_full_portfolio():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_minimal")))
    assert result.runStatus.value == "READY_FULL" and result.producedConceptCount == 5


def test_replay_is_deterministic_and_hash_bound(tmp_path):
    seed = CurrentIdeaBriefAdapter().adapt(fixture("food_minimal"))
    setup = ConceptPortfolioEngine(); setup._reset(); design = run(setup.analyze_seed(seed)); pool_size = 7
    plans = run(MockPortfolioProvider().plan_pool(seed, design, pool_size))
    payload = {"seed": seed.model_dump(mode="json"), "design": design.model_dump(mode="json"), "poolSize": pool_size}
    request_hash = ProviderGateway.request_hash("PLAN_POOL", payload)
    record = {"canonicalRequestHash": request_hash,
              "providerResponse": [item.model_dump(mode="json") for item in plans]}
    (tmp_path / f"{request_hash}.json").write_text(json.dumps(record, ensure_ascii=False), encoding="utf-8")
    gateway = ProviderGateway(ProviderMode.REPLAY, recordings_dir=tmp_path)
    first = run(gateway.plan_pool(seed, design, pool_size)); second = run(gateway.plan_pool(seed, design, pool_size))
    assert first == second


def test_downstream_handoff_mapping():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_minimal")))
    assert result.handoff.compatibility == "PASS"
    assert any(item.downstreamField == "finalHypotheses" for item in result.handoff.fieldMapping)


def test_current_downstream_schema_compatibility():
    result = run(ConceptPortfolioEngine().run_full(fixture("food_minimal")))
    assert result.handoff.marketAnalysisSeedSnapshot["contract"] == "market-analysis-seed-snapshot-v1"
    assert result.handoff.marketingSourceSnapshot["contract"] == "marketing-source-snapshot-v1"
    assert result.handoff.marketAnalysisSeedSnapshot["legalResult"]["legalStatus"] in {
        "IMPLEMENTABLE", "IMPLEMENTABLE_WITH_CONTROLS"}
    assert len(result.handoff.marketAnalysisSeedSnapshot["finalHypotheses"]) == 7


def test_full_mock_run_exercises_real_validators():
    result = run(ConceptPortfolioEngine().run_full(fixture("duplicate_plans")))
    assert result.runStatus.value in {"READY_FULL", "READY_LIMITED"}
    assert result.providerUsage.totalProviderCalls > 0
    assert result.rejectedPlans


def test_no_nonterminal_zombie_state():
    for name in ("food_minimal", "food_heavy_lock", "legal_redesign", "legal_replan", "lock_legal_conflict"):
        result = run(ConceptPortfolioEngine().run_full(fixture(name)))
        assert result.runtimeStage in {RunStage.READY, RunStage.NEEDS_INPUT, RunStage.FAILED}
