from __future__ import annotations

import asyncio
import json
from types import SimpleNamespace

import pytest
from pydantic import ValidationError

from app.api.executions import TASK_TYPES
from app.concept_portfolio_v2.models import LegalRoute
from app.concept_portfolio_v2.snapshot_hash import production_compatible_snapshot_hash
from app.tasks.concept_portfolio_v2.continuation_models import (
    ConceptPortfolioContinuationInput,
)
from app.tasks.concept_portfolio_v2.continuation_service import (
    ConceptPortfolioContinuationFacade,
)
from app.tasks.concept_portfolio_v2.models import (
    PRODUCTION_RESULT_SAFETY_BYTES,
    ConceptPortfolioContinuationArtifact,
    ConceptPortfolioContinuationContext,
    ConceptPortfolioProductionInput,
    ProductionRequiredInput,
)
from app.tasks.concept_portfolio_v2.observer import ProductionObservedConceptPortfolioEngine
from app.tasks.concept_portfolio_v2.service import ConceptPortfolioProductionContractError


SEED_INPUT = ConceptPortfolioProductionInput.model_validate({
    "seed": {
        "ideaBriefSnapshotId": "brief-p4",
        "ideaOverview": "소형 매장의 예약 확인 업무를 자동화한다.",
        "problem": "반복 예약 확인 때문에 운영 시간이 낭비된다.",
        "targetUsers": "예약 서비스를 운영하는 국내 소형 매장",
        "fields": [
            {"fieldKey": "ideaOverview", "value": "소형 매장의 예약 확인 업무를 자동화한다.",
             "source": "USER_CONFIRMED", "decisionState": "LOCKED"},
            {"fieldKey": "problem", "value": "반복 예약 확인 때문에 운영 시간이 낭비된다.",
             "source": "USER_CONFIRMED", "decisionState": "LOCKED"},
            {"fieldKey": "targetUsers", "value": "예약 서비스를 운영하는 국내 소형 매장",
             "source": "USER_CONFIRMED", "decisionState": "LOCKED"},
        ],
        "interpretation": {"summary": "예약 확인 운영 자동화"},
        "fixtureName": "p4-continuation",
    },
    "maxConcepts": 5,
})


@pytest.fixture(scope="module")
def continuation_bundle():
    engine = ProductionObservedConceptPortfolioEngine()
    raw = asyncio.run(engine.run_full(SEED_INPUT.seed, max_concepts=5,
                                      auto_confirm_hypotheses=False))
    candidate = raw.concepts[0]
    plan = engine.continuation_plan(candidate.planId)
    design = engine.continuation_design()
    legal = next(item for item in raw.legalSummaries if item.candidateId == candidate.candidateId)
    required = ProductionRequiredInput(
        candidateId=candidate.candidateId,
        scope="CANDIDATE",
        unknownFacts=["실제 판매 주체"],
        reason="판매 주체 확인이 필요합니다.",
        question="실제 판매 주체는 누구인가요?",
        safeSummary="실제 판매 주체를 확인해야 합니다.",
        affectedFields=["sellerRole"],
    )
    context = ConceptPortfolioContinuationContext(
        canonicalSeedSnapshot=SEED_INPUT.seed,
        canonicalSeedHash=production_compatible_snapshot_hash(SEED_INPUT.seed),
        designSnapshot=design,
        plans=[plan],
    )
    artifact = ConceptPortfolioContinuationArtifact(
        candidateId=candidate.candidateId,
        lineageId=candidate.lineageId,
        candidateSnapshot=candidate,
        planId=candidate.planId,
        latestLegalReview=legal,
        requiredInput=required,
        affectedFields=["sellerRole"],
        parentCandidateId=candidate.parentCandidateId,
        recoverySource=candidate.recoverySource,
        canonicalHash=production_compatible_snapshot_hash(candidate),
        acceptedPortfolioConceptIds=[],
    )
    return context, artifact, legal


def continuation_input(bundle, **updates):
    context, artifact, _ = bundle
    value = {
        "contract": "concept-portfolio-v2-continuation-input-v1",
        "contractVersion": "1.0",
        "schemaVersion": "1.0",
        "inputRequestId": "input-p4",
        "continuationContext": context.model_dump(mode="json"),
        "continuationArtifact": artifact.model_dump(mode="json"),
        "confirmedFacts": {"sellerRole": "사용자 사업체가 직접 판매합니다."},
        "comparisonConcepts": [],
    }
    value.update(updates)
    return value


class FakeContinuationEngine:
    def __init__(self, context, legal, *, accept=True, design=None, max_replans=0):
        self.max_replans = max_replans
        self.context = context
        self.legal = legal
        self.accept = accept
        self.restored_design = design or context.designSnapshot
        self.trace = []
        self.validation_call = None

    async def analyze_seed(self, seed, exploration_override=None):
        self.analyze_call = (seed, exploration_override)
        return self.restored_design

    async def validate_candidates(self, seed, plans, candidates, *, comparison_context=None):
        self.validation_call = (seed, plans, candidates, comparison_context)
        report = SimpleNamespace(safeSummary="Candidate validation 제외 사유")
        return ([candidates[0]] if self.accept else []), [report]

    async def review_legal_candidate(self, seed, candidate):
        self.legal_call = (seed, candidate)
        return self.legal.model_copy(update={"candidateId": candidate.candidateId}, deep=True)


