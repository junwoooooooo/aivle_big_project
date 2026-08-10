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
from app.tasks.idea_brief.service import execute_idea_brief_derivation

from .adapters import (
    CurrentDownstreamAdapter, CurrentIdeaBriefAdapter, CurrentSafetyAdapter, HYPOTHESIS_FIELDS,
    business_fingerprint,
)
from .anchor_policy import assess_anchor, build_opportunity_kernel
from .candidate_governance import (
    DIRECT_CANDIDATE_LOCKS, candidate_result_to_draft, normalize_candidate_draft,
)
from .distinctness import deterministic_distinctness, descriptor_values
from .language_policy import candidate_language_failures, plan_language_failures
from .hypothesis_validation import assess_hypotheses, assess_hypothesis_value
from .legal_fact_completeness import (
    assess_legal_fact_completeness, normalized_requirements, validate_redesign_requirements,
)
from .mechanics import GenericConceptNormalizer, derive_candidate_descriptor
from .models import (
    CandidateEnvelope, CandidatePortfolioPreparation, CandidateValidation, CanonicalSeed, ConceptPortfolioResult,
    DeltaLegalResult, DesignSpaceAnalysis, DiversityAssessment, DownstreamHandoff,
    ExplorationBreadth, FailureCode, HypothesisDecision, IdeaBriefLabContext, LegalPrecheck,
    LegalCandidatePreparation, LegalReview, LegalRoute, PlanPoolStatus, PlanValidationResult, PortfolioPlan,
    PortfolioPlanDraft, PortfolioStatus, ProviderMode, RejectedPlan, RunStage, RunSummary,
    SchemaPreflightReport, TraceEvent,
)
from .plan_fidelity import deterministic_plan_fidelity
from .plan_policy import assess_plan_content
from .planning import normalize_plan_drafts
from .providers import ProviderGateway, ReplayMiss
from .schema_preflight import StructuredOutputSchemaCompatibilityError, v2_schema_preflight_report
from .snapshot_hash import production_compatible_snapshot_hash


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


def _terms(value: Any) -> set[str]:
    return {item for item in re.findall(r"[0-9a-z가-힣]+", str(value).casefold()) if len(item) >= 2}


def _text_overlap(left: Any, right: Any) -> float:
    a, b = _terms(left), _terms(right)
    return len(a & b) / max(1, min(len(a), len(b)))


_NON_FACT_VALUES = {
    "없음", "해당없음", "해당사항없음", "필요시확인", "추후확인", "검토필요",
    "필요한자격", "관련자격", "해당활동에필요한자격", "필요시필요한자격",
    "필요한경우개인정보처리", "서비스이행을위한최소정보", "필요한경우물리활동",
}


