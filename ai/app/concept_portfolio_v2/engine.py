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

from .adapters import (
    CurrentDownstreamAdapter, CurrentIdeaBriefAdapter, CurrentSafetyAdapter, HYPOTHESIS_FIELDS,
    business_fingerprint,
)
from .anchor_policy import assess_anchor, build_opportunity_anchor
from .candidate_governance import (
    DIRECT_CANDIDATE_LOCKS, candidate_result_to_draft, normalize_candidate_draft,
)
from .distinctness import deterministic_distinctness, descriptor_values
from .models import (
    CandidateEnvelope, CandidateValidation, CanonicalSeed, ConceptPortfolioResult,
    DesignSpaceAnalysis, DiversityAssessment, DownstreamHandoff, ExplorationBreadth, FailureCode,
    HypothesisDecision, LegalPrecheck, LegalReview, LegalRoute, PlanValidationResult,
    PortfolioPlan, PortfolioPlanDraft, PortfolioStatus, ProviderMode, RejectedPlan, RunStage,
    RunSummary, SchemaPreflightReport, TraceEvent,
)
from .plan_fidelity import deterministic_plan_fidelity
from .planning import normalize_plan_drafts
from .providers import ProviderGateway, ReplayMiss
from .schema_preflight import StructuredOutputSchemaCompatibilityError, v2_schema_preflight_report


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
        self._last_plan_drafts: list[PortfolioPlanDraft] = []
        self._last_plan_pool: list[PortfolioPlan] = []
        self._last_design: DesignSpaceAnalysis | None = None

    @property
    def trace(self) -> list[TraceEvent]:
        return list(self._context.trace)

    def _reset(self):
        self._context = _RunContext(str(uuid.uuid4()), self.mode)
        self._last_plan_drafts = []
        self._last_plan_pool = []
        self._last_design = None
        self.gateway.usage = self.gateway.usage.model_copy(update={
            "logicalOperations": 0, "externalProviderCalls": 0, "totalProviderCalls": 0,
            "callsByStage": {}, "externalCallsByStage": {}, "retries": 0, "durationMs": 0,
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
        self.gateway.usage.logicalOperations += 1
        self.gateway.usage.callsByStage["SAFETY_CHECKING"] = \
            self.gateway.usage.callsByStage.get("SAFETY_CHECKING", 0) + 1
        self.gateway.usage.modeCounts[self.mode.value] = self.gateway.usage.modeCounts.get(self.mode.value, 0) + 1
        if self.mode == ProviderMode.LIVE:
            self.gateway.note_external_call("SAFETY_CHECKING")
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
        capacity = 5 if len(optional_locked) <= 2 else 4 if len(optional_locked) <= 5 else 3
        if not open_dimensions:
            capacity = 0
        analysis = DesignSpaceAnalysis(
            opportunityAnchor=build_opportunity_anchor(seed),
            semanticAnchors={"ideaOverview": seed.ideaOverview, "problem": seed.problem,
                             "targetUsers": seed.targetUsers}, sourceLocks=locked,
            explicitBusinessLocks=optional_locked,
            openDimensions=open_dimensions, constrainedDimensions=constrained,
            explorationBreadth=breadth, diversityCapacity=capacity, suggestedMaxConcepts=capacity,
            rationaleSummary=(f"선택 입력 LOCK {len(optional_locked)}개로 {len(open_dimensions)}개 설계 차원이 열려 있습니다. "
                              f"diversityCapacity={capacity}는 진단값이며 선택 hard cap이 아닙니다."),
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

    def schema_preflight_report(self) -> SchemaPreflightReport:
        return v2_schema_preflight_report()

    async def generate_plan_drafts(self, seed: CanonicalSeed, analysis: DesignSpaceAnalysis,
                                   max_concepts: int = 5) -> list[PortfolioPlanDraft]:
        if not 1 <= max_concepts <= 5:
            raise ValueError("max_concepts는 1~5여야 합니다")
        report = self.schema_preflight_report()
        if report.status != "PASS":
            failed = next(item for item in report.schemas if item.status == "FAIL")
            raise StructuredOutputSchemaCompatibilityError(failed.schemaName, failed.failures)
        self._event(RunStage.PLANNING, "STARTED", "RUNNING", f"최대 {max_concepts}개 동적 plan을 요청합니다.")
        pool_size = min(8, max_concepts + 2)
        drafts = await self.gateway.plan_pool(seed, analysis, pool_size)
        self._last_plan_drafts = drafts
        self._event(RunStage.PLANNING, "DRAFTS_GENERATED", "PASS", f"Plan draft pool={len(drafts)}",
                    provider_call=True)
        return drafts

    def normalize_plan_drafts(self, drafts: list[PortfolioPlanDraft],
                              analysis: DesignSpaceAnalysis) -> list[PortfolioPlan]:
        plans = normalize_plan_drafts(drafts, analysis)
        self._last_plan_pool = plans
        self._event(RunStage.PLANNING, "NORMALIZED", "PASS",
                    f"System metadata를 부여한 Plan={len(plans)}")
        return plans

    async def plan_portfolio(self, seed: CanonicalSeed, analysis: DesignSpaceAnalysis,
                             max_concepts: int = 5) -> list[PortfolioPlan]:
        drafts = await self.generate_plan_drafts(seed, analysis, max_concepts)
        return self.normalize_plan_drafts(drafts, analysis)

    def compare_plans(self, left: PortfolioPlan, right: PortfolioPlan) -> DiversityAssessment:
        return deterministic_distinctness(left.planId, right.planId, left.mechanics, right.mechanics)

    async def validate_plans(self, plans: list[PortfolioPlan], analysis: DesignSpaceAnalysis,
                             max_concepts: int = 5) -> PlanValidationResult:
        self._event(RunStage.PLAN_VALIDATING, "STARTED", "RUNNING", "Lock·Anchor·Mechanics distinctness를 검사합니다.")
        accepted: list[PortfolioPlan] = []
        rejected: list[RejectedPlan] = []
        comparisons: list[DiversityAssessment] = []
        for plan in plans:
            if any(plan.preservedLocks.get(key) != value for key, value in analysis.explicitBusinessLocks.items()):
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
                if comparison.decision == "AMBIGUOUS":
                    semantic = await self.gateway.judge_distinctness(
                        "PLAN", previous.model_dump(mode="json"), plan.model_dump(mode="json"))
                    comparison = comparison.model_copy(update={
                        "decision": semantic.decision,
                        "overlap": semantic.overlappingMechanics,
                        "materialDifferences": semantic.materiallyDifferentMechanics,
                        "whyDistinct": semantic.safeSummary,
                        "semanticJudgeUsed": True,
                    })
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
            elif len(accepted) < max_concepts:
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
            envelope = await self.expand_plan(seed, plan, index)
            result.append(envelope)
        return result

    async def expand_plan(self, seed: CanonicalSeed, plan: PortfolioPlan, candidate_index: int,
                          *, candidate_id: str | None = None, lineage_id: str | None = None) -> CandidateEnvelope:
        draft = await self.gateway.expand(seed, plan, candidate_index)
        strategy = self._last_design.explorationBreadth if self._last_design else ExplorationBreadth.EXPLORE
        candidate = normalize_candidate_draft(draft, seed, strategy, candidate_index)
        envelope = CandidateEnvelope(
            candidateId=candidate_id or f"C{candidate_index}", planId=plan.planId,
            lineageId=lineage_id or f"L{candidate_index}", mechanics=plan.mechanics, candidate=candidate)
        self._event(RunStage.EXPANDING, "EXPANDED", "PASS", f"{plan.planId} → {envelope.candidateId}",
                    entity_id=envelope.candidateId, parent_id=plan.planId, provider_call=True)
        return envelope

    def compare_candidates(self, left: CandidateEnvelope, right: CandidateEnvelope) -> DiversityAssessment:
        if business_fingerprint(left.candidate) == business_fingerprint(right.candidate):
            return DiversityAssessment(
                entityA=left.candidateId, entityB=right.candidateId, decision="DUPLICATE",
                overlap=list(descriptor_values(left.mechanics)), materialDifferences=[],
                whyDistinct="21-field canonical business fingerprint가 같습니다.",
                deterministicLevel="LEVEL_1", semanticJudgeUsed=False)
        return deterministic_distinctness(left.candidateId, right.candidateId,
                                          left.mechanics, right.mechanics)

    async def validate_candidates(self, seed: CanonicalSeed, plans: list[PortfolioPlan],
                                  candidates: list[CandidateEnvelope]) -> tuple[list[CandidateEnvelope], list[CandidateValidation]]:
        self._event(RunStage.CANDIDATE_VALIDATING, "STARTED", "RUNNING", "Candidate 검사를 분리 수행합니다.")
        plan_by_id = {item.planId: item for item in plans}
        accepted: list[CandidateEnvelope] = []
        reports = []
        locks = {item.fieldKey: item.value for item in seed.fields
                 if item.fieldKey in DIRECT_CANDIDATE_LOCKS
                 and item.decisionState == "LOCKED" and item.value.strip()}
        anchor = self._last_design.opportunityAnchor if self._last_design else build_opportunity_anchor(seed)
        for envelope in candidates:
            candidate, plan = envelope.candidate, plan_by_id[envelope.planId]
            lock_ok = all(not hasattr(candidate, key) or str(getattr(candidate, key)) == value
                          for key, value in locks.items())
            anchor_decision, _ = assess_anchor(anchor, candidate.problemScenario, candidate.targetUsers)
            anchor_ok = anchor_decision == "PASS"
            fidelity_decision, matched, missing = deterministic_plan_fidelity(plan, candidate)
            if fidelity_decision == "AMBIGUOUS":
                semantic_fidelity = await self.gateway.judge_fidelity(plan, candidate)
                fidelity_decision = semantic_fidelity.decision
            fidelity = fidelity_decision == "PASS"
            duplicate = None
            for previous in accepted:
                comparison = self.compare_candidates(previous, envelope)
                if comparison.decision == "AMBIGUOUS":
                    semantic = await self.gateway.judge_distinctness(
                        "CANDIDATE", previous.candidate.model_dump(mode="json"),
                        envelope.candidate.model_dump(mode="json"))
                    comparison = comparison.model_copy(update={"decision": semantic.decision,
                        "overlap": semantic.overlappingMechanics,
                        "materialDifferences": semantic.materiallyDifferentMechanics,
                        "whyDistinct": semantic.safeSummary, "semanticJudgeUsed": True})
                if comparison.decision == "DUPLICATE":
                    duplicate = previous
                    break
            reasons = []
            if not lock_ok: reasons.append(FailureCode.LOCK_VIOLATION)
            if not anchor_ok: reasons.append(FailureCode.ANCHOR_DRIFT)
            if not fidelity: reasons.append(FailureCode.PLAN_FIDELITY_FAILED)
            if duplicate: reasons.append(FailureCode.CANDIDATE_DUPLICATE)
            passed = not reasons
            if passed: accepted.append(envelope)
            reports.append(CandidateValidation(candidateId=envelope.candidateId, schemaValid=True,
                                               hardLockPreserved=lock_ok, semanticAnchorPreserved=anchor_ok,
                                               planFidelity=fidelity, anchorDecision=anchor_decision,
                                               fidelityDecision=fidelity_decision,
                                               accepted=passed, reasonCodes=reasons,
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
            review = await self.review_legal_candidate(seed, envelope)
            results.append(review)
        return results

    async def review_legal_candidate(self, seed: CanonicalSeed,
                                     envelope: CandidateEnvelope) -> LegalReview:
        review = await self.gateway.review_legal(envelope.candidateId, envelope.candidate, seed.fixtureName)
        self._event(RunStage.LEGAL_REVIEWING, "REVIEWED", review.route.value, review.safeSummary,
                    entity_id=envelope.candidateId, decision=review.route.value, provider_call=True)
        return review

    async def resolve_legal(self, seed: CanonicalSeed, plans: list[PortfolioPlan], candidates: list[CandidateEnvelope],
                            reviews: list[LegalReview]) -> tuple[list[CandidateEnvelope], list[LegalReview], list[dict[str, Any]], int, int]:
        self._event(RunStage.LEGAL_RECOVERING, "STARTED", "RUNNING", "Legal route별 복구를 수행합니다.")
        plan_by_id = {item.planId: item for item in self._last_plan_pool}
        review_by_id = {item.candidateId: item for item in reviews}
        final: list[CandidateEnvelope] = []
        all_reviews = list(reviews)
        required_inputs: list[dict[str, Any]] = []
        redesigned = replanned = 0
        redesigns_by_lineage: dict[str, int] = {}
        used_plan_ids = {item.planId for item in plans}
        for envelope in candidates:
            review = review_by_id[envelope.candidateId]
            if review.route == LegalRoute.ACCEPT:
                final.append(envelope)
                continue
            if review.route == LegalRoute.NEEDS_INPUT:
                required_inputs.append({key: getattr(review, key) for key in
                    ("conflictingLock", "currentValue", "requiredLegalChange", "reason", "possibleUserAction")})
                continue
            lineage_redesigns = redesigns_by_lineage.get(envelope.lineageId, 0)
            if review.route == LegalRoute.REDESIGN_WITHIN_LINEAGE and lineage_redesigns < self.max_redesigns:
                child_id = f"{envelope.candidateId}-R{envelope.redesignRound + 1}"
                raw_child = await self.gateway.redesign(seed, plan_by_id[envelope.planId], envelope.candidate,
                                                        review.redesignRequirements, envelope.candidate.candidateIndex)
                child_value = normalize_candidate_draft(
                    candidate_result_to_draft(raw_child), seed,
                    ExplorationBreadth(envelope.candidate.generationStrategy),
                    envelope.candidate.candidateIndex)
                child = CandidateEnvelope(candidateId=child_id, planId=envelope.planId, lineageId=envelope.lineageId,
                                          parentCandidateId=envelope.candidateId,
                                          redesignRound=envelope.redesignRound + 1,
                                          mechanics=envelope.mechanics, candidate=child_value)
                validated_children, _ = await self.validate_candidates(
                    seed, [plan_by_id[envelope.planId]], [child])
                anchor_decision, _ = assess_anchor(
                    self._last_design.opportunityAnchor, child.candidate.problemScenario,
                    child.candidate.targetUsers)
                duplicate_other = next((item for item in final
                    if self.compare_candidates(item, child).decision == "DUPLICATE"), None)
                if not validated_children or anchor_decision != "PASS":
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
                        redesigns_by_lineage[envelope.lineageId] = lineage_redesigns + 1
                        self._event(RunStage.LEGAL_RECOVERING, "REDESIGNED", "PASS", "같은 lineage에서 법률 mechanics를 보완했습니다.",
                                    entity_id=child_id, parent_id=envelope.candidateId,
                                    reason=FailureCode.LEGAL_REDESIGN_REQUIRED, provider_call=True)
                    elif child_review.route == LegalRoute.REDESIGN_WITHIN_LINEAGE:
                        all_reviews.append(LegalReview(
                            candidateId=child_id, route=LegalRoute.SYSTEM_FAILURE,
                            sourceStatus="REDESIGN_BUDGET",
                            safeSummary=(f"lineage {envelope.lineageId}의 redesign 예산 "
                                         f"{self.max_redesigns}회를 모두 사용했습니다.")))
                continue
            if review.route == LegalRoute.REPLAN_REQUIRED and replanned < self.max_replans:
                replacement = None
                for candidate_plan in self._last_plan_pool:
                    if candidate_plan.planId in used_plan_ids:
                        continue
                    comparisons = [deterministic_distinctness(
                        candidate_plan.planId, existing.planId,
                        candidate_plan.mechanics, existing.mechanics) for existing in plans]
                    if all(item.decision == "DISTINCT" for item in comparisons):
                        replacement = candidate_plan
                        break
                if replacement:
                    index = min(5, len(final) + 1)
                    plan_result = await self.validate_plans([replacement], self._last_design, max_concepts=1)
                    if not plan_result.acceptedPlans:
                        continue
                    child = await self.expand_plan(
                        seed, replacement, index, candidate_id=f"{envelope.candidateId}-REPLAN",
                        lineage_id=f"L-REPLAN-{replanned + 1}")
                    validated_children, _ = await self.validate_candidates(seed, [replacement], [child])
                    duplicate_final = any(self.compare_candidates(item, child).decision == "DUPLICATE"
                                          for item in final)
                    if not validated_children or duplicate_final:
                        continue
                    child_review = await self.gateway.review_legal(
                        child.candidateId, child.candidate, seed.fixtureName)
                    all_reviews.append(child_review)
                    if child_review.route == LegalRoute.ACCEPT:
                        final.append(child); replanned += 1
                        used_plan_ids.add(replacement.planId)
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
            legal_sensitive = item.hypothesisType in {
                "TARGET_REGION", "REVENUE_MODEL", "PRICE", "CHANNELS", "DIFFERENTIATORS"}
            delta_required = edited and value != item.proposedValue and legal_sensitive
            result.append(item.model_copy(update={"finalValue": value,
                "decisionStatus": "USER_EDITED_ACCEPTED" if edited else "ACCEPTED",
                "source": "USER_INPUT" if edited else item.source,
                "legalImpact": "DELTA_REVIEW_REQUIRED" if delta_required else item.legalImpact,
                "legalReviewStatus": "PENDING" if delta_required else item.legalReviewStatus,
                "deltaLegalRequired": delta_required}))
        return result

    def mark_delta_legal_reviewed(self, hypotheses: list[HypothesisDecision],
                                  approved_types: set[str]) -> list[HypothesisDecision]:
        return [item.model_copy(update={"legalReviewStatus": "PASSED"})
                if item.hypothesisType in approved_types and item.deltaLegalRequired else item
                for item in hypotheses]

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
                FailureCode.RESULT_SCHEMA_INVALID if getattr(failure, "code", None) == "RESULT_SCHEMA_INVALID" else
                FailureCode.PROVIDER_TRANSIENT if getattr(failure, "retryable", False) else FailureCode.PROVIDER_PERMANENT)
            self._event(RunStage.FAILED, "FAILED", "FAILED", str(failure), reason=code)
            return self._terminal(PortfolioStatus.FAILED, RunStage.FAILED, max_concepts, concepts, rejected,
                                  legal_reviews, required_inputs, "INVALID", started)
        except (ValidationError, RegistryError) as failure:
            self._event(RunStage.FAILED, "FAILED", "FAILED", str(failure),
                        reason=FailureCode.RESULT_SCHEMA_INVALID)
            return self._terminal(PortfolioStatus.FAILED, RunStage.FAILED, max_concepts, concepts, rejected,
                                  legal_reviews, required_inputs, "INVALID", started)
        except StructuredOutputSchemaCompatibilityError as failure:
            self._event(RunStage.FAILED, "SCHEMA_PREFLIGHT_FAILED", "FAILED", str(failure),
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
