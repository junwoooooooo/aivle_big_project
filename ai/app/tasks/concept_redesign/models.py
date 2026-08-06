from pydantic import Field

from app.tasks.concept_candidate.models import ConceptCandidateResult, StrictModel


class ConceptRedesignInput(StrictModel):
    candidate: ConceptCandidateResult
    safeConstraints: list[str] = Field(min_length=1, max_length=20)
    prohibitedVariants: list[str] = Field(max_length=20)


class ConceptRedesignResult(ConceptCandidateResult):
    pass
