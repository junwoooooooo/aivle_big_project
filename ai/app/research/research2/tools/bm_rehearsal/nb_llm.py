# -*- coding: utf-8 -*-
"""BM 완주 하네스 — **LLM 호출 포함** (판 ㉜-b ②). 노트북 셀 10·12·14 **전체 발췌**.

⚠ 우리 코드가 아니다. 한 글자도 고치지 않는다. `BM_MODEL` 만 환경변수로 고르는데,
   그것은 노트북 자체가 `os.getenv` 로 열어 둔 손잡이다(코드 수정이 아니다).
"""
from __future__ import annotations
import json, os, re
from typing import Any
from openai import AsyncOpenAI
from nb_cells import *                                            # noqa: F401,F403
from nb_cells import (BMAnalysisResult, BMCanvasItem, CanvasCell, CanvasStatus,
                      ResolvedBMInput, BMAnalysisInput, LegalContext,
                      resolve_bm_input)

BM_MODEL = os.getenv("BM_MODEL", "gpt-5.6-terra")
_client = None


def get_openai_client() -> AsyncOpenAI:
    global _client
    if _client is None:
        _client = AsyncOpenAI(api_key=os.environ["OPENAI_API_KEY"], max_retries=0)
    return _client


# BM 핵심 판정 — 법률 데이터는 입력하지 않는다

BM_ANALYSIS_PROMPT = """
너는 범용 상품·서비스의 비즈니스 모델 검증자다.

새로운 사업모델을 설계하거나 새로운 시장 가설을 만들지 않는다.
입력으로 제공된 concept_snapshot, market_join_data, execution_constraints에
존재하는 정보만 Business Model Canvas에 매핑하고 판정한다.

법률·규제를 검색하거나 추정하거나 판단하지 않는다.
입력에 없는 가격, 수익모델, 채널, 경쟁사, 파트너, 비용 숫자를 새로 만들지 않는다.

[시장조사 결과로 검증하는 영역]

1. CUSTOMER_SEGMENTS
- 시장분석의 타겟 고객과 모집단 근거를 사용한다.
- 고객 세그먼트가 시장 근거와 일치하는지 판정한다.

2. VALUE_PROPOSITIONS
- 문제 검증 결과와 경쟁사·대체재 비교 결과를 사용한다.
- 고객 문제가 실제로 확인되는지,
  경쟁·대안 대비 차별적 가치가 성립하는지 판정한다.
- 경쟁·대안은 별도의 Canvas 칸으로 만들지 않는다.

3. REVENUE_STREAMS
- 앞 단계에서 확정된 수익모델과 가격을 그대로 사용한다.
- 가격 판정, TAM/SAM/SOM, 성장 및 매출 관련 시장 근거와
  일관되는지 판정한다.
- 새로운 가격이나 수익모델을 제안하지 않는다.

4. CHANNELS
- 입력에 채널 정보가 있으면 해당 내용을 Canvas에 정리한다.
- 관련 시장 근거가 존재하는 경우에만 참고하여 적합성을 확인한다.
- 입력에 채널 정보가 없으면 content=[]로 두고 UNVERIFIED로 표시할 수 있다.
- 새로운 채널을 임의로 제안하지 않는다.
- 채널 정보 부족만으로 market_fit_status 또는 consistency_status를 낮추지 않는다.

[계획으로 정리하는 영역]

계획 영역은 입력에 명시적으로 존재하는 내용만 사용한다.
입력에 없으면 content=[]로 두며 새로운 활동·자원·고객관계·파트너·비용을 제안하지 않는다.
PLAN은 반드시 content가 있어야 한다는 뜻이 아니며, 정보가 없으면 content=[]로 유지한다.

5. CUSTOMER_RELATIONSHIPS
- concept_snapshot 또는 execution_constraints에 명시된 고객 관계 방식만 정리한다.
- 입력에 고객 관계 정보가 없으면 임의로 보완하지 않고 content=[]로 둔다.
- 시장조사 근거 유무와 관계없이 실행계획 수준이면 PLAN으로 표시한다.

6. KEY_ACTIVITIES
- concept_snapshot 또는 execution_constraints에 명시된 활동만 정리한다.
- 입력에 활동이 명시되지 않으면 content=[]로 둔다.
- 새로운 활동을 추론하거나 제안하지 않는다.
- PLAN으로 표시한다.

7. KEY_RESOURCES
- concept_snapshot 또는 execution_constraints에 명시된 자원만 정리한다.
- 입력에 자원이 명시되지 않으면 content=[]로 둔다.
- 새로운 자원을 추론하거나 제안하지 않는다.
- PLAN으로 표시한다.

8. COST_STRUCTURE
- execution_constraints와 입력에 명시된 비용 정보만 사용한다.
- 입력에 없는 비용 항목, 원가 또는 비용 숫자를 추론하거나 제안하지 않는다.
- 예산·기간·비용 정보가 전혀 없으면 content=[]로 두고 PLAN으로 표시한다.

9. KEY_PARTNERS
- 입력 또는 시장분석에 실제 파트너 정보가 있을 때만 작성한다.
- 근거가 없으면 content=[]로 두고 UNVERIFIED로 표시한다.
- 입력에 없는 파트너를 추론하거나 제안하지 않는다.
- KEY_PARTNERS가 비어 있다는 사실만으로 market_fit_status 또는
  consistency_status를 PARTIAL/FAIL로 낮추지 않는다.
- 해당 BM의 실행에 핵심 파트너가 반드시 필요한데도 정보가 없을 때만
  내부 일관성 문제로 판단한다.

[상태 기준]

VERIFIED:
시장 또는 입력 근거로 확인됨.

PARTIAL:
일부 근거는 있으나 충분하지 않음.

UNVERIFIED:
판정할 근거가 없음.

PLAN:
시장 검증 대상이 아니라 현재 사업 실행계획 수준의 내용임.

BLOCKED:
현재 BM 구조에서 실행이 불가능한 명확한 문제가 있음.

market_fit_status:
- PASS: 고객·문제·가치·수익이 시장 근거와 전반적으로 일치한다.
- PARTIAL: 핵심 시장 근거가 일부 부족하다.
- FAIL: BM의 핵심 주장과 시장 근거가 명확히 충돌한다.
- 채널 정보의 부족만으로 market_fit_status를 낮추지 않는다.

consistency_status:
- PASS: 고객→가치→수익의 핵심 구조와 실행구조에 중대한 모순이 없다.
- PARTIAL: 핵심 요소 간 일부 연결이 약하거나 미확인이다.
- FAIL: BM 핵심 요소 사이에 중대한 모순이 있다.
- 채널 정보가 없거나 부족하다는 사실만으로 consistency_status를 낮추지 않는다.
- 단, 입력에 명시된 채널이 수익모델 또는 고객 접근 방식과 명백히 충돌하는 경우에는
  내부 일관성 문제로 판단할 수 있다.


source_labels에는 각 Canvas content의 직접 출처를 다음 값 중 하나 이상으로 기록한다.
- concept_snapshot
- market_size
- growth_rate
- competitor_analysis
- price_analysis
- demand_evidence
- execution_constraints
content가 비어 있지 않으면 source_labels도 비어 있을 수 없다.
목록에 없는 출처 라벨을 만들지 않는다.

market_evidence_ids에는 market_join_data.evidence_list에 실제 존재하는 id만 사용한다.
""".strip()


