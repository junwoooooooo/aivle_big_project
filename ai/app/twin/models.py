"""트윈 조사 입출력 계약.

**부동소수점을 받지 않는다.** 백엔드의 canonical hash 가 실수를 거부하고, 그 실패는
런타임에만 터진다(`MarketResearchInputFactory.assertNoFloatingPoint`). 가격은 원 단위
정수, 표본 크기는 정수, 나머지는 문자열이다.
"""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


# 화면이 고르는 세 값. 임의 정수를 받지 않는 이유는 MDE 표를 이 세 값으로만 실측·표기하기
# 때문이다 — 표에 없는 n 을 받으면 화면이 못 보여준 측정 한계로 답하게 된다.
SampleSize = Literal[50, 100, 300]


class Side(StrictModel):
    """한 상품안. `attrs` 는 순서가 있는 «속성명 → 값» 이고 렌더 순서가 그 순서다."""

    label: str = Field(min_length=1, max_length=60)
    attrs: dict[str, str] = Field(min_length=1, max_length=8)
    priceKrw: int | None = Field(default=None, strict=True, ge=0, le=100_000_000)

    @model_validator(mode="after")
    def attrs_are_sane(self):
        for name, value in self.attrs.items():
            if not name.strip() or not value.strip():
                raise ValueError("attribute name and value must be non-empty")
            if len(name) > 40 or len(value) > 120:
                raise ValueError("attribute name/value too long")
        return self

    def render(self) -> str:
        """자극 문장. 속성은 준 순서대로, 가격은 맨 뒤."""
        parts = [f"{name} {value}" for name, value in self.attrs.items()]
        if self.priceKrw is not None:
            parts.append(f"가격 {self.priceKrw:,}원")
        return ", ".join(parts)


class Pair(StrictModel):
    pairId: str = Field(min_length=1, max_length=40)
    X: Side
    Y: Side

    @model_validator(mode="after")
    def same_attribute_space(self):
        if set(self.X.attrs) != set(self.Y.attrs):
            raise ValueError("X and Y must describe the same attribute set")
        if (self.X.priceKrw is None) != (self.Y.priceKrw is None):
            raise ValueError("price must be present on both sides or neither")
        return self

    def as_stimulus(self) -> dict:
        """분류기·프롬프트가 쓰는 모양 (`g3b_stimuli.json` 과 같은 구조)."""
        return {
            "pairId": self.pairId,
            "X": {"attrs": dict(self.X.attrs), "priceKrw": self.X.priceKrw,
                  "text": self.X.render(), "label": self.X.label},
            "Y": {"attrs": dict(self.Y.attrs), "priceKrw": self.Y.priceKrw,
                  "text": self.Y.render(), "label": self.Y.label},
        }


class TwinSurveyInput(StrictModel):
    situation: str = Field(min_length=5, max_length=300)
    pairs: list[Pair] = Field(min_length=1, max_length=4)
    sampleSize: SampleSize

    @model_validator(mode="after")
    def pair_ids_unique(self):
        ids = [p.pairId for p in self.pairs]
        if len(set(ids)) != len(ids):
            raise ValueError("pairId must be unique")
        return self
