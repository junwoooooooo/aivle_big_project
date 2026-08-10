from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, JsonValue, model_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class InternalExecutionRequestV1(StrictModel):
    contractVersion: str
    taskType: str
    taskSchemaVersion: str
    taskRunId: str = Field(min_length=1, max_length=128)
    taskAttemptId: str = Field(min_length=1, max_length=128)
    correlationId: str = Field(min_length=1, max_length=128)
    deadlineAt: str
    canonicalInputHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    locale: Literal["ko-KR"]
    input: dict[str, Any]


OpportunityFieldKey = Literal[
    "problem", "targetCustomer", "beneficiaries", "usageContext", "desiredOutcome",
    "targetRegion", "fixedConstraints", "preferredConstraints", "openDecisions",
    "assumptions", "prohibitedApproaches", "regulatorySensitiveActivities",
]


class ConversationQuestionV1(StrictModel):
    id: str = Field(min_length=1, max_length=80)
    fieldKey: OpportunityFieldKey
    prompt: str = Field(min_length=1, max_length=500)
    type: Literal["FREE_TEXT", "SINGLE_SELECT", "MULTI_SELECT", "UNDECIDED"]
    options: list[str]
    allowUndecided: bool


class TextPayloadV1(StrictModel):
    text: str


class QuestionSetPayloadV1(StrictModel):
    text: str
    questions: list[ConversationQuestionV1] = Field(max_length=4)
    contradictions: list[str]
    readiness: Literal["NEEDS_INPUT", "READY_FOR_CONFIRMATION"]


class BriefReviewPayloadV1(StrictModel):
    text: str
    contradictions: list[str]
    readiness: Literal["NEEDS_INPUT", "READY_FOR_CONFIRMATION"]


class AttachmentSummaryPayloadV1(StrictModel):
    text: str
    attachmentId: int = Field(strict=True, gt=0)


class JobStatusPayloadV1(StrictModel):
    messageKey: str = Field(min_length=1, max_length=120)
    messageParams: dict[str, JsonValue]


class ErrorPayloadV1(StrictModel):
    messageKey: str = Field(min_length=1, max_length=120)


ConversationPayloadV1 = (
    TextPayloadV1 | QuestionSetPayloadV1 | BriefReviewPayloadV1
    | AttachmentSummaryPayloadV1 | JobStatusPayloadV1 | ErrorPayloadV1
)


class ConversationEnvelopeV1(StrictModel):
    schemaVersion: Literal["1.0"]
    messageType: Literal[
        "TEXT", "QUESTION_SET", "BRIEF_REVIEW", "ATTACHMENT_SUMMARY", "JOB_STATUS", "ERROR"
    ]
    payload: ConversationPayloadV1

    @model_validator(mode="after")
    def payload_matches_message_type(self):
        expected = {
            "TEXT": TextPayloadV1,
            "QUESTION_SET": QuestionSetPayloadV1,
            "BRIEF_REVIEW": BriefReviewPayloadV1,
            "ATTACHMENT_SUMMARY": AttachmentSummaryPayloadV1,
            "JOB_STATUS": JobStatusPayloadV1,
            "ERROR": ErrorPayloadV1,
        }[self.messageType]
        if not isinstance(self.payload, expected):
            raise ValueError("message payload does not match messageType")
        return self


class ConversationMessageV1(StrictModel):
    messageId: int = Field(strict=True, gt=0)
    sequence: int = Field(strict=True, gt=0)
    role: Literal["USER", "ASSISTANT"]
    messageType: Literal[
        "TEXT", "QUESTION_SET", "BRIEF_REVIEW", "ATTACHMENT_SUMMARY", "JOB_STATUS", "ERROR"
    ]
    content: str = Field(min_length=1)
    envelope: ConversationEnvelopeV1 | None

    @model_validator(mode="after")
    def role_matches_envelope(self):
        if self.role == "USER" and (self.messageType != "TEXT" or self.envelope is not None):
            raise ValueError("USER message must be plain TEXT")
        if self.role == "ASSISTANT" and (
            self.envelope is None or self.envelope.messageType != self.messageType
        ):
            raise ValueError("ASSISTANT message requires a matching envelope")
        return self


class ConversationBriefFieldV1(StrictModel):
    valueJson: JsonValue
    decisionStatus: Literal["LOCKED", "PREFERRED", "OPEN", "ASSUMPTION"]
    sourceType: Literal[
        "USER_CONFIRMED", "SOURCE_EXTRACTED", "AI_PROPOSED", "DEFAULT_ASSUMPTION", "MISSING"
    ]
    userConfirmed: bool

    @model_validator(mode="after")
    def confirmed_source_is_consistent(self):
        if self.userConfirmed != (self.sourceType == "USER_CONFIRMED"):
            raise ValueError("user confirmation and sourceType are inconsistent")
        return self


class ConversationAttachmentV1(StrictModel):
    attachmentId: int = Field(strict=True, gt=0)
    contentHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    text: str = Field(min_length=1)


class ConversationSourceRulesV1(StrictModel):
    aiAllowed: list[Literal["SOURCE_EXTRACTED", "AI_PROPOSED", "MISSING"]] = Field(
        min_length=3, max_length=3
    )
    neverAutoConfirm: Literal[True]
    neverDefaultAssumption: Literal[True]

    @model_validator(mode="after")
    def allowed_sources_are_exact(self):
        if set(self.aiAllowed) != {"SOURCE_EXTRACTED", "AI_PROPOSED", "MISSING"}:
            raise ValueError("aiAllowed source set is invalid")
        return self


class IdeaConversationTurnInputV1(StrictModel):
    schemaVersion: Literal["1.0"]
    conversationContract: Literal["opportunity-brief-v1"]
    projectId: int = Field(strict=True, gt=0)
    ownerId: int = Field(strict=True, gt=0)
    conversationId: int = Field(strict=True, gt=0)
    sourceMessageId: int = Field(strict=True, gt=0)
    briefVersionId: int | None
    locale: Literal["ko-KR"]
    supportedFields: list[OpportunityFieldKey] = Field(min_length=12, max_length=12)
    sourceRules: ConversationSourceRulesV1
    messages: list[ConversationMessageV1] = Field(min_length=1)
    attachments: list[ConversationAttachmentV1]
    currentBrief: dict[OpportunityFieldKey, ConversationBriefFieldV1] | None
    legacyIdeaSource: str | None

    @model_validator(mode="after")
    def supported_fields_are_exact(self):
        expected = {
            "problem", "targetCustomer", "beneficiaries", "usageContext", "desiredOutcome",
            "targetRegion", "fixedConstraints", "preferredConstraints", "openDecisions",
            "assumptions", "prohibitedApproaches", "regulatorySensitiveActivities",
        }
        if set(self.supportedFields) != expected:
            raise ValueError("supportedFields set is invalid")
        sequences = [message.sequence for message in self.messages]
        if sequences != sorted(set(sequences)):
            raise ValueError("conversation message order is invalid")
        if (self.messages[-1].messageId != self.sourceMessageId
                or self.messages[-1].role != "USER"):
            raise ValueError("conversation source message is invalid")
        return self


class InternalExecutionSuccessResponseV1(StrictModel):
    contractVersion: str
    taskType: str
    taskSchemaVersion: str
    taskRunId: str
    taskAttemptId: str
    correlationId: str
    canonicalInputHash: str
    resultSchemaVersion: str
    result: dict[str, Any]
    warnings: list[dict[str, Any]]
    provenance: list[dict[str, Any]]
    usage: dict[str, Any] | None
