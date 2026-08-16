from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class SourceBinding(StrictModel):
    marketSeedSnapshotId: str = Field(min_length=1, max_length=64)
    selectionId: int = Field(strict=True, ge=1)
    selectionRevision: int = Field(strict=True, ge=0)
    marketSeedSnapshotHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    bmPlanRevision: int = Field(strict=True, ge=0)


class BusinessModelContext(StrictModel):
    plan: dict[str, Any]
    constraints: dict[str, Any]


class MarketInterviewInput(StrictModel):
    contract: Literal["market-interview-input-v1"]
    schemaVersion: Literal["1.0"]
    synthetic: Literal[True]
    source: SourceBinding
    selectedConcept: dict[str, Any]
    validatedHypotheses: dict[str, Any]
    businessModel: BusinessModelContext
    boundaries: list[str] = Field(min_length=3, max_length=6)


class Participant(StrictModel):
    participantId: str = Field(pattern=r"^P[1-5]$")
    label: str = Field(min_length=1, max_length=80)
    profile: str = Field(min_length=1, max_length=500)
    context: str = Field(min_length=1, max_length=500)
    needs: list[str] = Field(min_length=1, max_length=8)


class InterviewAnswer(StrictModel):
    question: str = Field(min_length=1, max_length=500)
    answer: str = Field(min_length=1, max_length=1200)
    uncertainty: str = Field(min_length=1, max_length=500)


class Interview(StrictModel):
    participantId: str = Field(pattern=r"^P[1-5]$")
    questions: list[InterviewAnswer] = Field(min_length=3, max_length=10)
    concerns: list[str] = Field(max_length=8)
    purchaseTriggers: list[str] = Field(max_length=8)
    objections: list[str] = Field(max_length=8)
    unmetNeeds: list[str] = Field(max_length=8)


class Theme(StrictModel):
    title: str = Field(min_length=1, max_length=120)
    description: str = Field(min_length=1, max_length=800)
    participantIds: list[str] = Field(min_length=1, max_length=5)


class MarketInterviewResult(StrictModel):
    contract: Literal["market-interview-result-v1"]
    schemaVersion: Literal["1.0"]
    synthetic: Literal[True]
    participants: list[Participant] = Field(min_length=3, max_length=5)
    interviews: list[Interview] = Field(min_length=3, max_length=5)
    themes: list[Theme] = Field(min_length=1, max_length=12)
    objections: list[str] = Field(max_length=12)
    unmetNeeds: list[str] = Field(max_length=12)
    purchaseTriggers: list[str] = Field(max_length=12)
    followUpQuestions: list[str] = Field(min_length=3, max_length=12)
    limitations: list[str] = Field(min_length=2, max_length=8)

    @model_validator(mode="after")
    def identities_match(self):
        ids = [participant.participantId for participant in self.participants]
        if len(ids) != len(set(ids)):
            raise ValueError("participant IDs must be unique")
        if {interview.participantId for interview in self.interviews} != set(ids):
            raise ValueError("every participant must have exactly one interview")
        if any(not set(theme.participantIds).issubset(set(ids)) for theme in self.themes):
            raise ValueError("theme participant reference is invalid")
        return self
