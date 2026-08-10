from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


FieldKey = Literal[
    "ideaOverview", "problem", "targetUsers", "targetRegion", "knownCompetitors",
    "revenueModel", "price", "channels", "differentiators", "budgetConstraint",
    "teamConstraint", "timelineConstraint", "otherConstraint",
]
QuestionType = Literal["FREE_TEXT", "SINGLE_SELECT", "MULTI_SELECT"]
DerivationMode = Literal["INITIAL", "CLARIFICATION", "FINAL_SYNTHESIS"]
SafetyDecision = Literal["ALLOW", "ALLOW_WITH_RESTRICTIONS", "BLOCK_OR_REFRAME"]
SafetyCategory = Literal[
    "CRIME_SUPPORT", "VIOLENCE_OR_PHYSICAL_HARM", "SEXUAL_EXPLOITATION",
    "SEXUALIZATION_OF_MINORS", "SELF_HARM_PROMOTION", "PRIVACY_ABUSE_OR_SURVEILLANCE",
    "PHISHING_IMPERSONATION_OR_DECEPTION", "HATE_OR_DISCRIMINATION",
    "DANGEROUS_OR_ILLEGAL_DISTRIBUTION", "CLEAR_EXPLOITATION",
]


class IdeaBriefFieldInput(StrictModel):
    fieldKey: FieldKey
    value: str = Field(max_length=20_000)
    decisionState: Literal["LOCKED", "OPEN"]


class IdeaBriefFieldMetadata(StrictModel):
    fieldKey: FieldKey
    requiredForConcept: bool
    regulatorySensitive: bool


class IdeaBriefDerivationInput(StrictModel):
    mode: DerivationMode
    ideaOverview: str = Field(min_length=1, max_length=20_000)
    fields: list[IdeaBriefFieldInput] = Field(max_length=20)
    attachmentFileIds: list[int] = Field(max_length=20)
    fieldMetadata: list[IdeaBriefFieldMetadata] = Field(min_length=1, max_length=20)


class SafetyReview(StrictModel):
    decision: SafetyDecision
    categories: list[SafetyCategory] = Field(max_length=10)
    restrictions: list[str] = Field(max_length=10)
    userFacingReason: str = Field(min_length=1, max_length=1_000)


class IdeaInterpretation(StrictModel):
    interpretedProblem: str = Field(min_length=1, max_length=20_000)
    interpretedTargetUsers: str = Field(min_length=1, max_length=20_000)
    usageContext: str = Field(min_length=1, max_length=20_000)
    industryCategory: str = Field(min_length=1, max_length=20_000)
    researchScope: str = Field(min_length=1, max_length=20_000)
    conciseIdeaDefinition: str = Field(min_length=1, max_length=20_000)
    targetRegionInterpretation: str = Field(max_length=20_000)
    relevantKnownCompetitorContext: str = Field(max_length=20_000)


CommitmentFieldKey = Literal[
    "targetRegion", "knownCompetitors", "revenueModel", "price", "channels",
    "differentiators", "budgetConstraint", "teamConstraint", "timelineConstraint",
    "otherConstraint",
]


class UserTextCommitmentCandidate(StrictModel):
    fieldKey: CommitmentFieldKey
    value: str = Field(min_length=1, max_length=20_000)
    evidenceQuote: str = Field(min_length=1, max_length=1_000)
    source: Literal["AI_DERIVED"]
    origin: Literal["USER_TEXT"]
    authority: Literal["REVIEWABLE"]


class ClarificationQuestion(StrictModel):
    targetFieldKey: Literal["ideaOverview", "problem", "targetUsers"]
    prompt: str = Field(min_length=1, max_length=500)
    type: QuestionType
    options: list[str] = Field(max_length=12)
    allowUndecided: Literal[False]


class Contradiction(StrictModel):
    fieldKeys: list[Literal["ideaOverview", "problem", "targetUsers"]] = Field(min_length=2, max_length=3)
    summary: str = Field(min_length=1, max_length=500)


class Readiness(StrictModel):
    status: Literal["NEEDS_INPUT", "READY_FOR_REVIEW"]
    score: int = Field(strict=True, ge=0, le=100)
    missingFieldKeys: list[Literal["ideaOverview", "problem", "targetUsers"]] = Field(max_length=3)


class IdeaBriefProviderResult(StrictModel):
    safetyReview: SafetyReview
    interpretation: IdeaInterpretation
    commitmentCandidates: list[UserTextCommitmentCandidate] = Field(max_length=10)
    clarificationQuestions: list[ClarificationQuestion] = Field(max_length=4)
    contradictions: list[Contradiction] = Field(max_length=12)
    readiness: Readiness
    userFacingSummary: str = Field(min_length=1, max_length=1_000)


class DomainQuestion(StrictModel):
    targetFieldKey: Literal["ideaOverview", "problem", "targetUsers"]
    prompt: str
    type: QuestionType
    options: list[str]


class IdeaBriefDomainResult(StrictModel):
    safetyReview: SafetyReview
    interpretation: IdeaInterpretation
    commitmentCandidates: list[UserTextCommitmentCandidate]
    questions: list[DomainQuestion]
    contradictions: list[Contradiction]
    readiness: Readiness
    userFacingSummary: str
