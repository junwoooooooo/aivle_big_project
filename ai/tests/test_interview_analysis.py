"""Insight 층 — 교차가 **응답자 명단 그대로**인지. LLM 호출 0회.

이 층이 거짓말을 하는 방법은 하나다: 세다가 사람을 흘리는 것. 그래서 여기서 검사하는 것은
거의 전부 「합이 맞는가」다.
"""

from app.interview.analysis import age_band, contrast, segments, suggestion_links

PROFILE = {"age": 34, "gender": "여성", "household": "3인 가구",
           "region": "서울", "income": "월소득 300~400만 원", "job": "사무직"}


def theme(axis, label, ids):
    return {"axis": axis, "label": label, "mentionCount": len(ids), "respondentIds": list(ids)}


def profiles(**overrides):
    base = {f"R{i}": dict(PROFILE) for i in range(1, 7)}
    base.update(overrides)
    return base


# ── 세그먼트 교차 ────────────────────────────────────────────────────
def test_every_dimension_adds_up_to_the_mention_count():
    rows = segments([theme("CONCERN", "가격", ["R1", "R2", "R3"])], profiles())
    for dimension in rows[0]["breakdown"]:
        assert sum(bucket["count"] for bucket in dimension["buckets"]) == 3


def test_an_unreadable_profile_field_becomes_its_own_bucket_instead_of_vanishing():
    """조용히 빼면 화면의 두 수가 어긋나고, 어긋난 이유를 아무도 못 찾는다."""
    rows = segments([theme("CONCERN", "가격", ["R1", "R2"])],
                    profiles(R2={**PROFILE, "income": None}))
    income = next(d for d in rows[0]["breakdown"] if d["dimension"] == "개인 소득")
    assert {b["label"] for b in income["buckets"]} == {"월소득 300~400만 원", "확인 안 됨"}
    assert sum(b["count"] for b in income["buckets"]) == 2


def test_a_long_tail_is_folded_not_dropped():
    """2026-08-13 실측 회귀: 소득 구간이 7종이라 버킷 합이 39 vs 40 으로 어긋났고,
    백엔드 계약이 결과를 통째로 거부했다(`RESULT_FIELD_CONSTRAINT_VIOLATION`).

    화면을 짧게 하려던 상한이 조사 전체를 죽인 자리다. 골든은 소득 값이 적어 못 잡았다.
    """
    people = {f"R{i}": {**PROFILE, "income": f"월소득 구간{i}"} for i in range(1, 10)}
    rows = segments([theme("CONCERN", "가격", list(people))], people)
    income = next(d for d in rows[0]["breakdown"] if d["dimension"] == "개인 소득")
    assert sum(bucket["count"] for bucket in income["buckets"]) == 9
    assert len(income["buckets"]) <= 6
    assert any("그 밖" in bucket["label"] for bucket in income["buckets"])


def test_a_tail_of_exactly_one_keeps_its_own_name():
    """한 칸을 「그 밖 1종」으로 뭉개면 이름표를 잃는 대신 아무것도 못 얻는다."""
    people = {f"R{i}": {**PROFILE, "income": f"월소득 구간{i}"} for i in range(1, 7)}
    rows = segments([theme("CONCERN", "가격", list(people))], people)
    income = next(d for d in rows[0]["breakdown"] if d["dimension"] == "개인 소득")
    assert len(income["buckets"]) == 6
    assert not any("그 밖" in bucket["label"] for bucket in income["buckets"])
    assert sum(bucket["count"] for bucket in income["buckets"]) == 6


def test_segments_take_at_most_two_themes_per_axis():
    themes = [theme("CONCERN", f"걱정{i}", [f"R{i}"]) for i in range(1, 6)]
    rows = segments(themes, profiles())
    assert len(rows) == 2


def test_age_bands_match_the_bands_used_for_sampling():
    """축이 갈리면 교차표를 표본과 못 맞춘다."""
    assert age_band(24) == "20대"
    assert age_band(59) == "50대"
    assert age_band(72) == "60+"
    assert age_band(None) is None


# ── 타겟 대비 ────────────────────────────────────────────────────────
def test_target_and_non_target_add_up_to_the_mention_count():
    rows = contrast([theme("LIKE", "빠르다", ["R1", "R2", "R3"])], {"R1"})
    assert rows[0]["targetCount"] == 1 and rows[0]["nonTargetCount"] == 2


def test_contrast_never_produces_a_ratio():
    """분모가 다른 두 수다. 나누는 순간 이 조사가 답하지 못하는 것을 답한 것이 된다."""
    rows = contrast([theme("LIKE", "빠르다", ["R1", "R2"])], {"R1"})
    assert set(rows[0]) == {"axis", "label", "targetCount", "nonTargetCount"}


# ── 제안 ↔ 우려 연결 ─────────────────────────────────────────────────
def test_a_link_is_exactly_the_number_of_people_who_said_both():
    themes = [theme("CONCERN", "가격이 비싸다", ["R1", "R2", "R3"]),
              theme("BARRIER", "가격", ["R1"]),
              theme("SUGGESTION", "값을 내려라", ["R1", "R2", "R9"])]
    rows = suggestion_links(themes)
    assert rows[0]["label"] == "값을 내려라"
    assert rows[0]["links"] == [{"axis": "CONCERN", "label": "가격이 비싸다", "overlapCount": 2},
                                {"axis": "BARRIER", "label": "가격", "overlapCount": 1}]


def test_problems_nobody_shares_with_the_suggestion_are_left_out():
    themes = [theme("CONCERN", "배터리", ["R5"]),
              theme("SUGGESTION", "값을 내려라", ["R1"])]
    assert suggestion_links(themes)[0]["links"] == []


def test_only_suggestions_get_a_row():
    themes = [theme("CONCERN", "가격", ["R1"]), theme("LIKE", "빠르다", ["R1"])]
    assert suggestion_links(themes) == []
