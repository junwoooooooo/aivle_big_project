"""Profile-bank market interview adapter with deterministic sampling and analysis."""

import asyncio
import json
import re
from collections import Counter
from collections.abc import Awaitable, Callable
from typing import Any

from pydantic import ValidationError

from app.providers import ProviderFailure
from app.tasks.market_interview.models import (
    AXES, AXIS_SOURCE, CodebookResult, CodingResult, MarketInterviewInput,
    MarketInterviewResult, PanelAnswerResult, TargetingResult,
)
from app.tasks.market_interview.panel_sampling import (
    TARGETING_POLICY, draw_panel, profile_taxonomy, public_profile,
)
from app.tasks.market_interview.provider import RESPONDENT_WORKLOAD, interview_concurrency
from app.tasks.market_interview.questions import QUESTIONS, build_prompt, concept_board
from app.tasks.market_interview.semantic_integrity import assert_semantic_integrity
from app.twin.bank import load as load_bank

StructuredCall = Callable[..., Awaitable[dict[str, Any]]]
ASSIGN_BATCH = 8
CODING_BATCH_ATTEMPTS = 2
RESPONDENT_ATTEMPTS = 2
RESPONDENT_RETRY_BACKOFF_SECONDS = 0.05
MIN_USABLE = 8

COMMON_BOUNDARY = """이 작업은 실제 고객 조사가 아니라 실측 profile bank에서 결정론적으로 뽑은
파생 프로필을 바탕으로 한 AI 가상 고객 정성 탐색이다. 실제 인물의 발언이라고 주장하지 마라.
언급 수를 백분율, 구매확률, 전환율, 시장 대표성 또는 모집단 일반화로 바꾸지 마라.
입력에 없는 경험을 사실처럼 확정하지 마라."""

TARGETING_PROMPT = COMMON_BOUNDARY + """
사업안의 targetUsers 자유문장을 아래 패널 필드로 표현 가능한 좁은 조건으로만 옮긴다.
나이, 성별, 가구원수, 광역지역, 개인소득 문자열, 직업 문자열, 자녀 동거, 가구 지위 외 조건은 만들지 않는다.
표현할 수 없는 행동·태도 조건은 비운다. 0과 빈 배열은 제한 없음이다. LLM은 조건만 만들고 실제 판정·표집은 코드가 한다.
""" + TARGETING_POLICY

TRANSCRIPT_PROMPT = COMMON_BOUNDARY + """
주어진 profile card 한 명의 관점에서 동일 상품 설명과 고정 9문항에 답한다.
participantId를 바꾸지 말고 아홉 답을 빠짐없이 반환한다. 가격·할인·수수료의 개별 조건에 %를 쓰는 것은 허용된다."""

CODEBOOK_PROMPT = COMMON_BOUNDARY + """
응답 원문을 읽고 LIKE, CONCERN, DIFFERENTIATION, USAGE_SCENE, BARRIER, SUGGESTION 여섯 축의 코드북을 만든다.
각 축에 적어도 하나의 구체적인 이름표를 만들고, 이름표는 전체 코드북에서 중복하지 않는다.
이 단계에는 respondent id나 언급 수를 쓰지 않는다. relevance 답에 나온 현재 해결 대안의
이름표는 alternatives에 별도로 만든다. 실제 고객에게 물을 중립 질문도 만든다.
한국어 서비스이므로 이름표와 후속 질문은 자연스러운 한국어로 작성한다."""

CODING_PROMPT = COMMON_BOUNDARY + """
고정 코드북으로 받은 응답자 전원을 정확히 한 줄씩 코딩한다. codebook의 이름표만 글자 그대로 쓴다.
모르는 이름표를 만들지 않는다. themeTitles는 실제 답에 근거할 때만 고른다. alternativeLabel은 relevance 답의 현재 대안이며 없으면 빈 문자열이다.
각 themeTitles에는 themeEvidence를 정확히 하나씩 만들고, 해당 축의 실제 answerField에서 연속된 원문 substring을 그대로 복사한 짧은 quote를 반환한다.
근거 인용이 없으면 그 테마를 배정하지 않는다. comprehension은 accurate/partial/misunderstood,
differentiation은 different/similar/unclear 중 하나다."""

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


