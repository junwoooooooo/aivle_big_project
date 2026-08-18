from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, JsonValue


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


Text = Annotated[str, Field(min_length=1, max_length=4000)]
ShortText = Annotated[str, Field(min_length=1, max_length=500)]
TableRow = Annotated[list[ShortText], Field(min_length=2, max_length=8)]


class SourceManifestItem(StrictModel):
    type: str = Field(min_length=1, max_length=50)
    id: str = Field(min_length=1, max_length=100)
    version: int | None = None
    revision: int | None = None
    resultHash: str | None = None
    generatedAt: str | None = None
    metadata: dict[str, JsonValue] | None = None


class EvidenceCatalogItem(StrictModel):
    evidenceKey: str = Field(pattern=r"^EV-[0-9a-f]{24}$")
    sourceType: str = Field(min_length=1, max_length=50)
    sourceId: str = Field(min_length=1, max_length=100)
    label: ShortText
    summary: Text
    value: Text | None = None
    sourcePath: ShortText
    asOf: str | None = None
    actualQuote: Annotated[str, Field(min_length=1, max_length=500)] | None = None
    respondentIds: list[str] | None = Field(default=None, max_length=80)
    limitation: ShortText | None = None


class FinalBusinessProposalInput(StrictModel):
    contract: Literal["final-business-proposal-input-v1"]
    projectId: int = Field(gt=0)
    version: int = Field(ge=1)
    sourceManifestHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    sourceManifest: list[SourceManifestItem] = Field(min_length=3, max_length=30)
    includedSourceTypes: list[str] = Field(min_length=3, max_length=30)
    omittedSourceTypes: list[str] = Field(max_length=30)
    sources: dict[str, JsonValue]
    evidenceCatalog: list[EvidenceCatalogItem] = Field(min_length=1, max_length=1000)
    allowedEvidenceKeys: list[str] = Field(min_length=1, max_length=1000)


class Cover(StrictModel):
    documentName: ShortText
    businessName: ShortText
    createdOn: ShortText
    version: ShortText
    documentStatus: ShortText
    approvalPlaceholder: ShortText


class ExecutiveDecisionSummary(StrictModel):
    businessDefinition: Text
    purpose: Text
    targetCustomers: list[ShortText] = Field(min_length=1, max_length=10)
    coreValue: Text
    marketEvidence: list[ShortText] = Field(max_length=10)
    financialHighlights: list[ShortText] = Field(max_length=10)
    keyRisks: list[ShortText] = Field(max_length=10)
    approvalRequest: Text
    evidenceSourceTypes: list[str] = Field(min_length=1, max_length=20)
    evidenceKeys: list[str] = Field(min_length=1, max_length=40)


class ProposalTable(StrictModel):
    title: ShortText
    columns: list[ShortText] = Field(min_length=2, max_length=8)
    rows: list[TableRow] = Field(max_length=30)


class ProposalNarrative(StrictModel):
    heading: ShortText
    body: Text


class ProposalSection(StrictModel):
    number: int = Field(ge=1, le=10)
    title: ShortText
    summary: Text
    narratives: list[ProposalNarrative] = Field(min_length=1, max_length=8)
    keyPoints: list[ShortText] = Field(max_length=20)
    tables: list[ProposalTable] = Field(max_length=8)
    evidenceSourceTypes: list[str] = Field(min_length=1, max_length=20)
    evidenceKeys: list[str] = Field(min_length=1, max_length=60)


class DecisionRequest(StrictModel):
    approvalRequests: list[ShortText] = Field(min_length=1, max_length=10)
    conditionalApprovals: list[ShortText] = Field(max_length=10)
    requiredChecks: list[ShortText] = Field(max_length=15)
    nextActions: list[ShortText] = Field(min_length=1, max_length=15)
    evidenceSourceTypes: list[str] = Field(min_length=1, max_length=20)
    evidenceKeys: list[str] = Field(min_length=1, max_length=40)


class ProposalAppendix(StrictModel):
    assumptions: list[ShortText] = Field(max_length=30)
    omittedAnalyses: list[ShortText] = Field(max_length=30)
    sourceVersions: list[ShortText] = Field(min_length=1, max_length=30)
    evidenceSourceTypes: list[str] = Field(min_length=1, max_length=30)
    evidenceKeys: list[str] = Field(min_length=1, max_length=80)


class FinalBusinessProposalResult(StrictModel):
    contract: Literal["final-business-proposal-result-v1"]
    cover: Cover
    executiveDecisionSummary: ExecutiveDecisionSummary
    sections: list[ProposalSection] = Field(min_length=8, max_length=10)
    decisionRequest: DecisionRequest
    appendix: ProposalAppendix
