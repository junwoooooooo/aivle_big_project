"""오케스트레이션과 경계 문구 — LLM 호출 0회 (러너·코더·조건식을 갈아끼운다).

가장 중요한 것 셋:
  · 입력이 틀렸으면 **LLM 앞에서** 거절되는가
  · 응답이 너무 적게 걷혔을 때 **조용히 줄여서** 내보내지 않는가
  · 전원 응답을 실으면서도 pid·카드 원문이 새지 않는가
"""

import asyncio

import pytest

import app.interview as I
from app.interview import caveats
from app.interview.coding import (Assignment, Codebook, CodebookAlternative, CodebookTheme,
                                  verify)
from app.interview.targeting import TargetCriteria
from app.providers import ProviderFailure

BOARD = {"conceptName": "귀가 알림 밴드", "targetUsers": "맞벌이 부모",
         "problemScenario": "하교 30분 동안 연락이 닿지 않는다",
         "featureSet": ["도착 알림"], "differentiators": "전화기가 필요 없다",
         "priceKrw": 39000}

ANY_ONE = TargetCriteria(ageMin=0, ageMax=0, genders=[], householdSizeMin=0,
                         householdSizeMax=0, regions=[], incomeKeywords=[], jobKeywords=[],
                         hasChildren=0, householdRoles=[])


def answer(index: int) -> dict:
    return {"firstImpression": f"{index}번 첫인상", "restatement": f"{index}번 이해",
            "like": f"{index}번 좋은 점", "concern": f"{index}번 걸리는 점",
            "differentiation": f"{index}번 차별성", "relevance": f"{index}번 필요성",
            "usageScene": f"{index}번 사용 장면", "barrier": f"{index}번 장벽",
            "suggestion": f"{index}번 제안"}


CARD = ("저는 만 41세 여성입니다. 서울 시 지역에 살고 있습니다. "
        "2세대가구(부부+자녀) 형태의 3인 가구이고, 개인 월소득은 300~400만 원 미만 "
        "수준입니다. 일은 일반 지원 사무직 쪽 일을 임금 근로자로 하고 있습니다.")


def install(monkeypatch, answered: int, requested: int = 20):
    """뱅크·조건식·러너·코더를 갈아끼운다. 돈도 카드 뱅크도 필요 없다."""
    pids = [f"pid{index:03d}" for index in range(1, requested + 1)]
    frame = [{"pid_hash": pid, "gender": "여", "band": "40대"} for pid in pids]
    monkeypatch.setattr(I, "load", lambda: ({pid: CARD for pid in pids}, frame))

    async def fake_criteria(target_users, problem, timeout):
        return ANY_ONE

    monkeypatch.setattr(I, "resolve_criteria", fake_criteria)
    # 앞 8할이 타겟이다 — 실제 분할과 같은 비율이라 대비 블록이 빈 채로 굳지 않는다.
    cut = round(requested * 0.8)
    monkeypatch.setattr(I, "draw_split", lambda cards, _frame, size, _criteria: (
        frame[:size], set(pids[:cut]),
        {"requested": size, "drawn": size, "strata": {}, "shortCells": {}},
        {"criteria": ANY_ONE.model_dump(), "criteriaText": "조건 없음",
         "targetRequested": cut, "nonTargetRequested": size - cut,
         "targetDrawn": cut, "nonTargetDrawn": size - cut, "shortfall": 0,
         "targetShortCells": {}, "nonTargetShortCells": {}}))

    async def fake_run(cards, board_text, budget):
        rows = [{"subject": pid, "ok": index <= answered, "kind": None,
                 "answers": answer(index) if index <= answered else None}
                for index, pid in enumerate(sorted(cards), 1)]
        return rows, {"cells": len(rows), "formatViolations": len(rows) - answered,
                      "failures": 0, "llmCalls": len(rows)}

    monkeypatch.setattr(I, "run_interviews", fake_run)

    async def fake_code(board_text, answers, timeout):
        ids = sorted(answers, key=lambda r: int(r[1:]))
        book = Codebook(
            themes=[CodebookTheme(axis="LIKE", label="자동 알림"),
                    CodebookTheme(axis="CONCERN", label="배터리"),
                    CodebookTheme(axis="DIFFERENTIATION", label="차이 없음"),
                    CodebookTheme(axis="USAGE_SCENE", label="하교 시간"),
                    CodebookTheme(axis="BARRIER", label="가격"),
                    CodebookTheme(axis="SUGGESTION", label="값을 내려라")],
            alternatives=[CodebookAlternative(label="직접 전화한다"),
                          CodebookAlternative(label="그냥 참는다")],
            misreadPoints=["위치 추적기로 오해했다"])
        rows = []
        for position, rid in enumerate(ids):
            rows.append(Assignment(
                id=rid,
                comprehension=("misunderstood" if position == len(ids) - 1
                               else "partial" if position == len(ids) - 2 else "accurate"),
                differentiationVerdict=("similar" if position % 3 == 0 else "different"),
                barrierResolved=(position == 0),
                likeLabels=["자동 알림"] if position % 2 == 0 else [],
                concernLabels=["배터리"] if position < 2 else [],
                differentiationLabels=["차이 없음"] if position % 3 == 0 else [],
                usageSceneLabels=["하교 시간"] if position < 4 else [],
                barrierLabels=["가격"] if position < 3 else [],
                suggestionLabels=["값을 내려라"] if position < 2 else [],
                alternativeLabel="직접 전화한다" if position % 2 == 0 else "그냥 참는다"))
        coded = verify(book, rows, answers)
        return type(coded)(**{**coded.__dict__, "llmCalls": 2})

    monkeypatch.setattr(I, "code_responses", fake_code)


