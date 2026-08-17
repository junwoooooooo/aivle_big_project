import json

from pydantic import BaseModel, ConfigDict, Field, ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.final_business_proposal.service import _close_evidence_vocabulary
from app.tasks.marketing_content.models import lint_provider_schema


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ReviewItem(StrictModel):
    rubric: str = Field(min_length=1, max_length=120)
    finding: str = Field(min_length=1, max_length=1500)
    evidenceSourceTypes: list[str] = Field(min_length=1, max_length=20)


class ProposalReviewResult(StrictModel):
    contract: str = Field(pattern=r"^final-business-proposal-review-v1$")
    wellPrepared: list[ReviewItem] = Field(max_length=12)
    needsImprovement: list[ReviewItem] = Field(max_length=12)
    requiredBeforeApproval: list[ReviewItem] = Field(max_length=12)
    followUpActions: list[ReviewItem] = Field(max_length=12)


async def execute_final_business_proposal_review(task_input: dict) -> dict:
    proposal = task_input.get("proposal")
    manifest = task_input.get("sourceManifest")
    if not isinstance(proposal, dict) or not isinstance(manifest, list) or not manifest:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False)
    allowed = sorted({str(item.get("type")) for item in manifest if item.get("type")})
    schema = ProposalReviewResult.model_json_schema()
    _close_evidence_vocabulary(schema, allowed)
    if lint_provider_schema(schema):
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "PROVIDER_RESPONSE_SCHEMA_REJECTED", 502, False)
    prompt = """사업기획서를 독립 검토한다. 사업 논리, 고객·문제, 시장 근거, BM, 재무 준비도,
기술·운영 실행성, 법률·규제, 미확인 가정, 의사결정 준비도를 확인한다.
입력 밖 사실과 조언 근거를 만들지 않는다. evidenceSourceTypes는 허용 목록에서만 고른다.
잘 갖춰진 부분, 보완 필요, 결재 전 필수 확인, 후속 조치로 나눠 JSON만 반환한다."""
    raw = await execute_structured_prompt(
        prompt, json.dumps({**task_input, "allowedEvidenceSourceTypes": allowed}, ensure_ascii=False),
        response_schema=schema, schema_name="final_business_proposal_review_v1",
        task_type="FINAL_BUSINESS_PROPOSAL_REVIEW",
    )
    try:
        result = ProposalReviewResult.model_validate(raw)
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    invalid = [source_type for group in (result.wellPrepared, result.needsImprovement,
        result.requiredBeforeApproval, result.followUpActions) for item in group
        for source_type in item.evidenceSourceTypes if source_type not in allowed]
    if invalid:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_EVIDENCE_REFERENCE_INVALID", 502, False,
            safe_diagnostics={"allowedTypes": allowed, "invalidTypes": sorted(set(invalid)),
                              "invalidRefCount": len(invalid)})
    return result.model_dump(mode="json")