async def _call(call: StructuredCall, system: str, value: Any, model, name: str, *,
                workload: str = "CLASSIFICATION") -> dict[str, Any]:
    return await call(system, _dump(value), response_schema=model.model_json_schema(),
                      schema_name=name, task_type="MARKET_INTERVIEW", workload=workload)


def _target_context(value: MarketInterviewInput) -> dict[str, str]:
    identity = value.selectedConcept.get("identity") or {}
    solution = value.selectedConcept.get("solution") or {}
    return {
        "targetUsers": str(identity.get("targetUsers") or value.selectedConcept.get("targetUsers") or ""),
        "problemScenario": str(solution.get("problemScenario")
                               or value.selectedConcept.get("problemScenario") or ""),
    }


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


class CodingValidationFailure(ValueError):
    """Safe, structured coding failure. It never contains answer or prompt text."""

    def __init__(self, rule: str, path: str, *, batch_index: int,
                 participant_id: str | None = None):
        super().__init__(rule)
        self.rule = rule
        self.path = path
        self.batch_index = batch_index
        self.participant_id = participant_id

    def diagnostic(self) -> dict[str, Any]:
        value: dict[str, Any] = {
            "stage": "CODING_EVIDENCE_VALIDATION",
            "batchIndex": self.batch_index,
            "rule": self.rule,
            "path": self.path,
        }
        if self.participant_id:
            value["participantId"] = self.participant_id
        return value


def _coding_failure(failure: CodingValidationFailure) -> ProviderFailure:
    diagnostic = failure.diagnostic()
    safe_path = failure.path
    if failure.participant_id:
        safe_path = f"{safe_path}.participant[{failure.participant_id}]"
    return ProviderFailure(
        "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False,
        schema_name="market_interview_assignment_v2",
        validation_fields=[{
            "path": safe_path[:200],
            "expectedType": f"CODING_EVIDENCE_VALIDATION {failure.rule}"[:80],
            "category": "coding_evidence",
        }],
        safe_diagnostics=diagnostic,
    )


def _validate_coding_batch(coded: CodingResult, batch: list[dict], *, batch_index: int,
                           known: dict, alternatives: list[str], answer_by_id: dict):
    expected = [row["participantId"] for row in batch]
    actual = [row.participantId for row in coded.assignments]
    if actual != expected or len(actual) != len(set(actual)):
        raise CodingValidationFailure(
            "PARTICIPANT_ORDER_MISMATCH", f"codingBatches[{batch_index}].participantId",
            batch_index=batch_index,
        )
    validated = []
    for row_index, row in enumerate(coded.assignments):
        base_path = f"codingBatches[{batch_index}].assignments[{row_index}]"
        for title in row.themeTitles:
            if title not in known:
                raise CodingValidationFailure(
                    "UNKNOWN_CODEBOOK_THEME", f"{base_path}.themeTitles",
                    batch_index=batch_index, participant_id=row.participantId,
                )
        if row.alternativeLabel.strip() and row.alternativeLabel.strip() not in alternatives:
            raise CodingValidationFailure(
                "UNKNOWN_ALTERNATIVE", f"{base_path}.alternativeLabel",
                batch_index=batch_index, participant_id=row.participantId,
            )
        evidence_titles = [item.themeTitle for item in row.themeEvidence]
        if (len(evidence_titles) != len(set(evidence_titles))
                or set(evidence_titles) != set(row.themeTitles)):
            raise CodingValidationFailure(
                "THEME_EVIDENCE_MISMATCH", f"{base_path}.themeEvidence",
                batch_index=batch_index, participant_id=row.participantId,
            )
        answer = answer_by_id[row.participantId]
        for evidence_index, evidence in enumerate(row.themeEvidence):
            evidence_path = f"{base_path}.themeEvidence[{evidence_index}]"
            theme = known.get(evidence.themeTitle)
            if theme is None or evidence.answerField != AXIS_SOURCE[theme.axis]:
                raise CodingValidationFailure(
                    "ANSWER_FIELD_AXIS_MISMATCH", f"{evidence_path}.answerField",
                    batch_index=batch_index, participant_id=row.participantId,
                )
            actual_answer = re.sub(r"\s+", " ", str(getattr(answer, evidence.answerField))).strip()
            quote = re.sub(r"\s+", " ", evidence.quote).strip()
            if not quote or quote not in actual_answer:
                raise CodingValidationFailure(
                    "VERBATIM_QUOTE_MISMATCH", f"{evidence_path}.quote",
                    batch_index=batch_index, participant_id=row.participantId,
                )
        validated.append(row)
    return validated


