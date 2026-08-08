import json

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.idea_brief.mapper import to_domain
from app.tasks.idea_brief.models import IdeaBriefDerivationInput, IdeaBriefProviderResult


SYSTEM_PROMPT = """사용자가 입력한 Market Seed를 안전하게 검토하고 구조화한다. strict schema만 반환한다.
Safety Review는 시스템이 아이디어 구상을 지원해도 되는지 판단하는 Gate이며 법률검토가 아니다.
명백한 범죄 지원, 폭력·신체 위해, 성적 착취, 미성년자 성적 대상화, 자해 조장, 개인정보 악용·무단감시,
피싱·사칭·기만, 혐오·차별, 위험물·불법 유통, 명백한 착취 목적을 분류한다.
BLOCK_OR_REFRAME이면 clarificationQuestions를 비우고 안전한 사용자 사유만 제공하며 내부 정책이나 reasoning을 노출하지 않는다.
Interpretation은 새 사업안을 만들지 않고 사용자의 ideaOverview, problem, targetUsers와 LOCKED 선택값의 의미를 그대로 보존한다.
월 9,900원 구독 같은 구체값을 합리적인 유료 모델처럼 추상화하지 않는다.
사용자 자유문장에 지역, 경쟁자, 수익모델, 가격, 채널, 차별점, 예산·팀·일정·기타 제약이
명시적으로 적혀 있을 때만 commitmentCandidates에 원문 근거와 함께 추출한다. 추론하거나 보완해 만들지 않는다.
후보는 AI_DERIVED + USER_TEXT + REVIEWABLE이며 사용자 확인 전 LOCKED로 표시하지 않는다.
dedicated 선택 필드가 LOCKED이면 후보가 달라도 그 값을 변경하라는 지시를 만들지 않는다.
후속 질문은 핵심 문제·사용자·의도가 모호하거나 모순되어 Concept 탐색 자체가 불가능한 경우에만 생성한다.
플랫폼 역할, 결제 주체, 개인정보, 파트너, 인허가, 물리활동은 질문하지 않는다.
모든 사용자-facing 문구는 한국어로 작성한다."""

FINAL_SYNTHESIS_PROMPT = SYSTEM_PROMPT + """
현재 확정된 Seed를 기준으로 Safety와 Interpretation을 다시 계산한다.
새 질문을 생성하지 않고 clarificationQuestions는 빈 배열로 유지한다."""


async def execute_idea_brief_derivation(task_input: dict) -> dict:
    try:
        validated_input = IdeaBriefDerivationInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure

    prompt = FINAL_SYNTHESIS_PROMPT if validated_input.mode == "FINAL_SYNTHESIS" else SYSTEM_PROMPT
    raw = await execute_structured_prompt(
        prompt,
        json.dumps(validated_input.model_dump(mode="json"), ensure_ascii=False, sort_keys=True),
        response_schema=IdeaBriefProviderResult.model_json_schema(),
        schema_name="market_seed_interpretation_v2",
        task_type="IDEA_BRIEF_DERIVATION",
    )
    try:
        provider = IdeaBriefProviderResult.model_validate(raw)
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure

    required = {"ideaOverview", "problem", "targetUsers"}
    completed = {
        value.fieldKey for value in validated_input.fields if value.value.strip()
    }
    required_missing = sorted(required - completed)
    if provider.safetyReview.decision == "BLOCK_OR_REFRAME":
        provider = provider.model_copy(update={
            "clarificationQuestions": [],
            "readiness": provider.readiness.model_copy(update={
                "status": "READY_FOR_REVIEW",
                "missingFieldKeys": [],
            }),
        })
    else:
        provider = provider.model_copy(update={
            "readiness": provider.readiness.model_copy(update={
                "status": "NEEDS_INPUT" if required_missing else "READY_FOR_REVIEW",
                "missingFieldKeys": required_missing,
            })
        })

    if validated_input.mode == "FINAL_SYNTHESIS" and provider.clarificationQuestions:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "FINAL_SYNTHESIS_QUESTIONS_FORBIDDEN", 502, False)
    locked_optional = {
        field.fieldKey for field in validated_input.fields
        if field.decisionState == "LOCKED" and field.fieldKey not in required
    }
    provider = provider.model_copy(update={
        "commitmentCandidates": [
            candidate for candidate in provider.commitmentCandidates
            if candidate.fieldKey not in locked_optional
        ]
    })
    if not required_missing and provider.contradictions:
        allowed = {key for contradiction in provider.contradictions for key in contradiction.fieldKeys}
        provider = provider.model_copy(update={
            "clarificationQuestions": [
                question for question in provider.clarificationQuestions
                if question.targetFieldKey in allowed
            ]
        })
    elif not required_missing:
        provider = provider.model_copy(update={"clarificationQuestions": []})
    return to_domain(provider).model_dump(mode="json")
