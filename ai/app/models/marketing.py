from enum import Enum

from pydantic import BaseModel, Field, field_validator


class AdvertisingMood(str, Enum):
    TRUSTWORTHY = "신뢰감 있는"
    BRIGHT_FRIENDLY = "밝고 친근한"
    EMOTIONAL = "감성적인"
    PROFESSIONAL = "전문적인"
    BOLD = "강렬한"
    LUXURIOUS = "고급스러운"
    MINIMAL = "미니멀한"


class BannerFormat(str, Enum):
    LANDSCAPE = "가로형 배너"
    SQUARE = "정사각형 SNS 광고"
    PORTRAIT = "세로형 모바일 광고"


class MarketingBannerRequest(BaseModel):
    promotion_name: str = Field(min_length=1, max_length=100)
    main_banner: str = Field(min_length=1, max_length=80)
    supporting_copy: str = Field(min_length=1, max_length=150)
    mood: AdvertisingMood
    banner_format: BannerFormat
    emphasis_keywords: list[str] = Field(
        default_factory=list,
        max_length=10,
    )

    @field_validator(
        "promotion_name",
        "main_banner",
        "supporting_copy",
        mode="before",
    )
    @classmethod
    def strip_text(cls, value: str) -> str:
        if isinstance(value, str):
            return value.strip()
        return value

    @field_validator("emphasis_keywords")
    @classmethod
    def clean_keywords(cls, keywords: list[str]) -> list[str]:
        cleaned_keywords: list[str] = []
        for keyword in keywords:
            cleaned_keyword = keyword.strip()
            if (
                cleaned_keyword
                and cleaned_keyword not in cleaned_keywords
            ):
                cleaned_keywords.append(cleaned_keyword)
        return cleaned_keywords


class MarketingBannerInput(MarketingBannerRequest):
    """Normalized request data returned as part of the Mock result."""