def run(payload):
    return asyncio.run(I.execute_market_interview(payload, budget_seconds=900.0))


@pytest.mark.parametrize("sample_size", [20, 40])
def test_main_core_completes_twenty_and_forty_with_provider_stubs(
        monkeypatch, sample_size):
    install(monkeypatch, answered=sample_size, requested=sample_size)
    result = run({"conceptBoard": BOARD, "sampleSize": sample_size})
    assert result["sampleSize"] == sample_size
    assert result["telemetry"]["answered"] == sample_size
    assert len(result["transcripts"]) == sample_size


def test_zero_traceable_themes_preserves_all_usable_transcripts(monkeypatch):
    install(monkeypatch, answered=40, requested=40)

    async def empty_code(board_text, answers, timeout):
        coded = verify(Codebook(themes=[], alternatives=[], misreadPoints=[]), [], answers)
        return type(coded)(**{**coded.__dict__, "llmCalls": 2})

    monkeypatch.setattr(I, "code_responses", empty_code)
    result = run({"conceptBoard": BOARD, "sampleSize": 40})
    assert result["themes"] == []
    assert result["telemetry"]["answered"] == 40
    assert len(result["transcripts"]) == 40


# ── 거절이 LLM 앞에서 일어나는가 ──────────────────────────────────────
def test_bad_input_is_refused_before_the_bank_is_even_loaded(monkeypatch):
    def explode(*_args, **_kwargs):
        raise AssertionError("입력 검증 전에 뱅크를 읽었다")

    monkeypatch.setattr(I, "load", explode)
    with pytest.raises(ProviderFailure) as failure:
        run({"conceptBoard": BOARD, "sampleSize": 25})
    assert failure.value.reason == "FIELD_CONSTRAINT_VIOLATION"


def test_too_few_usable_responses_fails_instead_of_shrinking_the_survey(monkeypatch):
    """8명 남은 80명 조사는 80명 조사가 아니다."""
    install(monkeypatch, answered=5, requested=20)
    with pytest.raises(ProviderFailure) as failure:
        run({"conceptBoard": BOARD, "sampleSize": 20})
    assert failure.value.reason == "MARKET_INTERVIEW_NO_USABLE_RESPONSE"


def test_half_the_sample_lost_also_fails(monkeypatch):
    install(monkeypatch, answered=9, requested=20)
    with pytest.raises(ProviderFailure):
        run({"conceptBoard": BOARD, "sampleSize": 20})


# ── 타겟 0명이면 돈을 쓰기 «전에» 멈춘다 (2026-08-15 신설) ──────────────
#
# 실측 판(n=40)은 「맞벌이」로 걸러 타겟이 0명인 것을 알고도 40회를 태웠다.
# 표집은 응답 수집보다 앞에 있으므로 그 시점에 이미 알 수 있었다.

def _no_target(monkeypatch, criteria):
    """타겟이 0명으로 갈린 표집. 응답을 걷으려 들면 그 자리에서 터뜨린다."""
    install(monkeypatch, answered=20, requested=20)

    async def fake_criteria(target_users, problem, timeout):
        return criteria

    monkeypatch.setattr(I, "resolve_criteria", fake_criteria)
    monkeypatch.setattr(I, "draw_split", lambda cards, frame, size, _criteria: (
        frame[:size], set(),
        {"requested": size, "drawn": size, "strata": {}, "shortCells": {}},
        {"criteria": _criteria.model_dump(), "criteriaText": "직업에 '맞벌이'(0명)",
         "targetRequested": 16, "nonTargetRequested": 4,
         "targetDrawn": 0, "nonTargetDrawn": size, "shortfall": 0,
         "targetShortCells": {}, "nonTargetShortCells": {}}))

    def explode(*_args, **_kwargs):
        raise AssertionError("타겟이 0명인데 응답을 걷었다 — 돈이 나갔다")

    monkeypatch.setattr(I, "run_interviews", explode)


