"""골든 픽스처 — `tests/fixtures/twin_survey/survey.json` 이 코드와 갈라지지 않게 묶는다.

프론트(`features/twin-survey/twinSurveyResult.test.js`)가 **같은 파일**을 읽는다.
두 층이 한 파일을 보므로 한쪽이 모양을 바꾸면 다른 쪽 테스트가 즉시 빨개진다.
사본을 만들면 그 성질이 사라진다 — 그래서 복사하지 않는다.

LLM 은 부르지 않는다. 뱅크와 러너만 결정론적 가짜로 갈아끼우고 나머지는 진짜 코드다.
픽스처를 새로 떠야 하면 이 테스트가 만드는 값을 그대로 파일에 쓰면 된다.
"""

import asyncio
import hashlib
import json
from pathlib import Path

import app.twin as twin
from app.twin.stimuli import DIRECTIONS

FIXTURE = Path(__file__).resolve().parent / "fixtures" / "twin_survey" / "survey.json"

# 쌍별 «내용층 비율, X 선호 비율» — 우열형은 강하게, 가격형은 팽팽하게 만든다.
SHAPE = {"P1": (0.94, 0.93), "P2": (0.82, 0.58)}

FRAME = [{"pid_hash": f"h{i:04d}",
          "gender": "남" if i % 2 else "여",
          "age": str(20 + (i * 7) % 50),
          "band": ["20대", "30대", "40대", "50대", "60+"][i % 5],
          "weight": "1.0", "screen_exclude": "0"}
         for i in range(400)]
CARDS = {row["pid_hash"]: f"저는 만 {row['age']}세 {row['gender']}성입니다. 가상의 카드입니다."
         for row in FRAME}

PAYLOAD = {
    "situation": "가게에서 연어를 하나 고릅니다. 진열대에 아래 두 상품이 있습니다.",
    "sampleSize": 100,
    "pairs": [
        {"pairId": "P1",
         "X": {"label": "신선 냉장", "attrs": {"형태": "신선(냉장)"}, "priceKrw": 4500},
         "Y": {"label": "냉동", "attrs": {"형태": "냉동"}, "priceKrw": 4500}},
        {"pairId": "P2",
         "X": {"label": "신선 냉장(비쌈)", "attrs": {"형태": "신선(냉장)"}, "priceKrw": 6600},
         "Y": {"label": "냉동", "attrs": {"형태": "냉동"}, "priceKrw": 4500}},
    ],
}


def _unit(*parts):
    blob = ":".join(str(p) for p in parts).encode("utf-8")
    return int(hashlib.sha256(blob).hexdigest()[:12], 16) / 16 ** 12


async def _fake_run_survey(cards, pairs, situation, budget_seconds):
    rows = []
    stats = {"cells": 0, "rateLimited": 0, "timeouts": 0, "retries": 0,
             "formatViolations": 0, "failures": 0, "truncated": 0, "waitSeconds": 0.0,
             "promptTokens": 0, "completionTokens": 0, "wave2Cells": 0,
             "model": "fixture-model", "requestFingerprint": "f" * 64, "concurrency": 32}
    for pair in pairs:
        lam, prefers = SHAPE[pair["pairId"]]
        for subject in sorted(cards):
            content = _unit(subject, pair["pairId"], "content") < lam
            picks_x = _unit(subject, pair["pairId"], "side") < prefers
            for direction in DIRECTIONS:
                if content:
                    xy = "X" if picks_x else "Y"
                    choice = ("A" if xy == "X" else "B") if direction == "fwd" \
                        else ("B" if xy == "X" else "A")
                else:
                    choice = "A"                                # 위치 고정 응답
                for rep in (1, 2):
                    rows.append({
                        "subject": subject, "pair_id": pair["pairId"],
                        "direction": direction, "rep": rep, "ok": True, "kind": None,
                        "choice": choice, "attempts": 1,
                        "raw": ("이 사람의 형편에서는 그쪽이 낫다고 봅니다. "
                                "가격 부담이 크지 않은 선택입니다.\n"
                                f"선택: {choice}"),
                        "model_reported": "fixture-model"})
                    stats["cells"] += 1
                    stats["promptTokens"] += 400
                    stats["completionTokens"] += 80
    stats["seconds"] = 12.3
    stats["llmCalls"] = stats["cells"]
    return rows, stats


def build(monkeypatch):
    monkeypatch.setattr(twin, "load", lambda: (CARDS, FRAME))
    monkeypatch.setattr(twin, "run_survey", _fake_run_survey)
    return asyncio.run(twin.execute_twin_survey(PAYLOAD, budget_seconds=600))


def test_result_matches_the_golden_fixture(monkeypatch):
    expected = json.loads(FIXTURE.read_text(encoding="utf-8"))
    assert build(monkeypatch) == expected, (
        "결과 모양이 픽스처와 갈라졌다. 의도한 변경이면 픽스처를 다시 뜨고, "
        "프론트 twinSurveyResult 도 같이 고쳐라.")


def test_every_pair_carries_caveats(monkeypatch):
    """빈 caveats 는 백엔드 계약이 거부한다 — 여기서 먼저 잡는다."""
    for pair in build(monkeypatch)["pairs"]:
        assert pair["caveats"], f"{pair['pairId']} 에 경계 문구가 없다"


def test_unmeasurable_pair_says_not_measured(monkeypatch):
    """n=100 의 가격형은 MDE 가 커서 «못 잼» 으로 떨어진다 — 그게 정직한 결과다."""
    pairs = {p["pairId"]: p for p in build(monkeypatch)["pairs"]}
    assert pairs["P2"]["measurable"] is False
    assert pairs["P2"]["winner"] == "TIE"
    assert abs(pairs["P2"]["deltaAvg"]) < pairs["P2"]["mde"]
    assert any("못 잼" in note for note in pairs["P2"]["caveats"])


def test_result_stays_far_below_the_envelope_limit(monkeypatch):
    """내부 API 응답 상한은 2 MiB 다. 셀 원장을 실으면 여기서 터진다."""
    payload = json.dumps(build(monkeypatch), ensure_ascii=False).encode("utf-8")
    assert len(payload) < 2 * 1024 * 1024


def test_no_raw_ledger_leaks_into_the_result(monkeypatch):
    result = build(monkeypatch)
    assert "rows" not in result and "cells" not in result
    for pair in result["pairs"]:
        assert "rows" not in pair
        assert len(pair["rationaleExcerpts"]) <= twin.EXCERPTS_PER_PAIR
