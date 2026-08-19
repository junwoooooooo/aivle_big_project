"""시장 인터뷰 — 확정된 사업안 하나를 던지고 반응을 듣는다.

한국미디어패널조사(KISDI) 응답자 1인당 1장으로 만든 카드를 LLM 에게 주고, 그 사람 역할로
컨셉보드 하나를 읽고 **고정 9문항**에 답하게 한다. 산출은 수치가 아니라 **말**이다 —
무엇을 이해했고, 무엇에 끌렸고, 무엇이 걸렸고, 왜 안 사겠다고 하는가.

**절대 척도를 묻지 않는다.** 구매의향 5점·지불의사 같은 문항은 임계가 응답자 카드가 아니라
실행 모델에 있어서, 같은 25명·같은 자극에 모델만 바꿔도 방향이 뒤집히는 것이 실측됐다
(`docs/TWIN_SURVEY_HANDOFF.md` §9). 이 조사가 내는 유일한 수치는 **언급 수**이고,
그것도 「이 표본에서 몇 명이 그 말을 했나」 이상으로 읽으면 안 된다.

네 단계다.

  1. 조건식 (`targeting.resolve_criteria`) — LLM 1회. 타겟/비타겟 8:2 로 뱅크를 가른다
  2. 응답 수집 (`runner.run_interviews`) — n 명 × 1 셀
  3. 주제 코딩 (`coding.code_responses`) — **2패스**. 코드북 1회 + 배정 ⌈n/40⌉회.
     **이름표만** 붙이고 숫자와 인용문은 여기서 코드가 만든다
  4. 교차 (`analysis`, `saturation`) — **LLM 0회.** 전부 응답자 집합 연산이다

**`themes[].respondentIds` 가 이 봉투의 심장이다.** 옛 구조는 언급 수를 센 뒤 그 명단을
버렸고, 그래서 「가격 우려 19명이 누구인가」를 물을 수 없었다. 명단을 남기면 세그먼트
교차도 제안↔우려 연결도 덧셈과 교집합으로 나온다.

등록(`app/api/executions.py` 의 `TASK_TYPES` 와 분기 체인, 그리고
`tests/test_internal_task_type_alignment.py`)은 이 모듈 밖이다.
"""

from pydantic import ValidationError

from app.interview import analysis, ledger
from app.interview import caveats as caveat_text
from app.interview.coding import CODED_FIELDS, code_responses
from app.interview.models import (AXES, AXIS_SOURCE, COMPREHENSION,
                                  DIFFERENTIATION_VERDICTS, MarketInterviewInput)
from app.interview.runner import run_interviews
from app.interview.saturation import homogeneity
from app.interview.targeting import (condition_matches, draw_split, has_conditions,
                                     resolve_criteria)
from app.providers import ProviderFailure
from app.twin.bank import load
from app.twin.profile import is_empty, parse_profile

__all__ = ["execute_market_interview"]

#: 대표 응답자 카드 수. 전원 응답은 `transcripts` 가 따로 싣는다 —
#: 이 칸은 「골라 보여주는 자리」이고 배분(아래 CARD_QUOTA)이 그 설계다.
INTERVIEW_CARDS = 5
QUOTE_MAX_CHARS = 300
CRITERIA_BUDGET_SECONDS = 60.0
CODING_BUDGET_SECONDS = 300.0

#: 정성 조사의 최소 표본. 이 아래로 떨어지면 결과가 아니라 일화다.
MIN_USABLE = 8

#: 대표 응답자 배분 — 제대로 이해한 사람 2 · 반만 이해한 사람 2 · 오해한 사람 1.
#: 오해한 사람을 반드시 한 자리 남기는 것이 설계다. 그 카드가 「컨셉이 나쁜 게 아니라
#: 설명이 나쁘다」를 화면에서 눈으로 보게 하는 유일한 자리다.
CARD_QUOTA = (("misunderstood", 1), ("accurate", 2), ("partial", 2))

