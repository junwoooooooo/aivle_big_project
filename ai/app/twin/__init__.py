"""패널 트윈 조사 — 실측 프로파일 카드로 선택형 설문을 돌린다.

한국미디어패널조사(KISDI) 응답자 1인당 1장으로 만든 카드를 LLM에게 주고 그 사람 역할로
두 상품안 중 하나를 고르게 한다. 산출은 **방향과 신뢰구간까지**다.

파는 것은 외적 타당성 시험에서 성적이 난 두 유형뿐이다(`task_type.py` 가 게이트다).
허용 유형이 아니면 **LLM을 한 번도 부르지 않고** 거절한다.

등록(`app/api/executions.py` 의 `TASK_TYPES` 와 분기 체인, 그리고
`tests/test_internal_task_type_alignment.py`)은 **이 모듈 밖**이다. 셋을 한꺼번에 고쳐야
하므로 별도 단계로 미뤄져 있다 — 이 모듈은 등록 없이도 import·테스트된다.
"""

import logging
from collections.abc import Callable

from pydantic import ValidationError

from app.providers import ProviderFailure
from app.twin import caveats as caveat_text
from app.twin.aggregate import analyze, classify_subjects, mde_effective, verdict
from app.twin.bank import load, stratified_sample
from app.twin.models import TwinSurveyInput
from app.twin.profile import is_empty, parse_profile
from app.twin.runner import run_survey
from app.twin.task_type import SERVICEABLE, classify

__all__ = ["execute_twin_survey"]

logger = logging.getLogger(__name__)
EventSink = Callable[[dict], None]


def _observe(event_sink: EventSink | None, stage: str, action: str, summary: str,
             status: str = "RUNNING", **optional) -> None:
    if event_sink is None:
        return
    try:
        event_sink({"stage": stage, "action": action, "status": status,
                    "safeSummary": summary, **optional})
    except Exception as failure:
        logger.warning("twin progress observer failed exceptionType=%s", failure.__class__.__name__)

INTERVIEWS_PER_PAIR = 5        # 응답 봉투 2 MiB 상한 — 셀 원장은 절대 싣지 않는다
EXCERPT_MAX_CHARS = 300

#: 인터뷰 배분 — 이긴 쪽 2 · 진 쪽 2 · 미결정 1. 모자라면 이긴 쪽 → 진 쪽 순으로 채운다.
INTERVIEW_QUOTA = ((("winner",), 2), (("loser",), 2), (("undecided",), 1))

NOTES = (
    "이 결과는 실존 인물의 응답이 아니라 실측 프로파일 기반 시뮬레이션이다.",
    "caveats 를 떨어뜨리지 마라 — 값과 함께 옮겨야 하는 문장이다.",
    "«못 잼»과 «차이 없음»은 다르다. measurable=false 는 앞의 것이다.",
    "크기·점유율·선택확률은 이 파이프라인이 산출하지 않는다 — 없는 것이지 0이 아니다.",
)


def _excerpt(raw: str | None) -> str | None:
    """이유 문장만 남긴다 — 마지막 «선택: …» 줄은 뗀다."""
    lines = [line for line in (raw or "").splitlines() if line.strip()]
    if not lines:
        return None
    body = " ".join(line.strip() for line in lines[:-1]).strip()
    if not body:
        return None
    return body[:EXCERPT_MAX_CHARS]


def _quote(usable: list[dict], subject: str, pair_id: str) -> str | None:
    """그 사람의 말. **fwd 방향 rep1 을 우선**한다.

    방향을 고정하는 이유: 인용문 안의 «A/B» 는 제시 순서를 가리키는데, rev 는 순서가
    뒤집혀 있다. 섞어 쓰면 화면의 라벨과 인용문의 A/B 가 어긋난다.
    """
    rows = [r for r in usable if r["subject"] == subject and r["pair_id"] == pair_id]
    rows.sort(key=lambda r: (r["direction"] != "fwd", r["rep"]))
    for row in rows:
        body = _excerpt(row.get("raw"))
        if body:
            return body
    return None


