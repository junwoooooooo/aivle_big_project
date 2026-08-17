from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


SampleSize = Literal[20, 40, 80]
Axis = Literal["LIKE", "CONCERN", "DIFFERENTIATION", "USAGE_SCENE", "BARRIER", "SUGGESTION"]
AXES = ("LIKE", "CONCERN", "DIFFERENTIATION", "USAGE_SCENE", "BARRIER", "SUGGESTION")
AXIS_SOURCE = {"LIKE": "like", "CONCERN": "concern", "DIFFERENTIATION": "differentiation",
               "USAGE_SCENE": "usageScene", "BARRIER": "barrier", "SUGGESTION": "suggestion"}


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
    contract: Literal["market-interview-input-v2"]
    schemaVersion: Literal["2.0"]
    synthetic: Literal[True]
    sampleSize: SampleSize
    source: SourceBinding
    selectedConcept: dict[str, Any]
    validatedHypotheses: dict[str, Any]
    businessModel: BusinessModelContext
    boundaries: list[str] = Field(min_length=3, max_length=6)


class TargetCriteria(StrictModel):
    ageMin: int = Field(strict=True, ge=0, le=120)
    ageMax: int = Field(strict=True, ge=0, le=120)
    genders: list[Literal["남성", "여성"]] = Field(max_length=2)
    householdSizeMin: int = Field(strict=True, ge=0, le=20)
    householdSizeMax: int = Field(strict=True, ge=0, le=20)
    regions: list[str] = Field(max_length=20)
    incomeKeywords: list[str] = Field(max_length=10)
    jobKeywords: list[str] = Field(max_length=15)
    hasChildren: int = Field(strict=True, ge=0, le=2)
    householdRoles: list[Literal["가구주", "가구주의 배우자", "가구주의 자녀", "부모"]] = Field(max_length=4)


class TargetingResult(StrictModel):
    criteria: TargetCriteria


class PanelAnswer(StrictModel):
    firstImpression: str = Field(min_length=1, max_length=1200)
    restatement: str = Field(min_length=1, max_length=1200)
    like: str = Field(min_length=1, max_length=1200)
    concern: str = Field(min_length=1, max_length=1200)
    differentiation: str = Field(min_length=1, max_length=1200)
    relevance: str = Field(min_length=1, max_length=1200)
    usageScene: str = Field(min_length=1, max_length=1200)
    barrier: str = Field(min_length=1, max_length=1200)
    suggestion: str = Field(min_length=1, max_length=1200)


class PanelAnswerResult(StrictModel):
    participantId: str = Field(pattern=r"^R\d{3}$")
    answers: PanelAnswer


class CodebookTheme(StrictModel):
    axis: Axis
    title: str = Field(min_length=1, max_length=120)
    description: str = Field(min_length=1, max_length=800)


class CodebookResult(StrictModel):
    themes: list[CodebookTheme] = Field(min_length=6, max_length=36)
    alternatives: list[str] = Field(max_length=8)
    followUpQuestions: list[str] = Field(min_length=3, max_length=12)


class CodingAssignment(StrictModel):
    participantId: str = Field(pattern=r"^R\d{3}$")
    themeTitles: list[str] = Field(max_length=18)
    alternativeLabel: str = Field(max_length=120)
    comprehension: Literal["accurate", "partial", "misunderstood"]
    differentiation: Literal["different", "similar", "unclear"]


class CodingResult(StrictModel):
    assignments: list[CodingAssignment] = Field(min_length=1, max_length=8)


class Participant(StrictModel):
    participantId: str = Field(pattern=r"^R\d{3}$")
    label: str = Field(min_length=1, max_length=80)
    profile: str = Field(min_length=1, max_length=500)
    context: str = Field(min_length=1, max_length=500)
    needs: list[str] = Field(max_length=8)
    group: Literal["TARGET", "COMPARISON"]


class InterviewAnswer(StrictModel):
    question: str = Field(min_length=1, max_length=500)
    answer: str = Field(min_length=1, max_length=1200)
    uncertainty: str = Field(min_length=1, max_length=500)


class Interview(StrictModel):
    participantId: str = Field(pattern=r"^R\d{3}$")
    questions: list[InterviewAnswer] = Field(min_length=9, max_length=9)
    concerns: list[str] = Field(max_length=8)
    purchaseTriggers: list[str] = Field(max_length=8)
    objections: list[str] = Field(max_length=8)
    unmetNeeds: list[str] = Field(max_length=8)


class Theme(StrictModel):
    axis: Axis
    title: str = Field(min_length=1, max_length=120)
    description: str = Field(min_length=1, max_length=800)
    participantIds: list[str] = Field(min_length=1, max_length=80)
    mentionCount: int = Field(strict=True, ge=1, le=80)
    targetCount: int = Field(strict=True, ge=0, le=80)
    nonTargetCount: int = Field(strict=True, ge=0, le=80)
    quote: str = Field(min_length=1, max_length=1200)


class CrossRelationship(StrictModel):
    suggestionTitle: str = Field(min_length=1, max_length=120)
    relatedAxis: Literal["CONCERN", "BARRIER"]
    relatedTitle: str = Field(min_length=1, max_length=120)
    respondentIds: list[str] = Field(min_length=1, max_length=80)
    overlapCount: int = Field(strict=True, ge=1, le=80)