def test_strict_fact_contract_rejects_unknown_type_and_unaffected_fields(continuation_bundle):
    base = continuation_input(continuation_bundle)
    with pytest.raises(ValidationError):
        ConceptPortfolioContinuationInput.model_validate({**base, "browserArtifact": {}})
    with pytest.raises(ValidationError):
        ConceptPortfolioContinuationInput.model_validate({**base, "confirmedFacts": {"paymentFlow": "현금"}})
    with pytest.raises(ValidationError):
        ConceptPortfolioContinuationInput.model_validate({**base, "confirmedFacts": {"providerRole": "직접 제공"}})
    with pytest.raises(ValidationError):
        ConceptPortfolioContinuationInput.model_validate({**base, "confirmedFacts": {"unapproved": "value"}})


def test_restores_design_actual_plan_and_patches_only_candidate_facts(continuation_bundle):
    context, artifact, legal = continuation_bundle
    comparison = artifact.candidateSnapshot.model_copy(update={
        "candidateId": "accepted-comparison", "lineageId": "accepted-lineage"
    }, deep=True)
    request = ConceptPortfolioContinuationInput.model_validate(
        continuation_input(continuation_bundle, comparisonConcepts=[comparison.model_dump(mode="json")])
    )
    fake = FakeContinuationEngine(context, legal)

    result = asyncio.run(ConceptPortfolioContinuationFacade(engine=fake).run(request))

    assert result.outcome == "ACCEPTED"
    assert fake.analyze_call == (context.canonicalSeedSnapshot,
                                 context.designSnapshot.explorationBreadth)
    _, plans, candidates, comparisons = fake.validation_call
    assert plans == context.plans
    assert comparisons == [comparison]
    patched = candidates[0]
    assert patched.candidateId == artifact.candidateId
    assert patched.lineageId == artifact.lineageId
    assert patched.planId == artifact.planId
    assert patched.candidate.sellerRole == "사용자 사업체가 직접 판매합니다."
    semantics = {item.fieldKey: item for item in patched.candidate.valueSemantics}
    assert semantics["sellerRole"].model_dump() == {
        "fieldKey": "sellerRole", "source": "USER_INPUT",
        "authority": "LOCKED", "decision": "ACCEPTED",
    }
    assert patched.descriptor != artifact.candidateSnapshot.descriptor
    assert not hasattr(fake, "run_full_call")
    assert fake.max_replans == 0


@pytest.mark.parametrize(
    ("route", "expected"),
    [
        (LegalRoute.ACCEPT, "ACCEPTED"),
        (LegalRoute.NEEDS_INPUT, "NEEDS_INPUT"),
        (LegalRoute.REDESIGN_WITHIN_LINEAGE, "EXCLUDED"),
        (LegalRoute.SYSTEM_FAILURE, "SYSTEM_FAILURE"),
    ],
)
def test_bounded_candidate_terminal_outcomes(continuation_bundle, route, expected):
    context, _, legal = continuation_bundle
    updates = {"route": route, "safeSummary": "안전한 continuation 결과"}
    if route == LegalRoute.NEEDS_INPUT:
        updates.update({"unknownFacts": ["실제 판매 주체"], "inputScope": "CANDIDATE",
                        "evidenceDiagnostics": {"affectedFields": ["sellerRole"]}})
    if route == LegalRoute.SYSTEM_FAILURE:
        updates["evidenceDiagnostics"] = {"failureCode": "DEPENDENCY_UNAVAILABLE"}
    fake = FakeContinuationEngine(context, legal.model_copy(update=updates, deep=True))

    result = asyncio.run(ConceptPortfolioContinuationFacade(engine=fake).run(
        continuation_input(continuation_bundle)
    ))

    assert result.outcome == expected
    if expected == "NEEDS_INPUT":
        assert result.requiredInput is not None
        assert result.continuationArtifact is not None
    assert len(json.dumps(result.model_dump(mode="json"), ensure_ascii=False).encode()) \
        < PRODUCTION_RESULT_SAFETY_BYTES


def test_validation_failure_is_candidate_local_exclusion(continuation_bundle):
    context, _, legal = continuation_bundle
    result = asyncio.run(ConceptPortfolioContinuationFacade(
        engine=FakeContinuationEngine(context, legal, accept=False)
    ).run(continuation_input(continuation_bundle)))
    assert result.outcome == "EXCLUDED"
    assert result.exclusionReason == "Candidate validation 제외 사유"


def test_design_mismatch_and_nonzero_replan_budget_are_contract_errors(continuation_bundle):
    context, _, legal = continuation_bundle
    changed = context.designSnapshot.model_copy(update={"rationaleSummary": "tampered"})
    with pytest.raises(ConceptPortfolioProductionContractError):
        asyncio.run(ConceptPortfolioContinuationFacade(
            engine=FakeContinuationEngine(context, legal, design=changed)
        ).run(continuation_input(continuation_bundle)))
    with pytest.raises(ConceptPortfolioProductionContractError):
        ConceptPortfolioContinuationFacade(
            engine=FakeContinuationEngine(context, legal, max_replans=1))


def test_dispatcher_registers_continuation_task():
    assert "CONCEPT_PORTFOLIO_V2_CONTINUE" in TASK_TYPES
