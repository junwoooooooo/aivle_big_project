"""자극 초안 — 프롬프트를 믿지 않고 코드가 거르는지 본다.

여기서 지키는 것은 둘이다. **팔 수 있는 유형만 나간다**, 그리고 **0쌍이면 정직하게 실패한다**.
프롬프트로 부탁한 규칙은 회귀해도 조용하지만, 이 두 가지가 새면 성적 없는 수치가 팔린다.
"""

import asyncio
import json
from pathlib import Path

import pytest

from app.providers import ProviderFailure
from app.twin import stimulus_draft
from app.twin.models import TwinSurveyInput
from app.twin.stimulus_draft import execute_twin_stimulus_draft
from app.twin.task_type import SERVICEABLE, classify

GOLDEN = Path(__file__).parent / "fixtures/twin_survey/stimulus_draft.json"


CONCEPT = {
    "conceptName": "새벽 밀키트",
    "targetUsers": "1인 가구 직장인",
    "problemScenario": "퇴근 후 장을 볼 시간이 없다",
    "featureSet": ["보관 형태", "배송 시간대"],
    "differentiators": "신선 보관과 새벽 배송을 함께 준다",
    "priceKrw": 9900,
}


def draft(axis, x_value, y_value, rationale="물어볼 만하다"):
    return {"axis": axis,
            "X": {"label": f"{x_value} 안", "value": x_value},
            "Y": {"label": f"{y_value} 안", "value": y_value},
            "rationale": rationale}


def stub(monkeypatch, situation, pairs):
    async def prompt(*_args, **_kwargs):
        return {"situation": situation, "pairs": pairs}
    monkeypatch.setattr(stimulus_draft, "execute_structured_prompt", prompt)


def run(payload=None):
    return asyncio.run(execute_twin_stimulus_draft(payload or CONCEPT))


def test_keeps_only_dominance_pairs_and_says_why_the_rest_went(monkeypatch):
    stub(monkeypatch, "가게에서 하나를 고릅니다.", [
        draft("보관 형태", "신선", "냉동"),
        draft("친환경 인증", "인증 있음", "인증 없음"),      # 윤리·가치형 — 영구 금지
        draft("배송 시간대", "새벽", "새벽"),               # 두 안이 같다
    ])

    result = run()

    assert [p["axis"] for p in result["pairs"]] == ["보관 형태"]
    assert {d["taskType"] for d in result["dropped"]} == {"ETHICAL_VALUE", "IDENTICAL"}


def test_price_is_the_same_on_both_sides_so_a_willingness_to_pay_pair_cannot_exist(monkeypatch):
    """가격을 LLM 이 만들지 않는다 — 차단된 가격형이 **표현될 수 없다**."""
    stub(monkeypatch, "가게에서 하나를 고릅니다.", [draft("보관 형태", "신선", "냉동")])

    pair = run()["pairs"][0]

    assert pair["X"]["priceKrw"] == pair["Y"]["priceKrw"] == 9900
    assert list(pair["X"]["attrs"]) == list(pair["Y"]["attrs"]) == ["보관 형태"]


def test_pair_ids_are_renumbered_so_the_survey_never_sees_a_duplicate(monkeypatch):
    stub(monkeypatch, "가게에서 하나를 고릅니다.", [
        draft("보관 형태", "신선", "냉동"),
        draft("배송 시간대", "새벽", "당일"),
    ])

    assert [p["pairId"] for p in run()["pairs"]] == ["P1", "P2"]


def test_refuses_honestly_when_the_gate_drops_everything(monkeypatch):
    stub(monkeypatch, "가게에서 하나를 고릅니다.", [
        draft("친환경 인증", "인증 있음", "인증 없음"),
        draft("보관 형태", "신선", "신선"),
    ])

    with pytest.raises(ProviderFailure) as raised:
        run()

    assert raised.value.reason == "TWIN_STIMULUS_NO_SERVICEABLE_PAIR"
    assert raised.value.status_code == 422
    assert len(raised.value.safe_diagnostics["dropped"]) == 2


def test_never_returns_more_pairs_than_the_survey_accepts(monkeypatch):
    stub(monkeypatch, "가게에서 하나를 고릅니다.",
         [draft(f"속성{index}", "높음", "낮음") for index in range(6)])

    assert len(run()["pairs"]) == 4


def test_a_sample_concept_label_is_unfolded_into_material(monkeypatch):
    """컨셉 파이프라인이 아직 안 찬 환경에서 이 단계를 시연·시험하는 길이다.
    표는 시장조사와 **같은 것**을 쓴다 — 따로 들고 있으면 두 화면이 다른 컨셉을 본다."""
    seen = {}

    async def prompt(system, user, **_kwargs):
        seen["user"] = user
        return {"situation": "가게에서 하나를 고릅니다.",
                "pairs": [draft("보관 형태", "신선", "냉동")]}
    monkeypatch.setattr(stimulus_draft, "execute_structured_prompt", prompt)

    result = asyncio.run(execute_twin_stimulus_draft({"conceptId": "beauty-noshow"}))

    assert "미용실" in seen["user"]          # 견본 컨셉의 이름이 프롬프트에 실렸다
    assert len(result["pairs"]) == 1
    # 견본의 가격은 null 이다 — 없는 가격을 지어내지 않는다.
    assert result["pairs"][0]["X"]["priceKrw"] is None


def test_rejects_an_unknown_concept_label():
    with pytest.raises(ProviderFailure) as raised:
        run({"conceptId": "그런-컨셉-없다"})
    assert raised.value.reason == "TWIN_STIMULUS_CONCEPT_UNKNOWN"
    assert "beauty-noshow" in raised.value.safe_diagnostics["known"]


def test_rejects_a_payload_with_neither_material_nor_label():
    with pytest.raises(ProviderFailure) as raised:
        run({"targetUsers": "1인 가구"})
    assert raised.value.reason == "FIELD_CONSTRAINT_VIOLATION"


def test_rejects_a_concept_payload_with_unknown_fields():
    with pytest.raises(ProviderFailure) as raised:
        run({**CONCEPT, "snapshotJson": "{}"})
    assert raised.value.reason == "FIELD_CONSTRAINT_VIOLATION"


def test_golden_draft_is_runnable_as_a_survey_without_a_single_edit():
    """이 파일은 백엔드 계약 테스트·프론트 테스트가 **같이** 읽는다.

    초안이 조사 입력으로 그대로 서는지가 이 기능의 이음매다. 여기가 어긋나면
    사용자는 「초안은 만들어졌는데 실행은 거절되는」 막다른 길을 본다.
    """
    golden = json.loads(GOLDEN.read_text(encoding="utf-8"))

    survey = TwinSurveyInput.model_validate({
        "situation": golden["situation"],
        "pairs": [{k: v for k, v in pair.items() if k in {"pairId", "X", "Y"}}
                  for pair in golden["pairs"]],
        "sampleSize": 100,
    })

    assert all(classify(pair.as_stimulus()).task_type in SERVICEABLE for pair in survey.pairs)


def test_rejects_a_floating_point_price():
    """가격은 원 단위 정수다 — canonical hash 가 실수를 거부한다."""
    with pytest.raises(ProviderFailure):
        run({**CONCEPT, "priceKrw": 9900.5})