class TranscriptProvenance(StrictModel):
    transcriptId: str = Field(pattern=r"^T-R\d{3}$")
    participantId: str = Field(pattern=r"^R\d{3}$")
    answerCount: Literal[9]
    group: Literal["TARGET", "COMPARISON"]


class CodingTrace(StrictModel):
    participantId: str = Field(pattern=r"^R\d{3}$")
    themeTitles: list[str] = Field(max_length=18)
    comprehension: Literal["accurate", "partial", "misunderstood"]
    differentiation: Literal["different", "similar", "unclear"]
    alternativeLabel: str = Field(max_length=120)
    group: Literal["TARGET", "COMPARISON"]


class TargetingSummary(StrictModel):
    criteria: TargetCriteria
    criteriaText: str = Field(min_length=1, max_length=3000)
    requestedSampleSize: SampleSize
    drawnSampleSize: int = Field(strict=True, ge=20, le=80)
    targetCount: int = Field(strict=True, ge=0, le=80)
    nonTargetCount: int = Field(strict=True, ge=0, le=80)
    targetCoverageWarning: str | None = Field(default=None, max_length=500)


class ClassificationSummary(StrictModel):
    accurate: int = Field(strict=True, ge=0, le=80)
    partial: int = Field(strict=True, ge=0, le=80)
    misunderstood: int = Field(strict=True, ge=0, le=80)


class DifferentiationSummary(StrictModel):
    different: int = Field(strict=True, ge=0, le=80)
    similar: int = Field(strict=True, ge=0, le=80)
    unclear: int = Field(strict=True, ge=0, le=80)


class SaturationSummary(StrictModel):
    participantCount: int = Field(strict=True, ge=20, le=80)
    codedParticipantCount: int = Field(strict=True, ge=0, le=80)
    themeCount: int = Field(strict=True, ge=1, le=36)
    axisLabelCounts: dict[str, int]
    maxMentionByAxis: dict[str, int]
    saturatedThemes: list[str] = Field(max_length=36)
    alternativeSum: int = Field(strict=True, ge=0, le=80)
    assessment: Literal["EXPLORATORY_ONLY"]
    limitation: str = Field(min_length=1, max_length=500)


class MarketInterviewResult(StrictModel):
    contract: Literal["market-interview-result-v2"]
    schemaVersion: Literal["2.0"]
    synthetic: Literal[True]
    source: SourceBinding
    targeting: TargetingSummary
    participants: list[Participant] = Field(min_length=1, max_length=5)
    interviews: list[Interview] = Field(min_length=1, max_length=5)
    themes: list[Theme] = Field(min_length=1, max_length=36)
    crossRelationships: list[CrossRelationship] = Field(max_length=24)
    comprehension: ClassificationSummary
    differentiation: DifferentiationSummary
    objections: list[str] = Field(max_length=12)
    unmetNeeds: list[str] = Field(max_length=12)
    purchaseTriggers: list[str] = Field(max_length=12)
    followUpQuestions: list[str] = Field(min_length=3, max_length=12)
    limitations: list[str] = Field(min_length=3, max_length=8)
    transcriptProvenance: list[TranscriptProvenance] = Field(min_length=20, max_length=80)
    codingTrace: list[CodingTrace] = Field(min_length=20, max_length=80)
    saturation: SaturationSummary

    @model_validator(mode="after")
    def identities_match(self):
        sampled = [item.participantId for item in self.transcriptProvenance]
        if len(sampled) != len(set(sampled)) or len(sampled) != self.targeting.drawnSampleSize:
            raise ValueError("sampled respondent identities must be complete and unique")
        sampled_set = set(sampled)
        if {item.participantId for item in self.codingTrace} != sampled_set or len(self.codingTrace) != len(sampled):
            raise ValueError("coding trace must include every sampled respondent exactly once")
        representative = [item.participantId for item in self.participants]
        if len(representative) != len(set(representative)):
            raise ValueError("representative participant IDs must be unique")
        if {item.participantId for item in self.interviews} != set(representative):
            raise ValueError("representative interviews must match representative participants")
        known_themes = {item.title for item in self.themes}
        if any(not set(item.themeTitles).issubset(known_themes) for item in self.codingTrace):
            raise ValueError("coding assignment references an unknown theme")
        for theme in self.themes:
            if (not set(theme.participantIds).issubset(sampled_set)
                    or theme.mentionCount != len(theme.participantIds)
                    or theme.targetCount + theme.nonTargetCount != theme.mentionCount):
                raise ValueError("theme mentionCount must be derived from respondentIds")
        if any(item.overlapCount != len(item.respondentIds)
               or not set(item.respondentIds).issubset(sampled_set) for item in self.crossRelationships):
            raise ValueError("cross relationship must be derived from respondentIds")
        if self.targeting.targetCount + self.targeting.nonTargetCount != len(sampled):
            raise ValueError("target/non-target counts must equal drawn sample")
        if self.saturation.participantCount != len(sampled):
            raise ValueError("saturation participant count must equal drawn sample")
        return self
