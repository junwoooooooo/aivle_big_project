"""타겟 사전 필터 — 술어 평가와 8:2 분할. LLM 호출 0회 (조건식은 직접 만든다).

가장 중요한 것: **조건이 좁아 타겟이 마를 때 죽지 않고 사실로 남기는가.**
조건이 좁다는 것 자체가 읽어야 할 정보이지 실패가 아니다.
"""

from app.interview.targeting import (TargetCriteria, condition_matches, criteria_text,
                                     draw_split, has_conditions, matches)
from app.twin.profile import parse_target_facts

CARD = ("저는 만 41세 여성입니다. 서울 시 지역에 살고 있습니다. "
        "2세대가구(부부+자녀) 형태의 3인 가구이고, 개인 월소득은 300~400만 원 미만 "
        "수준입니다. 일은 일반 지원 사무직 쪽 일을 임금 근로자로 하고 있습니다. "
        "가구 안에서는 가구주의 배우자입니다. 혼인 상태는 기혼입니다.")

PROFILE = {"age": 41, "gender": "여성", "household": "3인 가구", "region": "서울",
           "income": "월소득 300~400만 원", "job": "일반 지원 사무직"}


def criteria(**overrides) -> TargetCriteria:
    base = {"ageMin": 0, "ageMax": 0, "genders": [], "householdSizeMin": 0,
            "householdSizeMax": 0, "regions": [], "incomeKeywords": [], "jobKeywords": [],
            "hasChildren": 0, "householdRoles": []}
    base.update(overrides)
    return TargetCriteria(**base)


# ── 술어 평가 ────────────────────────────────────────────────────────
def test_no_condition_means_everyone_is_a_target():
    assert matches(PROFILE, criteria()) is True
    assert matches({}, criteria()) is True


def test_zero_means_unbounded_on_that_side():
    assert matches(PROFILE, criteria(ageMin=30)) is True
    assert matches(PROFILE, criteria(ageMin=50)) is False
    assert matches(PROFILE, criteria(ageMax=50)) is True
    assert matches(PROFILE, criteria(ageMax=30)) is False


def test_conditions_are_and_across_axes():
    assert matches(PROFILE, criteria(genders=["여성"], regions=["서울"])) is True
    assert matches(PROFILE, criteria(genders=["여성"], regions=["부산"])) is False


def test_a_list_inside_one_axis_is_or():
    assert matches(PROFILE, criteria(regions=["부산", "서울"])) is True


def test_household_size_is_read_out_of_the_profile_sentence():
    assert matches(PROFILE, criteria(householdSizeMin=3)) is True
    assert matches(PROFILE, criteria(householdSizeMin=4)) is False
    assert matches(PROFILE, criteria(householdSizeMax=2)) is False


def test_keywords_match_by_substring():
    assert matches(PROFILE, criteria(jobKeywords=["사무"])) is True
    assert matches(PROFILE, criteria(jobKeywords=["학생", "주부"])) is False
    assert matches(PROFILE, criteria(incomeKeywords=["300"])) is True


def test_an_unreadable_field_fails_the_condition_it_cannot_confirm():
    """확인할 수 없는 것을 타겟으로 세면 타겟 표본이 조용히 오염된다."""
    blank = {**PROFILE, "age": None, "household": None}
    assert matches(blank, criteria(ageMin=30)) is False
    assert matches(blank, criteria(householdSizeMin=2)) is False
    assert matches(blank, criteria(regions=["서울"])) is True   # 그 축엔 조건이 없다


def test_criteria_text_says_so_when_there_is_no_condition():
    assert "조건 없음" in criteria_text(criteria())
    assert criteria_text(criteria(ageMin=30, ageMax=49, genders=["여성"])) == \
        "만 30~49세 / 여성"


# ── 자녀 · 가구 안 지위 (2026-08-15 신설) ─────────────────────────────
#
# 「초등 저학년 자녀를 둔 맞벌이 부모」가 직업 키워드로 옮겨져 타겟이 0명이 된 판에서 왔다.
# 자녀 유무를 거를 칸이 아예 없었던 것이 원인이다.
FACTS = parse_target_facts(CARD)
#: 같은 가구에 사는 «자녀 본인». 세대구성은 부모와 똑같고 지위만 다르다.
CHILD_FACTS = parse_target_facts(
    CARD.replace("만 41세 여성", "만 22세 남성")
        .replace("가구 안에서는 가구주의 배우자입니다", "가구 안에서는 가구주의 자녀입니다")
        .replace("혼인 상태는 기혼입니다", "혼인 상태는 미혼입니다"))


def test_children_and_role_are_read_out_of_the_card():
    assert FACTS["hasChildren"] is True and FACTS["householdRole"] == "가구주의 배우자"
    assert CHILD_FACTS["hasChildren"] is True and CHILD_FACTS["householdRole"] == "가구주의 자녀"


def test_has_children_alone_lets_the_household_s_own_child_through():
    """★ 이 한 줄이 두 칸을 한 쌍으로 쓰는 이유다.

    실측(8,595장): 자녀가 있는 가구 5,919장 중 **1,611장이 그 집 자녀 본인**이다.
    자녀 유무만 걸면 22세 자녀가 「자녀를 둔 부모」 타겟에 들어간다.
    """
    assert matches(CHILD_FACTS, criteria(hasChildren=1)) is True
    parent_only = criteria(hasChildren=1, householdRoles=["가구주", "가구주의 배우자"])
    assert matches(CHILD_FACTS, parent_only) is False
    assert matches(FACTS, parent_only) is True


