"""인터뷰 가이드·컨셉보드 렌더 — LLM 호출 0회.

문항 순서와 문구가 흔들리면 조사 간 비교가 자극 차이가 아니라 질문 차이를 반영한다.
여기서 잡지 못한 것은 전부 돈을 쓴 뒤에 드러난다.
"""

import hashlib

import pytest
from pydantic import ValidationError

from app.interview import questions as Q
from app.interview.models import ConceptBoard, MarketInterviewInput

# 고정 재료로 렌더한 프롬프트의 sha256. 문항 하나, 쉼표 하나 바뀌어도 깨진다 —
# 깨지면 고칠 것은 이 테스트가 아니라 `questions.TEMPLATE` 이다.
#
# 2026-08-13 재동결: 7문항 → 9문항. 차별성·사용 장면을 새로 묻고, 3·4·8번에 파고들기를
# 내장했다. 옛 해시는 8ec56f3a…35d6 이었다.
FROZEN_PROMPT_SHA256 = "192e6f518153dedf0530b48c04ecd28003ee968af492a96a85f91d4418266e25"


def test_prompt_renders_byte_identical_to_the_frozen_guide():
    rendered = Q.build_prompt("나는 사람이다.", "이름: 무언가")
    assert hashlib.sha256(rendered.encode("utf-8")).hexdigest() == FROZEN_PROMPT_SHA256


def test_first_impression_comes_first():
    """무편집 첫반응을 잡는 자리다. 뒤로 밀리면 앞 문항이 생각을 오염시킨 답이 된다."""
    assert Q.QUESTIONS[0][0] == "firstImpression"
    assert Q.QUESTIONS[1][0] == "restatement"


def test_guide_has_exactly_the_nine_fields_the_answer_model_declares():
    from app.interview.models import InterviewAnswer

    assert [key for key, _, _ in Q.QUESTIONS] == list(InterviewAnswer.model_fields)
    assert len(Q.QUESTIONS) == 9


def test_probing_is_built_into_the_three_questions_that_need_it():
    """1인 1턴이라 조사원이 되물을 수 없다. 되묻기를 문항 안에 넣은 것이 설계다.

    이유 사슬이 원문에 남지 않으면 화면이 「가격이 비싸다 19명」에서 멈춘다.
    """
    text = {key: body for key, _, body in Q.QUESTIONS}
    assert "왜 본인에게 중요한지" in text["like"]
    assert "무엇과 비교해서" in text["concern"]
    assert "없어진다면 사실 것 같은지" in text["barrier"]


def test_usage_scene_is_labelled_as_imagined():
    """실제 행동이 아니라 상상이다. 질문에서부터 그렇게 묻는다."""
    text = {key: body for key, _, body in Q.QUESTIONS}
    assert "상상해서" in text["usageScene"]


def test_differentiation_offers_the_no_difference_answer():
    """「차이 없음」이 다수인 것 자체가 핵심 경고다 — 그 답을 막으면 안 된다."""
    text = {key: body for key, _, body in Q.QUESTIONS}
    assert "다른 게 없다면" in text["differentiation"]


def test_question_keys_are_unique():
    keys = [key for key, _, _ in Q.QUESTIONS]
    assert len(set(keys)) == len(keys)


def test_prompt_places_card_then_board():
    prompt = Q.build_prompt("저는 만 41세 여성입니다.", "이름: 밴드")
    assert prompt.startswith("저는 만 41세 여성입니다.\n\n당신은 위 인물입니다.")
    assert "--- 상품 설명 ---\n이름: 밴드\n-----------------" in prompt


def test_prompt_tells_the_respondent_not_to_dress_up_the_answer():
    """합성 응답자는 바람직한 답으로 쏠린다 — 그 편향을 프롬프트에서 한 번 누른다."""
    assert "좋게 보이려고 꾸미지 마세요" in Q.TEMPLATE
    assert "설명에 없는 기능을 있다고 치지 마세요" in Q.TEMPLATE


# ── 컨셉보드 ─────────────────────────────────────────────────────────
def test_board_renders_fields_in_fixed_order():
    board = ConceptBoard(conceptName="밴드", targetUsers="부모",
                         problemScenario="연락이 안 된다", featureSet=["도착 알림"],
                         differentiators="전화기가 필요 없다", priceKrw=39000)
    assert board.render() == (
        "이름: 밴드\n"
        "누구를 위한 것인가: 부모\n"
        "어떤 상황의 문제인가: 연락이 안 된다\n"
        "하는 일:\n"
        "  - 도착 알림\n"
        "다른 것과 다른 점: 전화기가 필요 없다\n"
        "가격: 39,000원"
    )


def test_board_drops_empty_lines_entirely():
    """「(없음)」을 보이면 응답자가 그 공백에 반응한다. 줄째로 뺀다."""
    board = ConceptBoard(conceptName="밴드")
    assert board.render() == "이름: 밴드\n가격: 아직 정해지지 않았습니다"


def test_board_states_that_price_is_undecided_rather_than_hiding_it():
    """가격만은 침묵이 더 나쁘다 — 안 보이면 응답자가 값을 상상하고 답에 섞인다."""
    board = ConceptBoard(conceptName="밴드", priceKrw=None)
    assert "가격: 아직 정해지지 않았습니다" in board.render()


def test_board_blank_only_features_are_dropped():
    board = ConceptBoard(conceptName="밴드", featureSet=["  ", ""])
    assert "하는 일" not in board.render()


# ── 입력 계약 ────────────────────────────────────────────────────────
def _payload(size=20, **overrides):
    board = {"conceptName": "밴드", "targetUsers": "부모", "problemScenario": "문제",
             "featureSet": ["알림"], "differentiators": "다르다", "priceKrw": 39000}
    board.update(overrides)
    return {"conceptBoard": board, "sampleSize": size}


def test_sample_size_is_restricted_to_the_three_offered_values():
    for size in (20, 40, 80):
        MarketInterviewInput.model_validate(_payload(size))
    for size in (10, 50, 100, 300):
        with pytest.raises(ValidationError):
            MarketInterviewInput.model_validate(_payload(size))


def test_price_rejects_floating_point():
    """백엔드 입력 계약이 실수를 거부한다 — 런타임에만 터지는 지뢰다."""
    with pytest.raises(ValidationError):
        MarketInterviewInput.model_validate(_payload(priceKrw=39000.5))


def test_concept_name_is_required():
    with pytest.raises(ValidationError):
        MarketInterviewInput.model_validate(_payload(conceptName=""))


def test_unknown_fields_are_rejected():
    payload = _payload()
    payload["situation"] = "옛 계약의 칸이다"
    with pytest.raises(ValidationError):
        MarketInterviewInput.model_validate(payload)