def _substantive_facts(values: list[str]) -> list[str]:
    result = []
    for value in values:
        normalized = _norm(value)
        if not normalized or normalized in _NON_FACT_VALUES:
            continue
        if normalized.startswith("필요시") and len(normalized) < 20:
            continue
        result.append(value)
    return result


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
                 max_replans: int = 1, max_redesigns: int = 1, max_planning_rounds: int = 2):
        self.mode = ProviderMode(mode)
        self.gateway = gateway or ProviderGateway(self.mode)
        self.max_replans = max_replans
        self.max_redesigns = max_redesigns
        self.max_planning_rounds = max_planning_rounds
        self.seed_adapter = CurrentIdeaBriefAdapter()
        self.safety_adapter = CurrentSafetyAdapter()
        self.downstream_adapter = CurrentDownstreamAdapter()
        self._context = _RunContext(str(uuid.uuid4()), self.mode)
        self._last_plan_drafts: list[PortfolioPlanDraft] = []
        self._last_plan_pool: list[PortfolioPlan] = []
        self._last_design: DesignSpaceAnalysis | None = None
        self._last_idea_context: IdeaBriefLabContext | None = None
        self._last_plan_pool_status: PlanPoolStatus | None = None
        self._delta_legal_results: list[DeltaLegalResult] = []
        self._legal_metrics: dict[str, int] = {}

    @property
    def trace(self) -> list[TraceEvent]:
        return list(self._context.trace)

    def _reset(self):
        self._context = _RunContext(str(uuid.uuid4()), self.mode)
        self._last_plan_drafts = []
        self._last_plan_pool = []
        self._last_design = None
        self._last_idea_context = None
        self._last_plan_pool_status = None
        self._delta_legal_results = []
        self._legal_metrics = {key: 0 for key in (
            "factAttempted", "factValidated", "factAccepted", "factExhausted",
            "redesignAttempted", "redesignValidated", "redesignAccepted", "redesignExhausted",
            "replanAttempted", "replanValidated", "replanAccepted", "replanExhausted",
        )}
        self.gateway.usage = self.gateway.usage.model_copy(update={
            "logicalOperations": 0, "topLevelExternalOperations": 0, "topLevelOperationsByStage": {},
            "externalProviderCalls": 0, "totalProviderCalls": 0,
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

    async def derive_idea_brief(self, seed: CanonicalSeed) -> IdeaBriefLabContext:
        if self._last_idea_context is not None:
            return self._last_idea_context
        if seed.interpretation:
            context = self.seed_adapter.local_context(seed)
        else:
            payload = self.seed_adapter.current_payload(seed)
            raw = await self.gateway.derive_idea_brief(
                payload, self.seed_adapter.local_context(seed),
                lambda: execute_idea_brief_derivation(payload))
            context = raw if isinstance(raw, IdeaBriefLabContext) else self.seed_adapter.lab_context(raw)
        seed.interpretation = dict(context.interpretation)
        self._last_idea_context = context
        self._event(RunStage.SAFETY_CHECKING, "IDEA_BRIEF_DERIVED", "PASS",
                    f"Idea interpretation/readiness를 보존했습니다: {context.readiness.get('status')}",
                    provider_call=True)
        if (context.readiness.get("status") == "READY_FOR_REVIEW"
                and not context.readiness.get("missingFieldKeys")
                and context.readiness.get("score") == 0):
            self._event(RunStage.SAFETY_CHECKING, "READINESS_INCONSISTENT", "WARNING",
                        "READY_FOR_REVIEW이지만 score=0입니다. V2 gating은 막지 않고 진단으로 표시합니다.",
                        decision="READINESS_INCONSISTENT")
        return context

    async def check_safety(self, seed: CanonicalSeed):
        self._event(RunStage.SAFETY_CHECKING, "STARTED", "RUNNING", "현행 Idea Brief Safety 의미로 검사합니다.")
        context = await self.derive_idea_brief(seed)
        result = context.safetyReview
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
            opportunityKernel=build_opportunity_kernel(seed),
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
        concrete = len(_norm(seed.ideaOverview)) >= 20
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
        reserve_available = max(0, len(drafts) - max_concepts)
        self._last_plan_pool_status = PlanPoolStatus(
            requestedPoolSize=pool_size, returnedPoolSize=len(drafts), initialTarget=max_concepts,
            reserveTarget=2, reserveAvailable=reserve_available,
            status="RESERVE_READY" if len(drafts) >= pool_size else "RESERVE_SHORTFALL")
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

    async def _semantic_normalize_plans(self, plans: list[PortfolioPlan]) -> list[PortfolioPlan]:
        pending = [plan for plan in plans
                   if GenericConceptNormalizer.needs_semantic_fallback(plan.descriptor)]
        if not pending:
            return plans
        items = [{
            "entityId": plan.planId,
            "businessText": " ".join((plan.operatingApproach, plan.partnerApproach,
                                       plan.transactionApproach, plan.commercialApproach,
                                       plan.fulfillmentApproach, plan.customerInteraction)),
            "currentArchitecture": plan.descriptor.architecture.model_dump(mode="json"),
        } for plan in pending]
        classified = await self.gateway.classify_architectures(items)
        replacements = {plan.planId: plan.model_copy(update={
            "descriptor": GenericConceptNormalizer.apply_semantic_fallback(plan.descriptor, result)
        }) for plan, result in zip(pending, classified, strict=True)}
        normalized = [replacements.get(plan.planId, plan) for plan in plans]
        self._event(RunStage.PLANNING, "ARCHITECTURE_SEMANTIC_FALLBACK", "PASS",
                    f"low-confidence Plan architecture {len(pending)}개를 batch 분류했습니다.",
                    provider_call=True)
        return normalized

    async def plan_portfolio(self, seed: CanonicalSeed, analysis: DesignSpaceAnalysis,
                             max_concepts: int = 5) -> list[PortfolioPlan]:
        drafts = await self.generate_plan_drafts(seed, analysis, max_concepts)
        plans = self.normalize_plan_drafts(drafts, analysis)
        plans = await self._semantic_normalize_plans(plans)
        self._last_plan_pool = plans
        return plans

    def compare_plans(self, left: PortfolioPlan, right: PortfolioPlan) -> DiversityAssessment:
        return deterministic_distinctness(left.planId, right.planId, left.descriptor, right.descriptor)

    @staticmethod
    def _plan_base_score(plan: PortfolioPlan, analysis: DesignSpaceAnalysis) -> float:
        kernel = analysis.opportunityKernel
        opportunity_fit = (
            _text_overlap(plan.problemFocus, kernel.problemCore)
            + _text_overlap(plan.targetSegment, kernel.targetCore)
            + max((_text_overlap(plan.useContext, item) for item in kernel.useContexts), default=0.0)
            + max((_text_overlap(plan.solutionThesis, item) for item in kernel.intentComponents), default=0.0)
        ) / 4
        clarity = sum(bool(getattr(plan, key).strip()) for key in (
            "targetSegment", "problemFocus", "useContext", "valueProposition",
            "offerThesis", "solutionThesis")) / 6
        constraint_fit = 1.0 if all(
            plan.preservedLocks.get(key) == value
            for key, value in analysis.explicitBusinessLocks.items()) else 0.0
        feasibility = 1.0 if len(plan.legalRiskHints) <= 2 else 0.7
        directness = 0.5 * (_norm(plan.targetSegment) == _norm(kernel.targetCore)) \
            + 0.5 * (_norm(plan.problemFocus) == _norm(kernel.problemCore))
        return min(1.0, 0.35 * opportunity_fit + 0.20 * clarity
                   + 0.20 * constraint_fit + 0.10 * feasibility + 0.15 * directness)

    def _select_plan_portfolio(self, usable: list[PortfolioPlan], analysis: DesignSpaceAnalysis,
                               max_concepts: int) -> tuple[list[PortfolioPlan], list[PortfolioPlan]]:
        remaining = list(usable)
        selected: list[PortfolioPlan] = []
        while remaining and len(selected) < max_concepts:
            ranked = []
            for plan in remaining:
                base = self._plan_base_score(plan, analysis)
                relations = [self.compare_plans(existing, plan) for existing in selected]
                relation_values = {"DUPLICATE": 0.0, "AMBIGUOUS": 0.35,
                                   "VARIANT": 0.65, "DISTINCT": 1.0}
                marginal = min((relation_values.get(item.decision, 0.5) for item in relations), default=1.0)
                same_family = sum(item.descriptor.familyId == plan.descriptor.familyId for item in selected)
                architecture_bonus = 0.05 if selected and same_family == 0 else 0.0
                family_penalty = 0.08 * max(0, same_family - 1)
                score = max(0.0, min(2.0, 0.65 * base + 0.30 * marginal
                                     + architecture_bonus - family_penalty))
                ranked.append((score, base, plan.title.casefold(), plan, relations))
            score, _, _, chosen, relations = sorted(
                ranked, key=lambda item: (-item[0], -item[1], item[2]))[0]
            relation_labels = [item.decision for item in relations]
            if not selected:
                reason = "Opportunity fit과 Concept clarity가 가장 높은 대표안"
                relation_summary = "PORTFOLIO_SEED"
            elif "DISTINCT" in relation_labels:
                reason = "현재 Portfolio에 주요 사업 선택의 비교 범위를 추가"
                relation_summary = "DISTINCT"
            elif "VARIANT" in relation_labels:
                reason = "같은 Family를 허용하면서 의미 있는 target/use/value Variant를 추가"
                relation_summary = "VARIANT"
            else:
                reason = "현재 Portfolio에서 남은 비교 가치를 보완"
                relation_summary = relation_labels[0] if relation_labels else "NOT_EVALUATED"
            chosen = chosen.model_copy(update={"selectionStatus": "SELECTED",
                "selectionScore": round(score, 4), "selectionReason": reason,
                "relationToPortfolio": relation_summary})
            selected.append(chosen)
            remaining.remove(next(item for item in remaining if item.planId == chosen.planId))
        reserve = []
        for plan in remaining:
            base = self._plan_base_score(plan, analysis)
            relations = [self.compare_plans(existing, plan) for existing in selected]
            labels = [item.decision for item in relations]
            marginal = min(({"DUPLICATE": 0.0, "AMBIGUOUS": 0.35,
                             "VARIANT": 0.65, "DISTINCT": 1.0}.get(value, 0.5)
                            for value in labels), default=1.0)
            score = min(2.0, 0.65 * base + 0.30 * marginal)
            reserve.append(plan.model_copy(update={"selectionStatus": "RESERVE",
                "selectionScore": round(score, 4),
                "selectionReason": "Selected Portfolio 대비 marginal value가 낮아 reserve로 보존",
                "relationToPortfolio": ",".join(sorted(set(labels))) or "NOT_EVALUATED"}))
        reserve.sort(key=lambda item: (-item.selectionScore, item.title.casefold()))
        return selected, reserve

    async def validate_plans(self, plans: list[PortfolioPlan], analysis: DesignSpaceAnalysis,
                             max_concepts: int = 5) -> PlanValidationResult:
        self._event(RunStage.PLAN_VALIDATING, "STARTED", "RUNNING",
                    "Opportunity·LOCK·DUPLICATE/VARIANT/DISTINCT 관계를 검사합니다.")
        usable: list[PortfolioPlan] = []
        rejected: list[RejectedPlan] = []
        comparisons: list[DiversityAssessment] = []
        for plan in plans:
            language_failures = plan_language_failures(plan)
            if language_failures:
                rejected.append(RejectedPlan(planId=plan.planId,
                    reasonCode=FailureCode.CONTENT_LANGUAGE_MISMATCH,
                    safeSummary="한국어 content policy 위반: " + ", ".join(language_failures)))
                continue
            if any(plan.preservedLocks.get(key) != value for key, value in analysis.explicitBusinessLocks.items()):
                rejected.append(RejectedPlan(planId=plan.planId, reasonCode=FailureCode.LOCK_CONFLICT,
                                             safeSummary="hard lock을 보존하지 못했습니다."))
                continue
            content_decision, content_reasons = assess_plan_content(plan, analysis)
            if content_decision == "AMBIGUOUS":
                semantic = await self.gateway.judge_distinctness(
                    "OPPORTUNITY_SCOPE", analysis.opportunityKernel.model_dump(mode="json"),
                    plan.descriptor.thesis.model_dump(mode="json"))
                content_decision = "FAIL" if semantic.decision == "OUT_OF_SCOPE" else "PASS"
            if content_decision != "PASS":
                lock_conflict = any("LOCK" in item for item in content_reasons)
                rejected.append(RejectedPlan(planId=plan.planId,
                    reasonCode=FailureCode.LOCK_CONFLICT if lock_conflict else FailureCode.OUT_OF_SCOPE,
                    safeSummary=" ".join(content_reasons)))
                continue
            if any(plan.preservedAnchors.get(key) != value for key, value in analysis.semanticAnchors.items()):
                rejected.append(RejectedPlan(planId=plan.planId, reasonCode=FailureCode.OUT_OF_SCOPE,
                                             safeSummary="semantic anchor가 변했습니다."))
                continue
            duplicate = None
            for previous in usable:
                comparison = self.compare_plans(previous, plan)
                if comparison.decision == "AMBIGUOUS":
                    semantic = await self.gateway.judge_distinctness(
                        "PLAN", previous.descriptor.model_dump(mode="json"),
                        plan.descriptor.model_dump(mode="json"))
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
                                             safeSummary="Concept Thesis와 Architecture가 기존 Plan과 사실상 같습니다.",
                                             conflictPlanId=duplicate.planId))
                self._event(RunStage.PLAN_VALIDATING, "REJECTED", "REJECTED", "중복 plan을 제거했습니다.",
                            entity_id=plan.planId, parent_id=duplicate.planId, reason=FailureCode.PLAN_DUPLICATE)
            else:
                usable.append(plan)
        accepted, reserve = self._select_plan_portfolio(usable, analysis, max_concepts)
        self._event(RunStage.PLAN_VALIDATING, "SELECTED", "PASS", f"Plans={len(accepted)} Rejected={len(rejected)}")
        if (self._last_plan_pool_status is not None
                and len(plans) == self._last_plan_pool_status.returnedPoolSize):
            reserve_available = len(reserve)
            self._last_plan_pool_status = self._last_plan_pool_status.model_copy(update={
                "reserveAvailable": reserve_available,
                "status": ("RESERVE_READY" if reserve_available >= self._last_plan_pool_status.reserveTarget
                           else "RESERVE_SHORTFALL"),
            })
        return PlanValidationResult(acceptedPlans=accepted, reservePlans=reserve,
                                    rejectedPlans=rejected, diversity=comparisons)

    async def prepare_portfolio_plans(self, seed: CanonicalSeed, analysis: DesignSpaceAnalysis,
                                      max_concepts: int = 5) -> PlanValidationResult:
        """초기 pool이 부족하면 제한된 round 안에서 의미 있는 비교 후보를 보충한다."""
        all_plans = await self.plan_portfolio(seed, analysis, max_concepts)
        validation = await self.validate_plans(all_plans, analysis, max_concepts)
        rounds = 0
        requested = 0
        while len(validation.acceptedPlans) < max_concepts and rounds < self.max_planning_rounds:
            missing = max_concepts - len(validation.acceptedPlans)
            request_count = min(5, max(3, missing + 1))
            rounds += 1
            rejected_context = [item.model_dump(mode="json") for item in validation.rejectedPlans]
            drafts = await self.gateway.replenish_plans(
                seed, analysis, all_plans, rejected_context, request_count, rounds)
            requested += len(drafts)
            if not drafts:
                break
            new_plans = normalize_plan_drafts(drafts, analysis, start_index=len(all_plans) + 1)
            new_plans = await self._semantic_normalize_plans(new_plans)
            before = len(validation.acceptedPlans)
            all_plans.extend(new_plans)
            self._last_plan_drafts.extend(drafts)
            self._last_plan_pool = all_plans
            validation = await self.validate_plans(all_plans, analysis, max_concepts)
            self._event(RunStage.PLANNING, "REPLENISHED", "PASS",
                        f"round={rounds}, usable={len(validation.acceptedPlans)}, requested={len(drafts)}",
                        provider_call=True)
            if len(validation.acceptedPlans) == before and rounds < self.max_planning_rounds:
                self._event(RunStage.PLANNING, "REPLENISHMENT_NO_GAIN", "RUNNING",
                            "이번 round에서 새 usable Plan이 없어 남은 budget 안에서 한 번 더 시도합니다.")
        return validation.model_copy(update={"planningRounds": 1 + rounds,
                                             "replenishmentRequested": requested})

    async def expand_plans(self, seed: CanonicalSeed, plans: list[PortfolioPlan]) -> list[CandidateEnvelope]:
        self._event(RunStage.EXPANDING, "STARTED", "RUNNING", f"통과 plan {len(plans)}개를 확장합니다.")
        result = []
        for index, plan in enumerate(plans, 1):
            envelope = await self.expand_plan(seed, plan, index)
            result.append(envelope)
        return result

    async def expand_plan(self, seed: CanonicalSeed, plan: PortfolioPlan, candidate_index: int,
                          *, candidate_id: str | None = None, lineage_id: str | None = None,
                          recovery_source: str = "INITIAL") -> CandidateEnvelope:
        draft = await self.gateway.expand(seed, plan, candidate_index)
        strategy = self._last_design.explorationBreadth if self._last_design else ExplorationBreadth.EXPLORE
        candidate = normalize_candidate_draft(draft, seed, strategy, candidate_index)
        actual_descriptor = derive_candidate_descriptor(candidate)
        envelope = CandidateEnvelope(
            candidateId=candidate_id or f"C{candidate_index}", planId=plan.planId,
            lineageId=lineage_id or f"L{candidate_index}", slotIndex=candidate_index,
            recoverySource=recovery_source, descriptor=actual_descriptor, candidate=candidate)
        self._event(RunStage.EXPANDING, "EXPANDED", "PASS", f"{plan.planId} → {envelope.candidateId}",
                    entity_id=envelope.candidateId, parent_id=plan.planId, provider_call=True)
        return envelope

    def compare_candidates(self, left: CandidateEnvelope, right: CandidateEnvelope) -> DiversityAssessment:
        if business_fingerprint(left.candidate) == business_fingerprint(right.candidate):
            return DiversityAssessment(
                entityA=left.candidateId, entityB=right.candidateId, decision="DUPLICATE",
                overlap=list(descriptor_values(left.descriptor)), materialDifferences=[],
                whyDistinct="21-field canonical business fingerprint가 같습니다.",
                deterministicLevel="CANONICAL_FINGERPRINT", semanticJudgeUsed=False,
                familyA=left.descriptor.familyId, familyB=right.descriptor.familyId)
        return deterministic_distinctness(left.candidateId, right.candidateId,
                                          left.descriptor, right.descriptor)

    async def validate_candidates(self, seed: CanonicalSeed, plans: list[PortfolioPlan],
                                  candidates: list[CandidateEnvelope], *,
                                  comparison_context: list[CandidateEnvelope] | None = None
                                  ) -> tuple[list[CandidateEnvelope], list[CandidateValidation]]:
        self._event(RunStage.CANDIDATE_VALIDATING, "STARTED", "RUNNING", "Candidate 검사를 분리 수행합니다.")
        plan_by_id = {item.planId: item for item in plans}
        accepted: list[CandidateEnvelope] = list(comparison_context or [])
        newly_accepted: list[CandidateEnvelope] = []
        reports = []
        locks = {item.fieldKey: item.value for item in seed.fields
                 if item.fieldKey in DIRECT_CANDIDATE_LOCKS
                 and item.decisionState == "LOCKED" and item.value.strip()}
        anchor = self._last_design.opportunityKernel if self._last_design else build_opportunity_kernel(seed)
        pending = [item for item in candidates
                   if GenericConceptNormalizer.needs_semantic_fallback(item.descriptor)]
        if pending:
            items = [{"entityId": item.candidateId,
                      "businessText": " ".join((item.candidate.platformRole,
                                                item.candidate.providerRole,
                                                item.candidate.sellerRole,
                                                item.candidate.intermediaryRole,
                                                item.candidate.operatingModel,
                                                item.candidate.partnerModel,
                                                " ".join(item.candidate.transactionFlow),
                                                " ".join(item.candidate.paymentFlow),
                                                item.candidate.revenueModel,
                                                item.candidate.channels)),
                      "currentArchitecture": item.descriptor.architecture.model_dump(mode="json")}
                     for item in pending]
            classified = await self.gateway.classify_architectures(items)
            replacements = {item.candidateId: item.model_copy(update={
                "descriptor": GenericConceptNormalizer.apply_semantic_fallback(item.descriptor, result)
            }) for item, result in zip(pending, classified, strict=True)}
            candidates = [replacements.get(item.candidateId, item) for item in candidates]
            self._event(RunStage.CANDIDATE_VALIDATING, "ARCHITECTURE_SEMANTIC_FALLBACK", "PASS",
                        f"low-confidence Candidate architecture {len(pending)}개를 batch 분류했습니다.",
                        provider_call=True)
        for envelope in candidates:
            candidate, plan = envelope.candidate, plan_by_id[envelope.planId]
            derived_descriptor = derive_candidate_descriptor(candidate)
            actual_descriptor = envelope.descriptor
            if derived_descriptor.thesis != envelope.descriptor.thesis:
                actual_descriptor = derived_descriptor
                envelope = envelope.model_copy(update={"descriptor": actual_descriptor})
            language_failures = candidate_language_failures(candidate)
            lock_ok = all(not hasattr(candidate, key) or str(getattr(candidate, key)) == value
                          for key, value in locks.items())
            anchor_decision, _ = assess_anchor(
                anchor, candidate.problemScenario, candidate.targetUsers,
                " ".join([candidate.conceptDefinition, candidate.solutionMechanism,
                           candidate.operatingModel, candidate.partnerModel]),
                self._last_design.explorationBreadth if self._last_design else ExplorationBreadth.EXPLORE)
            if anchor_decision == "AMBIGUOUS":
                semantic_anchor = await self.gateway.judge_distinctness(
                    "OPPORTUNITY_SCOPE", anchor.model_dump(mode="json"),
                    actual_descriptor.thesis.model_dump(mode="json"))
                anchor_decision = "OUT_OF_SCOPE" if semantic_anchor.decision == "OUT_OF_SCOPE" else "PASS"
            anchor_ok = anchor_decision != "OUT_OF_SCOPE"
            fidelity_decision, matched, missing = deterministic_plan_fidelity(plan, candidate, actual_descriptor)
            semantic_fidelity_used = False
            if fidelity_decision == "AMBIGUOUS":
                self._event(RunStage.CANDIDATE_VALIDATING, "FIDELITY_AMBIGUOUS", "RUNNING",
                            "lexical 판정이 불확실해 semantic fidelity를 요청합니다.",
                            entity_id=envelope.candidateId,
                            reason=FailureCode.CANDIDATE_FIDELITY_RECOVERABLE)
                semantic_fidelity = await self.gateway.judge_fidelity(plan, candidate)
                fidelity_decision = semantic_fidelity.decision
                matched = semantic_fidelity.matchedMechanics
                missing = semantic_fidelity.missingMechanics
                semantic_fidelity_used = True
                self._event(RunStage.CANDIDATE_VALIDATING, "SEMANTIC_FIDELITY_CHECKED",
                            "PASS" if fidelity_decision in {"PASS", "ADAPTED"} else "REJECTED",
                            semantic_fidelity.safeSummary, entity_id=envelope.candidateId,
                            decision=fidelity_decision, provider_call=True)
            fidelity = fidelity_decision in {"PASS", "ADAPTED"}
            duplicate = None
            for previous in accepted:
                if (previous.lineageId == envelope.lineageId
                        and (previous.candidateId == envelope.parentCandidateId
                             or envelope.candidateId == previous.parentCandidateId)):
                    continue
                comparison = self.compare_candidates(previous, envelope)
                if comparison.decision == "AMBIGUOUS":
                    semantic = await self.gateway.judge_distinctness(
                        "CANDIDATE", previous.descriptor.model_dump(mode="json"),
                        envelope.descriptor.model_dump(mode="json"))
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
            if language_failures: reasons.append(FailureCode.CONTENT_LANGUAGE_MISMATCH)
            passed = not reasons
            recoverable = (reasons == [FailureCode.PLAN_FIDELITY_FAILED])
            outcome = ("ACCEPTED" if passed else
                       "RECOVERABLE_FIDELITY_FAILURE" if recoverable else "TERMINAL_INVALID")
            if passed:
                accepted.append(envelope)
                newly_accepted.append(envelope)
            reports.append(CandidateValidation(candidateId=envelope.candidateId, schemaValid=True,
                                               hardLockPreserved=lock_ok, semanticAnchorPreserved=anchor_ok,
                                               planFidelity=fidelity, anchorDecision=anchor_decision,
                                               fidelityDecision=fidelity_decision,
                                               contentLanguageValid=not language_failures,
                                               accepted=passed, outcome=outcome,
                                               semanticFidelityUsed=semantic_fidelity_used,
                                               matchedIdentityComponents=matched,
                                               missingIdentityComponents=missing,
                                               reasonCodes=reasons,
                                               safeSummary="검사 통과" if passed else ", ".join(item.value for item in reasons)))
            self._event(RunStage.CANDIDATE_VALIDATING, "VALIDATED", "PASS" if passed else "REJECTED",
                        reports[-1].safeSummary, entity_id=envelope.candidateId,
                        reason=reasons[0] if reasons else None)
        return newly_accepted, reports

    async def _regenerate_failed_candidate(
        self, seed: CanonicalSeed, plan: PortfolioPlan, failed: CandidateEnvelope,
        report: CandidateValidation, comparison_context: list[CandidateEnvelope],
    ) -> tuple[CandidateEnvelope | None, list[CandidateValidation]]:
        slot = failed.slotIndex or failed.candidate.candidateIndex
        child_id = f"{failed.candidateId}-G1"
        self._event(RunStage.CANDIDATE_VALIDATING, "CANDIDATE_REGENERATION_STARTED", "RUNNING",
                    "같은 Plan identity를 보존하는 targeted regeneration을 시작합니다.",
                    entity_id=child_id, parent_id=failed.candidateId,
                    reason=FailureCode.CANDIDATE_FIDELITY_RECOVERABLE)
        draft = await self.gateway.regenerate_candidate(
            seed, self._last_design, plan, failed.candidate, report.safeSummary,
            report.missingIdentityComponents, slot)
        strategy = self._last_design.explorationBreadth if self._last_design else ExplorationBreadth.EXPLORE
        candidate = normalize_candidate_draft(draft, seed, strategy, slot)
        child = CandidateEnvelope(
            candidateId=child_id, planId=plan.planId, lineageId=failed.lineageId,
            parentCandidateId=failed.candidateId, candidateAttempt=2, slotIndex=slot,
            recoverySource="FIDELITY_REGENERATION",
            descriptor=derive_candidate_descriptor(candidate), candidate=candidate)
        accepted, reports = await self.validate_candidates(
            seed, [plan], [child], comparison_context=comparison_context)
        if accepted:
            self._event(RunStage.CANDIDATE_VALIDATING, "CANDIDATE_REGENERATED", "PASS",
                        "targeted regeneration Candidate가 전체 validation을 통과했습니다.",
                        entity_id=child_id, parent_id=failed.candidateId, decision="PASS",
                        provider_call=True)
            return accepted[0], reports
        self._event(RunStage.CANDIDATE_VALIDATING, "CANDIDATE_RECOVERY_FAILED", "REJECTED",
                    "Plan별 regeneration budget 1회를 사용했지만 통과하지 못했습니다.",
                    entity_id=child_id, parent_id=failed.candidateId,
                    reason=FailureCode.CANDIDATE_REGENERATION_EXHAUSTED, provider_call=True)
        return None, reports

    def _reserve_plan_value(self, plan: PortfolioPlan,
                            accepted: list[CandidateEnvelope]) -> float:
        relation_value = {"DUPLICATE": 0.0, "AMBIGUOUS": 0.35,
                          "VARIANT": 0.65, "DISTINCT": 1.0}
        relations = [deterministic_distinctness(
            item.candidateId, plan.planId, item.descriptor, plan.descriptor) for item in accepted]
        marginal = min((relation_value.get(item.decision, 0.5) for item in relations), default=1.0)
        clarity = min(1.0, len(_terms(plan.oneLineConcept + " " + plan.solutionThesis)) / 18)
        return 0.55 * plan.selectionScore + 0.35 * marginal + 0.10 * clarity

    async def prepare_candidate_portfolio(
        self, seed: CanonicalSeed, plan_validation: PlanValidationResult,
        max_concepts: int = 5, *, initial_candidates: list[CandidateEnvelope] | None = None,
    ) -> CandidatePortfolioPreparation:
        """초기 확장 → fidelity regeneration → reserve → replenishment 순으로 후보를 복구한다."""
        selected_plans = list(plan_validation.acceptedPlans)
        used_plans = list(selected_plans)
        plan_by_id = {item.planId: item for item in selected_plans + plan_validation.reservePlans}
        initial = initial_candidates if initial_candidates is not None else await self.expand_plans(seed, selected_plans)
        if {item.planId for item in initial} != {item.planId for item in selected_plans}:
            raise ValueError("initial_candidates는 selected Plan을 정확히 한 번씩 포함해야 합니다")
        accepted, initial_reports = await self.validate_candidates(seed, selected_plans, initial)
        reports = list(initial_reports)
        generated = len(initial)
        accepted_initially = len(accepted)
        regenerated = recovered = reserve_activated = recovery_replans = 0

        by_report = {item.candidateId: item for item in initial_reports}
        for failed in initial:
            report = by_report[failed.candidateId]
            if report.outcome != "RECOVERABLE_FIDELITY_FAILURE":
                continue
            child, child_reports = await self._regenerate_failed_candidate(
                seed, plan_by_id[failed.planId], failed, report, accepted)
            regenerated += 1
            reports.extend(child_reports)
            if child:
                accepted.append(child)
                recovered += 1

        def missing_slots() -> list[int]:
            occupied = {item.slotIndex or item.candidate.candidateIndex for item in accepted}
            return [index for index in range(1, max_concepts + 1) if index not in occupied]

        reserve = list(plan_validation.reservePlans)
        while reserve and missing_slots():
            plan = max(reserve, key=lambda item: (self._reserve_plan_value(item, accepted), item.title))
            reserve.remove(plan)
            slot = missing_slots()[0]
            reserve_activated += 1
            used_plans.append(plan)
            self._event(RunStage.CANDIDATE_VALIDATING, "RESERVE_PLAN_ACTIVATED", "RUNNING",
                        f"현재 Portfolio marginal value가 가장 높은 reserve {plan.planId}를 활성화했습니다.",
                        entity_id=plan.planId, decision="RESERVE")
            envelope = await self.expand_plan(
                seed, plan, slot, candidate_id=f"C{slot}-RSV{reserve_activated}",
                lineage_id=f"L{slot}-{plan.planId}", recovery_source="RESERVE_PLAN")
            generated += 1
            newly, reserve_reports = await self.validate_candidates(
                seed, [plan], [envelope], comparison_context=accepted)
            reports.extend(reserve_reports)
            if newly:
                accepted.extend(newly)
                recovered += 1
                continue
            if reserve_reports[0].outcome == "RECOVERABLE_FIDELITY_FAILURE":
                child, child_reports = await self._regenerate_failed_candidate(
                    seed, plan, envelope, reserve_reports[0], accepted)
                regenerated += 1
                reports.extend(child_reports)
                if child:
                    accepted.append(child)
                    recovered += 1

        all_plans = list(self._last_plan_pool)
        for round_number in range(1, self.max_planning_rounds + 1):
            if not missing_slots():
                break
            self._event(RunStage.CANDIDATE_VALIDATING, "CANDIDATE_REPLENISHMENT_STARTED", "RUNNING",
                        f"reserve 이후 부족한 Candidate slot={len(missing_slots())}을 보충합니다.")
            count = min(5, max(3, len(missing_slots()) + 1))
            drafts = await self.gateway.replenish_plans(
                seed, self._last_design, all_plans,
                [item.model_dump(mode="json") for item in reports if not item.accepted],
                count, round_number)
            recovery_replans += 1
            if not drafts:
                break
            new_plans = normalize_plan_drafts(
                drafts, self._last_design, start_index=1, prefix=f"CR{round_number}-P")
            new_plans = await self._semantic_normalize_plans(new_plans)
            combined = all_plans + new_plans
            self._last_plan_drafts.extend(drafts)
            self._last_plan_pool = combined
            validation = await self.validate_plans(combined, self._last_design, max_concepts=len(combined))
            new_ids = {item.planId for item in new_plans}
            available = [item for item in validation.acceptedPlans if item.planId in new_ids]
            all_plans = combined
            gained = 0
            while available and missing_slots():
                plan = max(available, key=lambda item: (self._reserve_plan_value(item, accepted), item.title))
                available.remove(plan)
                used_plans.append(plan)
                slot = missing_slots()[0]
                envelope = await self.expand_plan(
                    seed, plan, slot, candidate_id=f"C{slot}-CR{round_number}",
                    lineage_id=f"L{slot}-{plan.planId}", recovery_source="REPLENISHED_PLAN")
                generated += 1
                newly, new_reports = await self.validate_candidates(
                    seed, [plan], [envelope], comparison_context=accepted)
                reports.extend(new_reports)
                if newly:
                    accepted.extend(newly); recovered += 1; gained += 1
                elif new_reports[0].outcome == "RECOVERABLE_FIDELITY_FAILURE":
                    child, child_reports = await self._regenerate_failed_candidate(
                        seed, plan, envelope, new_reports[0], accepted)
                    regenerated += 1
                    reports.extend(child_reports)
                    if child:
                        accepted.append(child); recovered += 1; gained += 1
            if gained == 0 and round_number >= self.max_planning_rounds:
                break

        accepted.sort(key=lambda item: (item.slotIndex or item.candidate.candidateIndex, item.candidateId))
        accepted = accepted[:max_concepts]
        self._event(RunStage.CANDIDATE_VALIDATING, "CANDIDATE_PORTFOLIO_READY", "PASS",
                    f"Candidate Portfolio={len(accepted)}/{max_concepts}, recovered={recovered}",
                    decision="READY_FULL" if len(accepted) == max_concepts else "READY_LIMITED")
        return CandidatePortfolioPreparation(
            candidates=accepted, reports=reports, usedPlans=used_plans,
            candidateGenerated=generated, candidateAcceptedInitially=accepted_initially,
            candidateRegenerated=regenerated, candidateRecovered=recovered,
            reservePlansActivated=reserve_activated,
            candidateRecoveryReplans=recovery_replans)

    def legal_precheck(self, envelope: CandidateEnvelope) -> LegalPrecheck:
        candidate = envelope.candidate
        physical = _substantive_facts(candidate.physicalActivities)
        personal_data = _substantive_facts(candidate.personalDataUsage)
        qualifications = _substantive_facts(candidate.qualificationRequirements)
        return LegalPrecheck(candidateId=envelope.candidateId,
            directSeller="판매" in candidate.sellerRole, intermediary="중개" in candidate.intermediaryRole,
            regulatedPhysicalActivity=bool(physical),
            personalDataDependency=bool(personal_data),
            qualificationDependency=bool(qualifications),
            riskHints=[item for item, active in (("물리 활동", bool(physical)),
                                                ("개인정보", bool(personal_data)),
                                                ("자격 의존", bool(qualifications))) if active])

    async def prepare_legal_candidates(self, seed: CanonicalSeed, candidates: list[CandidateEnvelope]
                                       ) -> LegalCandidatePreparation:
        """MOLEG 전에 동일 lineage 내 사업 사실 누락을 한 번만 보완한다."""
        self._event(RunStage.LEGAL_RECOVERING, "LEGAL_FACT_COMPLETENESS_STARTED", "RUNNING",
                    f"Candidate {len(candidates)}개의 법률 사실패턴 완결성을 검사합니다.")
        plan_by_id = {item.planId: item for item in self._last_plan_pool}
        ready: list[CandidateEnvelope] = []
        reports = []
        excluded = []
        attempted = validated = accepted = exhausted = 0
        for envelope in candidates:
            report = assess_legal_fact_completeness(envelope.candidate).model_copy(
                update={"candidateId": envelope.candidateId})
            reports.append(report)
            self._event(RunStage.LEGAL_RECOVERING, "LEGAL_FACT_COMPLETENESS_CHECKED", report.status,
                        report.safeSummary, entity_id=envelope.candidateId, decision=report.status)
            if report.status == "COMPLETE":
                ready.append(envelope)
                continue
            if report.status == "INVALID":
                exhausted += 1
                excluded.append({"candidateId": envelope.candidateId, "scope": "CANDIDATE",
                    "reasonCode": FailureCode.LEGAL_FACT_COMPLETION_EXHAUSTED.value,
                    "safeSummary": report.safeSummary})
                continue
            attempted += 1
            draft = await self.gateway.complete_legal_facts(
                seed, plan_by_id[envelope.planId], envelope.candidate,
                report.completionRequirements, envelope.candidate.candidateIndex)
            strategy = ExplorationBreadth(envelope.candidate.generationStrategy)
            candidate = normalize_candidate_draft(
                draft, seed, strategy, envelope.candidate.candidateIndex)
            child = CandidateEnvelope(
                candidateId=f"{envelope.candidateId}-F1", planId=envelope.planId,
                lineageId=envelope.lineageId, parentCandidateId=envelope.candidateId,
                redesignRound=envelope.redesignRound, candidateAttempt=envelope.candidateAttempt,
                slotIndex=envelope.slotIndex, recoverySource="LEGAL_FACT_COMPLETION",
                descriptor=derive_candidate_descriptor(candidate), candidate=candidate)
            superseded = {item.parentCandidateId for item in ready if item.parentCandidateId}
            others = [*ready, *[item for item in candidates
                if item.candidateId != envelope.candidateId
                and item.candidateId not in superseded
                and item.candidateId not in {ready_item.candidateId for ready_item in ready}]]
            validated_children, _ = await self.validate_candidates(
                seed, [plan_by_id[envelope.planId]], [child], comparison_context=others)
            if not validated_children:
                exhausted += 1
                excluded.append({"candidateId": child.candidateId, "scope": "CANDIDATE",
                    "reasonCode": FailureCode.LEGAL_FACT_COMPLETION_EXHAUSTED.value,
                    "safeSummary": "Legal fact completion Candidate가 전체 validation을 통과하지 못했습니다."})
                continue
            validated += 1
            child = validated_children[0]
            recheck = assess_legal_fact_completeness(child.candidate).model_copy(
                update={"candidateId": child.candidateId})
            reports.append(recheck)
            if recheck.status == "COMPLETE":
                ready.append(child); accepted += 1
                self._event(RunStage.LEGAL_RECOVERING, "LEGAL_FACT_COMPLETION_ACCEPTED", "PASS",
                            recheck.safeSummary, entity_id=child.candidateId,
                            parent_id=envelope.candidateId, provider_call=True)
            else:
                exhausted += 1
                excluded.append({"candidateId": child.candidateId, "scope": "CANDIDATE",
                    "reasonCode": FailureCode.LEGAL_FACT_COMPLETION_EXHAUSTED.value,
                    "safeSummary": recheck.safeSummary})
        ready.sort(key=lambda item: (item.slotIndex or item.candidate.candidateIndex, item.candidateId))
        self._legal_metrics["factAttempted"] += attempted
        self._legal_metrics["factValidated"] += validated
        self._legal_metrics["factAccepted"] += accepted
        self._legal_metrics["factExhausted"] += exhausted
        return LegalCandidatePreparation(candidates=ready, reports=reports, excludedCandidates=excluded,
            completionAttempted=attempted, completionValidated=validated,
            completionAccepted=accepted, completionExhausted=exhausted)

    async def review_legal(self, seed: CanonicalSeed, candidates: list[CandidateEnvelope]) -> list[LegalReview]:
        self._event(RunStage.LEGAL_REVIEWING, "STARTED", "RUNNING", "공식 Legal contract route를 실행합니다.")
        results = []
        for envelope in candidates:
            review = await self.review_legal_candidate(seed, envelope)
            results.append(review)
        return results

    async def review_legal_candidate(self, seed: CanonicalSeed,
                                     envelope: CandidateEnvelope) -> LegalReview:
        review = await self.gateway.review_legal(envelope.candidateId, envelope.candidate, seed)
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
                scope = "GLOBAL" if review.conflictingLock else review.inputScope
                required_inputs.append({"candidateId": envelope.candidateId, "scope": scope,
                    **{key: getattr(review, key) for key in
                    ("conflictingLock", "currentValue", "requiredLegalChange", "reason", "possibleUserAction")},
                    "safeSummary": review.safeSummary})
                continue
            lineage_redesigns = redesigns_by_lineage.get(envelope.lineageId, 0)
            if (review.route == LegalRoute.REDESIGN_WITHIN_LINEAGE
                    and lineage_redesigns < self.max_redesigns
                    and envelope.redesignRound < self.max_redesigns):
                self._legal_metrics["redesignAttempted"] += 1
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
                                          candidateAttempt=envelope.candidateAttempt,
                                          slotIndex=envelope.slotIndex,
                                          recoverySource="LEGAL_REDESIGN",
                                          descriptor=derive_candidate_descriptor(child_value), candidate=child_value)
                validated_children, child_reports = await self.validate_candidates(
                    seed, [plan_by_id[envelope.planId]], [child], comparison_context=final)
                if not validated_children:
                    self._legal_metrics["redesignExhausted"] += 1
                    duplicate = bool(child_reports and FailureCode.CANDIDATE_DUPLICATE in child_reports[0].reasonCodes)
                    all_reviews.append(LegalReview(candidateId=child_id,
                        route=LegalRoute.REPLAN_REQUIRED if duplicate else LegalRoute.SYSTEM_FAILURE,
                        sourceStatus="VALIDATION",
                        safeSummary=("Redesign이 다른 portfolio concept과 중복됩니다." if duplicate else
                            "Redesign이 opportunity/Plan/LOCK 검사를 통과하지 못했습니다.")))
                else:
                    self._legal_metrics["redesignValidated"] += 1
                    child = validated_children[0]
                    compliance = validate_redesign_requirements(
                        envelope.candidate, child.candidate, review.redesignRequirements)
                    if compliance.status != "PASS":
                        repair_draft = await self.gateway.repair_redesign_compliance(
                            seed, plan_by_id[envelope.planId], envelope.candidate, child.candidate,
                            compliance.unsatisfiedRequirements, envelope.candidate.candidateIndex)
                        repaired_value = normalize_candidate_draft(
                            repair_draft, seed, ExplorationBreadth(envelope.candidate.generationStrategy),
                            envelope.candidate.candidateIndex)
                        repaired = child.model_copy(update={
                            "candidateId": f"{child_id}-CR1", "parentCandidateId": child_id,
                            "recoverySource": "LEGAL_REDESIGN_COMPLIANCE_REPAIR",
                            "descriptor": derive_candidate_descriptor(repaired_value), "candidate": repaired_value})
                        repaired_children, _ = await self.validate_candidates(
                            seed, [plan_by_id[envelope.planId]], [repaired], comparison_context=final)
                        if repaired_children:
                            repaired = repaired_children[0]
                            compliance = validate_redesign_requirements(
                                envelope.candidate, repaired.candidate, review.redesignRequirements)
                            child = repaired
                    if compliance.status != "PASS":
                        self._legal_metrics["redesignExhausted"] += 1
                        all_reviews.append(LegalReview(candidateId=child.candidateId,
                            route=LegalRoute.SYSTEM_FAILURE, sourceStatus="REDESIGN_COMPLIANCE",
                            recoveryResolution="LEGAL_REDESIGN_COMPLIANCE_EXHAUSTED",
                            safeSummary="Legal redesign 요구를 targeted repair 1회 후에도 충족하지 못했습니다."))
                        continue
                    prepared_child = await self.prepare_legal_candidates(seed, [child])
                    if not prepared_child.candidates:
                        self._legal_metrics["redesignExhausted"] += 1
                        all_reviews.append(LegalReview(candidateId=child.candidateId,
                            route=LegalRoute.SYSTEM_FAILURE, sourceStatus="LEGAL_FACT_COMPLETENESS",
                            recoveryResolution="LEGAL_FACT_COMPLETION_EXHAUSTED",
                            safeSummary="Redesign Candidate의 법률 사실패턴 완결성 복구가 소진되었습니다."))
                        continue
                    child = prepared_child.candidates[0]
                    child_id = child.candidateId
                    child_review = await self.gateway.review_legal(child_id, child.candidate, seed)
                    all_reviews.append(child_review)
                    if child_review.route == LegalRoute.ACCEPT:
                        final.append(child)
                        redesigned += 1
                        self._legal_metrics["redesignAccepted"] += 1
                        redesigns_by_lineage[envelope.lineageId] = lineage_redesigns + 1
                        self._event(RunStage.LEGAL_RECOVERING, "REDESIGNED", "PASS", "같은 lineage에서 법률 구조를 보완했습니다.",
                                    entity_id=child_id, parent_id=envelope.candidateId,
                                    reason=FailureCode.LEGAL_REDESIGN_REQUIRED, provider_call=True)
                    elif child_review.route == LegalRoute.REDESIGN_WITHIN_LINEAGE:
                        self._legal_metrics["redesignExhausted"] += 1
                        repeated = normalized_requirements(child_review.redesignRequirements) == normalized_requirements(
                            review.redesignRequirements)
                        all_reviews.append(LegalReview(
                            candidateId=child_id, route=LegalRoute.SYSTEM_FAILURE,
                            sourceStatus="REDESIGN_LOOP" if repeated else "REDESIGN_BUDGET",
                            recoveryResolution=("LEGAL_REDESIGN_LOOP_DETECTED" if repeated else
                                                "LEGAL_REDESIGN_BUDGET_EXHAUSTED"),
                            safeSummary=("동일한 Legal redesign 요구가 반복되어 loop를 종료했습니다." if repeated else
                                f"lineage {envelope.lineageId}의 redesign 예산 {self.max_redesigns}회를 모두 사용했습니다.")))
                    elif child_review.route == LegalRoute.NEEDS_INPUT:
                        scope = "GLOBAL" if child_review.conflictingLock else child_review.inputScope
                        required_inputs.append({"candidateId": child_id, "scope": scope,
                            **{key: getattr(child_review, key) for key in
                            ("conflictingLock", "currentValue", "requiredLegalChange", "reason", "possibleUserAction")},
                            "safeSummary": child_review.safeSummary})
                    elif child_review.route == LegalRoute.REPLAN_REQUIRED:
                        recovered, nested_reviews, nested_inputs, nested_redesigned, nested_replanned = await self.resolve_legal(
                            seed, plans, [child], [child_review])
                        final.extend(recovered); all_reviews.extend(nested_reviews[1:]); required_inputs.extend(nested_inputs)
                        redesigned += nested_redesigned; replanned += nested_replanned
                    elif child_review.route == LegalRoute.SYSTEM_FAILURE:
                        self._legal_metrics["redesignExhausted"] += 1
                continue
            if review.route == LegalRoute.REDESIGN_WITHIN_LINEAGE:
                self._legal_metrics["redesignExhausted"] += 1
                all_reviews.append(LegalReview(candidateId=envelope.candidateId,
                    route=LegalRoute.SYSTEM_FAILURE, sourceStatus="REDESIGN_BUDGET",
                    recoveryResolution="LEGAL_REDESIGN_BUDGET_EXHAUSTED",
                    safeSummary=f"lineage {envelope.lineageId}의 Legal redesign 예산이 소진되었습니다."))
                continue
            if review.route == LegalRoute.REPLAN_REQUIRED and replanned < self.max_replans:
                self._legal_metrics["replanAttempted"] += 1
                replacement = None
                for candidate_plan in self._last_plan_pool:
                    if candidate_plan.planId in used_plan_ids:
                        continue
                    candidate_plan_result = await self.validate_plans(
                        [candidate_plan], self._last_design, max_concepts=1)
                    if not candidate_plan_result.acceptedPlans:
                        continue
                    comparisons = []
                    for existing in plans:
                        comparison = deterministic_distinctness(
                            candidate_plan.planId, existing.planId,
                            candidate_plan.descriptor, existing.descriptor)
                        if comparison.decision == "AMBIGUOUS":
                            semantic = await self.gateway.judge_distinctness(
                                "PLAN", candidate_plan.descriptor.model_dump(mode="json"),
                                existing.descriptor.model_dump(mode="json"))
                            comparison = comparison.model_copy(update={"decision": semantic.decision,
                                "semanticJudgeUsed": True, "whyDistinct": semantic.safeSummary})
                        comparisons.append(comparison)
                    if all(item.decision in {"VARIANT", "DISTINCT"} for item in comparisons):
                        replacement = candidate_plan
                        break
                if replacement is None and self._last_design is not None:
                    targeted = await self.gateway.replacement_plans(
                        seed, self._last_design, self._last_plan_pool, count=2)
                    normalized = normalize_plan_drafts(
                        targeted, self._last_design, start_index=len(self._last_plan_pool) + 1, prefix="RP")
                    self._last_plan_pool.extend(normalized)
                    plan_by_id.update({item.planId: item for item in normalized})
                    for candidate_plan in normalized:
                        plan_result = await self.validate_plans([candidate_plan], self._last_design, max_concepts=1)
                        if plan_result.acceptedPlans:
                            replacement = candidate_plan
                            break
                if replacement:
                    index = min(5, len(final) + 1)
                    plan_result = await self.validate_plans([replacement], self._last_design, max_concepts=1)
                    if not plan_result.acceptedPlans:
                        continue
                    child = await self.expand_plan(
                        seed, replacement, index, candidate_id=f"{envelope.candidateId}-REPLAN",
                        lineage_id=f"L-REPLAN-{replanned + 1}", recovery_source="LEGAL_REPLAN")
                    validated_children, _ = await self.validate_candidates(
                        seed, [replacement], [child], comparison_context=final)
                    if not validated_children:
                        self._legal_metrics["replanExhausted"] += 1
                        continue
                    self._legal_metrics["replanValidated"] += 1
                    child = validated_children[0]
                    prepared_child = await self.prepare_legal_candidates(seed, [child])
                    if not prepared_child.candidates:
                        self._legal_metrics["replanExhausted"] += 1
                        all_reviews.append(LegalReview(candidateId=child.candidateId,
                            route=LegalRoute.SYSTEM_FAILURE, sourceStatus="LEGAL_FACT_COMPLETENESS",
                            recoveryResolution="LEGAL_FACT_COMPLETION_EXHAUSTED",
                            safeSummary="Legal replan Candidate의 사실패턴 복구가 소진되었습니다."))
                        continue
                    child = prepared_child.candidates[0]
                    child_review = await self.gateway.review_legal(
                        child.candidateId, child.candidate, seed)
                    all_reviews.append(child_review)
                    if child_review.route == LegalRoute.ACCEPT:
                        final.append(child); replanned += 1
                        self._legal_metrics["replanAccepted"] += 1
                        used_plan_ids.add(replacement.planId)
                        self._event(RunStage.LEGAL_RECOVERING, "REPLANNED", "PASS",
                                    "실패 Plan을 의미 있게 다른 Thesis 또는 Architecture Plan으로 교체했습니다.",
                                    entity_id=child.candidateId, parent_id=envelope.candidateId,
                                    reason=FailureCode.LEGAL_REPLAN_REQUIRED, provider_call=True)
                    elif child_review.route == LegalRoute.NEEDS_INPUT:
                        scope = "GLOBAL" if child_review.conflictingLock else child_review.inputScope
                        required_inputs.append({"candidateId": child.candidateId, "scope": scope,
                            **{key: getattr(child_review, key) for key in
                            ("conflictingLock", "currentValue", "requiredLegalChange", "reason", "possibleUserAction")},
                            "safeSummary": child_review.safeSummary})
                    elif child_review.route == LegalRoute.REDESIGN_WITHIN_LINEAGE:
                        recovered, nested_reviews, nested_inputs, nested_redesigned, _ = await self.resolve_legal(
                            seed, [*plans, replacement], [child], [child_review])
                        final.extend(recovered); all_reviews.extend(nested_reviews[1:]); required_inputs.extend(nested_inputs)
                        redesigned += nested_redesigned
                    else:
                        self._legal_metrics["replanExhausted"] += 1
                else:
                    self._legal_metrics["replanExhausted"] += 1
                continue
            if review.route == LegalRoute.REPLAN_REQUIRED:
                self._legal_metrics["replanExhausted"] += 1
                all_reviews.append(LegalReview(candidateId=envelope.candidateId,
                    route=LegalRoute.SYSTEM_FAILURE, sourceStatus="REPLAN_BUDGET",
                    recoveryResolution="LEGAL_REPLAN_BUDGET_EXHAUSTED",
                    safeSummary="Legal replan 예산이 소진되었습니다."))
        return final, all_reviews, required_inputs, redesigned, replanned

    def build_or_load_current_hypothesis_contract(self, envelope: CandidateEnvelope) -> list[HypothesisDecision]:
        semantics = {item.fieldKey: item for item in envelope.candidate.valueSemantics}
        result = []
        for hypothesis_type, field in HYPOTHESIS_FIELDS.items():
            semantic = semantics[field]
            value = getattr(envelope.candidate, field)
            locked = semantic.source in {"USER_INPUT", "USER_CONFIRMED"} and semantic.authority == "LOCKED"
            assessment = assess_hypothesis_value(hypothesis_type, value)
            result.append(HypothesisDecision(hypothesisType=hypothesis_type, proposedValue=value,
                                             finalValue=value if locked else None, source=semantic.source,
                                             decisionStatus="ACCEPTED" if locked else "PROPOSED", locked=locked,
                                             semanticStatus=assessment.status,
                                             semanticReason=assessment.reason))
        return result

    def validate_hypothesis_values(self, hypotheses: list[HypothesisDecision], *, use_final: bool = False):
        return assess_hypotheses(hypotheses, use_final=use_final)

    def confirm_hypotheses(self, hypotheses: list[HypothesisDecision],
                           edits: dict[str, Any] | None = None, *,
                           confirm_all_proposed: bool = False) -> list[HypothesisDecision]:
        edits = edits or {}
        result = []
        for item in hypotheses:
            if item.locked:
                result.append(item)
                continue
            edited = item.hypothesisType in edits
            if not edited and not confirm_all_proposed:
                result.append(item)
                continue
            value = edits.get(item.hypothesisType, item.proposedValue)
            assessment = assess_hypothesis_value(item.hypothesisType, value)
            if assessment.status != "VALID":
                result.append(item.model_copy(update={
                    "finalValue": None, "decisionStatus": "PROPOSED",
                    "source": "USER_INPUT" if edited else item.source,
                    "semanticStatus": assessment.status, "semanticReason": assessment.reason,
                    "legalImpact": "NONE", "legalReviewStatus": "NOT_REQUIRED",
                    "deltaLegalRequired": False,
                }))
                continue
            legal_sensitive = item.hypothesisType in {
                "TARGET_REGION", "REVENUE_MODEL", "PRICE", "CHANNELS", "DIFFERENTIATORS"}
            delta_required = edited and value != item.proposedValue and legal_sensitive
            result.append(item.model_copy(update={"finalValue": value,
                "decisionStatus": "USER_EDITED_ACCEPTED" if edited else "ACCEPTED",
                "source": "USER_INPUT" if edited else item.source,
                "legalImpact": "DELTA_REVIEW_REQUIRED" if delta_required else item.legalImpact,
                "legalReviewStatus": "PENDING" if delta_required else item.legalReviewStatus,
                "deltaLegalRequired": delta_required,
                "semanticStatus": assessment.status, "semanticReason": assessment.reason}))
        return result

    def mark_delta_legal_reviewed(self, hypotheses: list[HypothesisDecision],
                                  result: DeltaLegalResult) -> list[HypothesisDecision]:
        if not isinstance(result, DeltaLegalResult) or not result.approved:
            raise ValueError("실제 ACCEPT DeltaLegalResult가 필요합니다")
        approved_types = set(result.hypothesisTypes)
        return [item.model_copy(update={"legalReviewStatus": result.status})
                if item.hypothesisType in approved_types and item.deltaLegalRequired else item
                for item in hypotheses]

    async def review_delta_legal(self, seed: CanonicalSeed, selected: CandidateEnvelope,
                                 hypotheses: list[HypothesisDecision]) -> DeltaLegalResult:
        pending = [item for item in hypotheses if item.deltaLegalRequired and item.legalReviewStatus == "PENDING"]
        if not pending:
            raise ValueError("Delta Legal이 필요한 hypothesis가 없습니다")
        updates = {HYPOTHESIS_FIELDS[item.hypothesisType]: item.finalValue for item in pending}
        semantics = []
        pending_fields = set(updates)
        for semantic in selected.candidate.valueSemantics:
            if semantic.fieldKey in pending_fields:
                semantics.append(semantic.model_copy(update={"source": "USER_INPUT",
                    "decision": "USER_EDITED_ACCEPTED"}))
            else:
                semantics.append(semantic)
        changed_candidate = selected.candidate.model_copy(update={**updates, "valueSemantics": semantics})
        review = await self.gateway.review_legal(
            f"{selected.candidateId}-DELTA", changed_candidate, seed)
        hypothesis_types = [item.hypothesisType for item in pending]
        approved = review.route == LegalRoute.ACCEPT
        token = production_compatible_snapshot_hash({
            "candidateId": selected.candidateId, "hypothesisTypes": hypothesis_types,
            "changedCandidate": changed_candidate.model_dump(mode="json"),
            "legalReview": review.model_dump(mode="json")})
        result = DeltaLegalResult(reviewToken=token, candidateId=selected.candidateId,
            hypothesisTypes=hypothesis_types,
            status=review.productionStatus or ("PASSED" if approved else "FAILED"),
            approved=approved, legalReview=review)
        self._delta_legal_results.append(result)
        return result

    def build_downstream_handoff(self, seed: CanonicalSeed, selected: CandidateEnvelope,
                                 hypotheses: list[HypothesisDecision], legal_reviews: list[LegalReview]) -> DownstreamHandoff:
        legal = next((item for item in reversed(legal_reviews)
                      if item.candidateId == selected.candidateId and item.route == LegalRoute.ACCEPT), None)
        if not legal:
            raise ValueError("선택 Concept의 ACCEPT Legal 결과가 없습니다")
        delta_reviews = [item.model_dump(mode="json") for item in self._delta_legal_results
                         if item.candidateId == selected.candidateId]
        if delta_reviews:
            legal = legal.model_copy(update={"deltaLegalReviews": delta_reviews})
        handoff = self.downstream_adapter.build(seed, selected.candidateId, selected.candidate, hypotheses, legal)
        self._event(RunStage.PORTFOLIO_VALIDATING, "HANDOFF_VALIDATED", handoff.compatibility,
                    f"downstream contract compatibility={handoff.compatibility}", entity_id=selected.candidateId,
                    reason=FailureCode.DOWNSTREAM_HANDOFF_INVALID if handoff.compatibility != "PASS" else None)
        return handoff

    async def run_full(self, payload: dict[str, Any] | CanonicalSeed, max_concepts: int = 5,
                       exploration_override: ExplorationBreadth | str | None = None,
        auto_confirm_hypotheses: bool = False) -> ConceptPortfolioResult:
        self._reset()
        started = time.perf_counter()
        rejected: list[RejectedPlan] = []
        legal_reviews: list[LegalReview] = []
        required_inputs: list[dict[str, Any]] = []
        concepts: list[CandidateEnvelope] = []
        selected = None
        handoff = None
        redesigned = replanned = planned = duplicates = 0
        candidate_preparation: CandidatePortfolioPreparation | None = None
        legal_preparation: LegalCandidatePreparation | None = None
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
            validated = await self.prepare_portfolio_plans(seed, analysis, max_concepts)
            rejected = validated.rejectedPlans
            duplicates = sum(item.reasonCode == FailureCode.PLAN_DUPLICATE for item in rejected)
            candidate_preparation = await self.prepare_candidate_portfolio(seed, validated, max_concepts)
            legal_preparation = await self.prepare_legal_candidates(seed, candidate_preparation.candidates)
            candidates = legal_preparation.candidates
            pre_legal_excluded = list(legal_preparation.excludedCandidates)
            planned = len(self._last_plan_pool)
            legal_reviews = await self.review_legal(seed, candidates)
            concepts, legal_reviews, required_inputs, redesigned, replanned = await self.resolve_legal(
                seed, candidate_preparation.usedPlans, candidates, legal_reviews)
            required_inputs = [*pre_legal_excluded, *required_inputs]
            concepts = concepts[:max_concepts]
            global_inputs = [item for item in required_inputs if item.get("scope") == "GLOBAL"]
            unresolved_candidates = [item for item in required_inputs if item.get("scope") == "CANDIDATE"]
            if global_inputs:
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
                    hypotheses = self.confirm_hypotheses(hypotheses, confirm_all_proposed=True)
                    assessments = self.validate_hypothesis_values(hypotheses, use_final=True)
                    if all(item.accepted for item in hypotheses) and all(
                            item.status == "VALID" for item in assessments):
                        handoff = self.build_downstream_handoff(seed, selected, hypotheses, legal_reviews)
                    else:
                        self._event(RunStage.PORTFOLIO_VALIDATING, "HYPOTHESIS_SEMANTIC_UNRESOLVED",
                                    "NOT_READY", "의미상 미확정 Hypothesis가 있어 handoff를 생성하지 않습니다.",
                                    entity_id=selected.candidateId,
                                    reason=FailureCode.DOWNSTREAM_HANDOFF_INVALID)
            self._event(terminal, "COMPLETED", status.value, f"최종 Portfolio={len(concepts)}", decision=status.value)
            duration = int((time.perf_counter() - started) * 1000)
            summary = RunSummary(safety=safety_text, requestedMaximum=max_concepts, planned=planned,
                planDuplicatesRemoved=duplicates,
                candidatesExpanded=candidate_preparation.candidateGenerated,
                candidateGenerated=candidate_preparation.candidateGenerated,
                candidateAcceptedInitially=candidate_preparation.candidateAcceptedInitially,
                candidateRegenerated=candidate_preparation.candidateRegenerated,
                candidateRecovered=candidate_preparation.candidateRecovered,
                reservePlansActivated=candidate_preparation.reservePlansActivated,
                candidateRecoveryReplans=candidate_preparation.candidateRecoveryReplans,
                legalFactCompletionAttempted=self._legal_metrics["factAttempted"],
                legalFactCompletionValidated=self._legal_metrics["factValidated"],
                legalFactCompletionAccepted=self._legal_metrics["factAccepted"],
                legalFactCompletionExhausted=self._legal_metrics["factExhausted"],
                legalRedesignAttempted=self._legal_metrics["redesignAttempted"],
                legalRedesignValidated=self._legal_metrics["redesignValidated"],
                legalRedesignAccepted=self._legal_metrics["redesignAccepted"],
                legalRedesignExhausted=self._legal_metrics["redesignExhausted"],
                legalReplanAttempted=self._legal_metrics["replanAttempted"],
                legalReplanValidated=self._legal_metrics["replanValidated"],
                legalReplanAccepted=self._legal_metrics["replanAccepted"],
                legalReplanExhausted=self._legal_metrics["replanExhausted"],
                legalAccepted=sum(item.route == LegalRoute.ACCEPT for item in legal_reviews),
                legalRedesigned=redesigned, replanned=replanned, finalPortfolio=len(concepts),
                portfolioStatus=status, selectedConcept=selected.candidate.conceptName if selected else None,
                downstreamHandoff=handoff.compatibility if handoff else "PENDING_HYPOTHESIS_CONFIRMATION",
                providerCalls=self.gateway.usage.topLevelExternalOperations, totalDurationMs=duration)
            return ConceptPortfolioResult(runId=self._context.run_id, runStatus=status, runtimeStage=terminal,
                requestedMaxConcepts=max_concepts, producedConceptCount=len(concepts), concepts=concepts,
                rejectedPlans=rejected, legalSummaries=legal_reviews, requiredInputs=required_inputs,
                unresolvedCandidates=unresolved_candidates,
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
            unresolvedCandidates=[item for item in required if item.get("scope") == "CANDIDATE"],
            providerUsage=self.gateway.usage, downstreamReadiness=readiness)


def _locked_count(seed: CanonicalSeed) -> int:
    return sum(item.decisionState == "LOCKED" and bool(item.value.strip()) for item in seed.fields)
