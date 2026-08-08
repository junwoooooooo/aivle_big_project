from pydantic import Field

from app.tasks.concept_candidate.models import ConceptCandidateResult, StrictModel
from app.tasks.concept_legal_review.models import LegalFactPattern


class ConceptRedesignInput(StrictModel):
    candidate: ConceptCandidateResult
    safeConstraints: list[str] = Field(max_length=20)
    prohibitedVariants: list[str] = Field(max_length=20)
    designGaps: list[str] = Field(min_length=1, max_length=30)
    legalFactPattern: LegalFactPattern


class ConceptRedesignResult(ConceptCandidateResult):
    pass
