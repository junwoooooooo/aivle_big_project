"""포화 검출 — 「전원이 같은 말을 했다」가 조용히 지나가지 않는지. LLM 호출 0회.

2026-08-12 에 n=40 실행이 모든 주제를 40/40 으로 냈는데 **아무 장치도 그것을 잡지 못했다.**
계약도, 테스트도, 화면도 통과했다. 여기가 그 구멍을 메우는 자리다.
"""

from app.interview.saturation import homogeneity, saturated


def theme(axis, label, count):
    return {"axis": axis, "label": label, "mentionCount": count}


def test_a_theme_everyone_mentioned_is_flagged():
    """이게 관측된 고장 그 자체다 — 40명 중 40명."""
    themes = [theme("LIKE", "조리 시간이 짧다", 40), theme("LIKE", "포장이 좋다", 6)]
    assert saturated(themes, 40) == ["LIKE: 조리 시간이 짧다"]


def test_an_axis_with_only_one_label_is_flagged_even_when_not_everyone_mentioned_it():
    """이름표가 하나뿐이면 코더가 결을 못 찾은 것이다 — 언급 수와 무관하게 경고다."""
    assert saturated([theme("CONCERN", "가격이 비싸다", 12)], 40) == \
        ["CONCERN: 가격이 비싸다"]


def test_a_theme_is_not_flagged_twice():
    assert saturated([theme("BARRIER", "가격", 40)], 40) == ["BARRIER: 가격"]


def test_a_healthy_axis_is_not_flagged():
    themes = [theme("LIKE", "빠르다", 14), theme("LIKE", "싸다", 9),
              theme("LIKE", "맛있다", 3)]
    assert saturated(themes, 40) == []


def test_nothing_is_flagged_when_nobody_answered():
    assert saturated([theme("LIKE", "무언가", 0)], 0) == []


def test_homogeneity_reports_label_counts_and_peaks_per_axis():
    themes = [theme("LIKE", "빠르다", 14), theme("LIKE", "싸다", 9),
              theme("CONCERN", "가격", 30)]
    report = homogeneity(themes, [{"label": "참는다", "mentionCount": 12}], 40)
    assert report["axisLabelCounts"]["LIKE"] == 2
    assert report["axisLabelCounts"]["CONCERN"] == 1
    assert report["maxMentionByAxis"]["LIKE"] == 14
    assert report["axisLabelCounts"]["USAGE_SCENE"] == 0
    assert report["alternativeSum"] == 12


def test_homogeneity_carries_no_ratio_of_any_kind():
    """이 조사가 내는 수는 사람 수와 이름표 수뿐이다."""
    report = homogeneity([theme("LIKE", "빠르다", 14)], [], 40)
    assert all(not isinstance(value, float)
               for value in (report["alternativeSum"], *report["maxMentionByAxis"].values()))
