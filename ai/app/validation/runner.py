# -*- coding: utf-8 -*-
"""사업 검증 — 시장조사(FULL) → BM 캔버스(BM) 를 **한 실행**으로 잇는다.

새로 쓰는 것이 없다. 기존 `run_market_research` 를 두 번 부르고 봉투 하나로 합친다.
그래서 이 파일은 **오케스트레이션만** 한다 — 판정도, 프롬프트도, 검증도 여기 없다.

⚠ **왜 봉투를 새로 만들지 않나.** `MarketResearchContract.ENVELOPE` 는 최상위 필드 집합을
`exact()` 로 못박는다. 칸을 하나라도 늘리면 결과 전체가 `RESULT_UNKNOWN_FIELD` 로 거부된다.
그래서 늘리는 것은 **`mode` 값 하나**(`VALIDATION`)뿐이고, 두 걸음의 산출은 원래 비어 있던
칸에 들어간다 — FULL 이 `scorecard`·`market`·`evidence`·`summary` 를, BM 이 `canvas`·`bm` 을
채운다. 두 모드가 이미 같은 봉투를 쓰기 때문에 합치면 그냥 다 찬 봉투가 된다.

⚠ **BM 이 죽으면 조사 결과도 채택되지 않는다** — TaskRun 하나에 채택은 한 번이다.
완화책은 **원장 재사용**이다. 제품 사업안의 원장 이름은 `conceptId` 이고, 그 값은
`MarketAnalysisSeedLookup.conceptIdOf()` = 사업안의 `portfolioConceptId` 라
**시드가 재발급돼도 같다**. 그래서 다시 눌러도 수집(유료)은 다시 사지 않고 재채점만 한다.
"""
from __future__ import annotations

from typing import Any

#: 예산 배분. 실측은 FULL 약 23분(run 15: 07:36:53→07:59:42) · BM 18~39초다.
#: BM 몫을 넉넉히 떼어 두는 이유는 **떼어 두지 않으면 FULL 이 다 쓰기 때문**이다 —
#: 남은 예산으로 도는 구조라 앞 걸음이 굶기면 뒤 걸음이 시작도 못 한다.
BM_BUDGET_SHARE = 0.12
BM_BUDGET_FLOOR_SECONDS = 90.0


async def execute_business_validation(task_input: dict, run_id: str,
                                      timeout_seconds: float) -> dict:
    """FULL → BM 을 잇고 봉투 하나로 합친다.

    `task_input` 은 시장조사 FULL 과 **같은 봉투**다(`MarketResearchInputFactory.full`).
    `mode` 는 여기서 정한다 — 호출자가 실어 보낸 값은 무시한다. 두 걸음을 도는 것이
    이 TaskType 의 정의이지 입력이 고를 일이 아니다.
    """
    from app.research.pipeline import run_market_research   # noqa: PLC0415

    bm_budget = max(BM_BUDGET_FLOOR_SECONDS, timeout_seconds * BM_BUDGET_SHARE)
    full_budget = max(0.0, timeout_seconds - bm_budget)

    full = await run_market_research({**task_input, "mode": "FULL"}, run_id, full_budget)
    # BM 은 1단계 원장 위에서만 선다. 원장 이름은 `conceptId` 이므로 여기서 따로 넘기지
    # 않는다 — 파이프라인이 같은 규칙으로 되짚는다(`pipeline.py:259-275`).
    bm = await run_market_research({**task_input, "mode": "BM"}, run_id, bm_budget)

    return _merge(full, bm)


