from datetime import date, datetime
from typing import Literal

from pydantic import Field, model_validator

from app.tasks.concept_candidate.models import (
    Authority, DecisionStatus, StrictModel, ValueSource,
)


class GovernedText(StrictModel):
    value: str = Field(min_length=1, max_length=3000)
    source: ValueSource
    authority: Authority
    decision: DecisionStatus


class GovernedList(StrictModel):
    value: list[str] = Field(max_length=30)
    source: ValueSource
    authority: Authority
    decision: DecisionStatus


LegalSensitivity = Literal["LEGAL_SENSITIVE", "POTENTIALLY_LEGAL_SENSITIVE"]


class LegalSensitiveText(GovernedText):
    legalSensitivity: LegalSensitivity


class CommercialRoleModel(StrictModel):
    providerRole: GovernedText
    sellerRole: GovernedText
    intermediaryRole: GovernedText


class PartnerRolesModel(StrictModel):
    partnerModel: GovernedText
    partnerRequirements: GovernedList


class LegalSensitiveHypotheses(StrictModel):
    targetRegion: LegalSensitiveText
    revenueModel: LegalSensitiveText
    price: LegalSensitiveText
    channels: LegalSensitiveText
    differentiators: LegalSensitiveText


class LegalFactPattern(StrictModel):
    schemaVersion: Literal["2.0"]
    jurisdiction: Literal["KR"]
    actorRoles: GovernedList
    platformRole: GovernedText
    commercialRoles: CommercialRoleModel
    transactionFlow: GovernedList
    paymentFlow: GovernedList
    personalDataUsage: GovernedList
    physicalActivities: GovernedList
    partnerRoles: PartnerRolesModel
    qualificationRequirements: GovernedList
    advertisingClaims: GovernedList
    operatingModel: GovernedText
    hypotheses: LegalSensitiveHypotheses


ExternalFactKey = Literal[
    "existingLicenses", "mandatoryExistingPartners", "fixedJurisdiction",
    "claimedIntellectualProperty",
]


class ExternalFact(StrictModel):
    factKey: ExternalFactKey
    value: str = Field(min_length=1, max_length=20_000)
    source: Literal["USER_INPUT"]
    authority: Literal["LOCKED"]


class ExternalFactContext(StrictModel):
    sourceSnapshotHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    registryVersion: str = Field(min_length=1, max_length=80)
    facts: list[ExternalFact] = Field(max_length=4)


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
    legalFactPattern: LegalFactPattern
    factPatternHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    externalFactContext: ExternalFactContext


FindingType = Literal[
    "requiredControls", "requiredPartnersAndQualifications", "requiredDisclosures", "prohibitedVariants",
]


class FindingEvidenceCoverage(StrictModel):
    findingType: FindingType
    findingIndex: int = Field(strict=True, ge=0, le=29)
    evidenceReferenceIndexes: list[int] = Field(min_length=1, max_length=20)


class EvidenceBackedFinding(StrictModel):
    text: str = Field(min_length=1, max_length=1000)
    evidenceReferenceIndexes: list[int] = Field(min_length=1, max_length=20)


class ConceptLegalReviewProviderResult(StrictModel):
    status: Literal["IMPLEMENTABLE", "IMPLEMENTABLE_WITH_CONTROLS", "NEEDS_FACTS", "REDESIGNABLE", "REJECTED"]
    reviewedActivities: list[str] = Field(min_length=1, max_length=30)
    requiredControls: list[EvidenceBackedFinding] = Field(max_length=30)
    requiredPartnersAndQualifications: list[EvidenceBackedFinding] = Field(max_length=30)
    requiredDisclosures: list[EvidenceBackedFinding] = Field(max_length=30)
    prohibitedVariants: list[EvidenceBackedFinding] = Field(max_length=30)
    evidenceReferenceIndexes: list[int] = Field(max_length=200)
    redesignRequirements: list[str] = Field(max_length=30)
    unknownFacts: list[str] = Field(max_length=30)
    expertReviewRecommended: bool
    reviewBasisDate: date
    safeUserSummary: str = Field(min_length=1, max_length=1000)

    @model_validator(mode="after")
    def terminal_detail_is_explicit(self):
        if self.status == "REDESIGNABLE" and not self.redesignRequirements:
            raise ValueError("REDESIGNABLE requires explicit redesign requirements")
        if self.status == "NEEDS_FACTS" and not self.unknownFacts:
            raise ValueError("NEEDS_FACTS requires explicit external facts")
        if self.status in {"IMPLEMENTABLE", "IMPLEMENTABLE_WITH_CONTROLS"} and self.redesignRequirements:
            raise ValueError("implementable status must not include redesign requirements")
        if self.status in {"IMPLEMENTABLE", "IMPLEMENTABLE_WITH_CONTROLS"} and self.unknownFacts:
            raise ValueError("implementable status must not include unknown facts")
        if self.status == "NEEDS_FACTS" and self.redesignRequirements:
            raise ValueError("NEEDS_FACTS must not include redesign requirements")
        if self.status == "REDESIGNABLE" and self.unknownFacts:
            raise ValueError("REDESIGNABLE must not include external unknown facts")
        return self


class LegalQuestionClassification(StrictModel):
    question: str = Field(min_length=1, max_length=1000)
    kind: Literal["DESIGN_GAP", "UNAVOIDABLE_EXTERNAL_FACT", "CONTROL_CONVERTIBLE", "LEGAL_CLARIFICATION"]
    safeReason: str = Field(min_length=1, max_length=500)


class LegalQuestionClassificationBatch(StrictModel):
    results: list[LegalQuestionClassification] = Field(min_length=1, max_length=30)


class ConceptLegalReviewDomainResult(StrictModel):
    status: Literal["IMPLEMENTABLE", "IMPLEMENTABLE_WITH_CONTROLS", "NEEDS_FACTS", "REDESIGNABLE", "REJECTED"]
    reviewedActivities: list[str]
    requiredControls: list[str]
    requiredPartnersAndQualifications: list[str]
    requiredDisclosures: list[str]
    prohibitedVariants: list[str]
    redesignRequirements: list[str]
    unknownFacts: list[str]
    findingEvidence: list[FindingEvidenceCoverage]
    officialEvidence: list[OfficialEvidence]
    evidenceReferenceIndexes: list[int]
    expertReviewRecommended: bool
    reviewBasisDate: date
    safeUserSummary: str
    reviewedFactPatternSchemaVersion: Literal["2.0"]
    reviewedFactPatternHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    reviewLabel: Literal["공식 근거 기반 법률 구현 가능성 사전검토"]
    reviewLimitations: str = Field(min_length=1, max_length=1000)
    reviewPhase: str = "LEGAL_SOURCE"
    factCompletenessStatus: str | None = None
    legalSourceStatus: str | None = None
    finalEvidenceJudgmentExecuted: bool = False
    recoveryResolution: str | None = None
    sourceQuestionCount: int = 0
    resolvedByFactPatternCount: int = 0
    designGapCount: int = 0
    externalFactCount: int = 0
    controlConvertibleCount: int = 0
    legalClarificationCount: int = 0
