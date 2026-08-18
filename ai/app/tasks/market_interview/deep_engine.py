"""Profile-bank market interview adapter with deterministic sampling and analysis."""

import asyncio
import json
import re
import unicodedata
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

CODING_REPAIR_PROMPT = COMMON_BOUNDARY + """
한 응답자의 코딩 근거만 교정한다. 새 theme를 만들거나 participantId를 바꾸지 않는다.
기존 themeTitles 밖의 theme를 추가하지 않고, 유지하는 theme의 answerField를 바꾸지 않는다.
quote는 제공된 해당 answer에서 연속된 원문 substring을 그대로 복사한다.
정확한 원문 근거를 찾을 수 없는 theme는 themeTitles와 themeEvidence에서 함께 제거한다.
나머지 comprehension, differentiation, alternativeLabel은 제공된 assignment를 유지한다."""

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
                 participant_id: str | None = None, repair_attempts: int = 0,
                 exclusion_attempted: bool = False,
                 exclusion_blocked_reason: str | None = None,
                 invalid_participant_count: int | None = None,
                 invalid_theme_count: int | None = None,
                 recovery_applied: str | None = None):
        super().__init__(rule)
        self.rule = rule
        self.path = path
        self.batch_index = batch_index
        self.participant_id = participant_id
        self.repair_attempts = repair_attempts
        self.exclusion_attempted = exclusion_attempted
        self.exclusion_blocked_reason = exclusion_blocked_reason
        self.invalid_participant_count = invalid_participant_count
        self.invalid_theme_count = invalid_theme_count
        self.recovery_applied = recovery_applied

    def with_recovery(self, *, repair_attempts: int, exclusion_attempted: bool,
                      exclusion_blocked_reason: str | None = None):
        self.repair_attempts = repair_attempts
        self.exclusion_attempted = exclusion_attempted
        self.exclusion_blocked_reason = exclusion_blocked_reason
        return self

    def diagnostic(self) -> dict[str, Any]:
        value: dict[str, Any] = {
            "stage": "CODING_EVIDENCE_VALIDATION",
            "batchIndex": self.batch_index,
            "rule": self.rule,
            "path": self.path,
            "repairAttempts": self.repair_attempts,
            "exclusionAttempted": self.exclusion_attempted,
        }
        if self.participant_id:
            value["participantId"] = self.participant_id
        if self.exclusion_blocked_reason:
            value["exclusionBlockedReason"] = self.exclusion_blocked_reason
        if self.invalid_participant_count is not None:
            value["invalidParticipantCount"] = self.invalid_participant_count
        if self.invalid_theme_count is not None:
            value["invalidThemeCount"] = self.invalid_theme_count
        if self.recovery_applied:
            value["recoveryApplied"] = self.recovery_applied
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


_ZERO_WIDTH = {"\u200b", "\u200c", "\u200d", "\u2060", "\ufeff"}
_CHAR_NORMALIZATION = {
    "\u2018": "'", "\u2019": "'", "\u201a": "'", "\u201b": "'", "\u2032": "'",
    "\u201c": '"', "\u201d": '"', "\u201e": '"', "\u201f": '"', "\u2033": '"',
    "\u2010": "-", "\u2011": "-", "\u2012": "-", "\u2013": "-", "\u2014": "-",
    "\u2015": "-", "\u2212": "-",
}


def _normalized_text(value: str, *, retain_spans: bool = False):
    normalized: list[str] = []
    spans: list[tuple[int, int]] = []
    pending_space: tuple[int, int] | None = None
    for index, original in enumerate(str(value)):
        if original in _ZERO_WIDTH:
            continue
        fragment = unicodedata.normalize("NFKC", original)
        for character in fragment:
            character = _CHAR_NORMALIZATION.get(character, character)
            if character.isspace():
                if normalized:
                    pending_space = ((pending_space or (index, index + 1))[0], index + 1)
                continue
            if pending_space is not None:
                normalized.append(" ")
                spans.append(pending_space)
                pending_space = None
            normalized.append(character)
            spans.append((index, index + 1))
    text = "".join(normalized)
    return (text, spans) if retain_spans else text


def _resolve_original_quote(actual_answer: str, candidate: str) -> str | None:
    """Return the original answer span only for a normalized contiguous substring."""
    normalized_quote = _normalized_text(candidate)
    normalized_answer, spans = _normalized_text(actual_answer, retain_spans=True)
    if not normalized_quote:
        return None
    start = normalized_answer.find(normalized_quote)
    if start < 0:
        return None
    end = start + len(normalized_quote) - 1
    return str(actual_answer)[spans[start][0]:spans[end][1]]


