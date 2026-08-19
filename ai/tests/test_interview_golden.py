"""골든 결과 — 세 층이 같은 파일을 읽는다.

`tests/fixtures/market_interview/interview.json` 은 이 파일과
`backend/.../MarketInterviewContractTests.java`,
`frontEnd/src/features/market-interview/marketInterviewResult.test.js` 가 함께 읽는다.
하나이므로 한쪽만 고치면 반대쪽이 즉시 빨개진다 — 「AI 는 맞다는데 백엔드가 거부」 루프를
끊는 장치다.

**손으로 고치지 마라.** `_regenerate.py` 로 다시 굽는다(유료 호출 0회) — 세그먼트 버킷 합과
연결표 교집합 수는 사람이 적으면 코드가 내는 값과 조용히 어긋난다.
"""

import json
from pathlib import Path

import pytest

from app.interview.models import AXES, COMPREHENSION, DIFFERENTIATION_VERDICTS

GOLDEN = Path(__file__).parent / "fixtures/market_interview/interview.json"


@pytest.fixture(scope="module")
def golden() -> dict:
    return json.loads(GOLDEN.read_text(encoding="utf-8"))


def test_envelope_fields(golden):
    assert set(golden) == {"conceptBoard", "sampleSize", "sampling", "targeting",
                           "comprehension", "differentiation", "themes", "alternatives",
                           "segments", "contrast", "suggestionLinks", "interviews",
                           "transcripts", "telemetry", "caveats", "notes"}


def test_board_carries_what_the_respondents_saw(golden):
    board = golden["conceptBoard"]
    assert set(board) == {"conceptName", "targetUsers", "problemScenario", "featureSet",
                          "differentiators", "priceKrw", "rendered"}
    assert board["rendered"].startswith(f"이름: {board['conceptName']}")


def test_comprehension_counts_add_up_to_the_people_who_answered(golden):
    counts = golden["comprehension"]
    total = sum(counts[key] for key in (*COMPREHENSION, "unclassified"))
    assert total == golden["telemetry"]["answered"]


def test_differentiation_counts_add_up_too(golden):
    counts = golden["differentiation"]
    total = sum(counts[key] for key in (*DIFFERENTIATION_VERDICTS, "unclassified"))
    assert total == golden["telemetry"]["answered"]


def _keys(node) -> set:
    if isinstance(node, dict):
        return set(node) | {k for v in node.values() for k in _keys(v)}
    if isinstance(node, list):
        return {k for item in node for k in _keys(item)}
    return set()


def test_no_percentage_field_anywhere_in_the_envelope(golden):
    """이 조사가 내는 수치는 사람 수뿐이다. 비율 칸이 생기면 여기서 걸린다."""
    forbidden = ("percent", "ratio", "share", "rate", "proportion", "pct")
    # 계측(`telemetry`)은 화면이 값으로 읽는 자리가 아니라 실행 기록이다 — `rateLimited` 는 비율이 아니다.
    findings = {key: golden[key] for key in
                ("comprehension", "differentiation", "themes", "alternatives", "segments",
                 "contrast", "suggestionLinks", "interviews", "transcripts", "conceptBoard")}
    offenders = [key for key in _keys(findings)
                 if any(word in key.lower() for word in forbidden)
                 and key != "accurate"]            # «정확히 이해한 사람 수»는 비율이 아니다
    assert offenders == []


def test_mention_counts_never_exceed_the_people_who_answered(golden):
    answered = golden["telemetry"]["answered"]
    for row in (*golden["themes"], *golden["alternatives"]):
        assert 1 <= row["mentionCount"] <= answered


def test_mention_count_is_exactly_the_length_of_the_respondent_list(golden):
    """AI 가 센 수를 믿지 않는다 — 언급 수는 명단의 길이다."""
    for theme in golden["themes"]:
        assert theme["mentionCount"] == len(theme["respondentIds"])
        assert len(set(theme["respondentIds"])) == len(theme["respondentIds"])