def test_zero_target_stops_before_a_single_response_is_bought(monkeypatch):
    """타겟 0명인 타겟 조사는 타겟 조사가 아니다. 그리고 아직 한 푼도 안 썼다."""
    _no_target(monkeypatch, TargetCriteria(**{**ANY_ONE.model_dump(),
                                             "jobKeywords": ["맞벌이"]}))
    with pytest.raises(ProviderFailure) as failure:
        run({"conceptBoard": BOARD, "sampleSize": 20})
    assert failure.value.reason == "MARKET_INTERVIEW_NO_TARGET_SAMPLE"
    assert failure.value.retryable is False        # 다시 눌러도 같다 — 조건을 고쳐야 한다
    # 어느 조건이 0명이었는지를 사용자가 짚을 수 있어야 한다. 없으면 「AI 서비스 이상」만 남는다.
    diagnostics = failure.value.safe_diagnostics
    assert diagnostics["conditionMatches"][-1]["condition"] == "전부 동시에 만족"
    assert diagnostics["conditionMatches"][-1]["matched"] == 0


def test_a_survey_with_no_conditions_is_never_blocked(monkeypatch):
    """「누구나」로 돌린 조사에 「타겟이 없다」고 말하는 것은 경고가 아니라 소음이다."""
    _no_target(monkeypatch, ANY_ONE)
    with pytest.raises(AssertionError, match="응답을 걷었다"):
        run({"conceptBoard": BOARD, "sampleSize": 20})


# ── 정상 경로 ────────────────────────────────────────────────────────
def test_envelope_has_exactly_the_contracted_fields(monkeypatch):
    install(monkeypatch, answered=20)
    result = run({"conceptBoard": BOARD, "sampleSize": 20})
    assert set(result) == {"conceptBoard", "sampleSize", "sampling", "targeting",
                           "comprehension", "differentiation", "themes", "alternatives",
                           "segments", "contrast", "suggestionLinks", "interviews",
                           "transcripts", "telemetry", "caveats", "notes"}


def test_envelope_echoes_what_the_respondents_actually_saw(monkeypatch):
    install(monkeypatch, answered=20)
    result = run({"conceptBoard": BOARD, "sampleSize": 20})
    assert result["conceptBoard"]["rendered"].startswith("이름: 귀가 알림 밴드")
    assert "가격: 39,000원" in result["conceptBoard"]["rendered"]


def test_answered_count_is_reported_when_some_responses_were_lost(monkeypatch):
    install(monkeypatch, answered=17, requested=20)
    result = run({"conceptBoard": BOARD, "sampleSize": 20})
    assert result["telemetry"]["answered"] == 17
    assert result["sampling"]["drawn"] == 20


def test_coding_and_criteria_calls_are_counted_in_telemetry(monkeypatch):
    install(monkeypatch, answered=20)
    result = run({"conceptBoard": BOARD, "sampleSize": 20})
    assert result["telemetry"]["llmCalls"] == 23      # 수집 20 + 코드북·배정 2 + 조건식 1


def test_themes_keep_the_respondent_list_that_the_insight_layer_stands_on(monkeypatch):
    """옛 구조는 세고 나서 명단을 버렸다. 그래서 교차가 원리상 불가능했다."""
    install(monkeypatch, answered=20)
    result = run({"conceptBoard": BOARD, "sampleSize": 20})
    for theme in result["themes"]:
        assert theme["mentionCount"] == len(theme["respondentIds"])
        assert theme["respondentIds"]


def test_segment_buckets_add_up_to_the_mention_count(monkeypatch):
    """조용히 빼면 화면의 두 수가 어긋나고, 어긋난 이유를 아무도 못 찾는다."""
    install(monkeypatch, answered=20)
    result = run({"conceptBoard": BOARD, "sampleSize": 20})
    assert result["segments"]
    for row in result["segments"]:
        for dimension in row["breakdown"]:
            assert sum(b["count"] for b in dimension["buckets"]) == row["mentionCount"]


def test_contrast_splits_every_theme_without_losing_anyone(monkeypatch):
    install(monkeypatch, answered=20)
    result = run({"conceptBoard": BOARD, "sampleSize": 20})
    counts = {(t["axis"], t["label"]): t["mentionCount"] for t in result["themes"]}
    assert result["contrast"]
    for row in result["contrast"]:
        assert row["targetCount"] + row["nonTargetCount"] == counts[(row["axis"], row["label"])]


