from typing import Annotated, Literal

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    JsonValue,
)


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


ShortText = Annotated[
    str,
    Field(min_length=1, max_length=500),
]

LongText = Annotated[
    str,
    Field(min_length=1, max_length=4000),
]

EvidenceRef = Annotated[
    str,
    Field(
        min_length=3,
        max_length=200,
        pattern=r"^[A-Z_]+:.+$",
    ),
]


class SourceManifestItem(StrictModel):
    type: Annotated[
        str,
        Field(min_length=1, max_length=50),
    ]
    id: Annotated[
        str,
        Field(min_length=1, max_length=100),
    ]
    version: int | None = None
    revision: int | None = None
    resultHash: Annotated[
        str,
        Field(
            pattern=r"^sha256:[0-9a-f]{64}$",
        ),
    ] | None = None
    generatedAt: Annotated[
        str,
        Field(min_length=1, max_length=50),
    ] | None = None


class MarketingStrategyInput(StrictModel):
    contract: Literal[
        "marketing-strategy-input-v1"
    ]
    projectId: int = Field(gt=0)
    sourceManifestHash: Annotated[
        str,
        Field(
            pattern=r"^sha256:[0-9a-f]{64}$",
        ),
    ]
    sourceManifest: list[
        SourceManifestItem
    ] = Field(
        min_length=1,
        max_length=20,
    )
    sources: dict[str, JsonValue]


class ChannelStrategy(StrictModel):
    channel: ShortText
    objective: ShortText
    audience: ShortText
    actions: list[ShortText] = Field(
        min_length=1,
        max_length=10,
    )
    kpis: list[ShortText] = Field(
        min_length=1,
        max_length=10,
    )
    rationale: LongText


class CampaignPhase(StrictModel):
    phase: ShortText
    objective: ShortText
    actions: list[ShortText] = Field(
        min_length=1,
        max_length=10,
    )
    kpis: list[ShortText] = Field(
        min_length=1,
        max_length=10,
    )


class MarketingStrategyResult(StrictModel):
    contract: Literal[
        "marketing-strategy-result-v1"
    ]
    executiveSummary: LongText
    targetCustomers: list[ShortText] = Field(
        min_length=1,
        max_length=10,
    )
    positioning: LongText
    coreMessages: list[ShortText] = Field(
        min_length=1,
        max_length=10,
    )
    channelStrategies: list[
        ChannelStrategy
    ] = Field(
        min_length=1,
        max_length=10,
    )
    contentPillars: list[ShortText] = Field(
        min_length=1,
        max_length=10,
    )
    campaignRoadmap: list[
        CampaignPhase
    ] = Field(
        min_length=1,
        max_length=6,
    )
    budgetGuidelines: list[
        ShortText
    ] = Field(
        max_length=10,
    )
    risks: list[ShortText] = Field(
        min_length=1,
        max_length=20,
    )
    evidenceRefs: list[
        EvidenceRef
    ] = Field(
        min_length=1,
        max_length=50,
    )