def test_alternatives_never_sum_past_the_number_of_people(golden):
    """1인 1대안이다. 2026-08-12 에 대안 3개가 동시에 40/40 으로 나온 적이 있다."""
    answered = golden["telemetry"]["answered"]
    total = sum(row["mentionCount"] for row in golden["alternatives"])
    assert total <= answered
    assert golden["telemetry"]["homogeneity"]["alternativeSum"] == total


def test_no_axis_is_saturated_in_the_golden(golden):
    """골든이 포화 상태면 이 픽스처로는 포화 경고를 시험할 수 없다."""
    homogeneity = golden["telemetry"]["homogeneity"]
    assert homogeneity["saturatedThemes"] == []
    assert all(count >= 2 for count in homogeneity["axisLabelCounts"].values())


def test_themes_are_grouped_by_axis_in_display_order(golden):
    order = [AXES.index(theme["axis"]) for theme in golden["themes"]]
    assert order == sorted(order)
    assert {theme["axis"] for theme in golden["themes"]} == set(AXES)


def test_every_theme_quote_appears_verbatim_in_someones_answer(golden):
    """인용문은 코드가 실제 답에서 꺼낸다 — 지어낸 문장이면 원문에도 없다."""
    said = json.dumps(golden["transcripts"], ensure_ascii=False)
    quoted = [t["quote"] for t in golden["themes"] if t["quote"]]
    assert quoted, "인용문이 하나도 없다"
    assert all(quote in said for quote in quoted)


# ── Insight 층 — 세는 도중에 사람을 흘리지 않는가 ────────────────────
def test_segment_buckets_add_up_to_the_mention_count(golden):
    assert golden["segments"]
    for row in golden["segments"]:
        for dimension in row["breakdown"]:
            assert sum(b["count"] for b in dimension["buckets"]) == row["mentionCount"]


def test_contrast_covers_every_theme_and_splits_it_whole(golden):
    counts = {(t["axis"], t["label"]): t["mentionCount"] for t in golden["themes"]}
    assert len(golden["contrast"]) == len(golden["themes"])
    for row in golden["contrast"]:
        assert row["targetCount"] + row["nonTargetCount"] == counts[(row["axis"], row["label"])]


def test_suggestion_links_are_real_intersections(golden):
    members = {(t["axis"], t["label"]): set(t["respondentIds"]) for t in golden["themes"]}
    assert golden["suggestionLinks"]
    assert any(row["links"] for row in golden["suggestionLinks"])
    for row in golden["suggestionLinks"]:
        suggestion = members[("SUGGESTION", row["label"])]
        for link in row["links"]:
            assert link["overlapCount"] == len(suggestion & members[(link["axis"], link["label"])])


def test_targeting_shows_the_filter_it_actually_used(golden):
    targeting = golden["targeting"]
    assert targeting["criteriaText"]
    assert targeting["targetDrawn"] + targeting["nonTargetDrawn"] == golden["sampling"]["drawn"]
    assert [row["target"] for row in golden["transcripts"]].count(True) > 0
    assert [row["target"] for row in golden["transcripts"]].count(False) > 0


# ── 근거층 ───────────────────────────────────────────────────────────
def test_interview_cards_keep_a_seat_for_someone_who_misunderstood(golden):
    levels = [card["comprehension"] for card in golden["interviews"]]
    assert "misunderstood" in levels
    assert len(golden["interviews"]) <= 5


def test_transcripts_carry_everyone_who_answered(golden):
    assert len(golden["transcripts"]) == golden["telemetry"]["answered"]
    assert [row["id"] for row in golden["transcripts"]] == \
        [f"R{i}" for i in range(1, golden["telemetry"]["answered"] + 1)]


def test_neither_cards_nor_transcripts_carry_the_pid_or_the_raw_profile_card(golden):
    for section in ("interviews", "transcripts"):
        blob = json.dumps(golden[section], ensure_ascii=False)
        assert "pid" not in blob
        assert "임금 근로자" not in blob


def test_caveats_and_notes_are_not_empty(golden):
    assert golden["caveats"] and golden["notes"]
    assert any("외적 타당성 시험을 거치지 않았다" in note for note in golden["caveats"])
    assert any("상상해서 답한 것" in note for note in golden["caveats"])