def test_suggestion_links_are_plain_intersections(monkeypatch):
    install(monkeypatch, answered=20)
    result = run({"conceptBoard": BOARD, "sampleSize": 20})
    members = {(t["axis"], t["label"]): set(t["respondentIds"]) for t in result["themes"]}
    for row in result["suggestionLinks"]:
        suggestion = members[("SUGGESTION", row["label"])]
        for link in row["links"]:
            assert link["overlapCount"] == len(suggestion & members[(link["axis"], link["label"])])


def test_alternative_mentions_never_exceed_the_number_of_people(monkeypatch):
    """관측된 고장이 여기서 잡힌다 — 대안 3개가 동시에 40/40 이었다."""
    install(monkeypatch, answered=20)
    result = run({"conceptBoard": BOARD, "sampleSize": 20})
    total = sum(row["mentionCount"] for row in result["alternatives"])
    assert total <= result["telemetry"]["answered"]
    assert result["telemetry"]["homogeneity"]["alternativeSum"] == total


def test_neither_the_cards_nor_the_transcripts_carry_the_pid_or_the_raw_card(monkeypatch):
    """전원을 실으면 사람 수가 16배다 — 유출 검사도 같이 넓힌다."""
    install(monkeypatch, answered=20)
    result = run({"conceptBoard": BOARD, "sampleSize": 20})
    for section in ("interviews", "transcripts"):
        blob = repr(result[section])
        assert "pid0" not in blob
        assert "임금 근로자" not in blob                   # 카드 원문이 새지 않는다
    assert result["interviews"][0]["profile"]["region"] == "서울"
    assert len(result["transcripts"]) == 20
    assert [row["target"] for row in result["transcripts"]].count(True) == 16


def test_results_are_reproducible_for_the_same_input(monkeypatch):
    """⚠ 조건식을 고정한 상태에서의 재현성이다 — 조건식 자체는 LLM 이 만든다."""
    install(monkeypatch, answered=20)
    first = run({"conceptBoard": BOARD, "sampleSize": 20})
    install(monkeypatch, answered=20)
    second = run({"conceptBoard": BOARD, "sampleSize": 20})
    for key in ("themes", "alternatives", "interviews", "transcripts", "comprehension",
                "segments", "contrast", "suggestionLinks", "differentiation"):
        assert first[key] == second[key]


# ── 경계 문구 ────────────────────────────────────────────────────────
def test_caveats_are_never_empty(monkeypatch):
    install(monkeypatch, answered=20)
    result = run({"conceptBoard": BOARD, "sampleSize": 20})
    assert result["caveats"] and all(isinstance(c, str) and c for c in result["caveats"])
    assert result["notes"]


def test_caveats_say_this_format_was_never_externally_validated():
    notes = caveats.build("이름: 밴드")
    assert any("외적 타당성 시험을 거치지 않았다" in note for note in notes)
    assert any("백분율로 환산하지 마라" in note for note in notes)
    assert any("지불의사" in note for note in notes)


def test_new_blocks_bring_their_own_boundaries():
    """9문항이 새로 여는 칸마다 「이건 답이 아니다」가 붙어 있어야 한다."""
    notes = caveats.build("이름: 밴드")
    assert any("상상해서 답한 것" in note for note in notes)
    assert any("비언어" in note for note in notes)
    assert any("행동 기준으로 응답자를 고르지 못한다" in note for note in notes)
    assert any("3명 이하" in note for note in notes)


def test_instrument_equivalence_is_still_unconfirmed():
    """`INSTRUMENT_EQUIVALENCE_CONFIRMED` 는 배포로만 바뀐다. 승계 확인."""
    notes = caveats.build("이름: 밴드")
    assert any("성적 미전이" in note for note in notes)


def test_ethical_appeal_is_flagged_but_not_blocked():
    """친환경이 본질인 사업안을 조사 자체에서 막지는 않는다 — 값 옆에 적는다."""
    # 「친환경」은 「환경」도 함께 걸린다 — 부분 일치라 그렇다. 알림이 목적이므로 문제되지 않는다.
    assert caveats.ethical_hits("이름: 친환경 세제\n다른 것과 다른 점: 유기농 원료") == \
        ["유기농", "친환경", "환경"]
    notes = caveats.build("이름: 친환경 세제")
    assert any("대응 문항이 없어" in note for note in notes)


def test_non_ethical_board_does_not_get_the_ethical_caveat():
    notes = caveats.build("이름: 귀가 알림 밴드\n가격: 39,000원")
    assert not any("대응 문항이 없어" in note for note in notes)


def test_ethical_caveat_rides_along_in_the_real_envelope(monkeypatch):
    install(monkeypatch, answered=20)
    board = {**BOARD, "differentiators": "재활용 소재로 만든다"}
    result = run({"conceptBoard": board, "sampleSize": 20})
    assert any("대응 문항이 없어" in note for note in result["caveats"])
