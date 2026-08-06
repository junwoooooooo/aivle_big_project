from datetime import date
from typing import Literal

from pydantic import Field

from app.tasks.concept_candidate.models import ConceptCandidateResult, StrictModel


class OfficialEvidence(StrictModel):
    referenceIndex: int = Field(strict=True, ge=0, le=199)
    title: str = Field(min_length=1, max_length=500)
    officialSourceUri: str = Field(min_length=1, max_length=1000)
    reviewedAt: date


class SharedLegalContext(StrictModel):
    industry: str = Field(min_length=1, max_length=500)
    region: str = Field(min_length=1, max_length=500)
    platformRole: str = Field(min_length=1, max_length=1000)
    transactionStructure: str = Field(min_length=1, max_length=2000)
    payment: str = Field(min_length=1, max_length=1000)
    personalData: str = Field(min_length=1, max_length=1000)
    physicalActivities: list[str] = Field(max_length=20)
    qualificationsAndPermits: list[str] = Field(max_length=20)
    labelingAndAdvertising: list[str] = Field(max_length=20)
    officialEvidence: list[OfficialEvidence] = Field(min_length=1, max_length=200)


class ConceptLegalReviewInput(StrictModel):
    candidate: ConceptCandidateResult
    sharedContext: SharedLegalContext


class ConceptLegalReviewProviderResult(StrictModel):
    status: Literal["IMPLEMENTABLE", "IMPLEMENTABLE_WITH_CONTROLS", "NEEDS_FACTS", "REDESIGNABLE", "REJECTED"]
    reviewedActivities: list[str] = Field(min_length=1, max_length=30)
    requiredControls: list[str] = Field(max_length=30)
    requiredPartnersAndQualifications: list[str] = Field(max_length=30)
    requiredDisclosures: list[str] = Field(max_length=30)
    prohibitedVariants: list[str] = Field(max_length=30)
    unknownFacts: list[str] = Field(max_length=30)
    evidenceReferenceIndexes: list[int] = Field(min_length=1, max_length=50)
    expertReviewRecommended: bool
    reviewBasisDate: date
    safeUserSummary: str = Field(min_length=1, max_length=1000)


class ConceptLegalReviewDomainResult(StrictModel):
    status: Literal["IMPLEMENTABLE", "IMPLEMENTABLE_WITH_CONTROLS", "NEEDS_FACTS", "REDESIGNABLE", "REJECTED"]
    reviewedActivities: list[str]
    requiredControls: list[str]
    requiredPartnersAndQualifications: list[str]
    requiredDisclosures: list[str]
    prohibitedVariants: list[str]
    unknownFacts: list[str]
    evidenceReferences: list[OfficialEvidence]
    expertReviewRecommended: bool
    reviewBasisDate: date
    safeUserSummary: str
