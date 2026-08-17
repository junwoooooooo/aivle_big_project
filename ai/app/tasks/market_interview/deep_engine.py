"""Profile-bank market interview adapter with deterministic sampling and analysis."""

import asyncio
import json
from collections import Counter
from collections.abc import Awaitable, Callable
from typing import Any

from pydantic import ValidationError

from app.providers import ProviderFailure
from app.tasks.market_interview.models import (
    AXES, AXIS_SOURCE, CodebookResult, CodingResult, MarketInterviewInput,
    MarketInterviewResult, PanelAnswerResult, TargetingResult,
)
from app.tasks.market_interview.panel_sampling import criteria_text, draw_panel, public_profile
from app.tasks.market_interview.questions import QUESTIONS, build_prompt, concept_board
from app.twin.bank import load as load_bank

StructuredCall = Callable[..., Awaitable[dict[str, Any]]]
ASSIGN_BATCH = 8
WORKERS = 4

COMMON_BOUNDARY = """이 작업은 실제 고객 조사가 아니라 실측 profile bank에서 결정론적으로 뽑은
파생 프로필을 바탕으로 한 AI 가상 고객 정성 탐색이다. 실제 인물의 발언이라고 주장하지 마라.
언급 수를 백분율, 구매확률, 전환율, 시장 대표성 또는 모집단 일반화로 바꾸지 마라.
입력에 없는 경험을 사실처럼 확정하지 마라."""

TARGETING_PROMPT = COMMON_BOUNDARY + """
사업안의 targetUsers 자유문장을 아래 패널 필드로 표현 가능한 좁은 조건으로만 옮긴다.
나이, 성별, 가구원수, 광역지역, 개인소득 문자열, 직업 문자열, 자녀 동거, 가구 지위 외 조건은 만들지 않는다.
표현할 수 없는 행동·태도 조건은 비운다. 0과 빈 배열은 제한 없음이다. LLM은 조건만 만들고 실제 판정·표집은 코드가 한다."""

TRANSCRIPT_PROMPT = COMMON_BOUNDARY + """
주어진 profile card 한 명의 관점에서 동일 상품 설명과 고정 9문항에 답한다.
participantId를 바꾸지 말고 아홉 답을 빠짐없이 반환한다. 가격·할인·수수료의 개별 조건에 %를 쓰는 것은 허용된다."""

CODEBOOK_PROMPT = COMMON_BOUNDARY + """
응답 원문을 읽고 LIKE, CONCERN, DIFFERENTIATION, USAGE_SCENE, BARRIER, SUGGESTION 여섯 축의 코드북을 만든다.
각 축에 적어도 하나의 구체적인 이름표를 만들고, 이름표는 전체 코드북에서 중복하지 않는다.
이 단계에는 respondent id나 언급 수를 쓰지 않는다. relevance 답에 나온 현재 해결 대안의
이름표는 alternatives에 별도로 만든다. 실제 고객에게 물을 중립 질문도 만든다."""

CODING_PROMPT = COMMON_BOUNDARY + """
고정 코드북으로 받은 응답자 전원을 정확히 한 줄씩 코딩한다. codebook의 이름표만 글자 그대로 쓴다.
모르는 이름표를 만들지 않는다. themeTitles는 실제 답에 근거할 때만 고른다. alternativeLabel은 relevance 답의 현재 대안이며 없으면 빈 문자열이다.
comprehension은 accurate/partial/misunderstood, differentiation은 different/similar/unclear 중 하나다."""

LIMITATIONS = [
    "실제 고객 조사 결과가 아니라 실측 profile bank에서 표집한 파생 프로필 기반 AI 가상 고객 정성 탐색입니다.",
    "통계적 대표성이 없으며 언급 수를 비율, 구매 확률 또는 시장 모집단으로 일반화할 수 없습니다.",
    "원자료의 내부 식별자와 재배포 제한 microdata는 공개 결과에 포함하지 않았습니다.",
    "핵심 가설은 실제 고객 인터뷰로 다시 확인해야 합니다.",
]


def _dump(value: Any) -> str:
    if hasattr(value, "model_dump"):
        value = value.model_dump(mode="json")
    return json.dumps(value, ensure_ascii=False, sort_keys=True)


async def _call(call: StructuredCall, system: str, value: Any, model, name: str) -> dict[str, Any]:
    return await call(system, _dump(value), response_schema=model.model_json_schema(),
                      schema_name=name, task_type="MARKET_INTERVIEW")


def _target_text(value: MarketInterviewInput) -> str:
    identity = value.selectedConcept.get("identity") or {}
    return str(identity.get("targetUsers") or value.selectedConcept.get("targetUsers") or "")


