from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


ShortText = Annotated[str, Field(min_length=1, max_length=500)]
FeatureText = Annotated[str, Field(min_length=1, max_length=300)]
ClaimText = Annotated[str, Field(min_length=1, max_length=500)]
ArtifactRef = Annotated[str, Field(min_length=1, max_length=300)]
HashText = Annotated[str, Field(pattern=r"^sha256:[0-9a-f]{64}$")]

ContentType = Literal[
    "SOCIAL_POST", "AD_COPY", "LANDING_PAGE", "BLOG_INTRO", "EMAIL",
    "BANNER", "POSTER", "IMAGE_BRIEF",
]


class PreMarketSomShare(StrictModel):
    targetSharePercent: float = Field(gt=0)
    horizonYears: int = Field(gt=0)
    rationale: Annotated[str, Field(min_length=0, max_length=1000)] = ""
    assumptions: list[ClaimText] = Field(default_factory=list, max_length=30)


class PreMarketSom(StrictModel):
    amount: float = Field(gt=0)
    currency: Annotated[str, Field(min_length=1, max_length=20)]
    period: Annotated[str, Field(min_length=0, max_length=100)] = ""
    calculationBasis: Annotated[str, Field(min_length=0, max_length=1000)] = ""
    assumptions: list[ClaimText] = Field(default_factory=list, max_length=30)
    confidence: Annotated[str, Field(min_length=0, max_length=30)] = ""


class OfficialEvidenceReference(StrictModel):
    referenceIndex: int = -1
    sourceType: Annotated[str, Field(min_length=0, max_length=30)] = ""
    lawId: Annotated[str, Field(min_length=0, max_length=100)] = ""
    officialIdentifier: Annotated[str, Field(min_length=0, max_length=100)] = ""
    lawName: Annotated[str, Field(min_length=0, max_length=500)] = ""
    articleReference: Annotated[str, Field(min_length=0, max_length=200)] = ""
    title: Annotated[str, Field(min_length=0, max_length=500)] = ""
    officialSourceUri: Annotated[str, Field(min_length=0, max_length=1000)] = ""
    jurisdiction: Annotated[str, Field(min_length=0, max_length=10)] = ""
    promulgationDate: Annotated[str, Field(min_length=0, max_length=30)] = ""
    effectiveDate: Annotated[str, Field(min_length=0, max_length=30)] = ""
    retrievedAt: Annotated[str, Field(min_length=0, max_length=50)] = ""
    contentHash: HashText | None = None
    registryVersion: Annotated[str, Field(min_length=0, max_length=80)] = ""


class MarketingSourceSnapshot(StrictModel):
    contract: Literal["marketing-source-snapshot-v1"]
    schemaVersion: Literal["2.0"]
    snapshotId: Annotated[str, Field(min_length=1, max_length=64)]
    hash: HashText
    createdAt: Annotated[str, Field(min_length=1, max_length=50)]
    projectId: int
    selectionId: int
    conceptId: Annotated[str, Field(min_length=1, max_length=64)]
    marketAnalysisSeedSnapshotId: Annotated[str, Field(min_length=1, max_length=64)]
    marketAnalysisSeedSnapshotHash: HashText
    conceptName: ShortText
    targetSegment: ShortText
    problem: ShortText
    valueProposition: ShortText
    positioning: ShortText
    keyFeatures: list[FeatureText] = Field(min_length=1, max_length=30)
    pricing: ShortText
    targetRegion: ShortText
    revenueModel: ShortText
    price: ShortText
    channels: list[FeatureText] = Field(min_length=1, max_length=20)
    competitorDifferentiators: list[FeatureText] = Field(max_length=30)
    preMarketSomShare: PreMarketSomShare
    preMarketSom: PreMarketSom
    legalStatus: ShortText
    allowedClaims: list[ClaimText] = Field(max_length=30)
    prohibitedClaims: list[ClaimText] = Field(max_length=30)
    requiredDisclosures: list[ClaimText] = Field(max_length=30)
    requiredControls: list[ClaimText] = Field(max_length=30)
    communicationRequiredControls: list[ClaimText] = Field(max_length=30)
    officialEvidenceReferences: list[OfficialEvidenceReference] = Field(max_length=200)
    sourceSnapshotHash: HashText


class MarketingContentRequest(StrictModel):
    contract: Literal["marketing-content-request-v1"]
    marketingSourceSnapshotId: Annotated[str, Field(min_length=1, max_length=64)]
    contentType: ContentType
    channel: Annotated[str, Field(min_length=1, max_length=120)]
    purpose: Annotated[str, Field(min_length=1, max_length=500)]
    tone: Annotated[str, Field(min_length=1, max_length=100)]
    length: Literal["SHORT", "MEDIUM", "LONG"]
    requiredPhrases: list[ClaimText] = Field(default_factory=list, max_length=20)
    excludedPhrases: list[ClaimText] = Field(default_factory=list, max_length=20)
    additionalInstruction: Annotated[str, Field(min_length=1, max_length=2000)] | None = None


class MarketingContentInput(StrictModel):
    source: MarketingSourceSnapshot
    request: MarketingContentRequest


class LegalReview(StrictModel):
    compliant: bool
    warnings: list[ClaimText] = Field(max_length=30)
    requiredDisclosuresApplied: list[ClaimText] = Field(max_length=30)


class MarketingContentResult(StrictModel):
    contract: Literal["marketing-content-result-v1"]
    contentType: ContentType
    title: Annotated[str, Field(min_length=1, max_length=200)]
    body: Annotated[str, Field(min_length=1, max_length=20_000)]
    callToAction: Annotated[str, Field(min_length=1, max_length=500)] | None
    hashtags: list[Annotated[str, Field(min_length=1, max_length=100)]] = Field(max_length=30)
    imageBrief: Annotated[str, Field(min_length=1, max_length=4000)] | None
    legalReview: LegalReview
    artifactRefs: list[ArtifactRef] = Field(max_length=0)


def lint_provider_schema(schema: dict) -> list[str]:
    """Reject provider schemas that cannot be enforced as bounded strict JSON."""
    issues: list[str] = []

    def visit(value: object, path: str) -> None:
        if not isinstance(value, dict):
            return
        if "$ref" in value:
            return
        for branch in value.get("anyOf", []):
            visit(branch, f"{path}.anyOf")
        schema_type = value.get("type")
        if schema_type == "object" or "properties" in value:
            properties = value.get("properties")
            if not isinstance(properties, dict) or not properties:
                issues.append(f"{path}:unconstrained-object")
                return
            if value.get("additionalProperties") is not False:
                issues.append(f"{path}:additionalProperties")
            for name, child in properties.items():
                if not isinstance(child, dict) or not ({"type", "anyOf", "$ref", "const", "enum"} & child.keys()):
                    issues.append(f"{path}.{name}:untyped")
                visit(child, f"{path}.{name}")
        elif schema_type == "array":
            if "maxItems" not in value or not isinstance(value.get("items"), dict):
                issues.append(f"{path}:unbounded-array")
            visit(value.get("items"), f"{path}.items")
        elif schema_type == "string" and not ({"enum", "const", "pattern"} & value.keys()):
            if "minLength" not in value or "maxLength" not in value:
                issues.append(f"{path}:unbounded-string")

    visit(schema, "schema")
    for name, definition in schema.get("$defs", {}).items():
        visit(definition, f"schema.$defs.{name}")
    return issues