def _pick(candidates: list[str], want: int, cells: dict[str, str], used: set) -> list[str]:
    """층을 겹치지 않게 먼저 고르고, 그래도 모자라면 완화해서 채운다.

    난수를 쓰지 않는다 — `candidates` 가 pid 오름차순이라 같은 입력이면 같은 사람이 나온다
    (`bank.stratified_sample` 과 같은 규율).
    """
    picked: list[str] = []
    for subject in candidates:
        if len(picked) >= want:
            break
        cell = cells.get(subject)
        if cell is not None and cell in used:
            continue
        picked.append(subject)
        used.add(cell)
    for subject in candidates:                       # 층 제약을 못 지키면 인원이 우선이다
        if len(picked) >= want:
            break
        if subject not in picked:
            picked.append(subject)
    return picked


def _interviews(usable: list[dict], pair_id: str, winner: str | None,
                classes: dict[str, str], cards: dict[str, str],
                cells: dict[str, str]) -> list[dict]:
    """대표 응답자 몇 명의 «프로필 + 선택 + 그 사람의 말».

    위치응답자(`position_driven`·`anti_position`)는 **뺀다** — 내용이 아니라 제시 순서를
    보고 고른 사람이라, 그 인용을 이유로 읽으면 없는 근거가 생긴다.
    """
    by_class: dict[str, list[str]] = {}
    for subject, label in sorted(classes.items()):
        by_class.setdefault(label, []).append(subject)

    if winner == "Y":
        order = [("content_Y", "Y", 2), ("content_X", "X", 2), ("undecided", "UNDECIDED", 1)]
    else:
        # 이긴 쪽이 없어도(«못 잼») 양쪽을 같은 수로 보인다 — 한쪽만 보이면 판정처럼 읽힌다.
        order = [("content_X", "X", 2), ("content_Y", "Y", 2), ("undecided", "UNDECIDED", 1)]

    used_cells: set = set()
    chosen: list[tuple[str, str]] = []
    for label, choice, want in order:
        for subject in _pick(by_class.get(label, []), want, cells, used_cells):
            chosen.append((subject, choice))

    # 배분을 못 채웠으면 남은 사람으로 메운다. 실측에서 여기가 걸렸다 — 우열형은 한쪽이
    # 만장일치에 가까워 «진 쪽»이 0명인 일이 흔하고, 그대로 두면 카드가 3장만 나온다.
    # 채우는 순서는 배분 순서와 같다(이긴 쪽 → 진 쪽 → 미결정).
    taken = {subject for subject, _ in chosen}
    for label, choice, _want in order:
        for subject in by_class.get(label, []):
            if len(chosen) >= INTERVIEWS_PER_PAIR:
                break
            if subject not in taken:
                chosen.append((subject, choice))
                taken.add(subject)

    interviews = []
    for subject, choice in chosen:
        if len(interviews) >= INTERVIEWS_PER_PAIR:
            break
        profile = parse_profile(cards.get(subject))
        quote = _quote(usable, subject, pair_id)
        if is_empty(profile) or not quote:
            continue                                  # 빈 카드를 화면에 앉히지 않는다
        interviews.append({"choice": choice, "profile": profile, "quote": quote})
    return interviews


def _refuse(blocked: list[tuple[str, object]]) -> ProviderFailure:
    return ProviderFailure(
        "INVALID_REQUEST", "TWIN_TASK_TYPE_NOT_SERVICEABLE", 422, False,
        safe_diagnostics={
            "blocked": [{"pairId": pair_id, "taskType": v.task_type, "reason": v.reason}
                        for pair_id, v in blocked]})


