"""Concept Portfolio V2의 bounded Production integration 계약."""

from __future__ import annotations

import json
from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.concept_portfolio_v2.models import (
    CandidateEnvelope,
    CanonicalConceptDescriptor,
    CanonicalSeed,
    LegalLineageResolution,
    LegalReview,
    RunSummary,
    TraceEvent,
)


PRODUCTION_RESULT_CONTRACT = "concept-portfolio-v2-production-result-v1"
PRODUCTION_RESULT_SCHEMA_VERSION = "1.0"
CONTINUATION_CONTEXT_VERSION = "1.0"
MAX_INTERNAL_EXECUTION_BYTES = 2 * 1024 * 1024
PRODUCTION_RESULT_SAFETY_BYTES = 1536 * 1024


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ConceptPortfolioProductionInput(StrictModel):
    seed: CanonicalSeed
    maxConcepts: int = Field(default=5, strict=True, ge=1, le=5)


class ProductionTraceEvent(StrictModel):
    sequence: int = Field(strict=True, ge=1)
    stage: str
    action: str
    status: str
    safeSummary: str
    entityId: str | None = None
    parentId: str | None = None
    reasonCode: str | None = None
    decision: str | None = None
    occurredAt: datetime

    @classmethod
    def from_core(cls, event: TraceEvent, sequence: int) -> "ProductionTraceEvent":
        return cls(
            sequence=sequence,
            stage=event.stage.value,
            action=event.action,
            status=event.status,
            safeSummary=event.safeSummary,
            entityId=event.entityId,
            parentId=event.parentId,
            reasonCode=event.reasonCode.value if event.reasonCode else None,
            decision=event.decision,
            occurredAt=event.timestamp,
        )


class ProductionTraceSummary(StrictModel):
    eventCount: int = Field(strict=True, ge=0)
    firstOccurredAt: datetime | None = None
    lastOccurredAt: datetime | None = None
    terminalEvent: ProductionTraceEvent | None = None


class ProductionPreLegalExclusion(StrictModel):
    candidateId: str | None = None
    reasonCode: str | None = None
    affectedFields: list[str] = Field(default_factory=list, max_length=32)
    recoveryAttempted: bool = False
    recoveryResolution: str | None = None
    safeSummary: str | None = None

    @classmethod
    def from_engine(cls, value: dict[str, Any]) -> "ProductionPreLegalExclusion":
        fields = value.get("affectedFields")
        return cls(
            candidateId=_optional_text(value.get("candidateId")),
            reasonCode=_optional_text(value.get("reasonCode")),
            affectedFields=_bounded_text_list(fields, 32),
            recoveryAttempted=bool(value.get("recoveryAttempted", False)),
            recoveryResolution=_optional_text(value.get("recoveryResolution")),
            safeSummary=_optional_text(value.get("safeSummary")),
        )


class ConceptPortfolioContinuationArtifact(StrictModel):
    contextVersion: Literal["1.0"] = CONTINUATION_CONTEXT_VERSION
    candidateId: str
    lineageId: str
    candidateSnapshot: CandidateEnvelope
    planId: str
    planDescriptor: CanonicalConceptDescriptor
    canonicalSeedSnapshot: CanonicalSeed
    canonicalSeedHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    latestLegalReview: LegalReview
    requiredInputSnapshot: dict[str, Any]
    affectedFields: list[str] = Field(default_factory=list, max_length=32)
    parentCandidateId: str | None = None
    recoverySource: str
    canonicalHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    acceptedPortfolioConceptIds: list[str] = Field(default_factory=list, max_length=5)


class ConceptPortfolioProductionResult(StrictModel):
    contract: Literal["concept-portfolio-v2-production-result-v1"] = PRODUCTION_RESULT_CONTRACT
    contractVersion: Literal["1.0"] = PRODUCTION_RESULT_SCHEMA_VERSION
    schemaVersion: Literal["1.0"] = PRODUCTION_RESULT_SCHEMA_VERSION
    engineRunId: str
    engineStatus: str
    runtimeStage: str
    requestedMaxConcepts: int = Field(strict=True, ge=1, le=5)
    producedConceptCount: int = Field(strict=True, ge=0, le=5)
    concepts: list[CandidateEnvelope] = Field(default_factory=list, max_length=5)
    legalSummaries: list[LegalReview] = Field(default_factory=list, max_length=20)
    legalResolutions: list[LegalLineageResolution] = Field(default_factory=list, max_length=15)
    requiredInputs: list[dict[str, Any]] = Field(default_factory=list, max_length=20)
    preLegalExclusions: list[ProductionPreLegalExclusion] = Field(default_factory=list, max_length=30)
    runSummary: RunSummary | None = None
    downstreamReadiness: str
    engineDefaultConceptId: str | None = None
    userSelectedConceptId: None = None
    continuationArtifacts: list[ConceptPortfolioContinuationArtifact] = Field(default_factory=list, max_length=5)
    traceSummary: ProductionTraceSummary

    @model_validator(mode="after")
    def counts_match(self):
        if self.producedConceptCount != len(self.concepts):
            raise ValueError("producedConceptCount가 concepts 길이와 다릅니다")
        if self.producedConceptCount > self.requestedMaxConcepts:
            raise ValueError("producedConceptCount는 requestedMaxConcepts 이하여야 합니다")
        return self

    def serialized_size_bytes(self) -> int:
        value = json.dumps(
            self.model_dump(mode="json"), ensure_ascii=False, sort_keys=True, separators=(",", ":")
        )
        return len(value.encode("utf-8"))


def _optional_text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _bounded_text_list(value: Any, maximum: int) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item) for item in value if item is not None][:maximum]

