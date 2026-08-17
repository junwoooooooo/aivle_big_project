from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


SampleSize = Literal[20, 40, 80]
Group = Literal["TARGET", "COMPARISON", "PROXY", "EXPLORATORY"]
RepresentationStatus = Literal["REPRESENTABLE_TARGET", "PARTIAL_PROXY", "EXPLORATORY_ONLY", "TARGET_UNAVAILABLE"]
AnswerField = Literal["firstImpression", "restatement", "like", "concern", "differentiation",
                      "relevance", "usageScene", "barrier", "suggestion"]
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


class TargetingContext(StrictModel):
    marketSeries: Literal["A", "B", "C", "D"]
    customerUnit: Literal["ORGANIZATION", "PERSON", "TRANSACTION", "UNKNOWN"]
    buyerType: Literal["ORGANIZATION_BUYER", "PERSON_BUYER", "UNRESOLVED_BUYER"]
    denominator: str = Field(min_length=1, max_length=200)
    reason: str = Field(min_length=1, max_length=500)


class MarketInterviewInput(StrictModel):
    contract: Literal["market-interview-input-v2"]
    schemaVersion: Literal["2.0"]
    synthetic: Literal[True]
    sampleSize: SampleSize
    source: SourceBinding
    selectedConcept: dict[str, Any]
    validatedHypotheses: dict[str, Any]
    businessModel: BusinessModelContext
    targetingContext: TargetingContext
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


class ThemeEvidence(StrictModel):
    themeTitle: str = Field(min_length=1, max_length=120)
    answerField: AnswerField
    quote: str = Field(min_length=1, max_length=500)


class CodingAssignment(StrictModel):
    participantId: str = Field(pattern=r"^R\d{3}$")
    themeTitles: list[str] = Field(max_length=18)
    themeEvidence: list[ThemeEvidence] = Field(max_length=18)
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
    group: Group


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
    group: Group


class CodingTrace(StrictModel):
    participantId: str = Field(pattern=r"^R\d{3}$")
    themeTitles: list[str] = Field(max_length=18)
    themeEvidence: list[ThemeEvidence] = Field(max_length=18)
    comprehension: Literal["accurate", "partial", "misunderstood"]
    differentiation: Literal["different", "similar", "unclear"]
    alternativeLabel: str = Field(max_length=120)
    group: Group


class RespondentFailure(StrictModel):
    participantId: str = Field(pattern=r"^R\d{3}$")
    group: Group
    attempts: int = Field(strict=True, ge=1, le=2)
    code: Literal["TRANSIENT_RETRY_EXHAUSTED", "PERMANENT_PROVIDER_FAILURE",
                  "INVALID_RESPONDENT_OUTPUT", "INVALID_CODING_OUTPUT"]