ALLOWED_CANVAS_SOURCE_LABELS = {
    "concept_snapshot",
    "market_size",
    "growth_rate",
    "competitor_analysis",
    "price_analysis",
    "demand_evidence",
    "execution_constraints",
}


def validate_canvas_source_labels(
    result: BMAnalysisResult,
) -> BMAnalysisResult:
    """허용된 입력 출처만 남기고 출처 없는 Canvas 내용은 제거한다."""
    validated_canvas = []
    for item in result.canvas:
        labels = list(dict.fromkeys(
            label
            for label in item.source_labels
            if label in ALLOWED_CANVAS_SOURCE_LABELS
        ))
        update: dict[str, Any] = {"source_labels": labels}
        if item.content and not labels:
            update.update(
                content=[],
                market_evidence_ids=[],
                status=CanvasStatus.UNVERIFIED,
                reason="허용된 입력 출처 라벨이 없어 Canvas 내용을 제거했습니다.",
                missing_evidence=list(dict.fromkeys([
                    *item.missing_evidence,
                    "Canvas 내용의 입력 출처 라벨",
                ])),
            )
        validated_canvas.append(item.model_copy(update=update))
    return result.model_copy(update={"canvas": validated_canvas})


def validate_market_evidence_ids(
    result: BMAnalysisResult,
    market_data: MarketJoinData,
) -> BMAnalysisResult:
    allowed_ids = {
        str(item["id"])
        for item in market_data.evidence_list
        if item.get("id") is not None
    }

    validated_canvas = []
    for item in result.canvas:
        validated_ids = [
            evidence_id
            for evidence_id in item.market_evidence_ids
            if evidence_id in allowed_ids
        ]
        validated_canvas.append(
            item.model_copy(
                update={"market_evidence_ids": validated_ids}
            )
        )

    return result.model_copy(update={"canvas": validated_canvas})


async def run_bm_analysis(
    *,
    resolved: ResolvedBMInput,
    client: AsyncOpenAI | None = None,
    model: str = BM_MODEL,
) -> BMAnalysisResult:
    api = client or get_openai_client()
    payload = resolved.model_dump(mode="json")

    response = await api.responses.parse(
        model=model,
        input=[
            {"role": "system", "content": BM_ANALYSIS_PROMPT},
            {
                "role": "user",
                "content": json.dumps(payload, ensure_ascii=False),
            },
        ],
        text_format=BMAnalysisResult,
    )

    if response.output_parsed is None:
        raise RuntimeError("BM 분석 결과를 구조화된 형식으로 받지 못했습니다.")

    result = response.output_parsed
    if result.concept_id != resolved.concept_id:
        raise ValueError("BM 분석 결과의 concept_id가 입력과 다릅니다.")

    result = validate_market_evidence_ids(
        result,
        resolved.market_join_data,
    )
    result = validate_canvas_source_labels(result)
    return result



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



async def run_bm_pipeline_flow(
    bm_input: BMAnalysisInput,
    *,
    client: AsyncOpenAI | None = None,
    model: str = BM_MODEL,
) -> dict[str, Any]:
    resolved = resolve_bm_input(bm_input)

    # ResolvedBMInput에는 법률 정보를 넣지 않으므로 핵심 모델은 시장 데이터만 사용한다.
    bm_analysis = await run_bm_analysis(
        resolved=resolved,
        client=client,
        model=model,
    )

    final_result = finalize_bm_analysis(
        bm_analysis=bm_analysis,
        resolved=resolved,
        legal_context=bm_input.legal_context,
    )

    return {
        "bm_analysis": bm_analysis,
        "final_result": final_result,
    }

