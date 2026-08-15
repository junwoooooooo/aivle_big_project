"""Frozen Concept Portfolio V2 Core를 호출하는 thin Production facade."""

from __future__ import annotations

from typing import Any

from pydantic import ValidationError

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway, ProviderMode
from app.concept_portfolio_v2.models import ConceptPortfolioResult, LegalReview, PortfolioPlan
from app.concept_portfolio_v2.snapshot_hash import production_compatible_snapshot_hash

from .models import (
    ConceptPortfolioContinuationArtifact,
    ConceptPortfolioContinuationContext,
    ConceptPortfolioProductionInput,
    ConceptPortfolioProductionResult,
    ProductionPreLegalExclusion,
    ProductionRequiredInput,
    ProductionRunSummary,
    ProductionTraceEvent,
    ProductionTraceSummary,
)
from .observer import ProductionObservedConceptPortfolioEngine, TraceSink


class ConceptPortfolioProductionContractError(RuntimeError):
    """Core 실패와 구분되는 Production result materialization 계약 오류."""

    def __init__(self, reason: str, *, validation_fields: list[dict[str, str]] | None = None):
        super().__init__(reason)
        self.reason = reason
        self.validation_fields = list(validation_fields or [])[:12]


class ConceptPortfolioProductionFacade:
    def __init__(
        self,
        *,
        engine: ConceptPortfolioEngine | None = None,
        event_sink: TraceSink | None = None,
    ):
        self.engine = engine or ProductionObservedConceptPortfolioEngine(
            mode=ProviderMode.LIVE,
            gateway=ProviderGateway(ProviderMode.LIVE),
            event_sink=event_sink,
        )
        if engine is not None and event_sink is not None:
            setter = getattr(engine, "set_event_sink", None)
            if callable(setter):
                setter(event_sink)

    async def run(
        self, task_input: dict[str, Any] | ConceptPortfolioProductionInput
    ) -> ConceptPortfolioProductionResult:
        request = (
            task_input
            if isinstance(task_input, ConceptPortfolioProductionInput)
            else ConceptPortfolioProductionInput.model_validate(task_input)
        )
        result = await self.engine.run_full(
            request.seed,
            max_concepts=request.maxConcepts,
            auto_confirm_hypotheses=False,
        )
        try:
            return self._production_result(request, result)
        except ConceptPortfolioProductionContractError:
            raise
        except (AttributeError, KeyError, TypeError, ValidationError, ValueError) as failure:
            raise ConceptPortfolioProductionContractError("AI_RESULT_INVALID") from failure

    def _production_result(
        self, request: ConceptPortfolioProductionInput, result: ConceptPortfolioResult
    ) -> ConceptPortfolioProductionResult:
        trace = list(result.trace)
        terminal = ProductionTraceEvent.from_core(trace[-1], len(trace)) if trace else None
        latest_legal = _latest_legal_reviews(result.legalSummaries)
        required_inputs = [
            _production_required_input(item, latest_legal) for item in result.requiredInputs
        ]
        continuation_context, continuation_artifacts = self._continuation_contract(
            request, result, latest_legal, required_inputs
        )
        return ConceptPortfolioProductionResult(
            engineRunId=result.runId,
            engineStatus=result.runStatus.value,
            runtimeStage=result.runtimeStage.value,
            requestedMaxConcepts=result.requestedMaxConcepts,
            producedConceptCount=result.producedConceptCount,
            concepts=list(result.concepts),
            legalSummaries=list(latest_legal.values()),
            legalResolutions=list(result.legalResolutions),
            requiredInputs=required_inputs,
            preLegalExclusions=[
                ProductionPreLegalExclusion.from_engine(item)
                for item in result.preLegalExclusions[:30]
            ],
            runSummary=(ProductionRunSummary.from_core(result.runSummary)
                        if result.runSummary is not None else None),
            downstreamReadiness=result.downstreamReadiness,
            engineDefaultConceptId=result.selectedConceptId,
            userSelectedConceptId=None,
            continuationContext=continuation_context,
            continuationArtifacts=continuation_artifacts,
            traceSummary=ProductionTraceSummary(
                eventCount=len(trace),
                firstOccurredAt=trace[0].timestamp if trace else None,
                lastOccurredAt=trace[-1].timestamp if trace else None,
                terminalEvent=terminal,
            ),
        )

    def _continuation_contract(
        self,
        request: ConceptPortfolioProductionInput,
        result: ConceptPortfolioResult,
        latest_legal: dict[str, LegalReview],
        required_inputs: list[ProductionRequiredInput],
    ) -> tuple[
        ConceptPortfolioContinuationContext | None,
        list[ConceptPortfolioContinuationArtifact],
    ]:
        artifacts: list[ConceptPortfolioContinuationArtifact] = []
        accepted_ids = [item.candidateId for item in result.concepts]
        candidate_lookup = getattr(self.engine, "continuation_candidate", None)
        plan_lookup = getattr(self.engine, "continuation_plan", None)
        design_lookup = getattr(self.engine, "continuation_design", None)
        candidate_inputs = [item for item in required_inputs if item.scope == "CANDIDATE"]
        if not candidate_inputs:
            return None, []
        design = design_lookup() if callable(design_lookup) else None
        if design is None:
            raise ConceptPortfolioProductionContractError("AI_RESULT_INVALID")
        plans: dict[str, PortfolioPlan] = {}
        for required in candidate_inputs:
            candidate_id = (required.candidateId or "").strip()
            candidate = (candidate_lookup(candidate_id)
                         if candidate_id and callable(candidate_lookup) else None)
            review = latest_legal.get(candidate_id)
            if candidate is None or review is None:
                raise ConceptPortfolioProductionContractError("AI_RESULT_INVALID")
            plan = plan_lookup(candidate.planId) if callable(plan_lookup) else None
            if plan is None:
                raise ConceptPortfolioProductionContractError("AI_RESULT_INVALID")
            plans[plan.planId] = plan
            artifacts.append(ConceptPortfolioContinuationArtifact(
                candidateId=candidate.candidateId,
                lineageId=candidate.lineageId,
                candidateSnapshot=candidate,
                planId=candidate.planId,
                latestLegalReview=review,
                requiredInput=required,
                affectedFields=list(required.affectedFields),
                parentCandidateId=candidate.parentCandidateId,
                recoverySource=candidate.recoverySource,
                canonicalHash=production_compatible_snapshot_hash(candidate),
                acceptedPortfolioConceptIds=accepted_ids,
            ))
        context = ConceptPortfolioContinuationContext(
            canonicalSeedSnapshot=request.seed,
            canonicalSeedHash=production_compatible_snapshot_hash(request.seed),
            designSnapshot=design,
            plans=list(plans.values()),
        )
        return context, artifacts


def _production_required_input(
    required: dict[str, Any], latest_legal: dict[str, LegalReview]
) -> ProductionRequiredInput:
    candidate_id = str(required.get("candidateId") or "").strip()
    review = latest_legal.get(candidate_id)
    diagnostics = review.evidenceDiagnostics if review is not None else {}
    affected = diagnostics.get("affectedFields") if isinstance(diagnostics, dict) else []
    return ProductionRequiredInput.from_core(required, affected_fields=affected)


async def execute_concept_portfolio_v2(
    task_input: dict[str, Any] | ConceptPortfolioProductionInput,
    *,
    engine: ConceptPortfolioEngine | None = None,
    event_sink: TraceSink | None = None,
) -> dict[str, Any]:
    result = await ConceptPortfolioProductionFacade(
        engine=engine, event_sink=event_sink
    ).run(task_input)
    return result.model_dump(mode="json")


def _latest_legal_reviews(values: list[LegalReview]) -> dict[str, LegalReview]:
    latest: dict[str, LegalReview] = {}
    for value in values:
        if value.candidateId in latest:
            del latest[value.candidateId]
        latest[value.candidateId] = value
    if len(latest) <= 20:
        return latest
    return dict(list(latest.items())[-20:])
