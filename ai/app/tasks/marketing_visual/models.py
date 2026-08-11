from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field

from app.tasks.marketing_content.models import MarketingContentResult, MarketingSourceSnapshot


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class SourceImage(StrictModel):
    artifactId: Annotated[str, Field(min_length=1, max_length=64)]
    originalFilename: Annotated[str, Field(min_length=1, max_length=255)]
    mediaType: Literal["image/png", "image/jpeg", "image/webp"]
    sizeBytes: int = Field(gt=0, le=10 * 1024 * 1024)


class ResolvedSourceImage(StrictModel):
    bytesBase64: Annotated[str, Field(min_length=4, max_length=14_000_000)]


class VisualOptions(StrictModel):
    promotionName: Annotated[str, Field(min_length=1, max_length=100)]
    mainBanner: Annotated[str, Field(min_length=1, max_length=80)]
    supportingCopy: Annotated[str, Field(min_length=1, max_length=150)]
    mood: Literal[
        "신뢰감 있는", "밝고 친근한", "감성적인", "전문적인",
        "강렬한", "고급스러운", "미니멀한",
    ]
    bannerFormat: Literal["가로형 배너", "정사각형 SNS 광고", "세로형 모바일 광고"]
    emphasisKeywords: list[Annotated[str, Field(min_length=1, max_length=80)]] = Field(max_length=10)


class MarketingVisualInput(StrictModel):
    contract: Literal["marketing-visual-generation-input-v1"]
    marketingContentId: Annotated[str, Field(min_length=1, max_length=64)]
    marketingRevisionId: Annotated[str, Field(min_length=1, max_length=64)]
    source: MarketingSourceSnapshot
    content: MarketingContentResult
    sourceImage: SourceImage
    visual: VisualOptions
    resolvedSourceImage: ResolvedSourceImage


class GeneratedCopy(StrictModel):
    badge: Annotated[str, Field(min_length=1, max_length=120)]
    headline: Annotated[str, Field(min_length=1, max_length=200)]
    subheadline: Annotated[str, Field(min_length=1, max_length=500)]


class BannerResult(StrictModel):
    imageBase64: Annotated[str, Field(min_length=4, max_length=14_000_000)]
    mediaType: Literal["image/jpeg"]
    model: Annotated[str, Field(min_length=1, max_length=100)]
    size: Literal["1536x1024", "1024x1024", "1024x1536"]
    quality: Literal["high"]


class LegalReview(StrictModel):
    compliant: Literal[True]
    requiredDisclosuresApplied: list[Annotated[str, Field(min_length=1, max_length=500)]] = Field(max_length=30)
    requiredControlsApplied: list[Annotated[str, Field(min_length=1, max_length=500)]] = Field(max_length=30)


class MarketingVisualResult(StrictModel):
    contract: Literal["marketing-visual-generation-result-v1"]
    generatedCopy: GeneratedCopy
    promptPreview: Annotated[str, Field(min_length=1, max_length=10_000)]
    banner: BannerResult
    legalReview: LegalReview
