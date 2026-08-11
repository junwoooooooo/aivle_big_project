from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    field_validator,
)


class GeneratedMarketingCopy(BaseModel):
    """
    gpt-4o-mini가 생성하는 광고 카피 결과.

    Structured Outputs를 사용해 항상
    동일한 JSON 구조로 응답받는다.
    """

    model_config = ConfigDict(
        extra="forbid",
        str_strip_whitespace=True,
    )

    badge: str = Field(
        description=(
            "프로모션을 짧게 표현하는 상단 라벨. "
            "사용자가 제공한 프로모션 정보를 기반으로 작성한다."
        )
    )

    headline: str = Field(
        description=(
            "사용자의 메인 배너 문구를 광고 문구처럼 "
            "확장한 핵심 헤드라인."
        )
    )

    subheadline: str = Field(
        description=(
            "사용자의 보조 문구를 자연스럽고 설득력 있게 "
            "확장한 광고 설명."
        )
    )

    @field_validator(
        "badge",
        "headline",
        "subheadline",
    )
    @classmethod
    def validate_copy_text(
        cls,
        value: str,
    ) -> str:
        """
        빈 광고 문구를 허용하지 않고
        불필요한 연속 공백과 줄바꿈을 정리한다.
        """
        normalized_value = " ".join(value.split())

        if not normalized_value:
            raise ValueError(
                "광고 카피는 비어 있을 수 없습니다."
            )

        return normalized_value