def _validate_coding_batch_structure(coded: CodingResult, batch: list[dict], *, batch_index: int):
    """Validate only identities owned by the batch transport contract."""
    expected = [row["participantId"] for row in batch]
    actual = [row.participantId for row in coded.assignments]
    if actual != expected or len(actual) != len(set(actual)):
        raise CodingValidationFailure(
            "PARTICIPANT_ORDER_MISMATCH", f"codingBatches[{batch_index}].participantId",
            batch_index=batch_index,
            invalid_participant_count=len(set(expected).symmetric_difference(actual)) or 1,
        )
    return coded.assignments


def _validate_coding_assignment(row, *, row_index: int, batch_index: int,
                                known: dict, alternatives: list[str], answer_by_id: dict):
    """Validate and canonicalize one respondent without touching its batch peers."""
    base_path = f"codingBatches[{batch_index}].assignments[{row_index}]"
    for title in row.themeTitles:
        if title not in known:
            raise CodingValidationFailure(
                "UNKNOWN_CODEBOOK_THEME", f"{base_path}.themeTitles",
                batch_index=batch_index, participant_id=row.participantId,
                invalid_participant_count=1, invalid_theme_count=1,
            )
    if row.alternativeLabel.strip() and row.alternativeLabel.strip() not in alternatives:
        raise CodingValidationFailure(
            "UNKNOWN_ALTERNATIVE", f"{base_path}.alternativeLabel",
            batch_index=batch_index, participant_id=row.participantId,
            invalid_participant_count=1,
        )
    evidence_titles = [item.themeTitle for item in row.themeEvidence]
    if (len(evidence_titles) != len(set(evidence_titles))
            or set(evidence_titles) != set(row.themeTitles)):
        raise CodingValidationFailure(
            "THEME_EVIDENCE_MISMATCH", f"{base_path}.themeEvidence",
            batch_index=batch_index, participant_id=row.participantId,
            invalid_participant_count=1,
            invalid_theme_count=len(set(evidence_titles).symmetric_difference(row.themeTitles)) or 1,
        )
    answer = answer_by_id[row.participantId]
    for evidence_index, evidence in enumerate(row.themeEvidence):
        evidence_path = f"{base_path}.themeEvidence[{evidence_index}]"
        theme = known.get(evidence.themeTitle)
        if theme is None or evidence.answerField != AXIS_SOURCE[theme.axis]:
            raise CodingValidationFailure(
                "ANSWER_FIELD_AXIS_MISMATCH", f"{evidence_path}.answerField",
                batch_index=batch_index, participant_id=row.participantId,
                invalid_participant_count=1, invalid_theme_count=1,
            )
        actual_answer = str(getattr(answer, evidence.answerField))
        resolved_quote = _resolve_original_quote(actual_answer, evidence.quote)
        if resolved_quote is None:
            raise CodingValidationFailure(
                "VERBATIM_QUOTE_MISMATCH", f"{evidence_path}.quote",
                batch_index=batch_index, participant_id=row.participantId,
                invalid_participant_count=1, invalid_theme_count=1,
            )
        evidence.quote = resolved_quote
    return row


def _salvage_coding_assignment(row, *, known: dict, alternatives: list[str], answer_by_id: dict):
    """Keep only themes with exact respondent-level evidence; preserve the interview row."""
    answer = answer_by_id[row.participantId]
    requested = set(row.themeTitles)
    evidence_by_title = {}
    for evidence in row.themeEvidence:
        if evidence.themeTitle in evidence_by_title or evidence.themeTitle not in requested:
            continue
        theme = known.get(evidence.themeTitle)
        if theme is None or evidence.answerField != AXIS_SOURCE[theme.axis]:
            continue
        resolved_quote = _resolve_original_quote(
            str(getattr(answer, evidence.answerField)), evidence.quote,
        )
        if resolved_quote is None:
            continue
        evidence.quote = resolved_quote
        evidence_by_title[evidence.themeTitle] = evidence
    titles = [title for title in row.themeTitles if title in evidence_by_title]
    return row.model_copy(update={
        "themeTitles": titles,
        "themeEvidence": [evidence_by_title[title] for title in titles],
        "alternativeLabel": (row.alternativeLabel if row.alternativeLabel.strip() in alternatives else ""),
    })


