from enum import Enum

from pydantic import BaseModel, Field, field_validator


# 광고 분위기 선택값
class AdvertisingMood(str, Enum):
    TRUSTWORTHY = "신뢰감 있는"
    BRIGHT_FRIENDLY = "밝고 친근한"
    EMOTIONAL = "감성적인"
    PROFESSIONAL = "전문적인"
    BOLD = "강렬한"
    LUXURIOUS = "고급스러운"
    MINIMAL = "미니멀한"


# 배너 형식 선택값
class BannerFormat(str, Enum):
    LANDSCAPE = "가로형 배너"
    SQUARE = "정사각형 SNS 광고"
    PORTRAIT = "세로형 모바일 광고"


# 마케팅 배너 생성 요청 데이터
class MarketingBannerRequest(BaseModel):
    promotion_name: str = Field(
        min_length=1,
        max_length=100
    )
    main_banner: str = Field(
        min_length=1,
        max_length=80
    )
    supporting_copy: str = Field(
        min_length=1,
        max_length=150
    )
    mood: AdvertisingMood
    banner_format: BannerFormat
    emphasis_keywords: list[str] = Field(
        default_factory=list,
        max_length=10
    )

    # 문자열 앞뒤 공백 제거
    @field_validator(
        "promotion_name",
        "main_banner",
        "supporting_copy",
        mode="before"
    )
    @classmethod
    def strip_text(cls, value: str) -> str:
        if isinstance(value, str):
            return value.strip()

        return value

    # 키워드의 공백과 중복 제거
    @field_validator("emphasis_keywords")
    @classmethod
    def clean_keywords(cls, keywords: list[str]) -> list[str]:
        cleaned_keywords = []

        for keyword in keywords:
            cleaned_keyword = keyword.strip()

            if (
                cleaned_keyword
                and cleaned_keyword not in cleaned_keywords
            ):
                cleaned_keywords.append(cleaned_keyword)

        return cleaned_keywords