"""Concept Portfolio Engine V2의 canonical Python orchestration."""

from __future__ import annotations

import re
import time
import uuid
from dataclasses import dataclass, field
from typing import Any

from pydantic import ValidationError

from app.legal.registry import RegistryError
from app.providers import ProviderFailure

from .adapters import CurrentDownstreamAdapter, CurrentIdeaBriefAdapter, CurrentSafetyAdapter, HYPOTHESIS_FIELDS
from .models import (
    CandidateEnvelope, CandidateValidation, CanonicalSeed, ConceptPortfolioResult,
    DesignSpaceAnalysis, DiversityAssessment, DownstreamHandoff, ExplorationBreadth, FailureCode,
    HypothesisDecision, LegalPrecheck, LegalReview, LegalRoute, PlanValidationResult,
    PortfolioPlan, PortfolioStatus, ProviderMode, RejectedPlan, RunStage, RunSummary, TraceEvent,
)
from .providers import ProviderGateway, ReplayMiss


OPEN_DIMENSIONS = [
    "solutionMechanism", "valueDelivery", "operatingModel", "supplyStructure", "partnerModel",
    "transactionFlow", "fulfillment", "platformRole", "commercialFlow", "dataDependency",
    "physicalDependency",
]
OPTIONAL_DIMENSION_MAP = {
    "revenueModel": "commercialFlow", "price": "commercialFlow", "channels": "fulfillment",
    "targetRegion": "supplyStructure", "budgetConstraint": "operatingModel",
    "teamConstraint": "operatingModel", "timelineConstraint": "operatingModel",
    "otherConstraint": "physicalDependency", "differentiators": "valueDelivery",
}


def _norm(value: Any) -> str:
    return re.sub(r"[^0-9a-z가-힣]+", "", str(value).casefold())


def _plan_mechanics(plan: PortfolioPlan) -> dict[str, str]:
    return {"mechanism": _norm(plan.coreMechanism), "operation": _norm(plan.operatingApproach),
            "partner": _norm(plan.partnerApproach), "transaction": _norm(plan.transactionApproach),
            "commercial": _norm(plan.commercialApproach), "fulfillment": _norm(plan.fulfillmentApproach)}


def _candidate_mechanics(value: CandidateEnvelope) -> dict[str, str]:
    candidate = value.candidate
    return {"mechanism": _norm(candidate.solutionMechanism), "operation": _norm(candidate.operatingModel),
            "partner": _norm(candidate.partnerModel), "transaction": _norm(candidate.transactionFlow),
            "commercial": _norm(candidate.revenueModel), "fulfillment": _norm(candidate.physicalActivities),
            "platform": _norm(candidate.platformRole), "actors": _norm(candidate.actorRoles)}


@dataclass
class _RunContext:
    run_id: str
    mode: ProviderMode
    stage: RunStage = RunStage.CREATED
    trace: list[TraceEvent] = field(default_factory=list)
    started: float = field(default_factory=time.perf_counter)


