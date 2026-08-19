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
    AXES, AXIS_SOURCE, CodebookResult, CodingAssignment, CodingDraftAssignment,
    CodingTransportResult, MarketInterviewInput, MarketInterviewResult,
    PanelAnswerResult, TargetingResult, ThemeEvidence,
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
quote나 themeEvidence는 생성하지 않는다. 근거 원문은 서버가 코드북 axis와 실제 답을 연결해 결정론적으로 만든다.
comprehension은 accurate/partial/misunderstood,
differentiation은 different/similar/unclear 중 하나다."""

CODING_SINGLE_PROMPT = CODING_PROMPT + """
이번 입력에는 응답자가 한 명뿐이다. 제공된 participantId를 그대로 사용해 assignment 한 건만 반환한다."""

CODEBOOK_REPAIR_PROMPT = CODEBOOK_PROMPT + """
이전 코드북이 계약을 만족하지 못했다. 여섯 axis를 모두 포함하고, 전체 title은 중복 없이 한국어로 작성하며,
후속 질문도 한국어로 교정한다. 응답 원문이나 사실을 새로 만들지 않는다."""

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
    return {
        "targetUsers": value.conceptBoard.targetUsers,
        "problemScenario": value.conceptBoard.problemScenario,
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
    coded = sum(1 for row in assignments if row.classificationStatus == "CODED")
    return {"participantCount": answered,
            "codedParticipantCount": coded,
            "usableInterviewCount": answered,
            "codedInterviewCount": coded,
            "codingFailureCount": answered - coded,
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
        schema_name="market_interview_assignment_v3",
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


def _deterministic_excerpt(actual_answer: str, maximum: int = 500) -> str:
    """Return an exact original span; never a normalized or generated quotation."""
    text = str(actual_answer)
    if len(text) <= maximum:
        return text
    window = text[:maximum]
    boundaries = [match.end() for match in re.finditer(r"[.!?。！？](?:[\"'’”)]*)", window)]
    useful = [end for end in boundaries if end >= min(120, maximum // 2)]
    return window[:useful[-1]] if useful else window


def _build_coding_assignment(draft: CodingDraftAssignment, *, known: dict,
                             alternatives: list[str], answer_by_id: dict) -> CodingAssignment:
    """Canonicalize labels and derive all traceable evidence from respondent answers."""
    titles = []
    for raw_title in draft.themeTitles:
        title = str(raw_title).strip()
        if title in known and title not in titles:
            titles.append(title)
    answer = answer_by_id[draft.participantId]
    evidence = []
    for title in titles:
        answer_field = AXIS_SOURCE[known[title].axis]
        evidence.append(ThemeEvidence(
            themeTitle=title,
            answerField=answer_field,
            quote=_deterministic_excerpt(str(getattr(answer, answer_field))),
        ))
    alternative = draft.alternativeLabel.strip()
    return CodingAssignment(
        participantId=draft.participantId,
        themeTitles=titles,
        themeEvidence=evidence,
        alternativeLabel=alternative if alternative in alternatives else "",
        comprehension=draft.comprehension,
        differentiation=draft.differentiation,
        classificationStatus="CODED",
    )


def _unclassified_assignment(participant_id: str) -> CodingAssignment:
    """Keep a valid transcript when classification cannot be recovered without inventing facts."""
    return CodingAssignment(
        participantId=participant_id, themeTitles=[], themeEvidence=[], alternativeLabel="",
        comprehension="unclassified", differentiation="unclassified",
        classificationStatus="UNCLASSIFIED",
    )


def _parse_coding_transport(raw: dict[str, Any], expected_ids: list[str]) -> tuple[dict[str, CodingDraftAssignment], list[str]]:
    """Keep every unique, schema-valid expected row and report only unresolved ids."""
    transport = CodingTransportResult.model_validate(raw)
    candidates: dict[str, list[dict[str, Any]]] = {participant_id: [] for participant_id in expected_ids}
    for envelope in transport.assignments:
        value = envelope.model_dump(mode="python")
        participant_id = value.get("participantId")
        if participant_id in candidates:
            candidates[participant_id].append(value)
    valid: dict[str, CodingDraftAssignment] = {}
    unresolved: list[str] = []
    for participant_id in expected_ids:
        rows = candidates[participant_id]
        if len(rows) != 1:
            unresolved.append(participant_id)
            continue
        try:
            valid[participant_id] = CodingDraftAssignment.model_validate(rows[0])
        except ValidationError:
            unresolved.append(participant_id)
    return valid, unresolved


def _validate_codebook(codebook: CodebookResult) -> CodebookResult:
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
    return codebook


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
        codebook = None
        codebook_failure = None
        for codebook_attempt, prompt in enumerate((CODEBOOK_PROMPT, CODEBOOK_REPAIR_PROMPT), start=1):
            try:
                candidate = CodebookResult.model_validate(await _call(call, prompt, {
                    "concept": value.selectedConcept, "transcripts": transcript_rows,
                }, CodebookResult, "market_interview_codebook_v2"))
                codebook = _validate_codebook(candidate)
                break
            except (ValidationError, ValueError) as failure:
                codebook_failure = failure
        if codebook is None:
            raise ProviderFailure(
                "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False,
                schema_name="market_interview_codebook_v2",
                validation_fields=[{"path": "codebook", "expectedType": "six-axis Korean codebook",
                                    "category": "codebook_contract"}],
                safe_diagnostics={"stage": "CODEBOOK_VALIDATION", "rule": "CODEBOOK_REPAIR_EXHAUSTED",
                                  "repairAttempts": 1},
            ) from codebook_failure

        alternatives = [item.strip() for item in codebook.alternatives if item.strip()]

        known = {theme.title: theme for theme in codebook.themes}
        assignments = []
        for offset in range(0, len(transcript_rows), ASSIGN_BATCH):
            batch = transcript_rows[offset:offset + ASSIGN_BATCH]
            batch_index = offset // ASSIGN_BATCH
            expected_ids = [row["participantId"] for row in batch]
            recovered: dict[str, CodingDraftAssignment] = {}
            unresolved = list(expected_ids)
            for attempt in range(1, CODING_BATCH_ATTEMPTS + 1):
                retry_batch = [row for row in batch if row["participantId"] in unresolved]
                try:
                    raw = await _call(call, CODING_PROMPT, {
                        "codebook": codebook.model_dump(mode="json"), "transcripts": retry_batch,
                    }, CodingTransportResult, "market_interview_assignment_v3")
                    valid, unresolved = _parse_coding_transport(
                        raw, [row["participantId"] for row in retry_batch],
                    )
                    recovered.update(valid)
                except (ValidationError, ProviderFailure):
                    unresolved = [row["participantId"] for row in retry_batch]
                if not unresolved:
                    break

            # The batch is only a transport optimization. Recover unresolved rows one by one.
            unclassified: set[str] = set()
            for participant_id in unresolved:
                transcript_row = next(row for row in batch if row["participantId"] == participant_id)
                try:
                    raw = await _call(call, CODING_SINGLE_PROMPT, {
                        "codebook": codebook.model_dump(mode="json"),
                        "transcripts": [transcript_row],
                    }, CodingTransportResult, "market_interview_assignment_single_v1")
                    valid, still_unresolved = _parse_coding_transport(raw, [participant_id])
                    if not still_unresolved:
                        recovered.update(valid)
                        continue
                except (ValidationError, ProviderFailure):
                    pass
                # A respondent-level coding failure is a degradation, not a sample failure.
                # The transcript stays traceable and no classification value is fabricated.
                unclassified.add(participant_id)

            # Reorder by the expected transport ids; provider order is not a product contract.
            for participant_id in expected_ids:
                assignments.append(_unclassified_assignment(participant_id)
                    if participant_id in unclassified else _build_coding_assignment(
                        recovered[participant_id], known=known, alternatives=alternatives,
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
        coded_count = sum(row.classificationStatus == "CODED" for row in assignments)
        limitations = list(LIMITATIONS)
        if failures:
            limitations.append(
                f"요청 {value.sampleSize}명 중 유효 응답 {len(usable)}명만 분석했으며 "
                f"응답 생성에 실패한 {len(failures)}명은 모든 집계에서 제외했습니다."
            )
        result = {
            "contract": "market-interview-result-v2", "schemaVersion": "2.0", "synthetic": True,
            "source": value.source.model_dump(mode="json"),
            "conceptBoard": value.conceptBoard.model_dump(mode="json"),
            "usableInterviewCount": len(usable), "codedInterviewCount": coded_count,
            "codingFailureCount": len(usable) - coded_count,
            "targeting": {"criteria": sampling["criteria"].model_dump(mode="json"),
                          "criteriaText": sampling["criteriaText"],
                          "requestedSampleSize": value.sampleSize, "drawnSampleSize": len(panel),
                          "attemptedCount": len(panel), "usableCount": len(usable),
                          "failedCount": len(failures),
                          "targetRequested": sampling["targetRequested"],
                          "targetCount": sum(row["group"] == "TARGET" for row in usable_panel),
                          "nonTargetCount": sum(row["group"] != "TARGET" for row in usable_panel),
                          "proxyCount": sum(row["group"] == "PROXY" for row in usable_panel),
                          "exploratoryCount": sum(row["group"] == "EXPLORATORY" for row in usable_panel),
                          "representationStatus": sampling["representationStatus"],
                          "customerUnit": sampling["customerUnit"],
                          "targetCoverageWarning": sampling["warning"]},
            "participants": participants, "interviews": interviews, "themes": themes,
            "crossRelationships": cross_relationships[:24],
            "comprehension": {name: comprehension[name] for name in ("accurate", "partial", "misunderstood", "unclassified")},
            "differentiation": {name: differentiation[name] for name in ("different", "similar", "unclear", "unclassified")},
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
                             "group": group_by_id[row.participantId],
                             "classificationStatus": row.classificationStatus} for row in assignments],
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
        assert_semantic_integrity(value.selectedConcept, validated, value.conceptBoard.model_dump(mode="json"))
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
