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


class TranscriptProvenance(StrictModel):
    transcriptId: str = Field(pattern=r"^T-P[1-5]$")
    participantId: str = Field(pattern=r"^P[1-5]$")
    answerCount: int = Field(strict=True, ge=3, le=10)


class CodingAssignment(StrictModel):
    participantId: str = Field(pattern=r"^P[1-5]$")
    themeTitles: list[str] = Field(max_length=12)


class SaturationSummary(StrictModel):
    participantCount: int = Field(strict=True, ge=3, le=5)
    codedParticipantCount: int = Field(strict=True, ge=0, le=5)
    themeCount: int = Field(strict=True, ge=1, le=12)
    assessment: Literal["EXPLORATORY_ONLY"]
    limitation: str = Field(min_length=1, max_length=500)


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
    transcriptProvenance: list[TranscriptProvenance] = Field(min_length=3, max_length=5)
    codingTrace: list[CodingAssignment] = Field(min_length=3, max_length=5)
    saturation: SaturationSummary

    @model_validator(mode="after")
    def identities_match(self):
        ids = [participant.participantId for participant in self.participants]
        if len(ids) != len(set(ids)):
            raise ValueError("participant IDs must be unique")
        if {interview.participantId for interview in self.interviews} != set(ids):
            raise ValueError("every participant must have exactly one interview")
        if len(self.interviews) != len(ids):
            raise ValueError("every participant must have exactly one interview")
        if any(not set(theme.participantIds).issubset(set(ids)) for theme in self.themes):
            raise ValueError("theme participant reference is invalid")
        if (len(self.transcriptProvenance) != len(ids)
                or {item.participantId for item in self.transcriptProvenance} != set(ids)):
            raise ValueError("every participant must retain transcript provenance")
        if (len(self.codingTrace) != len(ids)
                or {item.participantId for item in self.codingTrace} != set(ids)):
            raise ValueError("every participant must retain a coding assignment")
        known_themes = {theme.title for theme in self.themes}
        if any(not set(item.themeTitles).issubset(known_themes) for item in self.codingTrace):
            raise ValueError("coding assignment references an unknown theme")
        if self.saturation.participantCount != len(ids):
            raise ValueError("saturation participant count must match transcripts")
        return self


class TargetingResult(StrictModel):
    participants: list[Participant] = Field(min_length=3, max_length=5)


class TranscriptResult(StrictModel):
    interview: Interview


class CodebookTheme(StrictModel):
    title: str = Field(min_length=1, max_length=120)
    description: str = Field(min_length=1, max_length=800)


class CodebookResult(StrictModel):
    themes: list[CodebookTheme] = Field(min_length=1, max_length=12)
    followUpQuestions: list[str] = Field(min_length=3, max_length=12)


class CodingResult(StrictModel):
    assignments: list[CodingAssignment] = Field(min_length=3, max_length=5)
