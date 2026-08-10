"""Frozen Core 공개 경계를 조합하는 Candidate-only continuation facade."""

from __future__ import annotations

from typing import Any

from pydantic import ValidationError

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway, ProviderMode
from app.concept_portfolio_v2.mechanics import derive_candidate_descriptor
from app.concept_portfolio_v2.models import LegalRoute
from app.concept_portfolio_v2.providers import ProviderFailure
from app.concept_portfolio_v2.snapshot_hash import production_compatible_snapshot_hash
from app.tasks.concept_candidate.models import ConceptCandidateResult

from .continuation_models import (
    ConceptPortfolioContinuationInput,
    ConceptPortfolioContinuationResult,
)
from .models import ProductionRequiredInput, ProductionTraceEvent, ProductionTraceSummary
from .observer import ProductionObservedConceptPortfolioEngine, TraceSink
from .service import ConceptPortfolioProductionContractError


class ConceptPortfolioContinuationFacade:
    def __init__(self, *, engine: ConceptPortfolioEngine | None = None,
                 event_sink: TraceSink | None = None):
        self.engine = engine or ProductionObservedConceptPortfolioEngine(
            mode=ProviderMode.LIVE,
            gateway=ProviderGateway(ProviderMode.LIVE),
            max_replans=0,
            event_sink=event_sink,
        )
        if self.engine.max_replans != 0:
            raise ConceptPortfolioProductionContractError("AI_RESULT_INVALID")

    async def run(self, task_input: dict[str, Any] | ConceptPortfolioContinuationInput
                  ) -> ConceptPortfolioContinuationResult:
        try:
            request = (task_input if isinstance(task_input, ConceptPortfolioContinuationInput)
                       else ConceptPortfolioContinuationInput.model_validate(task_input))
            return await self._continue(request)
        except ConceptPortfolioProductionContractError:
            raise
        except (AttributeError, KeyError, TypeError, ValidationError, ValueError) as failure:
            raise ConceptPortfolioProductionContractError("AI_RESULT_INVALID") from failure

    async def _continue(self, request: ConceptPortfolioContinuationInput
                        ) -> ConceptPortfolioContinuationResult:
        context = request.continuationContext
        artifact = request.continuationArtifact
        seed = context.canonicalSeedSnapshot
        restored = await self.engine.analyze_seed(
            seed, exploration_override=context.designSnapshot.explorationBreadth
        )
        if (restored.model_dump(mode="json") != context.designSnapshot.model_dump(mode="json")
                or production_compatible_snapshot_hash(restored)
                != production_compatible_snapshot_hash(context.designSnapshot)):
            raise ConceptPortfolioProductionContractError("AI_RESULT_INVALID")
        plan = next((item for item in context.plans if item.planId == artifact.planId), None)
        if plan is None:
            raise ConceptPortfolioProductionContractError("AI_RESULT_INVALID")

        patched = self._patch_candidate(request)
        accepted, reports = await self.engine.validate_candidates(
            seed, [plan], [patched], comparison_context=list(request.comparisonConcepts)
        )
        if not accepted:
            reason = reports[0].safeSummary if reports else "Candidate validation을 통과하지 못했습니다."
            return self._result(request, "EXCLUDED", exclusion_reason=reason)
        candidate = accepted[0]
        try:
            legal = await self.engine.review_legal_candidate(seed, candidate)
        except ProviderFailure as failure:
            return self._result(request, "SYSTEM_FAILURE", failure_code=failure.code)
        if legal.route == LegalRoute.ACCEPT:
            return self._result(request, "ACCEPTED", candidate=candidate, legal_review=legal)
        if legal.route == LegalRoute.NEEDS_INPUT:
            required = self._required_input(candidate.candidateId, legal, artifact.affectedFields)
            next_artifact = artifact.model_copy(update={
                "candidateId": candidate.candidateId,
                "lineageId": candidate.lineageId,
                "candidateSnapshot": candidate,
                "latestLegalReview": legal,
                "requiredInput": required,
                "affectedFields": list(required.affectedFields),
                "canonicalHash": production_compatible_snapshot_hash(candidate),
                "acceptedPortfolioConceptIds": [item.candidateId for item in request.comparisonConcepts],
            }, deep=True)
            return self._result(request, "NEEDS_INPUT", candidate=candidate,
                                legal_review=legal, required_input=required,
                                continuation_artifact=next_artifact)
        if legal.route == LegalRoute.SYSTEM_FAILURE:
            code = str(legal.evidenceDiagnostics.get("failureCode") or "RESULT_SCHEMA_INVALID")
            return self._result(request, "SYSTEM_FAILURE", legal_review=legal,
                                failure_code=code)
        return self._result(request, "EXCLUDED", legal_review=legal,
                            exclusion_reason=legal.safeSummary)

    def _patch_candidate(self, request: ConceptPortfolioContinuationInput):
        envelope = request.continuationArtifact.candidateSnapshot
        updates = request.confirmedFacts.model_dump(exclude_none=True)
        semantics = []
        for semantic in envelope.candidate.valueSemantics:
            if semantic.fieldKey in updates:
                semantics.append(semantic.model_copy(update={
                    "source": "USER_INPUT", "authority": "LOCKED", "decision": "ACCEPTED",
                }))
            else:
                semantics.append(semantic)
        candidate_payload = envelope.candidate.model_dump(mode="json")
        candidate_payload.update(updates)
        candidate_payload["valueSemantics"] = [item.model_dump(mode="json") for item in semantics]
        candidate = ConceptCandidateResult.model_validate(candidate_payload)
        return envelope.model_copy(update={
            "candidate": candidate,
            "descriptor": derive_candidate_descriptor(candidate),
        }, deep=True)

    def _required_input(self, candidate_id, legal, fallback_fields):
        diagnostics = legal.evidenceDiagnostics if isinstance(legal.evidenceDiagnostics, dict) else {}
        affected = diagnostics.get("affectedFields")
        if not isinstance(affected, list):
            affected = list(fallback_fields)
        return ProductionRequiredInput(
            candidateId=candidate_id,
            scope=legal.inputScope or "CANDIDATE",
            unknownFacts=list(legal.unknownFacts),
            conflictingLock=legal.conflictingLock,
            currentValue=legal.currentValue,
            requiredLegalChange=legal.requiredLegalChange,
            reason=legal.reason,
            question=diagnostics.get("factQuestion") or legal.safeSummary,
            possibleUserAction=legal.possibleUserAction,
            safeSummary=legal.safeSummary,
            affectedFields=affected,
        )

    def _result(self, request, outcome, *, candidate=None, legal_review=None,
                required_input=None, continuation_artifact=None,
                exclusion_reason=None, failure_code=None):
        artifact = request.continuationArtifact
        return ConceptPortfolioContinuationResult(
            inputRequestId=request.inputRequestId,
            candidateId=(candidate.candidateId if candidate is not None else artifact.candidateId),
            lineageId=artifact.lineageId,
            outcome=outcome,
            candidate=candidate,
            legalReview=legal_review,
            requiredInput=required_input,
            continuationArtifact=continuation_artifact,
            exclusionReason=exclusion_reason,
            failureCode=failure_code,
            traceSummary=_trace_summary(self.engine),
        )


def _trace_summary(engine: ConceptPortfolioEngine) -> ProductionTraceSummary:
    trace = list(engine.trace)
    terminal = ProductionTraceEvent.from_core(trace[-1], len(trace)) if trace else None
    return ProductionTraceSummary(
        eventCount=len(trace),
        firstOccurredAt=trace[0].timestamp if trace else None,
        lastOccurredAt=trace[-1].timestamp if trace else None,
        terminalEvent=terminal,
    )


async def execute_concept_portfolio_v2_continuation(
    task_input: dict[str, Any] | ConceptPortfolioContinuationInput,
    *, engine: ConceptPortfolioEngine | None = None,
    event_sink: TraceSink | None = None,
) -> dict[str, Any]:
    result = await ConceptPortfolioContinuationFacade(
        engine=engine, event_sink=event_sink
    ).run(task_input)
    return result.model_dump(mode="json")
