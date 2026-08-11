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

# 쌍별 «내용층 비율, X 선호 비율» — 한 쌍은 강하게(잰다), 한 쌍은 팽팽하게(못 잰다).
# 둘 다 **우열형**이다. 가격형은 2026-08-10 부터 실행 전에 거절된다.
SHAPE = {"P1": (0.94, 0.93), "P2": (0.82, 0.58)}
ABSTAIN = 0.06                 # «없음» 응답 비율 — 미결정 인터뷰가 나오게 한다

FRAME = [{"pid_hash": f"h{i:04d}",
          "gender": "남" if i % 2 else "여",
          "age": str(20 + (i * 7) % 50),
          "band": ["20대", "30대", "40대", "50대", "60+"][i % 5],
          "weight": "1.0", "screen_exclude": "0"}
         for i in range(400)]
#: 프로필 파서가 읽는 문장 모양을 그대로 흉내 낸다 — 6필드가 다 채워져야 인터뷰 카드가
#: 실제와 같은 모양으로 나온다. 실물 카드는 재배포 금지라 여기 넣지 않는다.
REGIONS = ["서울", "경기", "부산", "인천", "대구"]
JOBS = ["일반 지원 사무직", "영업직", "교육 전문가 및 관련직", "매장 판매 및 상품 대여직", "제조 관련직"]
INCOMES = ["200~300만 원", "300~400만 원", "400~500만 원", "500만 원 이상"]
CARDS = {row["pid_hash"]: (
    f"저는 만 {row['age']}세 {row['gender']}성입니다. "
    f"{REGIONS[i % len(REGIONS)]} 시 지역에 살고 있습니다. "
    f"2세대가구(부부+자녀) 형태의 {1 + i % 4}인 가구이고, 아파트에 거주합니다. "
    f"일은 {JOBS[i % len(JOBS)]} 쪽 일을 임금 근로자로 하고 있습니다. "
    f"개인 월소득은 {INCOMES[i % len(INCOMES)]} 미만 수준입니다.")
    for i, row in enumerate(FRAME)}

PAYLOAD = {
    "situation": "가게에서 연어를 하나 고릅니다. 진열대에 아래 두 상품이 있습니다.",
    "sampleSize": 100,
    "pairs": [
        {"pairId": "P1",
         "X": {"label": "신선 냉장", "attrs": {"형태": "신선(냉장)"}, "priceKrw": 4500},
         "Y": {"label": "냉동", "attrs": {"형태": "냉동"}, "priceKrw": 4500}},
        # 가격을 양쪽 같게 두고 속성 하나만 바꾼다 — 가격이 다르면 지불의사가 되어 거절된다.
        {"pairId": "P2",
         "X": {"label": "노르웨이산", "attrs": {"원산지": "노르웨이산"}, "priceKrw": 4500},
         "Y": {"label": "칠레산", "attrs": {"원산지": "칠레산"}, "priceKrw": 4500}},
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
            abstains = _unit(subject, pair["pairId"], "abstain") < ABSTAIN
            for direction in DIRECTIONS:
                if abstains:
                    choice = "없음"                             # 미결정 — 분모에서 빠진다
                elif content:
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
    """팽팽한 쌍은 MDE 가 |Δ| 보다 커서 «못 잼» 으로 떨어진다 — 그게 정직한 결과다."""
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
        assert len(pair["interviews"]) <= twin.INTERVIEWS_PER_PAIR
