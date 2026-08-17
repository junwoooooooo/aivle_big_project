import asyncio
import json
from collections.abc import Awaitable, Callable
from typing import Any

from pydantic import ValidationError

from app.providers import ProviderFailure
from app.tasks.market_interview.models import (
    CodebookResult,
    CodingResult,
    MarketInterviewInput,
    MarketInterviewResult,
    TargetingResult,
    TranscriptResult,
)


StructuredCall = Callable[..., Awaitable[dict[str, Any]]]

COMMON_BOUNDARY = """이 작업은 실제 고객 조사가 아니라 AI 가상 고객을 이용한 정성 탐색이다.
실제 인물이나 실제 고객 발언을 만들지 마라. 언급 수를 백분율, 구매확률, 시장 대표성 또는
모집단 일반화로 바꾸지 마라. 입력에 없는 경험을 사실처럼 확정하지 마라."""

TARGETING_PROMPT = COMMON_BOUNDARY + """
현재 사업안을 서로 다른 관점에서 검토할 가상 참여자 4명을 P1~P4로 만든다.
민감정보나 실명은 만들지 않고, 서로 다른 사용 맥락·우려·필요를 갖게 한다."""

TRANSCRIPT_PROMPT = COMMON_BOUNDARY + """
주어진 가상 참여자 한 명의 관점에서만 비선도 질문에 답한다. participantId를 바꾸지 마라.
문제 상황, 첫 반응, 망설임, 사용 계기와 미충족 요구가 개별 transcript에 남아야 한다."""

CODEBOOK_PROMPT = COMMON_BOUNDARY + """
모든 개별 transcript를 읽고 정성 주제 이름표와 설명을 만든다. 사람 수나 비율을 쓰지 마라.
실제 고객에게 확인할 중립적 후속 질문도 만든다. 아직 participantId를 주제에 배정하지 마라."""

CODING_PROMPT = COMMON_BOUNDARY + """
고정된 코드북을 사용해 각 participantId를 해당하는 주제 이름표에 배정한다.
모든 participantId를 정확히 한 번 포함하고 코드북에 없는 이름표를 만들지 마라."""

LIMITATIONS = [
    "실제 고객 조사 결과가 아니라 AI 가상 고객을 이용한 정성 탐색입니다.",
    "통계적 대표성이 없으며 언급 수를 비율이나 구매 확률로 일반화할 수 없습니다.",
    "핵심 가설은 실제 고객 인터뷰로 다시 확인해야 합니다.",
]


def _dump(value: Any) -> str:
    if hasattr(value, "model_dump"):
        value = value.model_dump(mode="json")
    return json.dumps(value, ensure_ascii=False, sort_keys=True)


async def _call(call: StructuredCall, system: str, value: Any, model, name: str) -> dict[str, Any]:
    return await call(
        system,
        _dump(value),
        response_schema=model.model_json_schema(),
        schema_name=name,
        task_type="MARKET_INTERVIEW",
    )


def _validate_ids(participants, interviews, assignments) -> None:
    ids = [item.participantId for item in participants]
    expected = [f"P{i}" for i in range(1, len(ids) + 1)]
    if ids != expected or len(ids) != len(set(ids)):
        raise ValueError("targeting must return the canonical P1..P5 prefix")
    if [item.participantId for item in interviews] != ids:
        raise ValueError("transcripts must remain individually traceable")
    if [item.participantId for item in assignments] != ids:
        raise ValueError("coding must include every transcript exactly once")


def _unique(values, maximum: int) -> list[str]:
    result: list[str] = []
    for value in values:
        text = str(value or "").strip()
        if text and text not in result:
            result.append(text)
        if len(result) >= maximum:
            break
    return result


async def execute_deep_interview(value: MarketInterviewInput, call: StructuredCall) -> dict[str, Any]:
    try:
        targeting = TargetingResult.model_validate(await _call(
            call, TARGETING_PROMPT, value, TargetingResult, "market_interview_targeting_v1"))

        async def transcript(participant):
            payload = {"concept": value.model_dump(mode="json"), "participant": participant.model_dump(mode="json")}
            result = TranscriptResult.model_validate(await _call(
                call, TRANSCRIPT_PROMPT, payload, TranscriptResult,
                f"market_interview_transcript_{participant.participantId.lower()}_v1"))
            if result.interview.participantId != participant.participantId:
                raise ValueError("transcript participant identity changed")
            return result.interview

        interviews = list(await asyncio.gather(*(transcript(item) for item in targeting.participants)))
        transcript_payload = {
            "concept": value.selectedConcept,
            "transcripts": [item.model_dump(mode="json") for item in interviews],
        }
        codebook = CodebookResult.model_validate(await _call(
            call, CODEBOOK_PROMPT, transcript_payload, CodebookResult, "market_interview_codebook_v1"))
        coding = CodingResult.model_validate(await _call(call, CODING_PROMPT, {
            "codebook": codebook.model_dump(mode="json"),
            "transcripts": transcript_payload["transcripts"],
        }, CodingResult, "market_interview_coding_v1"))

        _validate_ids(targeting.participants, interviews, coding.assignments)
        known = {theme.title: theme for theme in codebook.themes}
        memberships = {title: [] for title in known}
        for assignment in coding.assignments:
            for title in assignment.themeTitles:
                if title not in known:
                    raise ValueError("coding referenced an unknown codebook theme")
                memberships[title].append(assignment.participantId)
        themes = [
            {"title": title, "description": known[title].description, "participantIds": ids}
            for title, ids in memberships.items() if ids
        ]
        if not themes:
            raise ValueError("coding produced no traceable qualitative theme")

        result = {
            "contract": "market-interview-result-v1",
            "schemaVersion": "1.0",
            "synthetic": True,
            "participants": [item.model_dump(mode="json") for item in targeting.participants],
            "interviews": [item.model_dump(mode="json") for item in interviews],
            "themes": themes,
            "objections": _unique((text for item in interviews for text in item.objections), 12),
            "unmetNeeds": _unique((text for item in interviews for text in item.unmetNeeds), 12),
            "purchaseTriggers": _unique((text for item in interviews for text in item.purchaseTriggers), 12),
            "followUpQuestions": codebook.followUpQuestions,
            "limitations": LIMITATIONS,
            "transcriptProvenance": [
                {"transcriptId": f"T-{item.participantId}", "participantId": item.participantId,
                 "answerCount": len(item.questions)} for item in interviews
            ],
            "codingTrace": [item.model_dump(mode="json") for item in coding.assignments],
            "saturation": {
                "participantCount": len(interviews),
                "codedParticipantCount": sum(1 for item in coding.assignments if item.themeTitles),
                "themeCount": len(themes),
                "assessment": "EXPLORATORY_ONLY",
                "limitation": "소수의 AI 가상 참여자 정성 탐색이므로 포화나 시장 대표성을 입증하지 않습니다.",
            },
        }
        return MarketInterviewResult.model_validate(result).model_dump(mode="json")
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    except ValueError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