def _transcript_payload(answer) -> dict:
    return {name: str(getattr(answer, name))[:500] for name, _title, _question in QUESTIONS}


def _unique(values, maximum: int) -> list[str]:
    result = []
    for value in values:
        text = str(value or "").strip()
        if text and text not in result:
            result.append(text)
        if len(result) >= maximum:
            break
    return result


def _saturation(themes: list[dict], assignments, answered: int) -> dict:
    counts = {axis: 0 for axis in AXES}
    peaks = {axis: 0 for axis in AXES}
    for theme in themes:
        axis = theme["axis"]
        counts[axis] += 1
        peaks[axis] = max(peaks[axis], theme["mentionCount"])
    flagged = [f"{row['axis']}: {row['title']}" for row in themes if row["mentionCount"] >= answered]
    for axis in AXES:
        rows = [row for row in themes if row["axis"] == axis]
        if len(rows) == 1 and f"{axis}: {rows[0]['title']}" not in flagged:
            flagged.append(f"{axis}: {rows[0]['title']}")
    return {"participantCount": answered,
            "codedParticipantCount": sum(1 for row in assignments if row.themeTitles),
            "themeCount": len(themes), "axisLabelCounts": counts, "maxMentionByAxis": peaks,
            "saturatedThemes": flagged,
            "alternativeSum": sum(1 for row in assignments if row.alternativeLabel.strip()),
            "assessment": "EXPLORATORY_ONLY",
            "limitation": "사람 수와 이름표 수의 균질성 진단이며 시장 대표성이나 확률을 뜻하지 않습니다."}


def _representatives(panel: list[dict], assignments) -> list[str]:
    by_id = {row.participantId: row for row in assignments}
    chosen = []
    for bucket in ("misunderstood", "partial", "accurate"):
        for row in panel:
            assignment = by_id[row["participantId"]]
            if assignment.comprehension == bucket and row["participantId"] not in chosen:
                chosen.append(row["participantId"])
                if len(chosen) >= 5: return chosen
    return chosen[:5]


