"""자극·프롬프트 — 검증된 설계를 그대로 옮긴 것.

원본: `combine_csv/_build/g3b/g3b_template.txt` (바이트 동결),
      `combine_csv/_build/g3d/g3d_spec.py` (방향 반전·적응식 k).

**이 파일을 고치면 계기가 바뀐다.** 계기가 바뀌면 G3D 성적(우열형 E 4/4 · 가격형 B 3/4)이
이 파이프라인으로 잰 것이 아니게 된다. 템플릿은 상황 문장 한 자리만 슬롯이고,
`tests/test_twin_stimuli.py` 가 연어 상황으로 렌더한 결과가 동결본과 **바이트 동일**한지
검사한다. 그 테스트가 깨지면 고칠 것은 테스트가 아니라 이 파일이다.
"""

from collections import Counter

# 상황 문장만 슬롯. 나머지 한 글자도 바꾸지 않는다.
TEMPLATE = """{CARD_A_TEXT}

당신은 위 인물입니다. {SITUATION}

상품 A: {PROFILE_A}
상품 B: {PROFILE_B}

이 인물의 형편과 취향에서, 어느 쪽을 살지(또는 둘 다 사지 않을지) 이유를 2~3문장으로 먼저
쓰세요. 그 다음 마지막 줄에 아래 세 가지 중 하나만 정확히 쓰세요.

선택: A
선택: B
선택: 없음
"""

SYSTEM = "한국어로 답하세요."

# 동결본을 복원하는 상황 문장. 테스트가 이것으로 바이트 동일성을 확인한다.
SITUATION_FROZEN = "마트에서 연어를 사려고 합니다. 진열대에 아래 두 상품이 있습니다."

DIRECTIONS = ("fwd", "rev")

# 적응식 k — 2회 선행, 불일치 셀만 3회차. G3D 부록 A가 k=3 다수결과 판정 등가임을 보였다.
K_WAVE1 = 2
K_MAX = 3


def build_prompt(card_text: str, pair: dict, direction: str, situation: str) -> str:
    """`direction='fwd'` → A=X / `'rev'` → A=Y.

    해시 기반 위치 배정을 쓰지 않는다. 트윈마다 두 방향을 모두 돌리므로 배정 불균형이
    설계상 0이다 — G3B는 그 불균형이 관문 0 미달의 산술적 원인이었다.
    """
    if direction not in DIRECTIONS:
        raise ValueError(f"unknown direction: {direction}")
    a, b = (pair["X"], pair["Y"]) if direction == "fwd" else (pair["Y"], pair["X"])
    return (TEMPLATE.replace("{CARD_A_TEXT}", card_text)
                    .replace("{SITUATION}", situation)
                    .replace("{PROFILE_A}", a["text"])
                    .replace("{PROFILE_B}", b["text"]))


def to_xy(choice: str | None, direction: str) -> str | None:
    """A/B 응답을 자극 정의의 X/Y로 되돌린다."""
    if choice == "없음":
        return "없음"
    if choice not in ("A", "B"):
        return None
    if direction == "fwd":
        return "X" if choice == "A" else "Y"
    return "Y" if choice == "A" else "X"


def decide_adaptive(reps: dict[int, str | None]) -> str | None:
    """2회 일치면 확정(3회차는 판정에 영향을 줄 수 없다). 불일치면 3회 포함 최빈값.

    **불일치 셀을 제외하지 않는다** — 경합층을 통째로 날리면 표본이 편향된다.
    동수면 미결정(None)으로 남긴다.
    """
    r1, r2 = reps.get(1), reps.get(2)
    if r1 is None or r2 is None:
        return None                                    # 1파 미완 또는 형식 위반
    if r1 == r2:
        return r1
    values = [v for v in (r1, r2, reps.get(3)) if v is not None]
    ranked = Counter(values).most_common()
    if len(ranked) > 1 and ranked[0][1] == ranked[1][1]:
        return None
    return ranked[0][0]


def needs_wave2(reps: dict[int, str | None]) -> bool:
    """3회차가 필요한가 — 1파 두 값이 모두 있고 서로 다를 때만."""
    r1, r2 = reps.get(1), reps.get(2)
    return r1 is not None and r2 is not None and r1 != r2