NOTES = (
    "이 결과는 실존 인물의 응답이 아니라 실측 프로파일 기반 시뮬레이션이다.",
    "caveats 를 떨어뜨리지 마라 — 값과 함께 옮겨야 하는 문장이다.",
    "언급 수는 이 표본에서 그 말을 한 사람 수다. 백분율·점유율·구매율로 환산하지 마라.",
    "이해도는 컨셉이 좋은지 나쁜지가 아니라 컨셉보드가 읽히는지를 잰다.",
    "세그먼트 교차와 연결표는 계산일 뿐이다 — 무슨 뜻인지는 이 결과가 말하지 않는다.",
)


def _trim(text: str | None) -> str | None:
    body = (text or "").strip()
    return body[:QUOTE_MAX_CHARS] if body else None


def _quote(ids: list[str], answers: dict[str, dict], field: str) -> str | None:
    """그 주제에 든 사람 중 **실제로 그 칸에 쓴 첫 사람**의 말. 지어내지 않는다."""
    for rid in ids:
        body = _trim((answers.get(rid) or {}).get(field))
        if body:
            return body
    return None


def _pick(candidates: list[str], want: int, cells: dict[str, str], used: set) -> list[str]:
    """층이 겹치지 않게 먼저 고르고, 모자라면 완화해 채운다. 난수를 쓰지 않는다."""
    picked: list[str] = []
    for rid in candidates:
        if len(picked) >= want:
            break
        cell = cells.get(rid)
        if cell is not None and cell in used:
            continue
        picked.append(rid)
        used.add(cell)
    for rid in candidates:
        if len(picked) >= want:
            break
        if rid not in picked:
            picked.append(rid)
    return picked


def _cards(buckets: dict[str, list[str]], answers: dict[str, dict],
           profiles: dict[str, dict], cells: dict[str, str]) -> list[dict]:
    """대표 응답자 카드 ≤5장. 배분을 못 채우면 배분 순서대로 남은 사람으로 메운다."""
    used_cells: set = set()
    chosen: list[tuple[str, str]] = []
    for label, want in CARD_QUOTA:
        for rid in _pick(buckets.get(label, []), want, cells, used_cells):
            chosen.append((rid, label))

    taken = {rid for rid, _ in chosen}
    for label, _want in CARD_QUOTA:
        for rid in buckets.get(label, []):
            if len(chosen) >= INTERVIEW_CARDS:
                break
            if rid not in taken:
                chosen.append((rid, label))
                taken.add(rid)

    cards = []
    for rid, label in chosen:
        if len(cards) >= INTERVIEW_CARDS:
            break
        profile = profiles.get(rid) or {}
        answer = answers.get(rid) or {}
        if is_empty(profile):
            continue                                   # 빈 카드를 화면에 앉히지 않는다
        cards.append({
            "comprehension": label,
            "profile": profile,
            "firstImpression": _trim(answer.get("firstImpression")),
            **{field: _trim(answer.get(field)) for field in CODED_FIELDS},
        })
    return cards


def _transcripts(answers: dict[str, dict], profiles: dict[str, dict],
                 target_ids: set) -> list[dict]:
    """전원 응답 — 검증 통로다. 세그먼트 교차의 수를 화면에서 되짚을 수 있게 한다.

    **프로필 6필드와 답변만 싣는다.** 카드 원문(재배포 금지 마이크로데이터)도 `pid_hash` 도
    싣지 않는다. 답은 인용문과 같은 300자 상한을 건다 — 상한 없는 배열을 봉투에 여는 것이
    더 위험하고, 9문항 응답이 그 길이를 넘는 일은 사실상 없다.
    """
    return [{"id": rid, "target": rid in target_ids, "profile": profiles.get(rid) or {},
             "firstImpression": _trim(answers[rid].get("firstImpression")),
             **{field: _trim(answers[rid].get(field)) for field in CODED_FIELDS}}
            for rid in sorted(answers, key=lambda r: int(r[1:]))]