class TargetingSummary(StrictModel):
    criteria: TargetCriteria
    criteriaText: str = Field(min_length=1, max_length=3000)
    requestedSampleSize: SampleSize
    drawnSampleSize: int = Field(strict=True, ge=20, le=80)
    attemptedCount: int = Field(strict=True, ge=20, le=80)
    usableCount: int = Field(strict=True, ge=8, le=80)
    failedCount: int = Field(strict=True, ge=0, le=80)
    targetCount: int = Field(strict=True, ge=0, le=80)
    nonTargetCount: int = Field(strict=True, ge=0, le=80)
    proxyCount: int = Field(strict=True, ge=0, le=80)
    exploratoryCount: int = Field(strict=True, ge=0, le=80)
    representationStatus: RepresentationStatus
    customerUnit: Literal["ORGANIZATION", "PERSON", "TRANSACTION", "UNKNOWN"]
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
    participantCount: int = Field(strict=True, ge=8, le=80)
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
    participants: list[Participant] = Field(min_length=8, max_length=80)
    interviews: list[Interview] = Field(min_length=8, max_length=80)
    themes: list[Theme] = Field(min_length=1, max_length=36)
    crossRelationships: list[CrossRelationship] = Field(max_length=24)
    comprehension: ClassificationSummary
    differentiation: DifferentiationSummary
    objections: list[str] = Field(max_length=12)
    unmetNeeds: list[str] = Field(max_length=12)
    purchaseTriggers: list[str] = Field(max_length=12)
    followUpQuestions: list[str] = Field(min_length=3, max_length=12)
    limitations: list[str] = Field(min_length=3, max_length=8)
    transcriptProvenance: list[TranscriptProvenance] = Field(min_length=8, max_length=80)
    codingTrace: list[CodingTrace] = Field(min_length=8, max_length=80)
    respondentFailures: list[RespondentFailure] = Field(max_length=80)
    saturation: SaturationSummary

    @model_validator(mode="after")
    def identities_match(self):
        minimum_usable = max(8, (self.targeting.requestedSampleSize + 1) // 2)
        if self.targeting.usableCount < minimum_usable:
            raise ValueError("usable respondent count is below the requested-sample minimum")
        sampled = [item.participantId for item in self.transcriptProvenance]
        if len(sampled) != len(set(sampled)) or len(sampled) != self.targeting.usableCount:
            raise ValueError("sampled respondent identities must be complete and unique")
        sampled_set = set(sampled)
        group_by_id = {item.participantId: item.group for item in self.transcriptProvenance}
        failed = [item.participantId for item in self.respondentFailures]
        if (len(failed) != len(set(failed)) or len(failed) != self.targeting.failedCount
                or sampled_set.intersection(failed)):
            raise ValueError("failed respondent identities must be unique and excluded from usable responses")
        if (self.targeting.requestedSampleSize != self.targeting.drawnSampleSize
                or self.targeting.drawnSampleSize != self.targeting.attemptedCount
                or self.targeting.usableCount + self.targeting.failedCount != self.targeting.attemptedCount):
            raise ValueError("requested, attempted, usable and failed sample counts are inconsistent")
        if {item.participantId for item in self.codingTrace} != sampled_set or len(self.codingTrace) != len(sampled):
            raise ValueError("coding trace must include every sampled respondent exactly once")
        representative = [item.participantId for item in self.participants]
        if len(representative) != len(set(representative)) or set(representative) != sampled_set:
            raise ValueError("every usable participant must be available for traceability")
        if any(item.group != group_by_id.get(item.participantId) for item in self.participants):
            raise ValueError("representative participant group must match transcript provenance")
        if {item.participantId for item in self.interviews} != set(representative):
            raise ValueError("representative interviews must match representative participants")
        known_themes = {item.title for item in self.themes}
        if any(not set(item.themeTitles).issubset(known_themes) for item in self.codingTrace):
            raise ValueError("coding assignment references an unknown theme")
        if any(item.group != group_by_id.get(item.participantId) for item in self.codingTrace):
            raise ValueError("coding group must match transcript provenance")
        trace_by_id = {item.participantId: item for item in self.codingTrace}
        for trace in self.codingTrace:
            evidence_titles = [item.themeTitle for item in trace.themeEvidence]
            if len(evidence_titles) != len(set(evidence_titles)) or set(evidence_titles) != set(trace.themeTitles):
                raise ValueError("every coded theme must have one unique respondent answer evidence")
        for theme in self.themes:
            if len(theme.participantIds) != len(set(theme.participantIds)):
                raise ValueError("theme respondent identities must be unique")
            target_count = sum(group_by_id.get(item) == "TARGET" for item in theme.participantIds)
            non_target_count = sum(group_by_id.get(item) != "TARGET" for item in theme.participantIds)
            if (not set(theme.participantIds).issubset(sampled_set)
                    or theme.mentionCount != len(theme.participantIds)
                    or theme.targetCount != target_count
                    or theme.nonTargetCount != non_target_count):
                raise ValueError("theme mentionCount must be derived from respondentIds")
            if any(theme.title not in {item.themeTitle for item in trace_by_id[rid].themeEvidence}
                   for rid in theme.participantIds):
                raise ValueError("theme membership must be backed by respondent evidence")
        if any(item.overlapCount != len(item.respondentIds)
               or len(item.respondentIds) != len(set(item.respondentIds))
               or not set(item.respondentIds).issubset(sampled_set) for item in self.crossRelationships):
            raise ValueError("cross relationship must be derived from respondentIds")
        if (self.targeting.targetCount != sum(group == "TARGET" for group in group_by_id.values())
                or self.targeting.proxyCount != sum(group == "PROXY" for group in group_by_id.values())
                or self.targeting.exploratoryCount != sum(group == "EXPLORATORY" for group in group_by_id.values())
                or self.targeting.nonTargetCount != sum(group != "TARGET" for group in group_by_id.values())):
            raise ValueError("target/non-target counts must be derived from transcript provenance")
        if self.saturation.participantCount != len(sampled):
            raise ValueError("saturation participant count must equal drawn sample")
        return self