async def execute_deep_interview(value: MarketInterviewInput, call: StructuredCall) -> dict[str, Any]:
    try:
        targeting = TargetingResult.model_validate(await _call(
            call, TARGETING_PROMPT, {"targetUsers": _target_text(value)}, TargetingResult,
            "market_interview_target_criteria_v2"))
        cards, frame = load_bank()
        panel, sampling = draw_panel(cards, frame, targeting.criteria, value.sampleSize)
        board = concept_board(value)
        semaphore = asyncio.Semaphore(WORKERS)

        async def transcript(row):
            async with semaphore:
                result = PanelAnswerResult.model_validate(await _call(call, TRANSCRIPT_PROMPT, {
                    "participantId": row["participantId"],
                    "prompt": build_prompt(row["cardText"], board),
                }, PanelAnswerResult, "market_interview_answer_v2"))
            if result.participantId != row["participantId"]:
                raise ValueError("transcript participant identity changed")
            return result

        answers = list(await asyncio.gather(*(transcript(row) for row in panel)))
        answer_by_id = {row.participantId: row.answers for row in answers}
        transcript_rows = [{"participantId": row.participantId,
                            "answers": _transcript_payload(row.answers)} for row in answers]
        codebook = CodebookResult.model_validate(await _call(call, CODEBOOK_PROMPT, {
            "concept": value.selectedConcept, "transcripts": transcript_rows,
        }, CodebookResult, "market_interview_codebook_v2"))
        axes = {theme.axis for theme in codebook.themes}
        titles = [theme.title.strip() for theme in codebook.themes]
        if axes != set(AXES) or len(titles) != len(set(titles)):
            raise ValueError("codebook must cover every axis with globally unique labels")
        alternatives = [item.strip() for item in codebook.alternatives if item.strip()]
        if len(alternatives) != len(set(alternatives)):
            raise ValueError("codebook alternatives must be unique")

        assignments = []
        for offset in range(0, len(transcript_rows), ASSIGN_BATCH):
            batch = transcript_rows[offset:offset + ASSIGN_BATCH]
            coded = CodingResult.model_validate(await _call(call, CODING_PROMPT, {
                "codebook": codebook.model_dump(mode="json"), "transcripts": batch,
            }, CodingResult, "market_interview_assignment_v2"))
            expected = [row["participantId"] for row in batch]
            actual = [row.participantId for row in coded.assignments]
            if actual != expected or len(actual) != len(set(actual)):
                raise ValueError("coding batch must preserve every respondent exactly once")
            assignments.extend(coded.assignments)
        known = {theme.title: theme for theme in codebook.themes}
        if any(title not in known for row in assignments for title in row.themeTitles):
            raise ValueError("coding referenced an unknown codebook theme")
        if any(row.alternativeLabel.strip() and row.alternativeLabel.strip() not in alternatives
               for row in assignments):
            raise ValueError("coding referenced an unknown alternative")

        group_by_id = {row["participantId"]: row["group"] for row in panel}
        memberships = {title: [] for title in known}
        for row in assignments:
            for title in dict.fromkeys(row.themeTitles):
                memberships[title].append(row.participantId)
        themes = []
        for title, theme in known.items():
            ids = memberships[title]
            if not ids: continue
            quote = str(getattr(answer_by_id[ids[0]], AXIS_SOURCE[theme.axis])).strip()
            themes.append({"axis": theme.axis, "title": title, "description": theme.description,
                           "participantIds": ids, "mentionCount": len(ids),
                           "targetCount": sum(group_by_id.get(rid) == "TARGET" for rid in ids),
                           "nonTargetCount": sum(group_by_id.get(rid) == "COMPARISON" for rid in ids),
                           "quote": quote})
        if not themes:
            raise ValueError("coding produced no traceable theme")

        cross_relationships = []
        suggestions = [row for row in themes if row["axis"] == "SUGGESTION"]
        problems = [row for row in themes if row["axis"] in ("CONCERN", "BARRIER")]
        for suggestion in suggestions:
            members = set(suggestion["participantIds"])
            for problem in problems:
                shared = sorted(members.intersection(problem["participantIds"]))
                if shared:
                    cross_relationships.append({"suggestionTitle": suggestion["title"],
                                                "relatedAxis": problem["axis"],
                                                "relatedTitle": problem["title"],
                                                "respondentIds": shared,
                                                "overlapCount": len(shared)})
        cross_relationships.sort(key=lambda row: (-row["overlapCount"], row["suggestionTitle"], row["relatedTitle"]))
        representative_ids = _representatives(panel, assignments)
        participants = []
        interviews = []
        for row in panel:
            rid = row["participantId"]
            if rid not in representative_ids: continue
            answer = answer_by_id[rid]
            participants.append({"participantId": rid, "label": f"가상 패널 {rid}",
                                 "profile": public_profile(row["profile"]),
                                 "context": "타겟 조건 일치" if row["group"] == "TARGET" else "비교 관점",
                                 "needs": [], "group": row["group"]})
            interviews.append({"participantId": rid, "questions": [
                {"question": question, "answer": str(getattr(answer, field)),
                 "uncertainty": "실제 고객에게 동일 질문으로 확인이 필요합니다."}
                for field, _title, question in QUESTIONS],
                "concerns": [answer.concern], "purchaseTriggers": [answer.barrier],
                "objections": [answer.concern], "unmetNeeds": [answer.suggestion]})

        comprehension = Counter(row.comprehension for row in assignments)
        differentiation = Counter(row.differentiation for row in assignments)
        result = {
            "contract": "market-interview-result-v2", "schemaVersion": "2.0", "synthetic": True,
            "source": value.source.model_dump(mode="json"),
            "targeting": {"criteria": targeting.criteria.model_dump(mode="json"),
                          "criteriaText": criteria_text(targeting.criteria, sampling["matched"], sampling["total"]),
                          "requestedSampleSize": value.sampleSize, "drawnSampleSize": len(panel),
                          "targetCount": sum(row["group"] == "TARGET" for row in panel),
                          "nonTargetCount": sum(row["group"] == "COMPARISON" for row in panel),
                          "targetCoverageWarning": sampling["warning"]},
            "participants": participants, "interviews": interviews, "themes": themes,
            "crossRelationships": cross_relationships[:24],
            "comprehension": {name: comprehension[name] for name in ("accurate", "partial", "misunderstood")},
            "differentiation": {name: differentiation[name] for name in ("different", "similar", "unclear")},
            "objections": _unique((row.answers.concern for row in answers), 12),
            "unmetNeeds": _unique((row.answers.suggestion for row in answers), 12),
            "purchaseTriggers": _unique((row.answers.barrier for row in answers), 12),
            "followUpQuestions": codebook.followUpQuestions, "limitations": LIMITATIONS,
            "transcriptProvenance": [{"transcriptId": f"T-{row['participantId']}",
                                      "participantId": row["participantId"], "answerCount": 9,
                                      "group": row["group"]} for row in panel],
            "codingTrace": [{"participantId": row.participantId, "themeTitles": row.themeTitles,
                             "comprehension": row.comprehension, "differentiation": row.differentiation,
                             "alternativeLabel": row.alternativeLabel,
                             "group": group_by_id[row.participantId]} for row in assignments],
            "saturation": _saturation(themes, assignments, len(panel)),
        }
        return MarketInterviewResult.model_validate(result).model_dump(mode="json")
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    except ValueError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
