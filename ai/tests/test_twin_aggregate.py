"""Δ 분해·λ·MDE — LLM 호출 0회.

원장을 손으로 짜서 **닫힌 형태로 계산되는 값**과 대조한다. 실제 G3D 원장(19,994셀)과의
대조는 저장소 밖 자료라 여기서 못 돌리지만, 이식 시점에 16/16쌍·10개 수치 필드가
오차 1e-9로 일치함을 확인했다(`combine_csv/_build/g3e/g3e_aggregate.py --selfcheck`).
"""

import pytest

from app.twin.aggregate import Z_MDE, analyze, mde_effective, verdict

PAIR = "P1"


def cell(subject, direction, rep, choice):
    return {"subject": subject, "pair_id": PAIR, "direction": direction,
            "rep": rep, "choice": choice, "ok": True}


def both_reps(subject, direction, choice):
    return [cell(subject, direction, 1, choice), cell(subject, direction, 2, choice)]


def ledger():
    """content_X 6명 · content_Y 2명 · 위치응답 1명 · 미결정 1명.

    fwd 는 A→X, rev 는 A→Y 이므로
      content_X = fwd 'A' + rev 'B',  content_Y = fwd 'B' + rev 'A',
      위치응답  = 양방향 모두 'A'
    """
    rows = []
    for i in range(1, 7):                                    # content_X
        rows += both_reps(f"s{i:02d}", "fwd", "A") + both_reps(f"s{i:02d}", "rev", "B")
    for i in range(7, 9):                                    # content_Y
        rows += both_reps(f"s{i:02d}", "fwd", "B") + both_reps(f"s{i:02d}", "rev", "A")
    rows += both_reps("s09", "fwd", "A") + both_reps("s09", "rev", "A")   # 위치응답
    rows += [cell("s10", "fwd", 1, "A"), cell("s10", "fwd", 2, "B")]      # 미결정
    rows += both_reps("s10", "rev", "A")
    return rows


def test_delta_decomposition_matches_closed_form():
    stats = analyze(ledger(), PAIR)

    assert stats["n_subjects"] == 10
    assert stats["n_p"] == 9, "미결정 응시자는 대응표본 분모에서 빠진다"
    assert stats["delta_fwd"] == pytest.approx(5 / 9)
    assert stats["delta_rev"] == pytest.approx(3 / 9)
    assert stats["delta_avg"] == pytest.approx(4 / 9)
    assert stats["position"] == pytest.approx(1 / 9)


def test_respondent_classes_are_counted_separately():
    stats = analyze(ledger(), PAIR)
    assert stats["cls"] == {"content_X": 6, "content_Y": 2,
                            "position_driven": 1, "undecided": 1}


def test_lambda_and_mde_match_closed_form():
    stats = analyze(ledger(), PAIR)
    variance = 8 / 9 - (4 / 9) ** 2

    assert stats["lambda_p"] == pytest.approx(8 / 9)
    assert stats["var_p"] == pytest.approx(variance)
    assert stats["sd_p"] == pytest.approx((variance / 9) ** 0.5)
    assert stats["mde_p"] == pytest.approx(Z_MDE * (variance / 9) ** 0.5)


def test_position_bias_is_separated_not_removed():
    """한 방향만 물었다면 Δ 가 5/9 로 부풀었을 것이다 — 양방향 평균이 그것을 갈라낸다."""
    stats = analyze(ledger(), PAIR)
    assert stats["delta_fwd"] != stats["delta_avg"]
    assert stats["position"] == pytest.approx(
        (stats["delta_fwd"] - stats["delta_rev"]) / 2)


def test_empty_pair_yields_no_denominator():
    stats = analyze([], PAIR)
    assert stats["n_p"] == 0
    assert stats["delta_avg"] is None
    assert verdict(stats)["measurable"] is False


def test_mde_floor_saves_unanimous_pairs_from_degenerating():
    """λ=1·|Δ|=1 이면 Var=0 이라 MDE 가 0 이 된다 — 한 명 차이에도 «차이 있음»이 된다."""
    assert mde_effective(0.0, 100) == pytest.approx(0.06)
    assert mde_effective(0.0, 300) == pytest.approx(0.02)
    assert mde_effective(0.25, 100) == pytest.approx(0.25), "실측 한계가 크면 그쪽을 쓴다"
    assert mde_effective(0.0, 0) is None


def test_verdict_calls_a_winner_only_beyond_the_measurement_floor():
    rows = []
    for i in range(1, 21):                                   # 전원 content_X → Δ=+1
        rows += both_reps(f"s{i:02d}", "fwd", "A") + both_reps(f"s{i:02d}", "rev", "B")
    decision = verdict(analyze(rows, PAIR))

    assert decision["winner"] == "X"
    assert decision["measurable"] is True
    assert "confidenceInterval" in decision


def test_verdict_says_not_measured_rather_than_no_difference():
    """«못 잼»과 «차이 없음»을 섞으면 없는 결론이 생긴다."""
    rows = []
    for i in range(1, 6):                                    # content_X 5
        rows += both_reps(f"x{i:02d}", "fwd", "A") + both_reps(f"x{i:02d}", "rev", "B")
    for i in range(1, 6):                                    # content_Y 5 → Δ=0
        rows += both_reps(f"y{i:02d}", "fwd", "B") + both_reps(f"y{i:02d}", "rev", "A")
    decision = verdict(analyze(rows, PAIR))

    assert decision["winner"] == "TIE"
    assert decision["measurable"] is False
    assert "못 잼" in decision["reason"]
