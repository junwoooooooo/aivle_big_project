"""패널 트윈 조사 — 실측 프로파일 카드로 선택형 설문을 돌린다.

한국미디어패널조사(KISDI) 응답자 1인당 1장으로 만든 카드를 LLM에게 주고 그 사람 역할로
두 상품안 중 하나를 고르게 한다. 산출은 **방향과 신뢰구간까지**다.

파는 것은 외적 타당성 시험에서 성적이 난 두 유형뿐이다(`task_type.py` 가 게이트다).
허용 유형이 아니면 **LLM을 한 번도 부르지 않고** 거절한다.

등록(`app/api/executions.py` 의 `TASK_TYPES` 와 분기 체인, 그리고
`tests/test_internal_task_type_alignment.py`)은 **이 모듈 밖**이다. 셋을 한꺼번에 고쳐야
하므로 별도 단계로 미뤄져 있다 — 이 모듈은 등록 없이도 import·테스트된다.
"""

from pydantic import ValidationError

from app.providers import ProviderFailure
from app.twin import caveats as caveat_text
from app.twin.aggregate import analyze, mde_effective, verdict
from app.twin.bank import load, stratified_sample
from app.twin.models import TwinSurveyInput
from app.twin.runner import run_survey
from app.twin.task_type import SERVICEABLE, classify

__all__ = ["execute_twin_survey"]

EXCERPTS_PER_PAIR = 6          # 응답 봉투 2 MiB 상한 — 셀 원장은 절대 싣지 않는다
EXCERPT_MAX_CHARS = 300

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


def _refuse(blocked: list[tuple[str, object]]) -> ProviderFailure:
    return ProviderFailure(
        "INVALID_REQUEST", "TWIN_TASK_TYPE_NOT_SERVICEABLE", 422, False,
        safe_diagnostics={
            "blocked": [{"pairId": pair_id, "taskType": v.task_type, "reason": v.reason}
                        for pair_id, v in blocked]})


async def execute_twin_survey(payload: dict, budget_seconds: float = 600.0) -> dict:
    try:
        request = TwinSurveyInput.model_validate(payload)
    except ValidationError as failure:
        raise ProviderFailure(
            "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False,
            validation_fields=[{"field": ".".join(str(p) for p in e["loc"]),
                                "reason": e["type"]} for e in failure.errors()[:12]])

    stimuli = [pair.as_stimulus() for pair in request.pairs]

    # ── 판매 경계 — LLM 호출 전에 막는다 ────────────────────────────────
    verdicts = {s["pairId"]: classify(s) for s in stimuli}
    blocked = [(pid, v) for pid, v in verdicts.items() if v.task_type not in SERVICEABLE]
    if blocked:
        raise _refuse(blocked)

    cards_all, frame = load()
    drawn, sampling = stratified_sample(frame, request.sampleSize)
    cards = {row["pid_hash"]: cards_all[row["pid_hash"]] for row in drawn}

    rows, telemetry = await run_survey(cards, stimuli, request.situation, budget_seconds)
    usable = [r for r in rows if r.get("ok")]

    pairs_out = []
    for stimulus in stimuli:
        pair_id = stimulus["pairId"]
        stats = analyze(usable, pair_id)
        decision = verdict(stats)
        task_type = verdicts[pair_id].task_type

        notes = caveat_text.for_pair(task_type)
        if not decision["measurable"]:
            notes = notes + caveat_text.for_unmeasurable()

        excerpts = []
        for row in usable:
            if row["pair_id"] != pair_id or row["choice"] is None:
                continue
            body = _excerpt(row.get("raw"))
            if body:
                excerpts.append(body)
            if len(excerpts) >= EXCERPTS_PER_PAIR:
                break

        winner = decision["winner"]
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
            "rationaleExcerpts": excerpts,
            "caveats": notes,
        })

    return {
        "situation": request.situation,
        "sampleSize": request.sampleSize,
        "sampling": sampling,
        "pairs": pairs_out,
        "telemetry": telemetry,
        "notes": list(NOTES),
    }
