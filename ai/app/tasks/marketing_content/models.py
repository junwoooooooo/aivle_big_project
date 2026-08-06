from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


ContentType = Literal[
    "SOCIAL_POST", "AD_COPY", "LANDING_PAGE", "BLOG_INTRO", "EMAIL",
    "BANNER", "POSTER", "IMAGE_BRIEF",
]


class MarketingSourceSnapshot(StrictModel):
    conceptName: Any
    targetSegment: Any
    problem: Any
    valueProposition: Any
    positioning: Any
    keyFeatures: Any
    pricing: Any
    channels: Any
    competitorDifferentiators: Any
    allowedClaims: Any
    prohibitedClaims: Any
    requiredDisclosures: Any
    sourceSnapshotHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")


class MarketingContentRequest(StrictModel):
    contract: Literal["marketing-content-request-v1"]
    planningSnapshotId: str = Field(min_length=1, max_length=64)
    contentType: ContentType
    channel: str = Field(min_length=1, max_length=120)
    purpose: str = Field(min_length=1, max_length=500)
    tone: str = Field(min_length=1, max_length=100)
    length: Literal["SHORT", "MEDIUM", "LONG"]
    requiredPhrases: list[str] = Field(default_factory=list, max_length=20)
    excludedPhrases: list[str] = Field(default_factory=list, max_length=20)
    additionalInstruction: str | None = Field(default=None, max_length=2000)


class MarketingContentInput(StrictModel):
    source: MarketingSourceSnapshot
    request: MarketingContentRequest


class LegalReview(StrictModel):
    compliant: bool
    warnings: list[str] = Field(max_length=30)
    requiredDisclosuresApplied: list[str] = Field(max_length=30)


class MarketingContentResult(StrictModel):
    contract: Literal["marketing-content-result-v1"]
    contentType: ContentType
    title: str = Field(min_length=1, max_length=200)
    body: str = Field(min_length=1, max_length=20_000)
    callToAction: str | None = Field(max_length=500)
    hashtags: list[str] = Field(max_length=30)
    imageBrief: str | None = Field(max_length=4000)
    legalReview: LegalReview
    artifactRefs: list[str] = Field(max_length=20)
