"""판매 경계 게이트 — 프론트 거울과 같은 사례표로 검사한다.

`frontEnd/src/features/twin-survey/taskTypeGate.test.js` 가 **같은 파일**을 읽는다.
거울이 갈리면 화면은 실행 버튼을 열어주고 서버가 422 로 막는다. 그 상황은 사용자에게
「되는 줄 알았는데 안 된다」로 보이므로, 배포 전에 여기서 잡는다.
"""

import json
from pathlib import Path

import pytest

from app.twin.task_type import (DOMINANCE, ETHICAL_VALUE, IDENTICAL, PRICE,
                                SERVICEABLE, UNMEASURABLE, classify)

CASES = json.loads(
    (Path(__file__).resolve().parent / "fixtures" / "twin_survey" / "gate_cases.json")
    .read_text(encoding="utf-8"))["cases"]


@pytest.mark.parametrize("case", CASES, ids=[c["name"] for c in CASES])
def test_gate_matches_the_shared_case_table(case):
    pair = {"pairId": "P1", "X": case["X"], "Y": case["Y"]}
    verdict = classify(pair)
    assert verdict.task_type == case["expected"]
    # 분류가 같아도 «팔 수 있나»가 갈리면 화면과 서버가 갈린다. 둘 다 못박는다.
    assert verdict.serviceable is (case["expected"] in SERVICEABLE)


def test_case_table_covers_every_type():
    covered = {case["expected"] for case in CASES}
    assert covered == {DOMINANCE, PRICE, ETHICAL_VALUE, UNMEASURABLE, IDENTICAL}


def test_case_table_is_not_trivially_permissive():
    """막는 사례가 통과 사례보다 적으면 표가 게이트를 시험하지 못한다."""
    blocked = [c for c in CASES if c["expected"] not in SERVICEABLE]
    assert len(blocked) >= 5


def test_price_cases_are_classified_but_blocked():
    """2026-08-10 — 분류는 PRICE 로 남긴다(거절 이유를 말하려고), 판매는 막는다."""
    price = [c for c in CASES if c["expected"] == PRICE]
    assert price, "가격형 사례가 표에서 사라지면 차단이 회귀해도 아무도 모른다"
    for case in price:
        verdict = classify({"pairId": "P1", "X": case["X"], "Y": case["Y"]})
        assert verdict.blocked
