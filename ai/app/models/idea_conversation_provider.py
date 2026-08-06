from typing import Annotated, Any, Literal

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator

from app.models.journey import (
    OpportunityBriefDraftResult,
    OpportunityClarificationQuestion,
    OpportunityFieldKey,
)


class StrictProviderResult(BaseModel):
    model_config = ConfigDict(extra="forbid")


NonBlankText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]
StrictConfidence = Annotated[float, Field(strict=True, ge=0.0, le=1.0)]


class ProviderOpportunityFieldProposal(StrictProviderResult):
    fieldKey: OpportunityFieldKey
    valueKind: Literal["TEXT", "TEXT_LIST", "MISSING"]
    textValue: NonBlankText | None
    listValue: list[NonBlankText]
    decisionStatus: Literal["PREFERRED", "OPEN", "ASSUMPTION"]
    sourceType: Literal["SOURCE_EXTRACTED", "AI_PROPOSED", "MISSING"]
    confidence: StrictConfidence | None

    @model_validator(mode="after")
    def validate_value_shape(self):
        if self.valueKind == "TEXT":
            valid = (
                self.textValue is not None
                and self.listValue == []
                and self.sourceType in {"SOURCE_EXTRACTED", "AI_PROPOSED"}
                and self.confidence is not None
            )
        elif self.valueKind == "TEXT_LIST":
            valid = (
                self.textValue is None
                and len(self.listValue) > 0
                and self.sourceType in {"SOURCE_EXTRACTED", "AI_PROPOSED"}
                and self.confidence is not None
            )
        else:
            valid = (
                self.textValue is None
                and self.listValue == []
                and self.sourceType == "MISSING"
                and self.confidence is None
            )
        if not valid:
            raise ValueError("provider opportunity field value shape is invalid")
        return self


class ProviderOpportunityBriefDraftResult(StrictProviderResult):
    extractedFields: list[ProviderOpportunityFieldProposal]
    fieldSuggestions: list[ProviderOpportunityFieldProposal]
    assumptions: list[NonBlankText]
    openFields: list[OpportunityFieldKey]
    contradictions: list[NonBlankText]
    clarificationQuestions: Annotated[
        list[OpportunityClarificationQuestion], Field(max_length=4)
    ]
    readiness: Literal["NEEDS_INPUT", "READY_FOR_CONFIRMATION"]
    userFacingSummary: Annotated[
        str, StringConstraints(strip_whitespace=True, min_length=1, max_length=2000)
    ]

    @model_validator(mode="after")
    def require_bounded_questions(self):
        if self.readiness == "NEEDS_INPUT" and len(self.clarificationQuestions) < 2:
            raise ValueError("NEEDS_INPUT requires between two and four questions")
        return self


def provider_field_to_domain(field: ProviderOpportunityFieldProposal) -> dict[str, Any]:
    if field.valueKind == "TEXT":
        value_json: Any = field.textValue
    elif field.valueKind == "TEXT_LIST":
        value_json = field.listValue
    else:
        value_json = None
    return {
        "fieldKey": field.fieldKey,
        "valueJson": value_json,
        "decisionStatus": field.decisionStatus,
        "sourceType": field.sourceType,
        "confidence": field.confidence,
    }


def provider_result_to_domain(
    value: ProviderOpportunityBriefDraftResult | dict[str, Any],
) -> OpportunityBriefDraftResult:
    provider = (
        value if isinstance(value, ProviderOpportunityBriefDraftResult)
        else ProviderOpportunityBriefDraftResult.model_validate(value)
    )
    domain_value = provider.model_dump(mode="json")
    domain_value["extractedFields"] = [
        provider_field_to_domain(field) for field in provider.extractedFields
    ]
    domain_value["fieldSuggestions"] = [
        provider_field_to_domain(field) for field in provider.fieldSuggestions
    ]
    return OpportunityBriefDraftResult.model_validate(domain_value)


def lint_openai_strict_schema(schema: dict[str, Any]) -> list[str]:
    issues: list[str] = []

    def visit(value: Any, path: str) -> None:
        if isinstance(value, dict):
            if not value:
                issues.append(f"{path}:empty-schema")
                return
            properties = value.get("properties")
            if isinstance(properties, dict):
                if value.get("additionalProperties") is not False:
                    issues.append(f"{path}:additionalProperties")
                required = value.get("required")
                if not isinstance(required, list) or set(required) != set(properties):
                    issues.append(f"{path}:required")
                for name, child in properties.items():
                    child_path = f"{path}.properties.{name}"
                    if not isinstance(child, dict) or not ({"type", "anyOf", "$ref"} & set(child)):
                        issues.append(f"{child_path}:untyped")
                    visit(child, child_path)
            for key, child in value.items():
                if key != "properties":
                    visit(child, f"{path}.{key}")
        elif isinstance(value, list):
            for index, child in enumerate(value):
                visit(child, f"{path}[{index}]")

    visit(schema, "schema")
    return issues
