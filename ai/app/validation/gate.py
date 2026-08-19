# -*- coding: utf-8 -*-
"""판정 게이트 — **LLM 0회.** 집합 연산과 개수 세기뿐이라 공짜고 재현된다.

<b>왜 이 층이 있나.</b> `bm/finalize.py` 의 최종 판정을 정하는 신호는 셋인데
(`market_fit_status`·`consistency_status`·캔버스의 `BLOCKED`) **셋 다 모델이 써서 돌려준
값**이다. 모델이 "PASS" 라고 쓰면 통과한다. 기계가 독립적으로 반증하는 규칙이 하나도 없어서
산출물이 「검증된 상품」이 아니라 「검증했다고 적힌 문서」가 된다.

<b>그래서 이 층은 모델이 쓴 `status` 문자열을 안 믿는다.</b> 대신 **근거 개수와 라벨 집합**만
본다 — 그건 모델이 지어낼 수 없는 사실이다.

<b>내리기만 한다.</b> 규칙은 판정을 낮추는 것만 할 수 있고 절대 올릴 수 없다. 안 그러면
규칙층이 모델보다 관대해지는 일이 생긴다. 법률이 낸 `BLOCKED` 를 덮는 일도 없다.

⚠ **`serialize.canvas_cells()` 뒤에서 돌려야 한다.** 그 앞에서 재면 안 된다 —
`_stamp_user_plan()` 이 근거를 인용하지 않은 칸을 `PLAN` 으로 내리므로, 그 전에 재면 사용자가
입력한 계획을 관측으로 착각한다.
"""
from __future__ import annotations

#: 시장에서 **확인해야 하는** 칸. 근거가 붙는 것이 정상이다.
OBSERVED_CELLS = ("CUSTOMER_SEGMENTS", "VALUE_PROPOSITIONS", "CHANNELS", "REVENUE_STREAMS")

#: 사용자의 **계획**으로 채우는 칸. 근거가 없는 것이 정상이라 G1 이 세지 않는다.
PLANNED_CELLS = ("CUSTOMER_RELATIONSHIPS", "KEY_RESOURCES", "KEY_ACTIVITIES",
                 "KEY_PARTNERS", "COST_STRUCTURE")

#: 관측이 닿았다고 볼 수 있는 상태. 나머지(`PLAN`·`UNVERIFIED`·`BLOCKED`)는 안 닿은 것이다.
_OBSERVED_STATUSES = frozenset({"VERIFIED", "PARTIAL"})

#: **시장에서 온** 출처 라벨. `ALLOWED_CANVAS_SOURCE_LABELS` 일곱 중 다섯이다.
#:
#: ⚠ 빠진 둘이 핵심이다. `concept_snapshot` 은 **사용자가 쓴 컨셉 서술문**이고
#: `execution_constraints` 는 **사용자가 입력한 예산·기간·인원**이다. 둘 다 관측이 아니라
#: 입력이다. 이걸 근거로 세면 「자기가 쓴 말을 자기가 확인했다」가 통과한다.
#:
#: 실측(2026-08-13, 프로젝트 3 HMR): 관측 4칸 중 3칸이 `labels=['concept_snapshot']`,
#: `marketEvidenceIds=[]` 인데 상태가 **VERIFIED** 였고 최종 판정이 **PASS** 였다.
#: ⚠ `channel_analysis` 는 판 ㊺ 에 **더한 여덟째 라벨**이다. 그 전에는 채널에 맞는 라벨이
#: 없어 `mapping.CLAIM_TYPE_LABEL` 의 채널 자리가 비었고, 그래서 채널 칸의 파생 라벨이
#: 언제나 0건이라 폴백이 `concept_snapshot` 을 되살렸다.
_MARKET_LABELS = frozenset({"market_size", "growth_rate", "competitor_analysis",
                            "price_analysis", "demand_evidence", "channel_analysis"})

