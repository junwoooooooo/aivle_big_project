from app.tasks.idea_brief.models import (
    DomainQuestion,
    IdeaBriefDomainResult,
    IdeaBriefProviderResult,
)


def to_domain(result: IdeaBriefProviderResult) -> IdeaBriefDomainResult:
    return IdeaBriefDomainResult(
        safetyReview=result.safetyReview,
        interpretation=result.interpretation,
        commitmentCandidates=list(result.commitmentCandidates),
        questions=[
            DomainQuestion(
                targetFieldKey=value.targetFieldKey,
                prompt=value.prompt,
                type=value.type,
                options=list(value.options),
            )
            for value in result.clarificationQuestions
        ],
        contradictions=list(result.contradictions),
        readiness=result.readiness,
        userFacingSummary=result.userFacingSummary,
    )
