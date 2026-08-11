# -*- coding: utf-8 -*-
"""최종 BM 판정 — 노트북 셀 12. **LLM 0회.**

법률 결과는 **핵심 시장 판정에 들어가지 않고** 최종 상태·설명 보완에만 쓴다.
그 분리가 이 함수의 존재 이유다 — 섞으면 「시장 근거가 부족한 것」과
「법이 막는 것」이 한 판정으로 뭉개진다.
"""
from __future__ import annotations

from .contracts import (
    BMAnalysisResult,
    BMDecision,
    BMFinalResult,
    CanvasStatus,
    LegalContext,
    ResolvedBMInput,
)


def finalize_bm_analysis(
    *,
    bm_analysis: BMAnalysisResult,
    resolved: ResolvedBMInput,
    legal_context: LegalContext | None,
) -> BMFinalResult:
    if bm_analysis.concept_id != resolved.concept_id:
        raise ValueError(
            "BM 분석 결과와 정규화 입력의 concept_id가 다릅니다."
        )

    statuses = {
        item.status
        for item in bm_analysis.canvas
    }

    # BM 자체 판정
    if (
        bm_analysis.market_fit_status == "FAIL"
        or bm_analysis.consistency_status == "FAIL"
        or CanvasStatus.BLOCKED in statuses
    ):
        decision = BMDecision.REVISION_REQUIRED
        summary = "시장 적합성 또는 BM 내부 구조에 수정이 필요합니다."
    elif (
        bm_analysis.market_fit_status == "PARTIAL"
        or bm_analysis.consistency_status == "PARTIAL"
    ):
        decision = BMDecision.CONDITIONAL
        summary = "일부 시장 근거 또는 BM 구조의 추가 검증이 필요합니다."
    else:
        decision = BMDecision.PASS
        summary = "현재 시장 근거 범위에서 BM 타당성이 확인되었습니다."

    # 법률 결과는 최종 상태와 설명 보완에만 사용
    if legal_context is None:
        legal_status = "UNVERIFIED"
        legal_summary = "법률·규제 결과가 제공되지 않았습니다."
        legal_risks = []
        required_actions = []
    else:
        legal_status = legal_context.status
        legal_summary = legal_context.summary
        legal_risks = legal_context.risks
        required_actions = legal_context.required_actions

        if legal_status == "BLOCKED":
            decision = BMDecision.BLOCKED
            summary = "법률·규제 결과상 현재 BM 실행이 제한됩니다."
        elif legal_status == "CONDITIONAL":
            if decision == BMDecision.PASS:
                decision = BMDecision.CONDITIONAL
                summary = "법률·규제 조건을 충족한 후 실행할 수 있습니다."
            else:
                summary += " 추가로 법률·규제 조건의 확인이 필요합니다."

    # confidence는 법률 결과만이 아니라 전체 BM 판정의 신뢰도를 의미한다.
    if legal_context is None:
        confidence = "MEDIUM"
    elif legal_status == "BLOCKED":
        confidence = "HIGH"
    elif (
        bm_analysis.market_fit_status == "PASS"
        and bm_analysis.consistency_status == "PASS"
        and legal_status in {"PASS", "CONDITIONAL"}
    ):
        confidence = "HIGH"
    elif legal_status == "UNVERIFIED":
        confidence = "MEDIUM"
    else:
        confidence = "MEDIUM"

    return BMFinalResult(
        concept_id=resolved.concept_id,
        decision=decision,
        confidence=confidence,
        summary=summary,
        canvas=bm_analysis.canvas,
        strengths=bm_analysis.strengths,
        weaknesses=bm_analysis.weaknesses,
        risks=bm_analysis.risks,
        market_fit_summary=bm_analysis.market_fit_summary,
        consistency_summary=bm_analysis.consistency_summary,
        legal_context_used=legal_context is not None,
        legal_status=legal_status,
        legal_summary=legal_summary,
        legal_risks=legal_risks,
        required_legal_actions=required_actions,
    )
