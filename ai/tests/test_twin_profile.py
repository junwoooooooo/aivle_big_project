"""프로필 파서 — 화면 인터뷰 카드에 앉을 6필드.

이 파서가 조용히 실패하면 화면에 «이름 없는 사람»이 앉는다. 그래서 규율이 둘이다:
**필드마다 None 로 떨어지되 예외는 던지지 않는다**, 그리고 **카드 원문을 통째로 흘리지 않는다**.
"""

import json
from pathlib import Path

import pytest

from app.twin.profile import FIELDS, is_empty, parse_profile

BANK = Path(__file__).resolve().parents[1] / "app" / "twin" / "bank" / "twin_cards_generic.jsonl"

SAMPLE = ("저는 만 48세 남성입니다. 서울 시 지역에 살고 있습니다. "
          "1세대가구(부부) 형태의 2인 가구이고, 아파트에 거주합니다. "
          "가구 안에서는 가구주입니다. 최종 학력은 대졸 이하이고, 혼인 상태는 기혼입니다. "
          "일은 영업직 쪽 일을 임금 근로자로 하고 있습니다. "
          "개인 월소득은 300~400만 원 미만 수준입니다. "
          "가구 월소득은 700-750만 원 미만 수준입니다.")


def test_reads_the_six_fields():
    assert parse_profile(SAMPLE) == {
        "age": 48, "gender": "남성", "household": "2인 가구", "region": "서울",
        "income": "월소득 300~400만 원", "job": "영업직"}


def test_takes_personal_income_not_household():
    """가구 월소득(700-750)이 아니라 개인 월소득(300~400)이다 — 둘 다 있는 문장이다."""
    assert "300~400" in parse_profile(SAMPLE)["income"]
    assert "700" not in parse_profile(SAMPLE)["income"]


@pytest.mark.parametrize("text,expected", [
    ("저는 만 30세 여성입니다. 현재 학생입니다.", "학생"),
    ("저는 만 44세 여성입니다. 현재 전업주부입니다.", "전업주부"),
    ("저는 만 71세 남성입니다. 현재 따로 하는 일은 없습니다.", "무직"),
])
def test_job_variants(text, expected):
    assert parse_profile(text)["job"] == expected


def test_county_region_is_read_too():
    """«전남 군 지역» 같은 문장이 696장 있다 — 시 형태만 읽으면 그 사람들이 지역 없이 앉는다."""
    assert parse_profile("저는 만 60세 남성입니다. 전남 군 지역에 살고 있습니다.")["region"] == "전남"


def test_no_income_is_stated_not_dropped():
    """소득이 없는 것과 못 읽은 것은 다르다."""
    assert parse_profile("저는 만 22세 여성입니다. 개인 소득은 없습니다.")["income"] == "개인 소득 없음"


@pytest.mark.parametrize("value", [None, "", "아무 말이나", "저는 사람입니다."])
def test_never_raises_on_junk(value):
    profile = parse_profile(value)
    assert set(profile) == set(FIELDS)
    assert is_empty(profile)


@pytest.mark.skipif(not BANK.exists(), reason="카드 뱅크가 붙어 있지 않다(재배포 금지 자산)")
def test_every_real_card_parses_completely():
    """실물 8,604장 전수. 한 필드라도 빠지면 그 사람은 화면에서 반쪽으로 앉는다."""
    missing = {field: 0 for field in FIELDS}
    total = 0
    with BANK.open(encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            total += 1
            profile = parse_profile(json.loads(line)["text"])
            for field in FIELDS:
                if profile[field] is None:
                    missing[field] += 1
    assert total > 0
    assert missing == {field: 0 for field in FIELDS}, missing
