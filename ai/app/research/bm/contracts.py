# -*- coding: utf-8 -*-
"""BM 분석의 데이터 계약 — 노트북 셀 6 을 그대로 옮긴 것. **값을 바꾸지 않는다.**

⚠ `market_size`·`growth_rate`·`price_analysis` 는 `extra="forbid"` 다. 여기에 없는 칸을
   끼워 넣으려다 판 ㉜ 이 `price_base` 에 문자열을 넣어 죽었다 — 성격 표시는
   `market_size_calculation` 으로 간다(`service/bm_adapter.PRICE_BASE_LABEL`).

⚠ `evidence_list[].id` 다. `card_id` 가 아니다. 이 키가 어긋나면
   `analyze.validate_market_evidence_ids` 가 허용 id 집합을 **빈 것으로** 만들고
   모든 `market_evidence_ids` 가 **조용히** 탈락한다.
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
    #: 채널 절 근거 — 2026-08-15 신설. **기본값이 있어 옛 입력이 그대로 통과한다.**
    #: 나머지 라벨은 전부 이 모델의 필드 이름인데 채널만 자리가 없어, 채널 칸이 근거를
    #: 붙일 라벨을 구조적으로 못 만들었다(`bm/prompt.py` 머리말의 예외 기록 참조).
    #: ⚠ 사본이 `research2/service/bm_adapter.py` 에 있다 — `test_bm_contract_parity.py` 가 대조한다.
    channel_analysis: list[dict[str, Any]] = Field(default_factory=list)


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


class ResolvedBMInput(BaseModel):
    """노트북 셀 8. **법률 정보를 담지 않는다** — 핵심 판정은 시장 데이터만 본다."""

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
