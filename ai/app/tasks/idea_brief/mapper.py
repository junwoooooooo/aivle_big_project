from app.tasks.idea_brief.models import (
    DomainField,
    DomainQuestion,
    IdeaBriefDomainResult,
    IdeaBriefProviderResult,
)


def to_domain(result: IdeaBriefProviderResult) -> IdeaBriefDomainResult:
    fields = [
        DomainField(
            fieldKey=value.fieldKey,
            value=value.value,
            decisionState=value.decisionState,
            provenance="SOURCE_EXTRACTED",
        )
        for value in result.extractedFields
    ]
    fields.extend(
        DomainField(
            fieldKey=value.fieldKey,
            value=value.value,
            decisionState=value.decisionState,
            provenance="AI_PROPOSED",
        )
        for value in result.fieldSuggestions
    )
    questions = [
        DomainQuestion(
            targetFieldKey=value.targetFieldKey,
            prompt=value.prompt,
            type=value.type,
            options=list(value.options),
        )
        for value in result.clarificationQuestions
    ]
    return IdeaBriefDomainResult(
        fields=fields,
        questions=questions,
        contradictions=list(result.contradictions),
        readiness=result.readiness,
        userFacingSummary=result.userFacingSummary,
    )