def _buckets(coded, answers: dict[str, dict]) -> tuple[dict, dict[str, list[str]]]:
    """이해도 요약 + 칸별 명단. 배정이 1인 1값이라 배타는 구조로 성립한다.

    어디에도 안 든 사람(배정표에서 떨어진 사람)은 `unclassified` 로 남긴다 —
    조용히 «부분»에 몰아넣으면 없는 판정이 생긴다.
    """
    buckets = {label: list(coded.comprehension.get(label, [])) for label in COMPREHENSION}
    summary = {label: len(buckets[label]) for label in COMPREHENSION}
    summary["unclassified"] = len(answers) - sum(summary.values())
    summary["misreadPoints"] = [t for t in (_trim(p) for p in coded.misreadPoints) if t]
    return summary, buckets


def _differentiation(coded, answers: dict[str, dict]) -> dict:
    """「다르다 / 비슷하다 / 모르겠다」. **차이 없음이 다수인 것 자체가 핵심 경고다.**"""
    summary = {verdict: len(coded.differentiation.get(verdict, []))
               for verdict in DIFFERENTIATION_VERDICTS}
    summary["unclassified"] = len(answers) - sum(summary.values())
    return summary


def _themes(coded, answers: dict[str, dict], resolved: set) -> list[dict]:
    """축 순서 → 언급 수 내림차순 → 이름표 오름차순.

    `respondentIds` 를 **남긴다** — Insight 층 전체가 이 명단 위에 선다.
    `resolvedCount` 는 그 주제를 말한 사람 중 「걸림돌이 없어지면 사겠다」고 **말한** 사람 수다.
    """
    rows = [{"axis": theme["axis"], "label": theme["label"],
             "mentionCount": len(theme["respondentIds"]),
             "respondentIds": list(theme["respondentIds"]),
             "resolvedCount": sum(1 for rid in theme["respondentIds"] if rid in resolved),
             "quote": _quote(theme["respondentIds"], answers, AXIS_SOURCE[theme["axis"]])}
            for theme in coded.themes if theme["respondentIds"]]
    rows.sort(key=lambda r: (AXES.index(r["axis"]), -r["mentionCount"], r["label"]))
    return rows


def _alternatives(coded) -> list[dict]:
    """1인 1대안이라 **언급 수 합계가 응답자 수를 넘지 않는다** — 구조로 보장된다."""
    rows = [{"label": row["label"], "mentionCount": len(row["respondentIds"])}
            for row in coded.alternatives if row["respondentIds"]]
    rows.sort(key=lambda r: (-r["mentionCount"], r["label"]))
    return rows


