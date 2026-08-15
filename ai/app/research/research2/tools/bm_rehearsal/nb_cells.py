# -*- coding: utf-8 -*-
"""BM 리허설 하네스 — **그쪽 노트북 셀을 발췌해 그대로 태운다** (판 ㉜-b ①). LLM 0회.

⚠ **이 파일은 우리 코드가 아니다.** `bm_pipeline_v1_final_actual_input.ipynb` 의
   셀 6(스키마) · 8(정규화) · 10(검증 함수부) · 16(입력 생성)을 **그대로 발췌**한 것이다.
   한 글자도 고치지 않는다 — 고치면 「그쪽 파이프라인을 태웠다」가 거짓이 된다.
   LLM 호출부(`run_bm_analysis`)는 뺐다. ①은 **호출 직전까지**를 재는 단계다.

발췌 원본: bm_pipeline_v1_final_actual_input.ipynb (2026-08-09 수령)
"""
from __future__ import annotations
from enum import StrEnum
from typing import Any, Literal
from pydantic import BaseModel, Field, model_validator

class ConceptSnapshot(BaseModel):
    concept_name: str | None = None
    target_customer: str | None = None
    problem: str | None = None
    solution: str | None = None
    core_value: str | None = None
    differentiation: list[str] = Field(default_factory=list)
    revenue_model: str | None = None
    channel: str | list[str] | None = None

    model_config = {"extra": "allow"}


class MarketSizeData(BaseModel):
    tam: float | None = None
    sam: float | None = None
    som: float | None = None
    unit: str | None = None

    model_config = {"extra": "forbid"}


class GrowthRateData(BaseModel):
    value: float | None = None
    unit: str | None = None

    model_config = {"extra": "forbid"}


class PriceAnalysisData(BaseModel):
    price_min: float | None = None
    price_base: float | None = None
    price_max: float | None = None
    currency: str | None = None

    model_config = {"extra": "forbid"}


class MarketJoinData(BaseModel):
    concept_id: str
    concept_snapshot: ConceptSnapshot
    market_size: MarketSizeData
    growth_rate: GrowthRateData
    competitor_analysis: list[dict[str, Any]]
    price_analysis: PriceAnalysisData
    demand_evidence: list[dict[str, Any]]
    market_size_calculation: dict[str, Any]
    missing_items: list[dict[str, Any]] = Field(default_factory=list)
    evidence_list: list[dict[str, Any]] = Field(default_factory=list)


# BM 분석 요청에 포함되는 선택적 법률·규제 입력 계약
LegalStatus = Literal[
    "PASS",
    "CONDITIONAL",
    "BLOCKED",
    "UNVERIFIED",
]


class LegalContext(BaseModel):
    concept_id: str
    status: LegalStatus = "UNVERIFIED"
    summary: str = ""
    risks: list[str] = Field(default_factory=list)
    required_actions: list[str] = Field(default_factory=list)


class BMAnalysisInput(BaseModel):
    concept_id: str
    market_join_data: MarketJoinData
    legal_context: LegalContext | None = None
    execution_constraints: dict[str, Any] = Field(default_factory=dict)

    @model_validator(mode="after")
    def validate_concept_ids(self):
        ids = {
            self.concept_id,
            self.market_join_data.concept_id,
        }

        if self.legal_context is not None:
            ids.add(self.legal_context.concept_id)

        if len(ids) != 1:
            raise ValueError(
                "BM·시장분석·법률 결과의 concept_id가 일치하지 않습니다."
            )

        return self


class CanvasCell(StrEnum):
    CUSTOMER_SEGMENTS = "CUSTOMER_SEGMENTS"
    VALUE_PROPOSITIONS = "VALUE_PROPOSITIONS"
    CHANNELS = "CHANNELS"
    CUSTOMER_RELATIONSHIPS = "CUSTOMER_RELATIONSHIPS"
    REVENUE_STREAMS = "REVENUE_STREAMS"
    KEY_RESOURCES = "KEY_RESOURCES"
    KEY_ACTIVITIES = "KEY_ACTIVITIES"
    KEY_PARTNERS = "KEY_PARTNERS"
    COST_STRUCTURE = "COST_STRUCTURE"


class CanvasStatus(StrEnum):
    VERIFIED = "VERIFIED"
    PARTIAL = "PARTIAL"
    UNVERIFIED = "UNVERIFIED"
    PLAN = "PLAN"
    BLOCKED = "BLOCKED"



