"""Frozen Core public API만 사용하는 선택 이후 thin facade."""

from __future__ import annotations

from typing import Any

from pydantic import ValidationError

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway, ProviderMode
from app.concept_portfolio_v2.adapters import CurrentDownstreamAdapter
from app.concept_portfolio_v2.models import HypothesisDecision
from app.concept_portfolio_v2.snapshot_hash import production_compatible_snapshot_hash
from app.tasks.concept_hypothesis_alternative import execute_concept_hypothesis_alternative

from .selection_models import (
    HYPOTHESIS_TYPES,
    ConceptPortfolioSelectionActionInput,
    ConceptPortfolioSelectionActionResult,
)


class ConceptPortfolioSelectionActionFacade:
    def __init__(self, *, engine: ConceptPortfolioEngine | None = None):
        self.engine = engine or ConceptPortfolioEngine(
            mode=ProviderMode.LIVE,
            gateway=ProviderGateway(ProviderMode.LIVE),
        )

    async def run(
        self, task_input: dict[str, Any] | ConceptPortfolioSelectionActionInput
    ) -> ConceptPortfolioSelectionActionResult:
        value = (
            task_input
            if isinstance(task_input, ConceptPortfolioSelectionActionInput)
            else ConceptPortfolioSelectionActionInput.model_validate(task_input)
        )
        if value.action == "PREPARE_HYPOTHESES":
            hypotheses = self.engine.build_or_load_current_hypothesis_contract(value.selectedCandidate)
            hypotheses = await self.engine.resolve_hypothesis_semantics(hypotheses, use_final=False)
            _require_seven(hypotheses)
            return ConceptPortfolioSelectionActionResult(action=value.action, hypotheses=hypotheses)
        if value.action == "CONFIRM_HYPOTHESES":
            hypotheses = self.engine.confirm_hypotheses(
                value.hypotheses,
                edits=value.edits,
                confirm_all_proposed=True,
            )
            _require_seven(hypotheses)
            return ConceptPortfolioSelectionActionResult(action=value.action, hypotheses=hypotheses)
        if value.action == "PROPOSE_ALTERNATIVE":
            raw = await execute_concept_hypothesis_alternative({
                "hypothesisType": value.hypothesisType,
                "rejectedValue": value.rejectedValue,
                "proposalVersion": value.proposalVersion,
                "candidate": value.selectedCandidate.candidate,
            })
            alternative = HypothesisDecision(
                hypothesisType=raw["hypothesisType"],
                proposedValue=raw["proposedValue"],
                source=raw["source"],
                decisionStatus=raw["decisionStatus"],
                proposalVersion=raw["proposalVersion"],
            )
            return ConceptPortfolioSelectionActionResult(action=value.action, alternative=alternative)
        if value.action == "DELTA_LEGAL":
            result = await self.engine.review_delta_legal(
                value.seed, value.selectedCandidate, value.hypotheses
            )
            hypotheses = (
                self.engine.mark_delta_legal_reviewed(value.hypotheses, result)
                if result.approved
                else value.hypotheses
            )
            return ConceptPortfolioSelectionActionResult(
                action=value.action,
                hypotheses=hypotheses,
                deltaLegalResult=result,
            )

        legal = value.baseLegalReview.model_copy(update={
            "deltaLegalReviews": [
                item.model_dump(mode="json") for item in value.approvedDeltaLegalResults
            ]
        })
        handoff = CurrentDownstreamAdapter().build(
            value.seed,
            value.selectedCandidate.candidateId,
            value.selectedCandidate.candidate,
            value.hypotheses,
            legal,
        )
        if handoff.compatibility != "PASS":
            from .service import ConceptPortfolioProductionContractError
            raise ConceptPortfolioProductionContractError(
                "AI_RESULT_INVALID",
                validation_fields=[{
                    "path": "result.handoff.compatibility",
                    "expectedType": "PASS",
                    "category": "domain_invariant",
                }],
            )
        market = handoff.marketAnalysisSeedSnapshot
        binding = value.productionBinding
        source_hash = production_compatible_snapshot_hash({
            "canonicalSeed": value.seed,
            "selectedCandidate": value.selectedCandidate,
            "finalHypotheses": value.hypotheses,
            "baseLegalReview": value.baseLegalReview,
            "approvedDeltaLegalResults": value.approvedDeltaLegalResults,
        })
        market.update({
            "snapshotId": binding.marketSeedSnapshotId,
            "projectId": binding.projectId,
            "selectionId": binding.portfolioSelectionId,
            "conceptId": binding.portfolioConceptId,
            "sourceSnapshotHash": source_hash,
        })
        snapshot_hash = production_compatible_snapshot_hash(market)
        rebound = handoff.model_copy(update={"marketAnalysisSeedSnapshot": market})
        return ConceptPortfolioSelectionActionResult(
            action=value.action,
            hypotheses=value.hypotheses,
            handoff=rebound,
            marketSeedSnapshotHash=snapshot_hash,
        )


def _require_seven(values: list[HypothesisDecision]) -> None:
    if len(values) != 7 or tuple(item.hypothesisType for item in values) != HYPOTHESIS_TYPES:
        raise ValueError("Frozen Core 7개 가정 계약이 일치하지 않습니다")


async def execute_concept_portfolio_v2_selection_action(
    task_input: dict[str, Any], *, engine: ConceptPortfolioEngine | None = None
) -> dict[str, Any]:
    from .service import ConceptPortfolioProductionContractError
    try:
        result = await ConceptPortfolioSelectionActionFacade(engine=engine).run(task_input)
        return result.model_dump(mode="json")
    except ConceptPortfolioProductionContractError:
        raise
    except ValidationError as failure:
        fields = []
        for issue in failure.errors()[:12]:
            fields.append({
                "path": "result." + ".".join(str(part) for part in issue.get("loc", ())),
                "expectedType": "valid contract value",
                "category": str(issue.get("type", "invalid"))[:80],
            })
        raise ConceptPortfolioProductionContractError(
            "AI_RESULT_INVALID", validation_fields=fields
        ) from failure
    except (AttributeError, KeyError, TypeError, ValueError) as failure:
        raise ConceptPortfolioProductionContractError(
            "AI_RESULT_INVALID",
            validation_fields=[{
                "path": "result.handoff",
                "expectedType": "valid production handoff",
                "category": failure.__class__.__name__,
            }],
        ) from failure
