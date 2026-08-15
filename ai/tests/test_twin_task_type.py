"""판매 경계 게이트 — LLM 호출 0회.

이 게이트가 새면 외적 타당성 시험에서 **틀린** 유형을 팔게 된다. 그래서 경계 사례를
양쪽에서 민다.
"""

from app.twin.task_type import (DOMINANCE, ETHICAL_VALUE, IDENTICAL, PRICE,
                                SERVICEABLE, UNMEASURABLE, classify)


def pair(x_attrs, y_attrs, x_price=None, y_price=None):
    return {"pairId": "P1",
            "X": {"attrs": x_attrs, "priceKrw": x_price},
            "Y": {"attrs": y_attrs, "priceKrw": y_price}}


def test_single_non_price_attribute_at_equal_price_is_dominance():
    v = classify(pair({"형태": "신선"}, {"형태": "냉동"}, 4500, 4500))
    assert v.task_type == DOMINANCE and v.serviceable


def test_price_only_difference_is_dominance():
    """원본에서 E3(3,000원 vs 6,000원)이 «명백한 가격차» 우열형이다."""
    v = classify(pair({"형태": "신선"}, {"형태": "신선"}, 3000, 6000))
    assert v.task_type == DOMINANCE and v.serviceable


def test_premium_against_price_handicap_is_price_type_and_blocked():
    """분류는 여전히 PRICE 다 — 그래야 «가격형이라 막았다»고 말할 수 있다. 다만 못 판다."""
    v = classify(pair({"형태": "신선"}, {"형태": "냉동"}, 5000, 4500))
    assert v.task_type == PRICE and v.blocked
    assert "지불의사의 임계는 응답자가 아니라 실행 모델이 정한다" in v.reason
    assert "가격을 양쪽 같게" in v.reason


def test_two_differing_attributes_are_unmeasurable():
    v = classify(pair({"형태": "신선", "원산지": "칠레산"},
                      {"형태": "냉동", "원산지": "노르웨이산"}, 4500, 4500))
    assert v.task_type == UNMEASURABLE and v.blocked
    assert "한 번에 한 속성" in v.reason


def test_identical_sides_are_rejected():
    v = classify(pair({"형태": "신선"}, {"형태": "신선"}, 4500, 4500))
    assert v.task_type == IDENTICAL and v.blocked


def test_ethical_axis_is_blocked_even_when_it_is_the_only_difference():
    """원본 분류기는 E1(인증만, 가격 동일)을 우열형으로 뒀지만 여기서는 막는다.

    그 허용을 받치는 근거가 E1 한 쌍뿐인 반면, 틀린 3쌍(H1·H3·B1)은 전부 인증 쌍이었다.
    """
    v = classify(pair({"지속가능 양식 인증": "있음"}, {"지속가능 양식 인증": "없음"}, 4500, 4500))
    assert v.task_type == ETHICAL_VALUE and v.blocked


def test_ethical_axis_is_blocked_from_either_side():
    for attrs in ({"포장": "친환경 재활용"}, {"포장": "일반"}):
        other = {"포장": "일반"} if attrs.get("포장") != "일반" else {"포장": "친환경 재활용"}
        assert classify(pair(attrs, other, 4500, 4500)).task_type == ETHICAL_VALUE


def test_ethical_axis_is_blocked_when_traded_against_price():
    v = classify(pair({"인증": "있음"}, {"인증": "없음"}, 5200, 4500))
    assert v.task_type == ETHICAL_VALUE and v.blocked


def test_ethical_vocabulary_covers_the_known_failure_modes():
    for term in ("지속가능", "친환경", "ESG", "공정무역", "유기농", "탄소", "비건", "동물복지"):
        v = classify(pair({"속성": f"{term} 적용"}, {"속성": "일반"}, 4500, 4500))
        assert v.task_type == ETHICAL_VALUE, f"«{term}» 이 새어 나간다"


def test_ethical_attribute_not_on_the_axis_does_not_block():
    """양쪽이 같은 값이면 대비의 축이 아니다 — 그것까지 막으면 팔 수 있는 것도 못 판다."""
    v = classify(pair({"인증": "있음", "형태": "신선"},
                      {"인증": "있음", "형태": "냉동"}, 4500, 4500))
    assert v.task_type == DOMINANCE and v.serviceable


def test_serviceable_set_is_only_dominance():
    """2026-08-10 가격형 차단. 세 계기 실측이 방향 반전을 보였다(핸드오프 §8)."""
    assert SERVICEABLE == frozenset({DOMINANCE})