async def execute_twin_survey(payload: dict, budget_seconds: float = 600.0,
                              event_sink: EventSink | None = None) -> dict:
    try:
        request = TwinSurveyInput.model_validate(payload)
    except ValidationError as failure:
        raise ProviderFailure(
            "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False,
            validation_fields=[{"field": ".".join(str(p) for p in e["loc"]),
                                "reason": e["type"]} for e in failure.errors()[:12]])
    _observe(event_sink, "TWIN_VALIDATING", "COMPLETED", "요청 계약 검증 완료")

    stimuli = [pair.as_stimulus() for pair in request.pairs]

    # ── 판매 경계 — LLM 호출 전에 막는다 ────────────────────────────────
    verdicts = {s["pairId"]: classify(s) for s in stimuli}
    blocked = [(pid, v) for pid, v in verdicts.items() if v.task_type not in SERVICEABLE]
    if blocked:
        _observe(event_sink, "TWIN_GATE", "BLOCKED", f"허용되지 않은 비교 {len(blocked)}개",
                 status="FAILED", reasonCode="TWIN_TASK_TYPE_NOT_SERVICEABLE")
        raise _refuse(blocked)
    _observe(event_sink, "TWIN_GATE", "COMPLETED", f"허용된 비교 {len(stimuli)}개")

    _observe(event_sink, "TWIN_BANK_LOADING", "STARTED", "Twin Bank를 확인하고 있습니다.")
    cards_all, frame = load()
    _observe(event_sink, "TWIN_BANK_READY", "COMPLETED",
             f"카드 {len(cards_all)}개, 프레임 {len(frame)}개")
    drawn, sampling = stratified_sample(frame, request.sampleSize)
    _observe(event_sink, "TWIN_SAMPLING", "COMPLETED",
             f"요청 {request.sampleSize}명, 추출 {len(drawn)}명, 층 {len(sampling['strata'])}개, 부족 층 {len(sampling['shortCells'])}개")
    cards = {row["pid_hash"]: cards_all[row["pid_hash"]] for row in drawn}
    # 인터뷰 대표를 고를 때 층이 겹치지 않게 쓰는 성×연령 셀. 표집에 쓴 것과 같은 축이다.
    cells = {row["pid_hash"]: f"{row['gender']}{row['band']}" for row in drawn}

    rows, telemetry = await run_survey(cards, stimuli, request.situation, budget_seconds,
                                       event_sink=event_sink)
    usable = [r for r in rows if r.get("ok")]

    pairs_out = []
    _observe(event_sink, "TWIN_AGGREGATING", "STARTED", f"집계 대상 비교 {len(stimuli)}개")
    for stimulus in stimuli:
        pair_id = stimulus["pairId"]
        stats = analyze(usable, pair_id)
        decision = verdict(stats)
        task_type = verdicts[pair_id].task_type

        notes = caveat_text.for_pair(task_type)
        if not decision["measurable"]:
            notes = notes + caveat_text.for_unmeasurable()

        winner = decision["winner"]
        interviews = _interviews(usable, pair_id, winner,
                                 classify_subjects(usable, pair_id), cards, cells)
        pairs_out.append({
            "pairId": pair_id,
            "taskType": task_type,
            "taskTypeReason": verdicts[pair_id].reason,
            "labels": {"X": stimulus["X"]["label"], "Y": stimulus["Y"]["label"]},
            "profiles": {"X": stimulus["X"]["text"], "Y": stimulus["Y"]["text"]},
            "winner": winner,
            "winnerLabel": (stimulus[winner]["label"] if winner in ("X", "Y") else None),
            "measurable": decision["measurable"],
            "decisionReason": decision["reason"],
            "deltaAvg": stats["delta_avg"],
            "confidenceInterval": decision.get("confidenceInterval"),
            "positionComponent": stats["position"],
            "contentShare": stats["lambda_p"],
            "contentShareLower": stats["lambda_wilson_lo"],
            "mde": mde_effective(stats["mde_p"], stats["n_p"]),
            "nPaired": stats["n_p"],
            "nRespondents": stats["n_subjects"],
            "respondentClasses": stats["cls"],
            "interviews": interviews,
            "caveats": notes,
        })
        _observe(event_sink, "TWIN_AGGREGATING", "PROGRESS",
                 f"집계 완료 {len(pairs_out)}/{len(stimuli)}")

    result = {
        "situation": request.situation,
        "sampleSize": request.sampleSize,
        "sampling": sampling,
        "pairs": pairs_out,
        "telemetry": telemetry,
        "notes": list(NOTES),
    }
    _observe(event_sink, "TWIN_COMPLETED", "COMPLETED", "Twin 조사 결과 정리 완료",
             status="COMPLETED")
    return result
