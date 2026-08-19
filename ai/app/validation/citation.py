# -*- coding: utf-8 -*-
"""인용 강제 — **LLM 0회.** 「확인됐다」고 쓰려면 무엇을 보고 그랬는지 대야 한다.

<b>왜 필요한가.</b> 프롬프트는 `source_labels` 는 강제하면서
(「content 가 비어 있지 않으면 source_labels 도 비어 있을 수 없다」) `market_evidence_ids`
는 **요구한 적이 없다** — 「실제 존재하는 id만 사용한다」는 제한이지 요구가 아니다.
그래서 모델이 `concept_snapshot`(사용자가 쓴 컨셉 서술문) 하나만 붙이고 근거는 0건인 채로
칸을 `VERIFIED` 라고 쓸 수 있고, 규칙상 위반이 아니다.

실측(2026-08-13, 프로젝트 3 HMR): 원장에 근거 17건이 있고 BM 에 전달까지 됐는데
캔버스가 인용한 것은 **0건**이었다. 그러면서 관측 3칸이 `VERIFIED` 였다.

이 층은 <b>고쳐 주지 않는다.</b> 인용을 지어낼 수는 없으므로, 대신 <b>사실대로 내린다</b> —
근거 없이 「확인됨」이라고 쓴 칸을 `UNVERIFIED` 로 강등하고 무엇이 없는지 적는다.
캔버스를 더 좋게 만드는 것이 아니라 **덜 거짓말하게** 만든다.
"""
from __future__ import annotations

from dataclasses import dataclass

from ..research.bm.contracts import CanvasStatus
from .gate import OBSERVED_CELLS, _MARKET_LABELS

#: 근거를 대야 하는 상태. 「계획(PLAN)」·「미확인(UNVERIFIED)」은 애초에 주장이 없다.
_CLAIMED = frozenset({"VERIFIED", "PARTIAL"})

_REASON = "시장 근거를 인용하지 않아 「확인됨」 주장을 유지할 수 없다."
_MISSING = "이 칸을 뒷받침하는 시장 근거 인용"


@dataclass(frozen=True)
class Correction:
    """무엇을 왜 내렸나. 한 칸에 하나."""

    cell: str
    was: str


def enforce(analysis):
    """`BMAnalysisResult` → (교정본, 교정 목록).

    관측 4칸 중 **시장 근거 없이 확인을 주장한** 칸을 `UNVERIFIED` 로 내린다.
    `concept_snapshot`·`execution_constraints` 는 근거로 세지 않는다 — 사용자 입력이다.

    원본을 바꾸지 않고 복사본을 돌려준다.
    """
    corrections: list[Correction] = []
    cells = []
    for item in analysis.canvas:
        cell_name = str(item.canvas_cell)
        if (cell_name in OBSERVED_CELLS
                and str(item.status) in _CLAIMED
                and not item.market_evidence_ids
                and not (_MARKET_LABELS & set(item.source_labels))):
            corrections.append(Correction(cell=cell_name, was=str(item.status)))
            # ⚠ enum 을 넣는다. `model_copy` 는 검증을 안 거치므로 평문 문자열을 넣으면
            #   직렬화가 `status.value` 에서 터진다.
            cells.append(item.model_copy(update={
                "status": CanvasStatus.UNVERIFIED,
                "reason": f"{item.reason} — {_REASON}".strip(" —"),
                "missing_evidence": list(dict.fromkeys([*item.missing_evidence, _MISSING])),
            }))
        else:
            cells.append(item)
    if not corrections:
        return analysis, []
    return analysis.model_copy(update={"canvas": cells}), corrections