def _validate_repair_scope(original, repaired, *, batch_index: int):
    base_path = f"codingBatches[{batch_index}].repair"
    if repaired.participantId != original.participantId:
        raise CodingValidationFailure(
            "REPAIR_PARTICIPANT_MISMATCH", f"{base_path}.participantId",
            batch_index=batch_index, participant_id=original.participantId,
        )
    original_fields = {item.themeTitle: item.answerField for item in original.themeEvidence}
    if not set(repaired.themeTitles).issubset(set(original.themeTitles)):
        raise CodingValidationFailure(
            "REPAIR_THEME_SCOPE_MISMATCH", f"{base_path}.themeTitles",
            batch_index=batch_index, participant_id=original.participantId,
        )
    if any(original_fields.get(item.themeTitle) != item.answerField
           for item in repaired.themeEvidence):
        raise CodingValidationFailure(
            "REPAIR_ANSWER_FIELD_CHANGED", f"{base_path}.themeEvidence",
            batch_index=batch_index, participant_id=original.participantId,
        )
    if (repaired.alternativeLabel != original.alternativeLabel
            or repaired.comprehension != original.comprehension
            or repaired.differentiation != original.differentiation):
        raise CodingValidationFailure(
            "REPAIR_ASSIGNMENT_SCOPE_MISMATCH", base_path,
            batch_index=batch_index, participant_id=original.participantId,
        )


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
        for offset in range(0, len(transcript_rows), ASSIGN_BATCH):
            batch = transcript_rows[offset:offset + ASSIGN_BATCH]
            batch_index = offset // ASSIGN_BATCH
            failure = None
            last_coded = None
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
                    last_coded = coded
                    _validate_coding_batch_structure(coded, batch, batch_index=batch_index)
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
                # A structurally invalid batch cannot be mapped back to respondents safely.
                raise _coding_failure(failure.with_recovery(
                    repair_attempts=0, exclusion_attempted=False,
                    exclusion_blocked_reason="BATCH_STRUCTURE_INVALID",
                ))

            for row_index, original in enumerate(last_coded.assignments):
                candidate = original
                try:
                    assignments.append(_validate_coding_assignment(
                        candidate, row_index=row_index, batch_index=batch_index, known=known,
                        alternatives=alternatives, answer_by_id=answer_by_id,
                    ))
                    continue
                except CodingValidationFailure as participant_failure:
                    failure = participant_failure

                if failure.rule == "VERBATIM_QUOTE_MISMATCH":
                    transcript_row = batch[row_index]
                    try:
                        repaired_result = CodingResult.model_validate(await _call(
                            call,
                            CODING_REPAIR_PROMPT
                            + f"\n이 assignment는 {failure.rule} 규칙을 위반했다.",
                            {
                                "codebook": codebook.model_dump(mode="json"),
                                "transcripts": [transcript_row],
                                "assignment": original.model_dump(mode="json"),
                            },
                            CodingResult,
                            "market_interview_assignment_repair_v1",
                        ))
                        if len(repaired_result.assignments) != 1:
                            raise CodingValidationFailure(
                                "REPAIR_ASSIGNMENT_COUNT_MISMATCH",
                                f"codingBatches[{batch_index}].repair.assignments",
                                batch_index=batch_index, participant_id=original.participantId,
                            )
                        repaired = repaired_result.assignments[0]
                        _validate_repair_scope(original, repaired, batch_index=batch_index)
                        candidate = repaired
                        assignments.append(_validate_coding_assignment(
                            candidate, row_index=row_index, batch_index=batch_index, known=known,
                            alternatives=alternatives, answer_by_id=answer_by_id,
                        ))
                        continue
                    except (ValidationError, CodingValidationFailure):
                        # candidate is the scope-validated repair when respondent validation
                        # failed after repair; otherwise it is still the original assignment.
                        pass

                # An untraceable theme is not an assignment. Preserve the respondent and
                # every other theme that still has exact evidence in the original answer.
                assignments.append(_salvage_coding_assignment(
                    candidate, known=known, alternatives=alternatives,
                    answer_by_id=answer_by_id,
                ))
            progress("MI_CODING", "응답별 원문 근거를 확인하며 코딩하고 있습니다.",
                     completedCount=len(assignments),
                     totalCount=len(transcript_rows))

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