#: 판정의 무게. 게이트는 이 사다리를 **내려가기만** 한다.
_RANK = {"PASS": 0, "CONDITIONAL": 1, "REVISION_REQUIRED": 2, "BLOCKED": 3}


#: 관측 칸 → 그 칸을 뒷받침해야 하는 **성적표 과목**.
#:
#: ⚠ 2026-08-15 정정: 이 자리는 *"성적표 7과목에 채널 과목이 **없다** — 그래서 채널은
#: 「안 찾아졌는지」조차 판별할 수 없고 `cause` 가 `UNMAPPED` 이 된다"* 고 적고 있었다.
#: **판 ㊸ 에서 틀린 말이 됐다** — `serialize._SECTION_SUBJECT` 가 `CHANNEL`·
#: `UNIT_ECONOMICS`·`REGULATION` 세 과목을 넣었는데 이 표만 안 따라왔다. 그동안 화면은
#: 채널에 대해 **「조사 항목에 없어서 갈래를 알 수 없어요」**라고 말했다 — 알 수 있는데.
#:
#: ⚠ 이 줄은 **승격 카드가 캔버스에 닿는 것과 한 묶음**이다. 새 세 과목의 성적은 「그 절에
#: 실린 사실 건수」일 뿐이라(`serialize._section_rows`), 근거가 칸에 안 붙은 채 `FILLED` 를
#: 보면 여기가 「찾았는데 인용을 안 했다(UNCITED)」고 말한다. 승격 카드가 칸에 붙으면
#: 그 말이 **참이 되고**, 안 붙으면 거짓말이 된다.
_CELL_SUBJECT = {
    "CUSTOMER_SEGMENTS": "MARKET_SIZE",
    "VALUE_PROPOSITIONS": "DEMAND",
    "REVENUE_STREAMS": "PRICE",
    "CHANNELS": "CHANNEL",
}

#: 성적표가 「못 찾았다」고 적은 상태.
_MISSING_STATES = frozenset({"MISSING"})


def _cause(subject: str | None, states: dict[str, str], found: bool = False) -> str:
    """사유의 **갈래**. 「컨셉을 고쳐서 될 일인가」가 갈린다 (계획서 §5).

    - `UNCOLLECTED` — 원장에 **애초에 없다**. 재수집이 답이고, 그래도 없으면 「미확보」로
      확정하고 멈춘다. **컨셉을 고쳐 통과시키면 그게 우리가 만든 방식의 「다 패스」다**
    - `UNCITED` — 찾아는 놨는데 **칸이 인용을 안 했다**. 배선 문제이고 사용자가 할 일이 없다
    - `UNMAPPED` — 성적표가 그 칸을 **재지 않는다**. 갈래를 모른다는 사실을 그대로 적는다

    ⚠ `found` — **이 칸에 실제로 붙은 시장 근거가 있는가.** 있으면 성적표가 뭐라 하든
      `UNCOLLECTED`(=「못 찾음 · 다시 조사해야 해요」)라고 쓸 수 없다. **눈앞에 있는데
      없다고 말하는 것이기 때문이다.**

      이 갈래가 필요한 이유(2026-08-15 실측): BM 걸음은 성적표에 절 건수를 안 넘겨
      (`pipeline.py` 의 `serialize.scorecard(score)` — FULL 쪽은 `절["counts"]` 를 준다)
      채널 과목이 늘 `MISSING` 이 된다. 그러면 근거 4건이 붙은 칸에 「못 찾음」이 찍히고,
      같은 줄의 문장은 「참고할 만한 조사 결과 4건은 있다」라고 말한다 — **한 줄이 스스로를
      부정한다.** 성적표를 고치는 것이 더 깊은 수선이지만 그건 걸음 구조를 건드리는 일이라
      여기서는 **거짓말만 막는다.**
    """
    if found:
        return "UNCITED"
    if subject is None or subject not in states:
        return "UNMAPPED"
    return "UNCOLLECTED" if states[subject] in _MISSING_STATES else "UNCITED"


