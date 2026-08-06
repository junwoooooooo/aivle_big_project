from datetime import date, datetime
from typing import Literal

from pydantic import Field, field_validator

from app.tasks.concept_candidate.models import ConceptCandidateResult, StrictModel


CANONICAL_LEGAL_FIELDS = {
    "problem", "targetCustomers", "usageContext", "targetRegion", "fixedConditions",
    "prohibitedMethods", "physicalActivity", "personalData", "payment", "requiredPartners",
}


class LegalContextField(StrictModel):
    fieldKey: str = Field(min_length=1, max_length=80)
    value: str = Field(min_length=1, max_length=20_000)
    provenance: Literal["SOURCE_EXTRACTED", "DERIVED_CONTEXT"]

    @field_validator("fieldKey")
    @classmethod
    def canonical_key(cls, value: str) -> str:
        if value not in CANONICAL_LEGAL_FIELDS:
            raise ValueError("field is not part of the canonical legal context")
        return value


class SharedLegalContext(StrictModel):
    sourceSnapshotHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    registryVersion: str = Field(min_length=1, max_length=80)
    fields: list[LegalContextField] = Field(min_length=1, max_length=10)


class OfficialEvidence(StrictModel):
    referenceIndex: int = Field(strict=True, ge=0, le=199)
    sourceType: Literal["OFFICIAL_LAW"]
    lawId: str | None = Field(default=None, max_length=100)
    officialIdentifier: str = Field(min_length=1, max_length=100)
    lawName: str = Field(min_length=1, max_length=500)
    articleReference: str = Field(min_length=1, max_length=200)
    title: str = Field(max_length=500)
    officialSourceUri: str = Field(pattern=r"^https://www\.law\.go\.kr/", max_length=1000)
    jurisdiction: Literal["KR"]
    promulgationDate: str | None = None
    effectiveDate: str | None = None
    retrievedAt: datetime
    contentHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    boundedProvisionSummary: str = Field(min_length=1, max_length=1000)
    queryKey: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    registryVersion: str = Field(min_length=1, max_length=80)


class ConceptLegalReviewInput(StrictModel):
    candidate: ConceptCandidateResult
    sharedContext: SharedLegalContext


FindingType = Literal[
    "requiredControls", "requiredPartnersAndQualifications", "requiredDisclosures", "prohibitedVariants",
]


class FindingEvidenceCoverage(StrictModel):
    findingType: FindingType
    findingIndex: int = Field(strict=True, ge=0, le=29)
    evidenceReferenceIndexes: list[int] = Field(min_length=1, max_length=20)


class ConceptLegalReviewProviderResult(StrictModel):
    status: Literal["IMPLEMENTABLE", "IMPLEMENTABLE_WITH_CONTROLS", "NEEDS_FACTS", "REDESIGNABLE", "REJECTED"]
    reviewedActivities: list[str] = Field(min_length=1, max_length=30)
    requiredControls: list[str] = Field(max_length=30)
    requiredPartnersAndQualifications: list[str] = Field(max_length=30)
    requiredDisclosures: list[str] = Field(max_length=30)
    prohibitedVariants: list[str] = Field(max_length=30)
    unknownFacts: list[str] = Field(max_length=30)
    evidenceReferenceIndexes: list[int] = Field(min_length=1, max_length=50)
    findingEvidence: list[FindingEvidenceCoverage] = Field(max_length=120)
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
    findingEvidence: list[FindingEvidenceCoverage]
    officialEvidence: list[OfficialEvidence]
    evidenceReferenceIndexes: list[int]
    expertReviewRecommended: bool
    reviewBasisDate: date
    safeUserSummary: str
    reviewLabel: Literal["공식 근거 기반 법률 구현 가능성 사전검토"]
    reviewLimitations: str = Field(min_length=1, max_length=1000)