class ConceptPortfolioEngine:
    """노트북과 향후 production task가 함께 import할 공개 엔진."""

    def __init__(self, mode: ProviderMode | str = ProviderMode.MOCK, *, gateway: ProviderGateway | None = None,
                 max_replans: int = 1, max_redesigns: int = 1):
        self.mode = ProviderMode(mode)
        self.gateway = gateway or ProviderGateway(self.mode)
        self.max_replans = max_replans
        self.max_redesigns = max_redesigns
        self.seed_adapter = CurrentIdeaBriefAdapter()
        self.safety_adapter = CurrentSafetyAdapter()
        self.downstream_adapter = CurrentDownstreamAdapter()
        self._context = _RunContext(str(uuid.uuid4()), self.mode)
        self._last_plan_pool: list[PortfolioPlan] = []
        self._last_design: DesignSpaceAnalysis | None = None

    @property
    def trace(self) -> list[TraceEvent]:
        return list(self._context.trace)

    def _reset(self):
        self._context = _RunContext(str(uuid.uuid4()), self.mode)
        self._last_plan_pool = []
        self._last_design = None
        self.gateway.usage = self.gateway.usage.model_copy(update={
            "totalProviderCalls": 0, "callsByStage": {}, "retries": 0, "durationMs": 0,
            "modeCounts": {self.mode.value: 0}, "tokenUsage": None, "reportedCost": None})
        self._event(RunStage.CREATED, "CREATED", "RUNNING", "V2 Lab 실행을 생성했습니다.")

    def _event(self, stage: RunStage, action: str, status: str, summary: str, *, entity_id: str | None = None,
               parent_id: str | None = None, decision: str | None = None,
               reason: FailureCode | None = None, provider_call: bool = False):
        self._context.stage = stage
        self._context.trace.append(TraceEvent(
            runId=self._context.run_id, stage=stage, action=action, entityId=entity_id, parentId=parent_id,
            status=status, providerMode=self.mode,
            providerCallNumber=self.gateway.usage.totalProviderCalls if provider_call else None,
            safeSummary=summary, decision=decision, reasonCode=reason,
        ))

    async def check_safety(self, seed: CanonicalSeed):
        self._event(RunStage.SAFETY_CHECKING, "STARTED", "RUNNING", "현행 Idea Brief Safety 의미로 검사합니다.")
        result = await self.safety_adapter.evaluate(seed, self.mode)
        self._event(RunStage.SAFETY_CHECKING, "CHECKED", "PASS" if result.passed else "BLOCKED",
                    result.userFacingReason, decision=result.decision)
        return result

    async def analyze_seed(self, payload: dict[str, Any] | CanonicalSeed,
                           exploration_override: ExplorationBreadth | str | None = None) -> DesignSpaceAnalysis:
        seed = payload if isinstance(payload, CanonicalSeed) else self.seed_adapter.adapt(payload)
        self._event(RunStage.SEED_ANALYZING, "ANALYZED", "PASS",
                    f"필수 3개와 LOCK {_locked_count(seed)}개를 분류했습니다.", entity_id=seed.ideaBriefSnapshotId)
        locked = {item.fieldKey: item.value for item in seed.fields
                  if item.decisionState == "LOCKED" and item.value.strip()}
        optional_locked = {key: value for key, value in locked.items()
                           if key not in {"ideaOverview", "problem", "targetUsers"}}
        breadth = ExplorationBreadth(exploration_override) if exploration_override else self._breadth(seed, optional_locked)
        constrained = sorted({OPTIONAL_DIMENSION_MAP[key] for key in optional_locked if key in OPTIONAL_DIMENSION_MAP})
        open_dimensions = [item for item in OPEN_DIMENSIONS if item not in constrained]
        capacity = 5 if len(optional_locked) <= 2 else 3 if len(optional_locked) <= 5 else 2
        if not open_dimensions:
            capacity = 0
        analysis = DesignSpaceAnalysis(
            opportunityAnchors={"problem": seed.problem, "targetUsers": seed.targetUsers},
            semanticAnchors={"ideaOverview": seed.ideaOverview, "problem": seed.problem,
                             "targetUsers": seed.targetUsers}, hardLocks=locked,
            openDimensions=open_dimensions, constrainedDimensions=constrained,
            explorationBreadth=breadth, diversityCapacity=capacity, suggestedMaxConcepts=capacity,
            rationaleSummary=f"선택 입력 LOCK {len(optional_locked)}개로 {len(open_dimensions)}개 설계 차원이 열려 있습니다.",
        )
        self._last_design = analysis
        self._event(RunStage.SEED_ANALYZING, "DESIGN_SPACE_READY", "PASS",
                    f"Open={len(open_dimensions)} Constrained={len(constrained)} Breadth={breadth.value}")
        return analysis

    @staticmethod
    def _breadth(seed: CanonicalSeed, optional_locked: dict[str, str]) -> ExplorationBreadth:
        if not optional_locked:
            return ExplorationBreadth.EXPLORE
        concrete = len(_norm(seed.ideaOverview)) >= 20 and any(marker in seed.ideaOverview for marker in
                                                               ("방식", "연결", "자동", "구독", "플랫폼", "서비스"))
        commercial = bool({"revenueModel", "price"} & optional_locked.keys())
        channel_or_operation = bool({"channels", "targetRegion", "budgetConstraint", "teamConstraint",
                                     "timelineConstraint", "otherConstraint"} & optional_locked.keys())
        return ExplorationBreadth.AS_IS if concrete and len(optional_locked) >= 3 and commercial and channel_or_operation \
            else ExplorationBreadth.REFINE

    async def plan_portfolio(self, seed: CanonicalSeed, analysis: DesignSpaceAnalysis,
                             max_concepts: int = 5) -> list[PortfolioPlan]:
        if not 1 <= max_concepts <= 5:
            raise ValueError("max_concepts는 1~5여야 합니다")
        self._event(RunStage.PLANNING, "STARTED", "RUNNING", f"최대 {max_concepts}개 동적 plan을 요청합니다.")
        pool_size = min(8, max_concepts + 2)
        plans = await self.gateway.plan_pool(seed, analysis, pool_size)
        self._last_plan_pool = plans
        self._event(RunStage.PLANNING, "GENERATED", "PASS", f"Plan pool={len(plans)}",
                    provider_call=True)
        return plans

    def compare_plans(self, left: PortfolioPlan, right: PortfolioPlan) -> DiversityAssessment:
        a, b = _plan_mechanics(left), _plan_mechanics(right)
        overlap = [key for key in a if a[key] == b[key]]
        differences = [key for key in a if a[key] != b[key]]
        decision = "DUPLICATE" if not differences else "BORDERLINE" if len(overlap) >= 4 else "DISTINCT"
        return DiversityAssessment(entityA=left.planId, entityB=right.planId, decision=decision,
                                   overlap=overlap, materialDifferences=differences,
                                   whyDistinct=("사업 mechanics가 동일합니다." if decision == "DUPLICATE"
                                                else f"{', '.join(differences)}가 다릅니다."))

    async def validate_plans(self, plans: list[PortfolioPlan], analysis: DesignSpaceAnalysis,
                             max_concepts: int = 5) -> PlanValidationResult:
        self._event(RunStage.PLAN_VALIDATING, "STARTED", "RUNNING", "Lock·Anchor·Mechanics distinctness를 검사합니다.")
        accepted: list[PortfolioPlan] = []
        rejected: list[RejectedPlan] = []
        comparisons: list[DiversityAssessment] = []
        for plan in plans:
            if any(plan.preservedLocks.get(key) != value for key, value in analysis.hardLocks.items()):
                rejected.append(RejectedPlan(planId=plan.planId, reasonCode=FailureCode.LOCK_VIOLATION,
                                             safeSummary="hard lock을 보존하지 못했습니다."))
                continue
            if any(plan.preservedAnchors.get(key) != value for key, value in analysis.semanticAnchors.items()):
                rejected.append(RejectedPlan(planId=plan.planId, reasonCode=FailureCode.ANCHOR_DRIFT,
                                             safeSummary="semantic anchor가 변했습니다."))
                continue
            duplicate = None
            for previous in accepted:
                comparison = self.compare_plans(previous, plan)
                comparisons.append(comparison)
                if comparison.decision == "DUPLICATE":
                    duplicate = previous
                    break
            if duplicate:
                rejected.append(RejectedPlan(planId=plan.planId, reasonCode=FailureCode.PLAN_DUPLICATE,
                                             safeSummary="business mechanics가 기존 plan과 같습니다.",
                                             conflictPlanId=duplicate.planId))
                self._event(RunStage.PLAN_VALIDATING, "REJECTED", "REJECTED", "중복 plan을 제거했습니다.",
                            entity_id=plan.planId, parent_id=duplicate.planId, reason=FailureCode.PLAN_DUPLICATE)
            elif len(accepted) < min(max_concepts, analysis.suggestedMaxConcepts):
                accepted.append(plan)
            else:
                rejected.append(RejectedPlan(
                    planId=plan.planId, reasonCode=FailureCode.VARIATION_SPACE_LIMITED,
                    safeSummary="요청 최대치 또는 분석된 다양성 수용량 밖의 plan입니다."))
        self._event(RunStage.PLAN_VALIDATING, "SELECTED", "PASS", f"Plans={len(accepted)} Rejected={len(rejected)}")
        return PlanValidationResult(acceptedPlans=accepted, rejectedPlans=rejected, diversity=comparisons)

    async def expand_plans(self, seed: CanonicalSeed, plans: list[PortfolioPlan]) -> list[CandidateEnvelope]:
        self._event(RunStage.EXPANDING, "STARTED", "RUNNING", f"통과 plan {len(plans)}개를 확장합니다.")
        result = []
        for index, plan in enumerate(plans, 1):
            candidate = await self.gateway.expand(seed, plan, index)
            envelope = CandidateEnvelope(candidateId=f"C{index}", planId=plan.planId,
                                         lineageId=f"L{index}", candidate=candidate)
            result.append(envelope)
            self._event(RunStage.EXPANDING, "EXPANDED", "PASS", f"{plan.planId} → {envelope.candidateId}",
                        entity_id=envelope.candidateId, parent_id=plan.planId, provider_call=True)
        return result

    def compare_candidates(self, left: CandidateEnvelope, right: CandidateEnvelope) -> DiversityAssessment:
        a, b = _candidate_mechanics(left), _candidate_mechanics(right)
        overlap = [key for key in a if a[key] == b[key]]
        differences = [key for key in a if a[key] != b[key]]
        decision = "DUPLICATE" if not differences else "BORDERLINE" if len(overlap) >= 6 else "DISTINCT"
        return DiversityAssessment(entityA=left.candidateId, entityB=right.candidateId, decision=decision,
                                   overlap=overlap, materialDifferences=differences,
                                   whyDistinct=("이름과 문구를 제외한 사업 구조가 같습니다." if decision == "DUPLICATE"
                                                else f"{', '.join(differences)}가 실질적으로 다릅니다."))

    async def validate_candidates(self, seed: CanonicalSeed, plans: list[PortfolioPlan],
                                  candidates: list[CandidateEnvelope]) -> tuple[list[CandidateEnvelope], list[CandidateValidation]]:
        self._event(RunStage.CANDIDATE_VALIDATING, "STARTED", "RUNNING", "Candidate 검사를 분리 수행합니다.")
        plan_by_id = {item.planId: item for item in plans}
        accepted: list[CandidateEnvelope] = []
        reports = []
        locks = {item.fieldKey: item.value for item in seed.fields if item.decisionState == "LOCKED" and item.value.strip()}
        for envelope in candidates:
            candidate, plan = envelope.candidate, plan_by_id[envelope.planId]
            lock_ok = all(not hasattr(candidate, key) or str(getattr(candidate, key)) == value
                          for key, value in locks.items())
            anchor_ok = candidate.targetUsers == seed.targetUsers and candidate.problemScenario == seed.problem
            fidelity = (_norm(plan.coreMechanism) in _norm(candidate.solutionMechanism)
                        and _norm(plan.operatingApproach) == _norm(candidate.operatingModel))
            duplicate = next((item for item in accepted
                              if self.compare_candidates(item, envelope).decision == "DUPLICATE"), None)
            reasons = []
            if not lock_ok: reasons.append(FailureCode.LOCK_VIOLATION)
            if not anchor_ok: reasons.append(FailureCode.ANCHOR_DRIFT)
            if not fidelity: reasons.append(FailureCode.PLAN_FIDELITY_FAILED)
            if duplicate: reasons.append(FailureCode.CANDIDATE_DUPLICATE)
            passed = not reasons
            if passed: accepted.append(envelope)
            reports.append(CandidateValidation(candidateId=envelope.candidateId, schemaValid=True,
                                               hardLockPreserved=lock_ok, semanticAnchorPreserved=anchor_ok,
                                               planFidelity=fidelity, accepted=passed, reasonCodes=reasons,
                                               safeSummary="검사 통과" if passed else ", ".join(item.value for item in reasons)))
            self._event(RunStage.CANDIDATE_VALIDATING, "VALIDATED", "PASS" if passed else "REJECTED",
                        reports[-1].safeSummary, entity_id=envelope.candidateId,
                        reason=reasons[0] if reasons else None)
        return accepted, reports

    def legal_precheck(self, envelope: CandidateEnvelope) -> LegalPrecheck:
        candidate = envelope.candidate
        return LegalPrecheck(candidateId=envelope.candidateId,
            directSeller="판매" in candidate.sellerRole, intermediary="중개" in candidate.intermediaryRole,
            regulatedPhysicalActivity=bool(candidate.physicalActivities),
            personalDataDependency=bool(candidate.personalDataUsage),
            qualificationDependency=bool(candidate.qualificationRequirements),
            riskHints=[item for item, active in (("물리 활동", bool(candidate.physicalActivities)),
                                                ("개인정보", bool(candidate.personalDataUsage)),
                                                ("자격 의존", bool(candidate.qualificationRequirements))) if active])

    async def review_legal(self, seed: CanonicalSeed, candidates: list[CandidateEnvelope]) -> list[LegalReview]:
        self._event(RunStage.LEGAL_REVIEWING, "STARTED", "RUNNING", "공식 Legal contract route를 실행합니다.")
        results = []
        for envelope in candidates:
            review = await self.gateway.review_legal(envelope.candidateId, envelope.candidate, seed.fixtureName)
            results.append(review)
            self._event(RunStage.LEGAL_REVIEWING, "REVIEWED", review.route.value, review.safeSummary,
                        entity_id=envelope.candidateId, decision=review.route.value, provider_call=True)
        return results

    async def resolve_legal(self, seed: CanonicalSeed, plans: list[PortfolioPlan], candidates: list[CandidateEnvelope],
                            reviews: list[LegalReview]) -> tuple[list[CandidateEnvelope], list[LegalReview], list[dict[str, Any]], int, int]:
        self._event(RunStage.LEGAL_RECOVERING, "STARTED", "RUNNING", "Legal route별 복구를 수행합니다.")
        plan_by_id = {item.planId: item for item in plans}
        review_by_id = {item.candidateId: item for item in reviews}
        final: list[CandidateEnvelope] = []
        all_reviews = list(reviews)
        required_inputs: list[dict[str, Any]] = []
        redesigned = replanned = 0
        used_plan_signatures = {_norm(item.coreMechanism) for item in plans}
        for envelope in candidates:
            review = review_by_id[envelope.candidateId]
            if review.route == LegalRoute.ACCEPT:
                final.append(envelope)
                continue
            if review.route == LegalRoute.NEEDS_INPUT:
                required_inputs.append({key: getattr(review, key) for key in
                    ("conflictingLock", "currentValue", "requiredLegalChange", "reason", "possibleUserAction")})
                continue
            if review.route == LegalRoute.REDESIGN_WITHIN_LINEAGE and redesigned < self.max_redesigns:
                child_id = f"{envelope.candidateId}-R{envelope.redesignRound + 1}"
                child_value = await self.gateway.redesign(seed, plan_by_id[envelope.planId], envelope.candidate,
                                                          review.redesignRequirements, envelope.candidate.candidateIndex)
                child = CandidateEnvelope(candidateId=child_id, planId=envelope.planId, lineageId=envelope.lineageId,
                                          parentCandidateId=envelope.candidateId,
                                          redesignRound=envelope.redesignRound + 1, candidate=child_value)
                identity_ok = (child.candidate.targetUsers == envelope.candidate.targetUsers
                               and child.candidate.problemScenario == envelope.candidate.problemScenario
                               and child.candidate.conceptName == envelope.candidate.conceptName)
                duplicate_other = next((item for item in final
                                       if self.compare_candidates(item, child).decision == "DUPLICATE"), None)
                if not identity_ok:
                    all_reviews.append(LegalReview(candidateId=child_id, route=LegalRoute.SYSTEM_FAILURE,
                        sourceStatus="VALIDATION", safeSummary="Redesign이 parent identity를 벗어났습니다."))
                elif duplicate_other:
                    all_reviews.append(LegalReview(candidateId=child_id, route=LegalRoute.REPLAN_REQUIRED,
                        sourceStatus="VALIDATION", safeSummary="Redesign이 다른 portfolio concept과 중복됩니다."))
                else:
                    child_review = await self.gateway.review_legal(child_id, child.candidate, seed.fixtureName)
                    all_reviews.append(child_review)
                    if child_review.route == LegalRoute.ACCEPT:
                        final.append(child)
                        redesigned += 1
                        self._event(RunStage.LEGAL_RECOVERING, "REDESIGNED", "PASS", "같은 lineage에서 법률 mechanics를 보완했습니다.",
                                    entity_id=child_id, parent_id=envelope.candidateId,
                                    reason=FailureCode.LEGAL_REDESIGN_REQUIRED, provider_call=True)
                continue
            if review.route == LegalRoute.REPLAN_REQUIRED and replanned < self.max_replans:
                replacement = next((item for item in self._last_plan_pool
                                    if _norm(item.coreMechanism) not in used_plan_signatures), None)
                if replacement:
                    index = min(5, len(final) + 1)
                    value = await self.gateway.expand(seed, replacement, index)
                    child = CandidateEnvelope(candidateId=f"{envelope.candidateId}-REPLAN", planId=replacement.planId,
                                              lineageId=f"L-REPLAN-{replanned + 1}", candidate=value)
                    child_review = await self.gateway.review_legal(child.candidateId, child.candidate, seed.fixtureName)
                    all_reviews.append(child_review)
                    if child_review.route == LegalRoute.ACCEPT and not any(
                            self.compare_candidates(item, child).decision == "DUPLICATE" for item in final):
                        final.append(child); replanned += 1
                        self._event(RunStage.LEGAL_RECOVERING, "REPLANNED", "PASS", "실패 plan을 다른 mechanics plan으로 교체했습니다.",
                                    entity_id=child.candidateId, parent_id=envelope.candidateId,
                                    reason=FailureCode.LEGAL_REPLAN_REQUIRED, provider_call=True)
        return final, all_reviews, required_inputs, redesigned, replanned

    def build_or_load_current_hypothesis_contract(self, envelope: CandidateEnvelope) -> list[HypothesisDecision]:
        semantics = {item.fieldKey: item for item in envelope.candidate.valueSemantics}
        result = []
        for hypothesis_type, field in HYPOTHESIS_FIELDS.items():
            semantic = semantics[field]
            value = getattr(envelope.candidate, field)
            locked = semantic.source in {"USER_INPUT", "USER_CONFIRMED"} and semantic.authority == "LOCKED"
            result.append(HypothesisDecision(hypothesisType=hypothesis_type, proposedValue=value,
                                             finalValue=value if locked else None, source=semantic.source,
                                             decisionStatus="ACCEPTED" if locked else "PROPOSED", locked=locked))
        return result

    def confirm_hypotheses(self, hypotheses: list[HypothesisDecision],
                           edits: dict[str, Any] | None = None) -> list[HypothesisDecision]:
        edits = edits or {}
        result = []
        for item in hypotheses:
            if item.locked:
                result.append(item)
                continue
            edited = item.hypothesisType in edits
            value = edits.get(item.hypothesisType, item.proposedValue)
            result.append(item.model_copy(update={"finalValue": value,
                "decisionStatus": "USER_EDITED_ACCEPTED" if edited else "ACCEPTED",
                "source": "USER_INPUT" if edited else item.source}))
        return result

    def build_downstream_handoff(self, seed: CanonicalSeed, selected: CandidateEnvelope,
                                 hypotheses: list[HypothesisDecision], legal_reviews: list[LegalReview]) -> DownstreamHandoff:
        legal = next((item for item in reversed(legal_reviews)
                      if item.candidateId == selected.candidateId and item.route == LegalRoute.ACCEPT), None)
        if not legal:
            raise ValueError("선택 Concept의 ACCEPT Legal 결과가 없습니다")
        handoff = self.downstream_adapter.build(seed, selected.candidateId, selected.candidate, hypotheses, legal)
        self._event(RunStage.PORTFOLIO_VALIDATING, "HANDOFF_VALIDATED", handoff.compatibility,
                    f"downstream contract compatibility={handoff.compatibility}", entity_id=selected.candidateId,
                    reason=FailureCode.DOWNSTREAM_HANDOFF_INVALID if handoff.compatibility != "PASS" else None)
        return handoff

    async def run_full(self, payload: dict[str, Any] | CanonicalSeed, max_concepts: int = 5,
                       exploration_override: ExplorationBreadth | str | None = None,
        auto_confirm_hypotheses: bool = True) -> ConceptPortfolioResult:
        self._reset()
        started = time.perf_counter()
        rejected: list[RejectedPlan] = []
        legal_reviews: list[LegalReview] = []
        required_inputs: list[dict[str, Any]] = []
        concepts: list[CandidateEnvelope] = []
        selected = None
        handoff = None
        redesigned = replanned = planned = duplicates = 0
        safety_text = "FAIL"
        if not 1 <= max_concepts <= 5:
            self._event(RunStage.NEEDS_INPUT, "INPUT_REJECTED", "NEEDS_INPUT",
                        "max_concepts는 1~5여야 합니다.", reason=FailureCode.USER_INPUT_CONSTRAINED)
            return self._terminal(PortfolioStatus.NEEDS_INPUT, RunStage.NEEDS_INPUT, 5,
                                  [], [], [], [{"reason": "max_concepts는 1~5여야 합니다."}],
                                  "NOT_READY", started)
        try:
            seed = payload if isinstance(payload, CanonicalSeed) else self.seed_adapter.adapt(payload)
        except (ValueError, ValidationError) as failure:
            self._event(RunStage.NEEDS_INPUT, "INPUT_REJECTED", "NEEDS_INPUT", str(failure),
                        reason=FailureCode.USER_INPUT_CONSTRAINED)
            return self._terminal(PortfolioStatus.NEEDS_INPUT, RunStage.NEEDS_INPUT, max_concepts,
                                  [], [], [], [{"reason": str(failure)}], "NOT_READY", started)
        try:
            safety = await self.check_safety(seed)
            safety_text = "PASS" if safety.passed else "BLOCKED"
            if not safety.passed:
                return self._terminal(PortfolioStatus.NEEDS_INPUT, RunStage.NEEDS_INPUT, max_concepts, [], [], [],
                                      [{"safety": safety.userFacingReason}], "BLOCKED", started)
            analysis = await self.analyze_seed(seed, exploration_override)
            if analysis.diversityCapacity == 0:
                return self._terminal(PortfolioStatus.NEEDS_INPUT, RunStage.NEEDS_INPUT, max_concepts, [], [], [],
                                      [{"reason": "열린 business design 차원이 없습니다."}], "NOT_READY", started)
            pool = await self.plan_portfolio(seed, analysis, max_concepts)
            planned = len(pool)
            validated = await self.validate_plans(pool, analysis, max_concepts)
            rejected = validated.rejectedPlans
            duplicates = sum(item.reasonCode == FailureCode.PLAN_DUPLICATE for item in rejected)
            expanded = await self.expand_plans(seed, validated.acceptedPlans)
            candidates, _ = await self.validate_candidates(seed, validated.acceptedPlans, expanded)
            legal_reviews = await self.review_legal(seed, candidates)
            concepts, legal_reviews, required_inputs, redesigned, replanned = await self.resolve_legal(
                seed, validated.acceptedPlans, candidates, legal_reviews)
            concepts = concepts[:max_concepts]
            if required_inputs:
                status, terminal = PortfolioStatus.NEEDS_INPUT, RunStage.NEEDS_INPUT
            elif not concepts:
                status, terminal = PortfolioStatus.FAILED, RunStage.FAILED
            elif len(concepts) == max_concepts:
                status, terminal = PortfolioStatus.READY_FULL, RunStage.READY
            else:
                status, terminal = PortfolioStatus.READY_LIMITED, RunStage.READY
            if concepts and status != PortfolioStatus.NEEDS_INPUT:
                selected = concepts[0]
                hypotheses = self.build_or_load_current_hypothesis_contract(selected)
                if auto_confirm_hypotheses:
                    hypotheses = self.confirm_hypotheses(hypotheses)
                    handoff = self.build_downstream_handoff(seed, selected, hypotheses, legal_reviews)
            self._event(terminal, "COMPLETED", status.value, f"최종 Portfolio={len(concepts)}", decision=status.value)
            duration = int((time.perf_counter() - started) * 1000)
            summary = RunSummary(safety=safety_text, requestedMaximum=max_concepts, planned=planned,
                planDuplicatesRemoved=duplicates, candidatesExpanded=len(candidates),
                legalAccepted=sum(item.route == LegalRoute.ACCEPT for item in legal_reviews),
                legalRedesigned=redesigned, replanned=replanned, finalPortfolio=len(concepts),
                portfolioStatus=status, selectedConcept=selected.candidate.conceptName if selected else None,
                downstreamHandoff=handoff.compatibility if handoff else "PENDING_HYPOTHESIS_CONFIRMATION",
                providerCalls=self.gateway.usage.totalProviderCalls, totalDurationMs=duration)
            return ConceptPortfolioResult(runId=self._context.run_id, runStatus=status, runtimeStage=terminal,
                requestedMaxConcepts=max_concepts, producedConceptCount=len(concepts), concepts=concepts,
                rejectedPlans=rejected, legalSummaries=legal_reviews, requiredInputs=required_inputs,
                trace=self.trace, providerUsage=self.gateway.usage,
                downstreamReadiness=handoff.compatibility if handoff else "PENDING_HYPOTHESIS_CONFIRMATION",
                selectedConceptId=selected.candidateId if selected else None, handoff=handoff, runSummary=summary)
        except (ProviderFailure, ReplayMiss) as failure:
            code = FailureCode.REPLAY_MISS if isinstance(failure, ReplayMiss) else (
                FailureCode.PROVIDER_TRANSIENT if getattr(failure, "retryable", False) else FailureCode.PROVIDER_PERMANENT)
            self._event(RunStage.FAILED, "FAILED", "FAILED", str(failure), reason=code)
            return self._terminal(PortfolioStatus.FAILED, RunStage.FAILED, max_concepts, concepts, rejected,
                                  legal_reviews, required_inputs, "INVALID", started)
        except (ValidationError, RegistryError) as failure:
            self._event(RunStage.FAILED, "FAILED", "FAILED", str(failure),
                        reason=FailureCode.RESULT_SCHEMA_INVALID)
            return self._terminal(PortfolioStatus.FAILED, RunStage.FAILED, max_concepts, concepts, rejected,
                                  legal_reviews, required_inputs, "INVALID", started)

    async def run(self, seed: dict[str, Any] | CanonicalSeed, max_concepts: int = 5) -> ConceptPortfolioResult:
        return await self.run_full(seed, max_concepts=max_concepts)

    def _terminal(self, status, stage, maximum, concepts, rejected, legal, required, readiness, started):
        if not self.trace or self.trace[-1].stage != stage:
            self._event(stage, "COMPLETED", status.value, f"종료 상태={status.value}", decision=status.value)
        return ConceptPortfolioResult(runId=self._context.run_id, runStatus=status, runtimeStage=stage,
            requestedMaxConcepts=maximum, producedConceptCount=len(concepts), concepts=concepts,
            rejectedPlans=rejected, legalSummaries=legal, requiredInputs=required, trace=self.trace,
            providerUsage=self.gateway.usage, downstreamReadiness=readiness)


def _locked_count(seed: CanonicalSeed) -> int:
    return sum(item.decisionState == "LOCKED" and bool(item.value.strip()) for item in seed.fields)