def _coverage_is_safe(panel: list[dict], usable_panel: list[dict], warning: str | None) -> bool:
    if warning is not None:
        return True
    attempted = Counter(row["group"] for row in panel)
    usable = Counter(row["group"] for row in usable_panel)
    return not any(attempted[group] and usable[group] * 2 < attempted[group]
                   for group in ("TARGET", "COMPARISON"))


async def execute_deep_interview(value: MarketInterviewInput, call: StructuredCall,
                                 event_sink=None) -> dict[str, Any]:
    def progress(stage: str, summary: str, **counts) -> None:
        if event_sink:
            event_sink({"stage": stage, "action": "COMPLETED", "status": "RUNNING",
                        "safeSummary": summary, **counts})

    try:
        cards, frame = load_bank()
        progress("MI_BANK_READY", "프로필 뱅크의 패널 후보를 탐색했습니다.", candidateCount=len(frame))
        target_context = _target_context(value)
        target_text = "\n".join(text for text in target_context.values() if text)
        progress("MI_TARGETING", "사업안의 고객 단위와 관찰 가능한 타겟 조건을 해석하고 있습니다.")
        targeting = TargetingResult.model_validate(await _call(
            call, TARGETING_PROMPT + "\n" + profile_taxonomy(cards), target_context, TargetingResult,
            "market_interview_target_criteria_v2"))
        panel, sampling = draw_panel(cards, frame, targeting.criteria, value.sampleSize, target_text,
                                     value.targetingContext.customerUnit)
        progress("MI_PANEL_READY", "타겟 표현 가능성을 반영해 패널 구성을 완료했습니다.",
                 completedCount=len(panel), totalCount=value.sampleSize)
        board = concept_board(value)
        semaphore = asyncio.Semaphore(interview_concurrency())

        async def transcript(row):
            for attempt in range(1, RESPONDENT_ATTEMPTS + 1):
                try:
                    async with semaphore:
                        result = PanelAnswerResult.model_validate(await _call(call, TRANSCRIPT_PROMPT, {
                            "participantId": row["participantId"],
                            "prompt": build_prompt(row["cardText"], board),
                        }, PanelAnswerResult, "market_interview_answer_v2",
                            workload=RESPONDENT_WORKLOAD))
                    if result.participantId != row["participantId"]:
                        raise ValueError("transcript participant identity changed")
                    return {"row": row, "answer": result, "failure": None}
                except ProviderFailure as failure:
                    if failure.retryable and attempt < RESPONDENT_ATTEMPTS:
                        await asyncio.sleep(RESPONDENT_RETRY_BACKOFF_SECONDS)
                        continue
                    code = ("TRANSIENT_RETRY_EXHAUSTED" if failure.retryable
                            else "PERMANENT_PROVIDER_FAILURE")
                    return {"row": row, "answer": None, "failure": {
                        "participantId": row["participantId"], "group": row["group"],
                        "attempts": attempt, "code": code,
                    }}
                except (ValidationError, ValueError):
                    return {"row": row, "answer": None, "failure": {
                        "participantId": row["participantId"], "group": row["group"],
                        "attempts": attempt, "code": "INVALID_RESPONDENT_OUTPUT",
                    }}
            raise AssertionError("respondent retry loop must terminate")

        completed_interviews = 0
        async def transcript_with_progress(row):
            nonlocal completed_interviews
            outcome = await transcript(row)
            completed_interviews += 1
            if completed_interviews == len(panel) or completed_interviews % 5 == 0:
                progress("MI_INTERVIEWING", "가상 인터뷰 응답을 생성하고 있습니다.",
                         completedCount=completed_interviews, totalCount=len(panel))
            return outcome

        outcomes = list(await asyncio.gather(*(transcript_with_progress(row) for row in panel)))
        usable = [outcome for outcome in outcomes if outcome["answer"] is not None]
        failures = [outcome["failure"] for outcome in outcomes if outcome["failure"] is not None]
        minimum_usable = max(MIN_USABLE, (value.sampleSize + 1) // 2)
        if len(usable) < minimum_usable:
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "MARKET_INTERVIEW_USABLE_SAMPLE_INSUFFICIENT",
                                  502, False)
        usable_panel = [outcome["row"] for outcome in usable]
        answers = [outcome["answer"] for outcome in usable]
        if not _coverage_is_safe(panel, usable_panel, sampling["warning"]):
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "MARKET_INTERVIEW_TARGET_COVERAGE_INSUFFICIENT",
                                  502, False)
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
        if any(not re.search(r"[가-힣]", title) for title in titles):
            raise ValueError("codebook labels must be localized for the Korean service")
        if any(not re.search(r"[가-힣]", question) for question in codebook.followUpQuestions):
            raise ValueError("follow-up questions must be localized for the Korean service")
        alternatives = [item.strip() for item in codebook.alternatives if item.strip()]
        if len(alternatives) != len(set(alternatives)):
            raise ValueError("codebook alternatives must be unique")

        known = {theme.title: theme for theme in codebook.themes}
        assignments = []
        coding_failed_ids: set[str] = set()
        for offset in range(0, len(transcript_rows), ASSIGN_BATCH):
            batch = transcript_rows[offset:offset + ASSIGN_BATCH]
            batch_index = offset // ASSIGN_BATCH
            failure = None
            for attempt in range(1, CODING_BATCH_ATTEMPTS + 1):
                try:
                    coding_prompt = CODING_PROMPT if failure is None else (
                        CODING_PROMPT
                        + f"\n이 묶음의 이전 출력은 {failure.rule} 규칙을 위반했다. "
                          "원문이나 참가자를 바꾸지 말고 해당 계약만 교정한다."
                    )
                    coded = CodingResult.model_validate(await _call(call, coding_prompt, {
                        "codebook": codebook.model_dump(mode="json"), "transcripts": batch,
                    }, CodingResult, "market_interview_assignment_v2"))
                    assignments.extend(_validate_coding_batch(
                        coded, batch, batch_index=batch_index, known=known,
                        alternatives=alternatives, answer_by_id=answer_by_id,
                    ))
                    failure = None
                    break
                except ValidationError as invalid_schema:
                    first = invalid_schema.errors()[0] if invalid_schema.errors() else {}
                    path = ".".join(str(part) for part in first.get("loc", ()))
                    failure = CodingValidationFailure(
                        "FINAL_PYDANTIC_VALIDATION",
                        f"codingBatches[{batch_index}].{path or 'result'}",
                        batch_index=batch_index,
                    )
                except CodingValidationFailure as invalid_coding:
                    failure = invalid_coding
                if attempt < CODING_BATCH_ATTEMPTS:
                    continue
            if failure is not None:
                # A respondent-scoped failure may be excluded only when the already validated
                # sample remains above the product minimum and preserves target coverage.
                failed_id = failure.participant_id
                remaining_panel = [row for row in usable_panel if row["participantId"] != failed_id]
                if (failed_id is None or len(remaining_panel) < minimum_usable
                        or not _coverage_is_safe(panel, remaining_panel, sampling["warning"])):
                    raise _coding_failure(failure)
                try:
                    remaining_batch = [row for row in batch if row["participantId"] != failed_id]
                    remaining_coded = CodingResult(assignments=[
                        row for row in coded.assignments if row.participantId != failed_id
                    ])
                    assignments.extend(_validate_coding_batch(
                        remaining_coded, remaining_batch, batch_index=batch_index, known=known,
                        alternatives=alternatives, answer_by_id=answer_by_id,
                    ))
                except (ValidationError, CodingValidationFailure) as remaining_failure:
                    if isinstance(remaining_failure, CodingValidationFailure):
                        raise _coding_failure(remaining_failure) from remaining_failure
                    raise _coding_failure(failure) from remaining_failure
                coding_failed_ids.add(failed_id)
                failures.append({
                    "participantId": failed_id,
                    "group": next(row["group"] for row in usable_panel
                                  if row["participantId"] == failed_id),
                    "attempts": CODING_BATCH_ATTEMPTS,
                    "code": "INVALID_CODING_OUTPUT",
                })
            progress("MI_CODING", "응답별 원문 근거를 확인하며 코딩하고 있습니다.",
                     completedCount=len(assignments) + len(coding_failed_ids),
                     totalCount=len(transcript_rows))

        if coding_failed_ids:
            usable_panel = [row for row in usable_panel if row["participantId"] not in coding_failed_ids]
            usable = [row for row in usable if row["row"]["participantId"] not in coding_failed_ids]
            answers = [row for row in answers if row.participantId not in coding_failed_ids]
            transcript_rows = [row for row in transcript_rows if row["participantId"] not in coding_failed_ids]
            answer_by_id = {row.participantId: row.answers for row in answers}

        evidence_by_participant: dict[str, dict[str, Any]] = {}
        for row in assignments:
            validated: dict[str, Any] = {}
            for evidence in row.themeEvidence:
                validated[evidence.themeTitle] = evidence
            evidence_by_participant[row.participantId] = validated

        group_by_id = {row["participantId"]: row["group"] for row in usable_panel}
        memberships = {title: [] for title in known}
        for row in assignments:
            for title in dict.fromkeys(row.themeTitles):
                memberships[title].append(row.participantId)
        themes = []
        for title, theme in known.items():
            ids = memberships[title]
            if not ids: continue
            quote = evidence_by_participant[ids[0]][title].quote
            themes.append({"axis": theme.axis, "title": title, "description": theme.description,
                           "participantIds": ids, "mentionCount": len(ids),
                           "targetCount": sum(group_by_id.get(rid) == "TARGET" for rid in ids),
                           "nonTargetCount": sum(group_by_id.get(rid) != "TARGET" for rid in ids),
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
        progress("MI_PATTERNS", "응답별 코딩에서 반복 패턴과 연결 관계를 정리했습니다.")
        participants = []
        interviews = []
        for row in usable_panel:
            rid = row["participantId"]
            answer = answer_by_id[rid]
            context = {"TARGET": "직접 타겟 조건 일치", "COMPARISON": "비교 관점",
                       "PROXY": "관찰 가능한 대리 조건", "EXPLORATORY": "일반 관점의 탐색 표본"}[row["group"]]
            participants.append({"participantId": rid, "label": f"가상 패널 {rid}",
                                 "profile": public_profile(row["profile"]),
                                 "context": context,
                                 "needs": [], "group": row["group"]})
            interviews.append({"participantId": rid, "questions": [
                {"question": question, "answer": str(getattr(answer, field)),
                 "uncertainty": "실제 고객에게 동일 질문으로 확인이 필요합니다."}
                for field, _title, question in QUESTIONS],
                "concerns": [answer.concern], "purchaseTriggers": [answer.barrier],
                "objections": [answer.concern], "unmetNeeds": [answer.suggestion]})

        comprehension = Counter(row.comprehension for row in assignments)
        differentiation = Counter(row.differentiation for row in assignments)
        limitations = list(LIMITATIONS)
        if failures:
            limitations.append(
                f"요청 {value.sampleSize}명 중 유효 응답 {len(usable)}명만 분석했으며 "
                f"응답 생성에 실패한 {len(failures)}명은 모든 집계에서 제외했습니다."
            )
        result = {
            "contract": "market-interview-result-v2", "schemaVersion": "2.0", "synthetic": True,
            "source": value.source.model_dump(mode="json"),
            "targeting": {"criteria": sampling["criteria"].model_dump(mode="json"),
                          "criteriaText": sampling["criteriaText"],
                          "requestedSampleSize": value.sampleSize, "drawnSampleSize": len(panel),
                          "attemptedCount": len(panel), "usableCount": len(usable),
                          "failedCount": len(failures),
                          "targetCount": sum(row["group"] == "TARGET" for row in usable_panel),
                          "nonTargetCount": sum(row["group"] != "TARGET" for row in usable_panel),
                          "proxyCount": sum(row["group"] == "PROXY" for row in usable_panel),
                          "exploratoryCount": sum(row["group"] == "EXPLORATORY" for row in usable_panel),
                          "representationStatus": sampling["representationStatus"],
                          "customerUnit": sampling["customerUnit"],
                          "targetCoverageWarning": sampling["warning"]},
            "participants": participants, "interviews": interviews, "themes": themes,
            "crossRelationships": cross_relationships[:24],
            "comprehension": {name: comprehension[name] for name in ("accurate", "partial", "misunderstood")},
            "differentiation": {name: differentiation[name] for name in ("different", "similar", "unclear")},
            "objections": _unique((row.answers.concern for row in answers), 12),
            "unmetNeeds": _unique((row.answers.suggestion for row in answers), 12),
            "purchaseTriggers": _unique((row.answers.barrier for row in answers), 12),
            "followUpQuestions": codebook.followUpQuestions, "limitations": limitations,
            "transcriptProvenance": [{"transcriptId": f"T-{row['participantId']}",
                                      "participantId": row["participantId"], "answerCount": 9,
                                      "group": row["group"]} for row in usable_panel],
            "codingTrace": [{"participantId": row.participantId, "themeTitles": row.themeTitles,
                             "themeEvidence": [item.model_dump(mode="json") for item in row.themeEvidence],
                             "comprehension": row.comprehension, "differentiation": row.differentiation,
                             "alternativeLabel": row.alternativeLabel,
                             "group": group_by_id[row.participantId]} for row in assignments],
            "respondentFailures": failures,
            "saturation": _saturation(themes, assignments, len(usable)),
        }
        try:
            validated = MarketInterviewResult.model_validate(result).model_dump(mode="json")
        except ValidationError as failure:
            fields = []
            for issue in failure.errors()[:12]:
                path = ".".join(str(part) for part in issue.get("loc", ())) or "result"
                fields.append({"path": path[:200], "expectedType": "valid result contract",
                               "category": str(issue.get("type", "invalid"))[:80]})
            raise ProviderFailure(
                "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False,
                schema_name="market_interview_result_v2", validation_fields=fields,
                safe_diagnostics={"stage": "FINAL_RESULT_VALIDATION",
                                  "rule": "FINAL_PYDANTIC_VALIDATION",
                                  "path": fields[0]["path"] if fields else "result"},
            ) from failure
        assert_semantic_integrity(value.selectedConcept, validated)
        progress("MI_RESULT_READY", "현재 사업안과 일치하는 인터뷰 결과를 구성했습니다.")
        return validated
    except CodingValidationFailure as failure:
        raise _coding_failure(failure) from failure
    except ValidationError as failure:
        fields = []
        for issue in failure.errors()[:12]:
            path = ".".join(str(part) for part in issue.get("loc", ())) or "result"
            fields.append({"path": path[:200], "expectedType": "valid contract value",
                           "category": str(issue.get("type", "invalid"))[:80]})
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False,
            validation_fields=fields,
            safe_diagnostics={"stage": "SCHEMA_VALIDATION", "rule": "PYDANTIC_VALIDATION",
                              "path": fields[0]["path"] if fields else "result"},
        ) from failure
    except ValueError as failure:
        rule = {
            "codebook must cover every axis with globally unique labels": "CODEBOOK_AXIS_OR_TITLE_INVALID",
            "codebook labels must be localized for the Korean service": "CODEBOOK_LOCALE_INVALID",
            "follow-up questions must be localized for the Korean service": "FOLLOW_UP_LOCALE_INVALID",
            "codebook alternatives must be unique": "CODEBOOK_ALTERNATIVE_DUPLICATE",
            "coding produced no traceable theme": "NO_TRACEABLE_THEME",
        }.get(str(failure), "RESULT_DOMAIN_INVARIANT_VIOLATION")
        stage = "CODEBOOK_VALIDATION" if rule.startswith(("CODEBOOK", "FOLLOW_UP")) else "RESULT_COMPOSITION"
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False,
            validation_fields=[{"path": "marketInterview.result", "expectedType": rule,
                                "category": "domain_invariant"}],
            safe_diagnostics={"stage": stage, "rule": rule, "path": "marketInterview.result"},
        ) from failure