def _reason(code: str, cell: str | None, message: str,
            evidence_ids: list[str] | None = None,
            cause: str = "UNMAPPED") -> dict:
    return {"code": code, "cell": cell, "message": message,
            "evidenceIds": list(evidence_ids or []), "cause": cause}


def evaluate(cells: list[dict], scorecard: list[dict] | None = None) -> list[dict]:
    """직렬화된 캔버스 9칸 → 발동한 사유 목록. 아무것도 안 걸리면 빈 목록.

    받는 것은 `serialize.canvas_cells()` 의 산출물이다
    (`canvasCell`·`status`·`sourceLabels`·`marketEvidenceIds` …).

    `scorecard` 는 `serialize.scorecard()` 산출물이다. **없으면 갈래를 못 가른다** —
    그때는 모든 사유가 `UNMAPPED` 이 된다(추측해서 `UNCOLLECTED` 라고 적지 않는다).
    """
    by_cell = {cell.get("canvasCell"): cell for cell in cells}
    states = {row.get("subject"): row.get("state") for row in (scorecard or [])}
    reasons: list[dict] = []

    # ── G1. 관측 칸인데 **시장** 근거가 0건이다 ───────────────────────────
    # 상태(`status`)를 안 본다. 모델이 「확인됨」이라고 써도 인용한 시장 자료가 없으면
    # 확인된 것이 아니다. 「부분 확인」이 아니라 「아무것도 못 찾았다」이므로 이 규칙만
    # REVISION_REQUIRED 를 낸다.
    #
    # ⚠ **`marketEvidenceIds` 가 비었는지만 보면 안 된다**(2026-08-15 실측으로 잡음).
    #   승격 절 사실이 근거로 붙기 시작하면서, 채널 칸이 `UNVERIFIED` 인데도 id 가 4건
    #   있어 이 규칙이 **조용해졌다** — 판정이 `REVISION_REQUIRED` 에서 `CONDITIONAL` 로
    #   저절로 완화됐다. 붙은 4건은 「그 절에 실을 만한 사실」이지 이 칸을 확인해 준 것이
    #   아니고(`mapping.derive` 가 그래서 상태를 안 올린다), 그러면 **화면에 아무 사유도
    #   안 뜬 채 빈 칸만 남는다.** 그래서 **상태**를 함께 본다.
    for name in OBSERVED_CELLS:
        cell = by_cell.get(name)
        if cell is None:
            continue
        # ⚠ **법률이 막은 칸은 이 규칙이 안 본다**(2026-08-15 감사로 잡음). 위에서 상태를
        #   같이 보게 만든 순간, 근거가 붙어 있는데 `BLOCKED` 인 칸이 이 그물에 걸려
        #   **「이 칸을 확인해 준 근거가 없다」는 거짓말**을 하게 됐다. `BLOCKED` 는 시장
        #   근거의 문제가 아니라 법률의 문제이고, `mapping.apply` 도 그 상태만은 안 덮는다.
        if cell.get("status") == "BLOCKED":
            continue
        참고 = list(cell.get("marketEvidenceIds") or [])
        확인됨 = cell.get("status") in _OBSERVED_STATUSES
        if 참고 and 확인됨:
            continue
        if 확인됨 and _MARKET_LABELS & set(cell.get("sourceLabels") or []):
            continue
        # 「하나도 못 찾았다」와 「찾긴 했는데 이 칸을 확인해 주진 못한다」를 가른다 —
        # 사용자가 할 다음 행동이 다르다(재조사 ↔ 그 근거를 직접 읽어 보기).
        message = ("출처가 컨셉 서술과 입력값뿐 — 시장 근거 0건." if not 참고 else
                   f"이 칸을 확인해 준 근거가 없다 — 참고할 만한 조사 결과 {len(참고)}건은 있다.")
        reasons.append(_reason(
            "G1", name, message, 참고,
            cause=_cause(_CELL_SUBJECT.get(name), states, found=bool(참고))))

    # ── G4. 캔버스가 통째로 사용자 계획이다 ───────────────────────────────
    # 계획 칸이 전부 관측 미달이면 「시장이 확인해 준 것」이 하나도 없이 캔버스가 선다.
    stranded = [name for name in PLANNED_CELLS
                if (by_cell.get(name) or {}).get("status") not in _OBSERVED_STATUSES]
    if len(stranded) == len(PLANNED_CELLS):
        # 계획 칸은 성적표가 재는 대상이 아니다 — 갈래는 언제나 `UNMAPPED` 이다.
        reasons.append(_reason(
            "G4", None, "계획 5칸 전부 관측 미달 — 시장이 확인해 준 칸이 없다."))

    # ── G5. 수익 구조가 수요 근거를 안 물었다 ─────────────────────────────
    # 가격 자료만 보고 세운 수익 구조는 「받을 수 있는 값」이지 「살 사람이 있다」가 아니다.
    #
    # ⚠ **수요 근거는 캔버스 전체에서 센다. 수익원 칸만 보면 안 된다.**
    #   `validation.mapping` 이 근거 id·라벨을 기계로 확정하면서 `demand_evidence` 는
    #   `PAIN` 카드에서만 나오고 `PAIN` 은 **가치 제안 칸으로** 간다
    #   (`mapping.CLAIM_TYPE_CELL` — 정본은 `research2/harness/vocab.json`).
    #   그래서 수익원 칸의 라벨은 어떤 입력에서도 `price_analysis` 뿐이고, 자기 칸만 보면
    #   이 규칙은 **반증 불가능**해진다 — 무슨 자료를 모아도 항상 걸리고 `_CEILING` 에 의해
    #   BM 판정 상한이 영구 `CONDITIONAL`(PASS 도달 불가)이 된다.
    #   ⇒ 「수익 구조를 세울 만한 **수요 근거를 이 조사가 아예 못 찾았는가**」로 읽는다.
    #      `PAIN` 근거가 한 건이라도 있으면 이 규칙은 걷힌다. 사유의 `cell` 은 여전히
    #      결론이 서 있는 칸(`REVENUE_STREAMS`)을 지목한다.
    #   (선택지 (b) — `PAIN` 카드를 수익원 칸에도 붙이기 — 는 칸 표를 vocab 과 갈라놓기
    #    때문에 택하지 않았다. 갈래별 사유 `cause` 는 계획서 1-2 에서 붙인다)
    revenue = by_cell.get("REVENUE_STREAMS")
    demand_anywhere = any("demand_evidence" in (cell.get("sourceLabels") or [])
                          for cell in cells)
    if revenue is not None and not demand_anywhere:
        reasons.append(_reason(
            "G5", "REVENUE_STREAMS",
            "수요 근거 인용 0건 — 「받을 수 있는 값」은 「살 사람이 있다」가 아니다.",
            revenue.get("marketEvidenceIds"),
            # ⚠ G5 는 수익원 칸이 아니라 **수요 과목**이 갈래를 정한다. 규칙 자체가
            #   「이 조사가 수요 근거를 아예 못 찾았는가」를 묻기 때문이다.
            cause=_cause("DEMAND", states)))

    return reasons


#: 사유 코드 → 이 규칙이 허용하는 **가장 관대한** 판정.
_CEILING = {"G1": "REVISION_REQUIRED", "G4": "CONDITIONAL", "G5": "CONDITIONAL"}


def apply_decision(decision: str, reasons: list[dict]) -> str:
    """판정을 사유만큼 **내린다**. 올리지 않는다.

    이미 더 무거운 판정(법률 `BLOCKED` 등)이 서 있으면 그대로 둔다.
    """
    if decision not in _RANK:
        raise ValueError(f"알 수 없는 판정: {decision!r}")
    rank = _RANK[decision]
    for reason in reasons:
        ceiling = _CEILING.get(reason["code"])
        if ceiling is not None:
            rank = max(rank, _RANK[ceiling])
    for name, value in _RANK.items():
        if value == rank:
            return name
    raise AssertionError("도달할 수 없다")
