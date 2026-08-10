"""Candidate 단위 Concept Portfolio V2 continuation 계약."""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import Field, model_validator

from app.concept_portfolio_v2.models import CandidateEnvelope, LegalReview
from app.concept_portfolio_v2.snapshot_hash import production_compatible_snapshot_hash

from .models import (
    ConceptPortfolioContinuationArtifact,
    ConceptPortfolioContinuationContext,
    ProductionRequiredInput,
    ProductionTraceSummary,
    StrictModel,
)


CONTINUATION_INPUT_CONTRACT = "concept-portfolio-v2-continuation-input-v1"
CONTINUATION_RESULT_CONTRACT = "concept-portfolio-v2-continuation-result-v1"
CONTINUATION_SCHEMA_VERSION = "1.0"
FactText = Annotated[str, Field(min_length=1, max_length=4000)]
FactList = Annotated[list[FactText], Field(min_length=1, max_length=20)]


class ConfirmedCandidateFacts(StrictModel):
    sellerRole: FactText | None = None
    providerRole: FactText | None = None
    intermediaryRole: FactText | None = None
    transactionFlow: FactList | None = None
    paymentFlow: FactList | None = None
    partnerRequirements: FactList | None = None
    personalDataUsage: FactList | None = None
    physicalActivities: FactList | None = None

    @model_validator(mode="after")
    def contains_a_fact(self):
        if not self.model_dump(exclude_none=True):
            raise ValueError("confirmedFacts에는 최소 하나의 사실이 필요합니다")
        return self

    def keys_set(self) -> set[str]:
        return set(self.model_dump(exclude_none=True))


class ConceptPortfolioContinuationInput(StrictModel):
    contract: Literal["concept-portfolio-v2-continuation-input-v1"] = CONTINUATION_INPUT_CONTRACT
    contractVersion: Literal["1.0"] = CONTINUATION_SCHEMA_VERSION
    schemaVersion: Literal["1.0"] = CONTINUATION_SCHEMA_VERSION
    inputRequestId: Annotated[str, Field(min_length=1, max_length=64)]
    continuationContext: ConceptPortfolioContinuationContext
    continuationArtifact: ConceptPortfolioContinuationArtifact
    confirmedFacts: ConfirmedCandidateFacts
    comparisonConcepts: list[CandidateEnvelope] = Field(default_factory=list, max_length=5)

    @model_validator(mode="after")
    def references_are_consistent(self):
        artifact = self.continuationArtifact
        if artifact.candidateId != artifact.candidateSnapshot.candidateId:
            raise ValueError("Continuation Candidate reference가 일치하지 않습니다")
        if artifact.lineageId != artifact.candidateSnapshot.lineageId:
            raise ValueError("Continuation lineage reference가 일치하지 않습니다")
        if artifact.planId != artifact.candidateSnapshot.planId:
            raise ValueError("Continuation Plan reference가 일치하지 않습니다")
        if artifact.requiredInput.candidateId != artifact.candidateId:
            raise ValueError("Required Input Candidate reference가 일치하지 않습니다")
        plan_ids = {item.planId for item in self.continuationContext.plans}
        if artifact.planId not in plan_ids:
            raise ValueError("Continuation Plan snapshot이 없습니다")
        if production_compatible_snapshot_hash(
            self.continuationContext.canonicalSeedSnapshot
        ) != self.continuationContext.canonicalSeedHash:
            raise ValueError("Canonical Seed hash가 일치하지 않습니다")
        if production_compatible_snapshot_hash(artifact.candidateSnapshot) != artifact.canonicalHash:
            raise ValueError("Candidate snapshot hash가 일치하지 않습니다")
        affected = set(artifact.affectedFields or artifact.requiredInput.affectedFields)
        if affected and not self.confirmedFacts.keys_set().issubset(affected):
            raise ValueError("confirmedFacts가 affectedFields 범위를 벗어납니다")
        return self


class ConceptPortfolioContinuationResult(StrictModel):
    contract: Literal["concept-portfolio-v2-continuation-result-v1"] = CONTINUATION_RESULT_CONTRACT
    contractVersion: Literal["1.0"] = CONTINUATION_SCHEMA_VERSION
    schemaVersion: Literal["1.0"] = CONTINUATION_SCHEMA_VERSION
    inputRequestId: str
    candidateId: str
    lineageId: str
    outcome: Literal["ACCEPTED", "NEEDS_INPUT", "EXCLUDED", "SYSTEM_FAILURE"]
    candidate: CandidateEnvelope | None = None
    legalReview: LegalReview | None = None
    requiredInput: ProductionRequiredInput | None = None
    continuationArtifact: ConceptPortfolioContinuationArtifact | None = None
    exclusionReason: str | None = None
    failureCode: str | None = None
    traceSummary: ProductionTraceSummary

    @model_validator(mode="after")
    def outcome_payload_is_bounded(self):
        if self.outcome == "ACCEPTED":
            if self.candidate is None or self.legalReview is None or self.legalReview.route != "ACCEPT":
                raise ValueError("ACCEPTED result 계약이 불완전합니다")
        elif self.outcome == "NEEDS_INPUT":
            if self.requiredInput is None or self.continuationArtifact is None:
                raise ValueError("NEEDS_INPUT result 계약이 불완전합니다")
        elif self.outcome == "EXCLUDED" and not self.exclusionReason:
            raise ValueError("EXCLUDED result에는 안전한 사유가 필요합니다")
        elif self.outcome == "SYSTEM_FAILURE" and not self.failureCode:
            raise ValueError("SYSTEM_FAILURE result에는 failureCode가 필요합니다")
        return self