def _merge(full: dict, bm: dict) -> dict:
    """두 봉투를 하나로. **칸을 늘리지 않는다** — 빈 칸을 채울 뿐이다.

    걸음 기록(`stages`)과 격하(`degradations`)는 **이어 붙인다.** 뒤 걸음 것만 남기면
    「조사에서 무엇이 건너뛰어졌나」가 사라지고, 그 순간 캔버스의 빈 칸에 이유가 없어진다.
    """
    merged: dict[str, Any] = dict(full)
    for name in ("canvas", "bm"):
        merged[name] = bm.get(name)
    # 뒤 걸음이 채운 칸이 있으면 그것이 이긴다 — BM 은 FULL 원장을 읽고 다시 쓴 것이다.
    for name in ("market", "summary"):
        if bm.get(name) is not None:
            merged[name] = bm[name]
    # ⚠ **`evidence`·`scorecard` 는 통째로 갈아끼우지 않는다.** BM 은 절 체인을 안 돌아
    #   FULL 보다 **가진 것이 적다**. 통째 교체는 이 함수의 첫 줄(「빈 칸을 채울 뿐이다」)과
    #   어긋나고, 판 ㊸ 에서 실제로 **FULL 이 승격한 절 사실 132장이 통째로 사라진다.**
    merged["evidence"] = _merge_evidence(full.get("evidence"), bm.get("evidence"))
    merged["scorecard"] = _merge_scorecard(full.get("scorecard"), bm.get("scorecard"))
    # 2·8·9절은 FULL 만 만든다. BM 의 `null` 이 이기면 조사 결과가 조용히 사라진다.
    for name in ("judgment", "prescriptions", "synthesis", "report"):
        merged[name] = full.get(name) if full.get(name) is not None else bm.get(name)
    merged["stages"] = list(full.get("stages") or []) + list(bm.get("stages") or [])
    merged["degradations"] = list(full.get("degradations") or []) + list(bm.get("degradations") or [])
    merged["notes"] = _dedupe(list(full.get("notes") or []) + list(bm.get("notes") or []))
    merged["generatedAt"] = bm.get("generatedAt") or full.get("generatedAt")
    merged["mode"] = "VALIDATION"
    return merged


def _merge_evidence(full: Any, bm: Any) -> Any:
    """근거는 **합집합**이다. id 가 겹치면 **절이 붙은 쪽**을 남긴다.

    FULL 은 절 체인으로 승격한 카드(실측 132장)를 들고 오고 BM 은 슬롯 카드(15장)만
    들고 온다. 통째로 갈아끼우면 사업가가 9절 문장의 수를 검산하러 갔을 때 **근거가
    없다**. 「뒤 걸음이 이긴다」는 **같은 것을 다시 쓴 경우**의 규칙이지, 덜 가진 봉투가
    더 가진 봉투를 지우라는 뜻이 아니다.
    """
    if not isinstance(bm, list):
        return full
    if not isinstance(full, list):
        return bm
    out: dict[str, dict] = {}
    for item in list(full) + list(bm):
        if not isinstance(item, dict) or not item.get("id"):
            continue
        old = out.get(item["id"])
        if old is not None and old.get("section") and not item.get("section"):
            continue                    # 절이 붙은 옛것을 절 없는 새것으로 덮지 않는다
        out[item["id"]] = item
    return list(out.values())


def _merge_scorecard(full: Any, bm: Any) -> Any:
    """성적표는 **과목별로** 합친다. BM 이 「안 쟀다」인 과목은 FULL 의 성적을 남긴다.

    BM 은 절 체인을 안 돌아 채널·원가·수익성·규제 세 과목을 늘 `MISSING` 으로 낸다.
    통째로 갈아끼우면 FULL 이 실제로 잰 성적이 **「안 쟀다」로 뒤집힌다.**
    """
    if not isinstance(bm, list):
        return full
    if not isinstance(full, list):
        return bm
    앞 = {r.get("subject"): r for r in full if isinstance(r, dict)}
    out = []
    for row in bm:
        old = 앞.get(row.get("subject")) if isinstance(row, dict) else None
        # **BM 의 `MISSING` 은 정보가 아니다.** BM 은 절 체인을 안 돌기 때문에 구조적으로
        # 그렇게 나온다. FULL 의 줄이 있으면 언제나 FULL 이 이긴다.
        #
        # ⚠ 예전에는 `old.state != "MISSING"` 을 같이 요구해서 **둘 다 `MISSING` 이면
        #   BM 이 이겼다.** 그 줄의 사유가 「이 실행은 절 조사를 돌리지 않았다 — 0건이
        #   아니라 «안 쟀다»다」라, 절 체인이 `OK` 로 돌아 **재고 0건이었던** 실행이
        #   화면에서 **「안 쟀다」로 뒤집혔다**(VALIDATION 실측 2026-08-15).
        #   재서 없는 것과 안 잰 것을 가르려고 만든 문장이 정반대로 쓰인 것이다.
        #   같은 이유로 FULL 이 붙인 「정황 근거 n건은 아래에 있다」도 통째로 사라졌다.
        if old and row.get("state") == "MISSING":
            out.append(old)
        else:
            out.append(row)
    return out


def _dedupe(lines: list[str]) -> list[str]:
    """순서를 지키며 중복을 없앤다 — 두 모드의 주의문이 겹친다."""
    seen: set[str] = set()
    out: list[str] = []
    for line in lines:
        if line not in seen:
            seen.add(line)
            out.append(line)
    return out
