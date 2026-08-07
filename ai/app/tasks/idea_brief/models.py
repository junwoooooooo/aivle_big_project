from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


FieldKey = Literal[
    "problem", "targetCustomers", "beneficiaries", "usageContext",
    "expectedOutcome", "targetRegion", "fixedConditions", "preferredConditions",
    "openDecisions", "assumptions", "prohibitedMethods", "physicalActivity",
    "personalData", "payment", "requiredPartners",
]
DecisionState = Literal["PREFERRED", "OPEN", "ASSUMPTION"]
QuestionType = Literal["FREE_TEXT", "SINGLE_SELECT", "MULTI_SELECT", "UNDECIDED"]
DerivationMode = Literal["INITIAL", "CLARIFICATION", "FINAL_SYNTHESIS"]


class IdeaBriefFieldInput(StrictModel):
    fieldKey: FieldKey
    value: str = Field(max_length=20_000)
    decisionState: Literal["LOCKED", "PREFERRED", "OPEN", "ASSUMPTION"]


class IdeaBriefFieldMetadata(StrictModel):
    fieldKey: FieldKey
    requiredForConcept: bool
    regulatorySensitive: bool


class IdeaBriefDerivationInput(StrictModel):
    mode: DerivationMode
    overview: str = Field(min_length=1, max_length=20_000)
    fields: list[IdeaBriefFieldInput] = Field(max_length=32)
    attachmentFileIds: list[int] = Field(max_length=20)
    fieldMetadata: list[IdeaBriefFieldMetadata] = Field(min_length=1, max_length=32)


class ExtractedField(StrictModel):
    fieldKey: FieldKey
    value: str = Field(min_length=1, max_length=20_000)
    decisionState: DecisionState
    sourceReference: str = Field(min_length=1, max_length=120)


class FieldSuggestion(StrictModel):
    fieldKey: FieldKey
    value: str = Field(min_length=1, max_length=20_000)
    decisionState: DecisionState
    rationale: str = Field(min_length=1, max_length=500)


class ClarificationQuestion(StrictModel):
    targetFieldKey: FieldKey
    prompt: str = Field(min_length=1, max_length=500)
    type: QuestionType
    options: list[str] = Field(max_length=12)
    allowUndecided: bool


class Contradiction(StrictModel):
    fieldKeys: list[FieldKey] = Field(min_length=2, max_length=6)
    summary: str = Field(min_length=1, max_length=500)


class Readiness(StrictModel):
    status: Literal["NEEDS_INPUT", "READY_FOR_REVIEW"]
    score: int = Field(strict=True, ge=0, le=100)
    missingFieldKeys: list[FieldKey] = Field(max_length=15)


class IdeaBriefProviderResult(StrictModel):
    extractedFields: list[ExtractedField] = Field(max_length=15)
    fieldSuggestions: list[FieldSuggestion] = Field(max_length=15)
    clarificationQuestions: list[ClarificationQuestion] = Field(max_length=4)
    contradictions: list[Contradiction] = Field(max_length=12)
    readiness: Readiness
    userFacingSummary: str = Field(min_length=1, max_length=1_000)


class DomainField(StrictModel):
    fieldKey: FieldKey
    value: str
    decisionState: DecisionState
    provenance: Literal["SOURCE_EXTRACTED", "AI_PROPOSED"]


class DomainQuestion(StrictModel):
    targetFieldKey: FieldKey
    prompt: str
    type: QuestionType
    options: list[str]


class IdeaBriefDomainResult(StrictModel):
    fields: list[DomainField]
    questions: list[DomainQuestion]
    contradictions: list[Contradiction]
    readiness: Readiness
    userFacingSummary: str
