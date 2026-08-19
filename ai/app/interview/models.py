"""시장 인터뷰 입출력 계약.

**부동소수점을 받지 않는다.** 백엔드의 canonical hash 경로가 실수를 거부하고 그 실패는
런타임에만 터진다(`MarketResearchInputFactory.assertNoFloatingPoint`). 가격은 원 단위
정수, 표본 크기는 정수, 나머지는 문자열이다. `app/twin/models.py` 와 같은 규율이다.
"""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


#: 화면이 고르는 세 값. 정성 조사의 표준 n(8~20)에 맞춰 20 을 바닥으로 둔다.
#: 임의 정수를 받지 않는 이유는 DB CHECK·화면 슬라이더와 세 값으로 맞물려 있기 때문이다.
SampleSize = Literal[20, 40, 80]


class ConceptBoard(StrictModel):
    """응답자에게 보여줄 컨셉보드 여섯 칸.

    **스냅샷 전체가 아니다.** 확정 사업안 스냅샷에서 백엔드가 이 여섯 칸만 꺼내 보낸다
    (`MarketInterviewBoardService`). 법률 근거·평가 원문까지 프롬프트에 실으면 자극이
    상품 설명이 아니라 사업계획서가 된다.
    """

    conceptName: str = Field(min_length=1, max_length=200)
    targetUsers: str = Field(default="", max_length=1000)
    problemScenario: str = Field(default="", max_length=2000)
    featureSet: list[str] = Field(default_factory=list, max_length=30)
    #: 자유문장이다. 확정 가설의 값이 텍스트라 그 모양 그대로 받는다.
    differentiators: str = Field(default="", max_length=2000)
    #: 원 단위 정수 또는 null. 「월 9,900원」 같은 자유문장 중 깨끗이 읽히는 것만 정수로 온다.
    priceKrw: int | None = Field(default=None, strict=True, ge=0, le=100_000_000)

    def render(self) -> str:
        """자극 문장. 칸 순서가 곧 읽는 순서다 — 응답자마다 같아야 한다.

        빈 칸은 **줄째로 뺀다.** 「(없음)」을 보이면 응답자가 그 공백에 반응한다.
        """
        lines = [f"이름: {self.conceptName}"]
        if self.targetUsers.strip():
            lines.append(f"누구를 위한 것인가: {self.targetUsers.strip()}")
        if self.problemScenario.strip():
            lines.append(f"어떤 상황의 문제인가: {self.problemScenario.strip()}")
        features = [f.strip() for f in self.featureSet if f.strip()]
        if features:
            lines.append("하는 일:")
            lines.extend(f"  - {feature}" for feature in features)
        if self.differentiators.strip():
            lines.append(f"다른 것과 다른 점: {self.differentiators.strip()}")
        # 가격이 없으면 «미정»이라고 밝힌다. 여기만은 침묵이 더 나쁘다 — 값을 안 보이면
        # 응답자가 제 마음대로 값을 상상하고, 그 상상이 답에 섞인다.
        lines.append(f"가격: {self.priceKrw:,}원" if self.priceKrw is not None
                     else "가격: 아직 정해지지 않았습니다")
        return "\n".join(lines)


class MarketInterviewInput(StrictModel):
    conceptBoard: ConceptBoard
    sampleSize: SampleSize


class InterviewAnswer(StrictModel):
    """응답자 한 명의 답 — 고정 9문항과 1:1이다.

    기본값을 두지 않는다. OpenAI `json_schema` strict 모드가 **모든 속성을 required 로**
    요구하고, 여기서 기본값을 주면 그 계약이 조용히 깨진다.

    필드 순서는 `questions.QUESTIONS` 순서와 같아야 한다 —
    `tests/test_interview_questions.py` 가 그 일치를 검사한다.
    """

    firstImpression: str
    restatement: str
    like: str
    concern: str
    differentiation: str
    relevance: str
    usageScene: str
    barrier: str
    suggestion: str


#: 코딩 단계가 묶는 여섯 축. 화면의 절 순서와 같다.
AXES = ("LIKE", "CONCERN", "DIFFERENTIATION", "USAGE_SCENE", "BARRIER", "SUGGESTION")

#: 응답자 답에서 각 축이 오는 자리. 인용문을 여기서 꺼낸다.
AXIS_SOURCE = {"LIKE": "like", "CONCERN": "concern",
               "DIFFERENTIATION": "differentiation", "USAGE_SCENE": "usageScene",
               "BARRIER": "barrier", "SUGGESTION": "suggestion"}

#: 차별성 인식 3분류. 이해도와 같이 **1인 1값**이다(배정 단계가 구조로 보장한다).
DIFFERENTIATION_VERDICTS = ("different", "similar", "unclear")

#: 이해도 3분류.
COMPREHENSION = ("accurate", "partial", "misunderstood")
