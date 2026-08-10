"""Frozen Concept Portfolio V2 Core를 호출하는 thin Production facade."""

from __future__ import annotations

from typing import Any

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway, ProviderMode
from app.concept_portfolio_v2.models import ConceptPortfolioResult, LegalReview
from app.concept_portfolio_v2.snapshot_hash import production_compatible_snapshot_hash

from .models import (
    ConceptPortfolioContinuationArtifact,
    ConceptPortfolioProductionInput,
    ConceptPortfolioProductionResult,
    ProductionPreLegalExclusion,
    ProductionTraceEvent,
    ProductionTraceSummary,
)
from .observer import ProductionObservedConceptPortfolioEngine, TraceSink


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
        return self._production_result(request, result)

    def _production_result(
        self, request: ConceptPortfolioProductionInput, result: ConceptPortfolioResult
    ) -> ConceptPortfolioProductionResult:
        trace = list(result.trace)
        terminal = ProductionTraceEvent.from_core(trace[-1], len(trace)) if trace else None
        latest_legal = _latest_legal_reviews(result.legalSummaries)
        continuation = self._continuation_artifacts(request, result, latest_legal)
        return ConceptPortfolioProductionResult(
            engineRunId=result.runId,
            engineStatus=result.runStatus.value,
            runtimeStage=result.runtimeStage.value,
            requestedMaxConcepts=result.requestedMaxConcepts,
            producedConceptCount=result.producedConceptCount,
            concepts=list(result.concepts),
            legalSummaries=list(latest_legal.values()),
            legalResolutions=list(result.legalResolutions),
            requiredInputs=[dict(item) for item in result.requiredInputs],
            preLegalExclusions=[
                ProductionPreLegalExclusion.from_engine(item)
                for item in result.preLegalExclusions[:30]
            ],
            runSummary=result.runSummary,
            downstreamReadiness=result.downstreamReadiness,
            engineDefaultConceptId=result.selectedConceptId,
            userSelectedConceptId=None,
            continuationArtifacts=continuation,
            traceSummary=ProductionTraceSummary(
                eventCount=len(trace),
                firstOccurredAt=trace[0].timestamp if trace else None,
                lastOccurredAt=trace[-1].timestamp if trace else None,
                terminalEvent=terminal,
            ),
        )

    def _continuation_artifacts(
        self,
        request: ConceptPortfolioProductionInput,
        result: ConceptPortfolioResult,
        latest_legal: dict[str, LegalReview],
    ) -> list[ConceptPortfolioContinuationArtifact]:
        artifacts: list[ConceptPortfolioContinuationArtifact] = []
        accepted_ids = [item.candidateId for item in result.concepts]
        lookup = getattr(self.engine, "continuation_candidate", None)
        for required in result.requiredInputs:
            if required.get("scope") != "CANDIDATE":
                continue
            candidate_id = str(required.get("candidateId") or "").strip()
            candidate = lookup(candidate_id) if candidate_id and callable(lookup) else None
            review = latest_legal.get(candidate_id)
            if candidate is None or review is None:
                raise ValueError("CONTINUATION_CONTEXT_INCOMPLETE")
            diagnostics = review.evidenceDiagnostics if isinstance(review.evidenceDiagnostics, dict) else {}
            affected = required.get("affectedFields") or diagnostics.get("affectedFields") or []
            artifacts.append(ConceptPortfolioContinuationArtifact(
                candidateId=candidate.candidateId,
                lineageId=candidate.lineageId,
                candidateSnapshot=candidate,
                planId=candidate.planId,
                planDescriptor=candidate.descriptor,
                canonicalSeedSnapshot=request.seed,
                canonicalSeedHash=production_compatible_snapshot_hash(request.seed),
                latestLegalReview=review,
                requiredInputSnapshot=dict(required),
                affectedFields=[str(item) for item in affected][:32],
                parentCandidateId=candidate.parentCandidateId,
                recoverySource=candidate.recoverySource,
                canonicalHash=production_compatible_snapshot_hash(candidate.candidate),
                acceptedPortfolioConceptIds=accepted_ids,
            ))
        return artifacts


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
