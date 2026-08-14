"""대표 응답자 선정 — 화면이 «누구의 말»을 보이는지 정하는 규칙.

여기서 지키는 것 셋:
  ① **결정론** — 같은 입력이면 같은 사람. 난수를 쓰면 새로고침마다 대표가 바뀐다.
  ② **위치응답자 제외** — 순서를 보고 고른 사람의 말을 이유로 읽으면 없는 근거가 생긴다.
  ③ **양쪽을 같이 보인다** — 이긴 쪽만 보이면 인터뷰가 판정을 두 번 말하게 된다.
"""

import app.twin as twin
from app.twin.aggregate import classify_subjects


def rows_for(subject: str, pair_id: str, fwd: str, rev: str) -> list[dict]:
    """한 사람의 4셀(양방향 × 반복 2). `fwd`·`rev` 는 그 방향에서 고른 «A/B/없음»."""
    out = []
    for direction, choice in (("fwd", fwd), ("rev", rev)):
        for rep in (1, 2):
            out.append({"subject": subject, "pair_id": pair_id, "direction": direction,
                        "rep": rep, "ok": True, "choice": choice,
                        "raw": f"{subject} 의 이유입니다.\n선택: {choice}"})
    return out


CARD = "저는 만 {age}세 {gender}입니다. 서울 시 지역에 살고 있습니다. " \
       "1세대가구(부부) 형태의 2인 가구이고, 아파트에 거주합니다. " \
       "일은 영업직 쪽 일을 임금 근로자로 하고 있습니다. " \
       "개인 월소득은 300~400만 원 미만 수준입니다."


def scenario():
    """X 3명 · Y 3명 · 미결정 2명 · 위치응답 2명."""
    rows, cards, cells = [], {}, {}
    plan = ([(f"x{i}", "A", "B", "content") for i in range(3)]
            + [(f"y{i}", "B", "A", "content") for i in range(3)]
            + [(f"u{i}", "없음", "없음", "undecided") for i in range(2)]
            + [(f"p{i}", "A", "A", "position") for i in range(2)])
    for index, (subject, fwd, rev, _kind) in enumerate(plan):
        rows += rows_for(subject, "P1", fwd, rev)
        cards[subject] = CARD.format(age=20 + index, gender="남성" if index % 2 else "여성")
        cells[subject] = f"셀{index}"                      # 층은 전부 다르게 둔다
    return rows, cards, cells


def build(winner="X"):
    rows, cards, cells = scenario()
    return twin._interviews(rows, "P1", winner, classify_subjects(rows, "P1"), cards, cells)


def test_quota_is_two_two_one():
    interviews = build()
    assert [item["choice"] for item in interviews] == ["X", "X", "Y", "Y", "UNDECIDED"]


def test_winner_side_comes_first_when_y_wins():
    assert [item["choice"] for item in build(winner="Y")][:2] == ["Y", "Y"]


def test_position_responders_are_never_interviewed():
    rows, cards, cells = scenario()
    classes = classify_subjects(rows, "P1")
    assert {classes[f"p{i}"] for i in range(2)} == {"position_driven"}
    quoted = {item["quote"].split(" ")[0] for item in build()}
    assert not any(name.startswith("p") for name in quoted)


def test_is_deterministic():
    assert build() == build()


def test_never_exceeds_the_cap():
    assert len(build()) <= twin.INTERVIEWS_PER_PAIR


def test_quote_drops_the_choice_line():
    """«선택: A» 는 화면이 배지로 보여준다. 인용문에 남으면 사람 말이 아니라 로그로 읽힌다."""
    for item in build():
        assert "선택:" not in item["quote"]


def test_profile_carries_the_six_fields():
    for item in build():
        assert set(item["profile"]) == {"age", "gender", "household", "region", "income", "job"}


def test_card_text_never_leaks_whole():
    """카드 원문은 재배포 금지 자산이다 — 계약에 통째로 실리면 안 된다."""
    for item in build():
        assert "최종 학력" not in item["quote"]
        assert "가구 안에서는" not in str(item["profile"])


def test_same_stratum_is_not_picked_twice_when_avoidable():
    rows, cards, cells = scenario()
    for subject in cells:                                  # 전원을 한 층에 몰아넣는다
        cells[subject] = "남40대" if subject.startswith("x") else f"기타{subject}"
    interviews = twin._interviews(rows, "P1", "X", classify_subjects(rows, "P1"), cards, cells)
    # 층이 겹치면 한 명만 뽑히고, 인원이 모자라면 완화해서 채운다 — 둘 다 정상 경로다.
    assert len([item for item in interviews if item["choice"] == "X"]) == 2


def test_fills_the_quota_when_one_side_is_empty():
    """우열형은 한쪽이 만장일치에 가까워 «진 쪽»이 0명인 일이 흔하다.

    실측(n=50 스모크)에서 카드가 3장만 나왔다 — 배분표대로만 뽑고 남은 자리를 비웠기
    때문이다. 화면이 5장을 전제하므로 남은 사람으로 메운다.
    """
    rows, cards, cells = [], {}, {}
    plan = [(f"x{i}", "A", "B") for i in range(6)] + [(f"u{i}", "없음", "없음") for i in range(2)]
    for index, (subject, fwd, rev) in enumerate(plan):
        rows += rows_for(subject, "P1", fwd, rev)
        cards[subject] = CARD.format(age=20 + index, gender="남성")
        cells[subject] = f"셀{index}"
    interviews = twin._interviews(rows, "P1", "X", classify_subjects(rows, "P1"), cards, cells)
    assert len(interviews) == twin.INTERVIEWS_PER_PAIR
    assert [item["choice"] for item in interviews] == ["X", "X", "UNDECIDED", "X", "X"]


def test_does_not_invent_people_when_the_pool_is_small():
    """메우기는 있는 사람 안에서만 한다 — 모자라면 모자란 대로 낸다."""
    rows, cards, cells = [], {}, {}
    for index, subject in enumerate(("x0", "x1")):
        rows += rows_for(subject, "P1", "A", "B")
        cards[subject] = CARD.format(age=30 + index, gender="여성")
        cells[subject] = f"셀{index}"
    interviews = twin._interviews(rows, "P1", "X", classify_subjects(rows, "P1"), cards, cells)
    assert len(interviews) == 2
