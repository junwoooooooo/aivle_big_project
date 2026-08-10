# -*- coding: utf-8 -*-
"""「못 찾은 것」 갈래표가 엔진과 갈리지 않는지.

`serialize._NOT_FOUND` 는 엔진 `schema.NOT_FOUND_KEYS` 의 **사본이 아니라 분류표**다.
엔진에 새 진단이 생기면 이 검사가 먼저 빨개져야 한다 — 안 그러면 새 진단이 조용히
`ContractDrift` 로 런타임에 터지거나, 더 나쁘게 잘못된 서랍에 들어간다.
"""
from app.research import serialize
from app.research.research2 import schema


def test_every_engine_diagnostic_key_has_a_drawer():
    missing = set(schema.NOT_FOUND_KEYS) - set(serialize._NOT_FOUND)
    assert not missing, f"갈래가 없는 진단 키: {sorted(missing)}"


# 반대 방향(「표에만 있고 엔진엔 없는 키」)은 검사하지 않는다.
# `schema.NOT_FOUND_KEYS` 가 엔진이 실제로 내는 것의 **부분집합**이기 때문이다 —
# `blocks/c_chain.py` 는 `range_capped`·`skipped_checks`·`unit_mismatch` 를 내보내는데
# 그 선언 목록에는 없다(beauty-13 원장으로 실측). research2 는 동결 구역이라 여기서
# 고치지 않고, 표는 실제로 오는 것을 기준으로 넓게 잡는다.


def test_every_group_has_a_label():
    assert set(serialize._NOT_FOUND.values()) <= set(serialize._NOT_FOUND_GROUP_LABEL)


def test_unknown_key_is_refused_not_swallowed():
    """조용히 흘리면 새 진단이 잘못된 서랍에 들어간다. fail-closed 로 막는다."""
    try:
        serialize._not_found_blocks({"완전히_새로운_진단": ["x"]}, [])
    except serialize.ContractDrift:
        return
    raise AssertionError("계약 밖 키를 받아들였다")


def test_slot_id_becomes_a_human_phrase():
    slots = [{"slot_id": "S2", "var_id": "V2", "subject": "두발 미용업",
              "metric": "종사자 1인 사업체 비율", "period": "2025", "unit": "%"}]
    blocks = serialize._not_found_blocks({"empty_slots": ["S2"]}, slots)
    assert blocks[0]["detail"] == "S2 — 두발 미용업 · 종사자 1인 사업체 비율 (2025, %)"


def test_adapters_that_work_are_not_reported_as_missing():
    """`kosis ok` 는 「못 찾은 것」이 아니다. 실어 보내면 잡음이자 거짓이다."""
    blocks = serialize._not_found_blocks(
        {"adapters": {"kosis": "ok", "web": "ok", "dart": "not_configured"}}, [])
    assert [b["detail"] for b in blocks] == ["dart — not_configured"]


def test_a_variable_without_a_slot_says_so_instead_of_inventing_one():
    blocks = serialize._not_found_blocks({"unfilled_vars": ["V5"]}, [])
    assert blocks[0]["detail"] == "V5 — 대응 슬롯 없음 (식의 계수)"


def test_numbers_do_not_leak_float_repr():
    assert serialize._num(1025336520.0000002) == "1,025,336,520"
    assert serialize._num(2.2823, 2) == "2.28"
    assert serialize._num(None) == "미확보"