class BMCanvasItem(BaseModel):
    canvas_cell: CanvasCell
    content: list[str]
    source_labels: list[str] = Field(default_factory=list)
    market_evidence_ids: list[str] = Field(default_factory=list)
    status: CanvasStatus
    reason: str
    missing_evidence: list[str] = Field(default_factory=list)


class BMAnalysisResult(BaseModel):
    concept_id: str
    concept_name: str
    canvas: list[BMCanvasItem]
    market_fit_status: Literal["PASS", "PARTIAL", "FAIL"]
    consistency_status: Literal["PASS", "PARTIAL", "FAIL"]
    market_fit_summary: str
    consistency_summary: str
    strengths: list[str] = Field(default_factory=list)
    weaknesses: list[str] = Field(default_factory=list)
    risks: list[str] = Field(default_factory=list)
    @model_validator(mode="after")
    def validate_canvas(self):
        actual = [item.canvas_cell for item in self.canvas]
        expected = set(CanvasCell)
        if len(actual) != 9 or set(actual) != expected:
            raise ValueError("BM Canvas 9개 칸을 각각 정확히 한 번 포함해야 합니다.")
        return self


class BMDecision(StrEnum):
    PASS = "PASS"
    CONDITIONAL = "CONDITIONAL"
    REVISION_REQUIRED = "REVISION_REQUIRED"
    BLOCKED = "BLOCKED"


class BMFinalResult(BaseModel):
    concept_id: str
    decision: BMDecision
    confidence: Literal["HIGH", "MEDIUM"]
    summary: str
    canvas: list[BMCanvasItem]
    strengths: list[str]
    weaknesses: list[str]
    risks: list[str]
    market_fit_summary: str
    consistency_summary: str
    legal_context_used: bool
    legal_status: LegalStatus
    legal_summary: str = ""
    legal_risks: list[str] = Field(default_factory=list)
    required_legal_actions: list[str] = Field(default_factory=list)


class BMFinancialHandoff(BaseModel):
    concept_id: str
    revenue_model: str | None = None
    price_min: float | None = None
    price_base: float | None = None
    price_max: float | None = None
    tam: float | None = None
    sam: float | None = None
    som: float | None = None
    market_growth_rate: float | None = None
    expected_revenue: float | None = None
    unit_cost: float | None = None
    fixed_cost_items: list[dict[str, Any]] = Field(default_factory=list)
    variable_cost_items: list[dict[str, Any]] = Field(default_factory=list)
    missing_financial_inputs: list[str] = Field(default_factory=list)
    handoff_status: Literal["READY", "PARTIAL", "BLOCKED"]



# BM 입력 정규화

class ResolvedBMInput(BaseModel):
    concept_id: str
    concept_name: str
    target_customer: str
    problem: str
    solution: str
    core_value: str
    differentiation: list[str]
    revenue_model: str | None = None
    market_join_data: MarketJoinData
    execution_constraints: dict[str, Any]


def resolve_bm_input(
    source: BMAnalysisInput,
) -> ResolvedBMInput:
    concept = source.market_join_data.concept_snapshot
    return ResolvedBMInput(
        concept_id=source.concept_id,
        concept_name=concept.concept_name or source.concept_id,
        target_customer=concept.target_customer or "",
        problem=concept.problem or "",
        solution=concept.solution or "",
        core_value=concept.core_value or "",
        differentiation=concept.differentiation,
        revenue_model=concept.revenue_model,
        market_join_data=source.market_join_data,
        execution_constraints=source.execution_constraints,
    )


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




def create_bm_analysis_input(
    *,
    market_data: MarketJoinData | dict[str, Any],
    legal_data: LegalContext | dict[str, Any] | None = None,
    execution_constraints: dict[str, Any] | None = None,
) -> BMAnalysisInput:
    """앞 단계의 실제 출력으로 BMAnalysisInput을 생성한다."""
    if isinstance(market_data, MarketJoinData):
        market_join_data = market_data
    else:
        if isinstance(market_data, BaseModel):
            market_data = market_data.model_dump(mode="python")
        market_join_data = MarketJoinData.model_validate(market_data)

    if legal_data is None:
        legal_context = None
    elif isinstance(legal_data, LegalContext):
        legal_context = legal_data
    else:
        if isinstance(legal_data, BaseModel):
            legal_data = legal_data.model_dump(mode="python")
        legal_context = LegalContext.model_validate(legal_data)

    return BMAnalysisInput(
        concept_id=market_join_data.concept_id,
        market_join_data=market_join_data,
        legal_context=legal_context,
        execution_constraints=dict(execution_constraints or {}),
    )
