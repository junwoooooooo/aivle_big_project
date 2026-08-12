from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ConfirmedFact(StrictModel):
    factId: str
    path: str
    value: str
    source: str
    evidenceLevel: int = 1
    status: str = "CONFIRMED_FACT"


class ExternalEvidence(StrictModel):
    evidenceId: str
    title: str
    url: str
    source: str
    evidenceLevel: int = 2
    status: str = "UPSTREAM_SOURCE"


class MarketSignal(StrictModel):
    topic: str
    value: str
    sourceType: str
    caveat: str
    basisIds: list[str] = Field(default_factory=list)


class TechOpsCommercializationInput(StrictModel):
    productSummary: str
    marketSignals: list[MarketSignal] = Field(max_length=24)
    bmAssumptions: list[str] = Field(max_length=24)
    missingInputs: list[str] = Field(max_length=24)
    layer1Facts: list[ConfirmedFact] = Field(max_length=160)
    layer2Evidence: list[ExternalEvidence] = Field(max_length=24)
