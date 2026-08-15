"""입력 계약·거절 경로·경계 문구 — LLM 호출 0회.

가장 중요한 것은 **거절이 LLM 앞에서 일어나는가**다. 뒤에서 일어나면 팔 수 없는 질문에
돈을 쓰고 나서 거절하게 된다.
"""

import asyncio

import pytest
from pydantic import ValidationError

from app.twin import caveats, execute_twin_survey
from app.twin.models import Side, TwinSurveyInput
from app.twin.task_type import DOMINANCE, PRICE, SERVICEABLE


def request_payload(x_attrs, y_attrs, x_price=None, y_price=None, size=50):
    return {"situation": "가게에서 하나를 고릅니다. 아래 두 상품이 있습니다.",
            "sampleSize": size,
            "pairs": [{"pairId": "P1",
                       "X": {"label": "A안", "attrs": x_attrs, "priceKrw": x_price},
                       "Y": {"label": "B안", "attrs": y_attrs, "priceKrw": y_price}}]}


# ── 입력 계약 ─────────────────────────────────────────────────────────
def test_side_renders_attributes_in_given_order_with_price_last():
    side = Side(label="A안", attrs={"원산지": "한국산", "형태": "신선"}, priceKrw=4500)
    assert side.render() == "원산지 한국산, 형태 신선, 가격 4,500원"


def test_side_without_price_renders_attributes_only():
    side = Side(label="A안", attrs={"형태": "신선"})
    assert side.render() == "형태 신선"


def test_sample_size_is_restricted_to_the_measured_options():
    """MDE 표를 실측한 세 값만 받는다 — 표에 없는 n 은 화면이 한계를 못 보여준다."""
    for size in (50, 100, 300):
        TwinSurveyInput.model_validate(request_payload({"형태": "신선"}, {"형태": "냉동"}, size=size))
    with pytest.raises(ValidationError):
        TwinSurveyInput.model_validate(request_payload({"형태": "신선"}, {"형태": "냉동"}, size=75))


def test_both_sides_must_share_the_attribute_space():
    with pytest.raises(ValidationError):
        TwinSurveyInput.model_validate(request_payload({"형태": "신선"}, {"원산지": "한국산"}))


def test_price_must_be_present_on_both_sides_or_neither():
    with pytest.raises(ValidationError):
        TwinSurveyInput.model_validate(
            request_payload({"형태": "신선"}, {"형태": "냉동"}, 4500, None))


def test_price_rejects_floating_point():
    """백엔드 canonical hash 가 실수를 거부한다 — 런타임에만 터지는 지뢰다."""
    with pytest.raises(ValidationError):
        TwinSurveyInput.model_validate(
            request_payload({"형태": "신선"}, {"형태": "냉동"}, 4500.5, 4500.5))


def test_unknown_fields_are_rejected():
    payload = request_payload({"형태": "신선"}, {"형태": "냉동"})
    payload["quantity"] = 3
    with pytest.raises(ValidationError):
        TwinSurveyInput.model_validate(payload)


# ── 거절이 LLM 앞에서 일어나는가 ──────────────────────────────────────
def _explode(*_args, **_kwargs):
    raise AssertionError("거절해야 할 요청에 뱅크/LLM 을 건드렸다")


def test_ethical_pair_is_refused_before_touching_the_bank(monkeypatch):
    monkeypatch.setattr("app.twin.load", _explode)
    monkeypatch.setattr("app.twin.run_survey", _explode)

    with pytest.raises(Exception) as caught:
        asyncio.run(execute_twin_survey(
            request_payload({"인증": "있음"}, {"인증": "없음"}, 4500, 4500)))

    assert caught.value.reason == "TWIN_TASK_TYPE_NOT_SERVICEABLE"
    assert caught.value.status_code == 422


def test_multi_attribute_pair_is_refused_before_touching_the_bank(monkeypatch):
    monkeypatch.setattr("app.twin.load", _explode)
    monkeypatch.setattr("app.twin.run_survey", _explode)

    with pytest.raises(Exception) as caught:
        asyncio.run(execute_twin_survey(request_payload(
            {"형태": "신선", "원산지": "칠레산"},
            {"형태": "냉동", "원산지": "노르웨이산"}, 4500, 4500)))

    assert caught.value.reason == "TWIN_TASK_TYPE_NOT_SERVICEABLE"


def test_malformed_input_is_refused_before_touching_the_bank(monkeypatch):
    monkeypatch.setattr("app.twin.load", _explode)
    monkeypatch.setattr("app.twin.run_survey", _explode)

    with pytest.raises(Exception) as caught:
        asyncio.run(execute_twin_survey({"situation": "짧", "pairs": [], "sampleSize": 50}))

    assert caught.value.reason == "FIELD_CONSTRAINT_VIOLATION"


# ── 경계 문구 ─────────────────────────────────────────────────────────
def test_every_serviceable_type_carries_non_empty_caveats():
    for task_type in SERVICEABLE:
        assert caveats.for_pair(task_type), "빈 caveats 는 계약 위반이다"


def test_caveats_always_carry_the_mandated_disclosures():
    notes = " / ".join(caveats.for_pair(DOMINANCE))
    assert "외적 타당성 시험 종합 미달" in notes
    assert "한국미디어패널조사(KISDI)" in notes
    assert "실존 인물 인터뷰가 아니다" in notes
    assert "시장 점유율도 실제 구매확률도 아니다" in notes
    assert "차이의 크기는 이 설계가 답하지 못한다" in notes


def test_dominance_carries_the_instrument_stability_evidence():
    """우열형은 계기를 바꿔도 방향이 유지되는 것이 실측됐다 — 그 사실이 값과 같이 나가야 한다."""
    notes = " / ".join(caveats.for_pair(DOMINANCE))
    assert "실행 모델을 바꿔도 방향이 유지되는 것을" in notes


def test_instrument_claim_defaults_to_unverified():
    """0단계 판정 전에는 어떤 성적 문구도 근거로 서지 않는다."""
    assert caveats.INSTRUMENT_EQUIVALENCE_CONFIRMED is False
    notes = " / ".join(caveats.for_pair(DOMINANCE))
    assert "성적 미전이" in notes
    assert "계기 동등성 확인" not in notes


def test_forbidden_phrasings_never_appear():
    for task_type in (DOMINANCE, PRICE):
        notes = " / ".join(caveats.for_pair(task_type))
        assert "부분 검증됨" not in notes