def test_a_card_without_a_generation_sentence_cannot_pass_a_children_condition():
    """확인할 수 없는 것을 타겟으로 세면 표본이 조용히 오염된다. 뱅크의 11% 가 이 모양이다."""
    blank = parse_target_facts("저는 만 41세 여성입니다. 서울 시 지역에 살고 있습니다.")
    assert blank["hasChildren"] is None
    assert matches(blank, criteria(hasChildren=1)) is False
    assert matches(blank, criteria(hasChildren=2)) is False


def test_an_unknown_role_label_does_not_slip_through():
    facts = parse_target_facts(CARD.replace("가구주의 배우자입니다", "동거인입니다"))
    assert facts["householdRole"] is None
    assert matches(facts, criteria(householdRoles=["가구주"])) is False


# ── 조건별 적중 수와 교집합 ───────────────────────────────────────────
def test_condition_matches_counts_each_axis_and_the_intersection():
    """★ 교집합 줄이 없으면 「축마다 다 초록인데 타겟 0명」으로 또 속는다."""
    cards = {"a": CARD, "b": CARD.replace("만 41세 여성", "만 68세 남성")}
    rows = condition_matches(cards, criteria(ageMax=50, genders=["여성"]))
    assert rows[-1] == {"condition": "전부 동시에 만족", "matched": 1}
    assert [row["matched"] for row in rows[:-1]] == [1, 1]


def test_condition_matches_is_empty_when_nothing_is_filtered():
    assert condition_matches({"a": CARD}, criteria()) == []
    assert has_conditions(criteria()) is False
    assert has_conditions(criteria(hasChildren=1)) is True


def test_criteria_text_carries_the_counts_and_the_intersection():
    """봉투 칸을 늘리지 않고 이 문자열 안에 실어 화면까지 보낸다."""
    cards = {"a": CARD, "b": CARD.replace("만 41세 여성", "만 68세 남성")}
    text = criteria_text(criteria(ageMax=50, genders=["여성"]),
                         condition_matches(cards, criteria(ageMax=50, genders=["여성"])))
    assert "만 50세 이하(1명)" in text and "여성(1명)" in text
    assert "전부 동시에 만족: 1명" in text


def test_a_condition_the_panel_cannot_express_shows_up_as_zero():
    """「맞벌이」는 뱅크 8,595장에 0회 나온다 — 그 사실이 문구에 드러나야 한다."""
    text = criteria_text(criteria(jobKeywords=["맞벌이"]),
                         condition_matches({"a": CARD}, criteria(jobKeywords=["맞벌이"])))
    assert "직업에 '맞벌이'(0명)" in text and "전부 동시에 만족: 0명" in text


# ── 8:2 분할 ─────────────────────────────────────────────────────────
def _bank(target_count: int, other_count: int):
    cards, frame = {}, []
    for index in range(target_count):
        pid = f"t{index:03d}"
        cards[pid] = CARD
        frame.append({"pid_hash": pid, "gender": "여", "band": "40대"})
    for index in range(other_count):
        pid = f"x{index:03d}"
        cards[pid] = CARD.replace("만 41세 여성", "만 68세 남성")
        frame.append({"pid_hash": pid, "gender": "남", "band": "60+"})
    return cards, frame


def test_target_gets_eight_tenths_and_non_target_the_rest():
    cards, frame = _bank(50, 50)
    drawn, targets, sampling, targeting = draw_split(cards, frame, 20, criteria(ageMax=50))
    assert targeting["targetDrawn"] == 16 and targeting["nonTargetDrawn"] == 4
    assert len(drawn) == 20 and len(targets) == 16
    assert sampling["requested"] == 20 and sampling["drawn"] == 20
    assert targeting["shortfall"] == 0


def test_a_shallow_target_frame_is_recorded_not_raised():
    """조건이 좁다는 것 자체가 읽어야 할 정보다. 여기서 죽이면 그 정보가 사라진다."""
    cards, frame = _bank(5, 60)
    drawn, targets, _sampling, targeting = draw_split(cards, frame, 20, criteria(ageMax=50))
    assert targeting["targetDrawn"] == 5
    assert targeting["nonTargetDrawn"] == 15          # 부족분을 비타겟에서 채운다
    assert len(drawn) == 20 and len(targets) == 5
    assert targeting["shortfall"] == 0


def test_shortfall_is_reported_when_neither_side_can_fill_the_sample():
    cards, frame = _bank(3, 4)
    drawn, _targets, sampling, targeting = draw_split(cards, frame, 20, criteria(ageMax=50))
    assert len(drawn) == 7
    assert targeting["shortfall"] == 13
    assert sampling["requested"] == 20 and sampling["drawn"] == 7


def test_the_criteria_ride_along_so_a_wrong_filter_is_visible():
    """자유 서술을 기계가 옮긴 것이라 틀릴 수 있고, 틀렸는지는 사용자만 안다."""
    cards, frame = _bank(30, 30)
    _drawn, _targets, _sampling, targeting = draw_split(
        cards, frame, 20, criteria(ageMax=50, genders=["여성"]))
    assert targeting["criteria"]["ageMax"] == 50
    assert "여성" in targeting["criteriaText"]


def test_sampling_merges_both_halves_into_the_four_contract_fields():
    cards, frame = _bank(50, 50)
    _drawn, _targets, sampling, _targeting = draw_split(cards, frame, 20, criteria(ageMax=50))
    assert set(sampling) == {"requested", "drawn", "strata", "shortCells"}
    assert sum(sampling["strata"].values()) == 20
