"""자극·프롬프트 — LLM 호출 0회.

`combine_csv/_build/g3e/g3e_02_plumbing.py` 의 배관검사를 옮긴 것이다. 여기서 잡지 못한
결함은 전부 돈을 쓴 뒤에 드러난다(G3B가 위치 배정 불균형 하나로 $102를 버렸다).
"""

import hashlib

from app.twin import stimuli as S


# `combine_csv/_build/g3b/g3b_template.txt` 의 sha256. 그 파일은 G1·G3B·G3C·G3D 레코드가
# 가리키는 바이트 동결본이라 여기 해시로 박아 둔다 — 제품 저장소에서 그 파일을 읽을 수
# 없으므로(다른 트리) 해시가 유일한 연결점이다.
FROZEN_TEMPLATE_SHA256 = "6c734c5b4dbedc758362c7a0b0c77efe03507d2c7c338289f31b4fecda0efc94"


def test_template_renders_byte_identical_to_frozen_original():
    """상황 슬롯에 연어 문장을 넣으면 동결 템플릿과 **바이트 동일**해야 한다.

    이게 깨지면 계기가 바뀐 것이다 — 고칠 것은 이 테스트가 아니라 `stimuli.TEMPLATE` 이다.
    """
    rendered = S.TEMPLATE.replace("{SITUATION}", S.SITUATION_FROZEN)
    digest = hashlib.sha256(rendered.encode("utf-8")).hexdigest()
    assert digest == FROZEN_TEMPLATE_SHA256


def test_template_keeps_the_three_choice_lines():
    for line in ("선택: A", "선택: B", "선택: 없음"):
        assert f"\n{line}\n" in S.TEMPLATE


def test_direction_swaps_the_two_positions():
    """fwd 는 A=X, rev 는 A=Y. 양방향 전수라 위치 배정 불균형이 설계상 0이다."""
    pair = {"pairId": "P1", "X": {"text": "엑스"}, "Y": {"text": "와이"}}
    forward = S.build_prompt("카드", pair, "fwd", "상황입니다.")
    reverse = S.build_prompt("카드", pair, "rev", "상황입니다.")

    assert forward != reverse
    assert "상품 A: 엑스" in forward and "상품 B: 와이" in forward
    assert "상품 A: 와이" in reverse and "상품 B: 엑스" in reverse


def test_build_prompt_places_card_and_situation():
    pair = {"pairId": "P1", "X": {"text": "엑스"}, "Y": {"text": "와이"}}
    prompt = S.build_prompt("나는 사람이다.", pair, "fwd", "가게에서 고릅니다.")
    assert prompt.startswith("나는 사람이다.\n\n당신은 위 인물입니다. 가게에서 고릅니다.\n")


def test_build_prompt_rejects_unknown_direction():
    pair = {"pairId": "P1", "X": {"text": "엑스"}, "Y": {"text": "와이"}}
    try:
        S.build_prompt("카드", pair, "sideways", "상황")
    except ValueError:
        return
    raise AssertionError("알 수 없는 방향을 통과시켰다")


def test_to_xy_round_trip():
    assert S.to_xy("A", "fwd") == "X"
    assert S.to_xy("B", "fwd") == "Y"
    assert S.to_xy("A", "rev") == "Y"
    assert S.to_xy("B", "rev") == "X"
    assert S.to_xy("없음", "fwd") == "없음"
    assert S.to_xy("없음", "rev") == "없음"
    assert S.to_xy(None, "fwd") is None
    assert S.to_xy("C", "fwd") is None


def test_adaptive_k_two_agreeing_reps_are_final():
    """부록 A — 2회 일치면 3회차가 판정을 바꿀 수 없다."""
    assert S.decide_adaptive({1: "X", 2: "X"}) == "X"
    assert S.decide_adaptive({1: "X", 2: "X", 3: "Y"}) == "X"


def test_adaptive_k_breaks_ties_with_third_rep():
    assert S.decide_adaptive({1: "X", 2: "Y", 3: "X"}) == "X"
    assert S.decide_adaptive({1: "X", 2: "Y", 3: "Y"}) == "Y"


def test_adaptive_k_leaves_true_ties_undecided():
    """동수는 미결정으로 남긴다 — 경합층을 임의로 한쪽에 몰지 않는다."""
    assert S.decide_adaptive({1: "X", 2: "Y"}) is None
    assert S.decide_adaptive({1: None, 2: "X"}) is None
    assert S.decide_adaptive({1: "X"}) is None
    assert S.decide_adaptive({}) is None


def test_wave2_targets_only_disagreeing_cells():
    assert S.needs_wave2({1: "X", 2: "Y"}) is True
    assert S.needs_wave2({1: "X", 2: "X"}) is False
    assert S.needs_wave2({1: "X"}) is False
    assert S.needs_wave2({1: None, 2: "X"}) is False