async def execute_market_interview(payload: dict, budget_seconds: float = 900.0) -> dict:
    try:
        request = MarketInterviewInput.model_validate(payload)
    except ValidationError as failure:
        raise ProviderFailure(
            "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False,
            validation_fields=[{"field": ".".join(str(p) for p in e["loc"]),
                                "reason": e["type"]} for e in failure.errors()[:12]]) from failure

    board_text = request.conceptBoard.render()

    cards_all, frame = load()
    criteria = await resolve_criteria(request.conceptBoard.targetUsers,
                                      request.conceptBoard.problemScenario,
                                      CRITERIA_BUDGET_SECONDS)
    drawn, target_pids, sampling, targeting = draw_split(
        cards_all, frame, request.sampleSize, criteria)

    # ── 조건을 걸었는데 맞는 사람이 0명이면 **여기서 멈춘다. 아직 한 푼도 안 썼다.**
    #
    # 표집은 응답 수집보다 앞에 있으므로 「타겟 0명」은 돈을 쓰기 전에 이미 안다.
    # 2026-08-15 실측 판은 그것을 알고도 40회를 태웠고, 화면 경고는 0건이었다
    # (`shortfall` 이 비타겟으로 채운 뒤엔 언제나 0이라서). 사용자는 40회를 다 쓴 뒤에야
    # 헛돈 것을 알았다.
    #
    # `MIN_USABLE` 이 세운 원리 그대로다 — 「8명 남은 80명 조사는 80명 조사가 아니다」면
    # 「타겟 0명인 타겟 조사도 타겟 조사가 아니다」.
    #
    # ⚠ **조건이 하나도 없는 조사(「누구나」)는 막지 않는다.** 그때는 전원이 타겟이고,
    #   0명이라고 말하는 것은 경고가 아니라 소음이다.
    if has_conditions(criteria) and targeting["targetDrawn"] == 0:
        raise ProviderFailure(
            "EXECUTION_FAILED", "MARKET_INTERVIEW_NO_TARGET_SAMPLE", 422, False,
            safe_diagnostics={
                "criteriaText": targeting["criteriaText"],
                # 어느 조건이 0명이었는지를 사용자가 **직접 짚을 수 있게** 싣는다.
                # 이 진단이 없으면 화면에는 「AI 서비스 이상」만 남는다.
                "conditionMatches": condition_matches(cards_all, criteria),
                "panelSize": len(cards_all)})

    cards = {row["pid_hash"]: cards_all[row["pid_hash"]] for row in drawn}
    # 대표 카드를 고를 때 층이 겹치지 않게 쓰는 성×연령 셀. 표집에 쓴 것과 같은 축이다.
    cells_by_pid = {row["pid_hash"]: f"{row['gender']}{row['band']}" for row in drawn}

    rows, telemetry = await run_interviews(
        cards, board_text,
        budget_seconds - CODING_BUDGET_SECONDS - CRITERIA_BUDGET_SECONDS)

    # ── 응답자 id 를 여기서 매긴다. pid 오름차순이라 같은 표본이면 같은 번호다.
    #    LLM 에게는 이 번호만 보낸다 — pid_hash 는 프롬프트에도 결과에도 실리지 않는다.
    usable = [row for row in rows if row["ok"]]
    answers = {f"R{index}": row["answers"] for index, row in enumerate(usable, 1)}
    pid_of = {f"R{index}": row["subject"] for index, row in enumerate(usable, 1)}

    if len(answers) < MIN_USABLE or len(answers) * 2 < request.sampleSize:
        # 조용히 줄여서 내보내지 않는다. 8명 남은 80명 조사는 80명 조사가 아니다.
        raise ProviderFailure(
            "EXECUTION_FAILED", "MARKET_INTERVIEW_NO_USABLE_RESPONSE", 500, True,
            safe_diagnostics={"requested": request.sampleSize, "answered": len(answers),
                              "formatViolations": telemetry.get("formatViolations"),
                              "failures": telemetry.get("failures")})

    coded = await code_responses(board_text, answers, CODING_BUDGET_SECONDS)

    summary, buckets = _buckets(coded, answers)
    profiles = {rid: parse_profile(cards.get(pid)) for rid, pid in pid_of.items()}
    cells = {rid: cells_by_pid.get(pid) for rid, pid in pid_of.items()}
    target_ids = {rid for rid, pid in pid_of.items() if pid in target_pids}

    themes = _themes(coded, answers, set(coded.barrierResolvedIds))
    alternatives = _alternatives(coded)

    telemetry["answered"] = len(answers)
    telemetry["llmCalls"] = telemetry.get("llmCalls", 0) + coded.llmCalls + 1  # +조건식 1회
    telemetry["homogeneity"] = homogeneity(themes, alternatives, len(answers))

    result = {
        "conceptBoard": {**request.conceptBoard.model_dump(), "rendered": board_text},
        "sampleSize": request.sampleSize,
        "sampling": sampling,
        "targeting": targeting,
        "comprehension": summary,
        "differentiation": _differentiation(coded, answers),
        "themes": themes,
        "alternatives": alternatives,
        "segments": analysis.segments(themes, profiles),
        "contrast": analysis.contrast(themes, target_ids),
        "suggestionLinks": analysis.suggestion_links(themes),
        "interviews": _cards(buckets, answers, profiles, cells),
        "transcripts": _transcripts(answers, profiles, target_ids),
        "telemetry": telemetry,
        "caveats": caveat_text.build(board_text),
        "notes": list(NOTES),
    }
    ledger.write(answers, profiles, coded, result)
    return result
