"""V2 structured provider의 MOCK / REPLAY / LIVE 구현."""

from __future__ import annotations

import hashlib
import asyncio
import json
import time
from abc import ABC, abstractmethod
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from pydantic import Field

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.concept_candidate.models import (
    ConceptCandidateDraft, ConceptCandidateResult, SemanticField, ValueSemantics,
)

from .adapters import CurrentLegalAdapter
from .models import (
    CanonicalSeed, DesignSpaceAnalysis, LegalReview, LegalRoute, PortfolioPlan, ProviderMode,
    ProviderUsage, StrictModel,
)


PLAN_PROMPT_VERSION = "concept-portfolio-plan-v2.0"
CANDIDATE_PROMPT_VERSION = "concept-portfolio-candidate-v2.0"


class PlanPool(StrictModel):
    plans: list[PortfolioPlan] = Field(min_length=1, max_length=8)


class PortfolioProvider(ABC):
    @abstractmethod
    async def plan_pool(self, seed: CanonicalSeed, design: DesignSpaceAnalysis, pool_size: int) -> list[PortfolioPlan]: ...

    @abstractmethod
    async def expand(self, seed: CanonicalSeed, plan: PortfolioPlan, candidate_index: int) -> ConceptCandidateResult: ...

    @abstractmethod
    async def review_legal(self, candidate_id: str, candidate: ConceptCandidateResult,
                           fixture_name: str) -> LegalReview: ...

    @abstractmethod
    async def redesign(self, seed: CanonicalSeed, plan: PortfolioPlan, candidate: ConceptCandidateResult,
                       requirements: list[str], candidate_index: int) -> ConceptCandidateResult: ...


MECHANICS = [
    ("주간 소분 구독", "수요예측 기반 정기 소분", "직접 구독", "지역 소분센터", "정기배송"),
    ("동네 공동구매", "이웃 수요를 묶는 공동주문", "공동구매 수수료", "지역 식자재점 제휴", "거점수령"),
    ("레시피 마켓", "남은 재료 기반 레시피·재료 번들", "번들 판매", "셰프·생산자 큐레이션", "온디맨드 배송"),
    ("냉장고 코치", "보유 재료 인식과 보충 추천", "프리미엄 앱", "리테일 데이터 제휴", "매장 픽업"),
    ("기업 복지 키트", "직장 단위 맞춤 식재료 키트", "B2B 계약", "기업·급식 파트너", "사무실 일괄배송"),
    ("생산자 예약장터", "생산 전 수요예약과 직거래", "거래 중개 수수료", "지역 생산자 네트워크", "생산자 직배송"),
    ("무인 소분 스테이션", "생활권 무인 소분·회수", "사용량 기반 결제", "공간·물류 사업자", "무인함 수령"),
    ("전문가 식단 동행", "영양 전문가 검수 식단과 구매", "상담 결합 구독", "자격 보유 전문가", "예약 배송"),
]


def _locked(seed: CanonicalSeed) -> dict[str, str]:
    return {item.fieldKey: item.value for item in seed.fields
            if item.decisionState == "LOCKED" and item.value.strip()}


def _plan(seed: CanonicalSeed, index: int, mechanics_index: int | None = None) -> PortfolioPlan:
    name, mechanism, commercial, partner, fulfillment = MECHANICS[mechanics_index if mechanics_index is not None else index]
    locks = _locked(seed)
    return PortfolioPlan(
        planId=f"P{index + 1}", title=name, oneLineConcept=f"{seed.targetUsers}를 위한 {name}",
        coreMechanism=mechanism, customerInteraction=f"{name} 전용 모바일·현장 접점",
        valueDelivery=f"{seed.problem}을 줄이는 {mechanism}", operatingApproach=mechanism,
        partnerApproach=partner, transactionApproach=commercial, commercialApproach=commercial,
        fulfillmentApproach=fulfillment,
        differentiatingMechanics=[mechanism, partner, commercial, fulfillment],
        preservedAnchors={"problem": seed.problem, "targetUsers": seed.targetUsers,
                          "ideaOverview": seed.ideaOverview}, preservedLocks=locks,
        mainChanges=["solutionMechanism", "operatingModel", "partnerModel"],
        secondaryChanges=["transactionFlow", "fulfillment"], legalRiskHints=[],
        reasonForPortfolioRole=f"사업 작동방식 {index + 1}의 독립 대안",
    )


def _semantics(seed: CanonicalSeed) -> list[ValueSemantics]:
    locked = _locked(seed)
    result = []
    for key in SemanticField.__args__:
        if key in ("preMarketSomShareHypothesis", "preMarketSomHypothesis"):
            result.append(ValueSemantics(fieldKey=key, source="AI_HYPOTHESIS", authority="OPEN", decision="PROPOSED"))
        elif key in locked or key in {"conceptDefinition", "problemScenario", "targetUsers"}:
            result.append(ValueSemantics(fieldKey=key, source="USER_INPUT", authority="LOCKED", decision="ACCEPTED"))
        else:
            result.append(ValueSemantics(fieldKey=key, source="CONCEPT_GENERATED", authority="REVIEWABLE", decision="PROPOSED"))
    return result


def _candidate(seed: CanonicalSeed, plan: PortfolioPlan, index: int, *, redesigned: bool = False) -> ConceptCandidateResult:
    locks = _locked(seed)
    get = lambda key, default: locks.get(key) or default
    mechanism = plan.coreMechanism + ("(인허가 보유 파트너가 제공)" if redesigned else "")
    values = dict(
        schemaVersion="2.0", generationStrategy="REFINE", candidateIndex=index, originalCandidate=False,
        conceptName=plan.title, conceptDefinition=plan.oneLineConcept, introduction=plan.reasonForPortfolioRole,
        coreValue=plan.valueDelivery, targetUsers=seed.targetUsers, industryCategory="푸드테크",
        researchScope="대한민국 식재료 구매·소비", targetRegion=get("targetRegion", "대한민국"),
        revenueModel=get("revenueModel", plan.commercialApproach), price=get("price", "실험 후 확정"),
        channels=get("channels", plan.customerInteraction),
        differentiators=get("differentiators", ", ".join(plan.differentiatingMechanics)),
        preMarketSomShareHypothesis={"targetSharePercent": 1.0 + index, "horizonYears": 3,
                                     "rationale": "시장 분석 전 검증할 가설", "assumptions": ["초기 가설"]},
        preMarketSomHypothesis={"amount": float(100_000_000 * index), "currency": "KRW", "period": "연간",
                                "calculationBasis": "시장 분석 전 임시 산식", "assumptions": ["초기 가설"],
                                "confidence": "LOW"},
        problemScenario=seed.problem, solutionMechanism=mechanism,
        featureSet=[plan.coreMechanism, plan.fulfillmentApproach], actorRoles=["사용자", "운영사", "파트너"],
        platformRole="거래·운영 조정", operatingModel=plan.operatingApproach,
        partnerModel=plan.partnerApproach, providerRole="서비스 운영과 품질 통제",
        sellerRole="계약에 따른 상품 판매", intermediaryRole="해당 시 거래 연결",
        transactionFlow=["사용자 요청", plan.transactionApproach, plan.fulfillmentApproach],
        paymentFlow=["사용자 결제", plan.commercialApproach, "파트너 정산"],
        personalDataUsage=["주문 이행을 위한 최소 정보"],
        physicalActivities=[plan.fulfillmentApproach],
        partnerRequirements=["품질 기준 준수"] + (["필요 인허가 보유"] if redesigned else []),
        qualificationRequirements=["해당 활동에 필요한 자격"] if redesigned else [],
        advertisingClaims=["검증 가능한 편의성"], constraintCompliance=list(plan.preservedLocks),
        valueSemantics=_semantics(seed),
    )
    return ConceptCandidateResult.model_validate(values)


class MockPortfolioProvider(PortfolioProvider):
    async def plan_pool(self, seed: CanonicalSeed, design: DesignSpaceAnalysis, pool_size: int) -> list[PortfolioPlan]:
        count = min(pool_size, max(1, design.diversityCapacity + 2))
        plans = [_plan(seed, index, index % len(MECHANICS)) for index in range(count)]
        if seed.fixtureName == "duplicate_plans" and len(plans) >= 2:
            plans[1] = _plan(seed, 1, 0)
        return plans

    async def expand(self, seed: CanonicalSeed, plan: PortfolioPlan, candidate_index: int) -> ConceptCandidateResult:
        return _candidate(seed, plan, candidate_index)

    async def review_legal(self, candidate_id: str, candidate: ConceptCandidateResult,
                           fixture_name: str) -> LegalReview:
        first = candidate_id in {"C1", "C1-R1", "C1-REPLAN"}
        if fixture_name == "legal_redesign" and candidate_id == "C1":
            return LegalReview(candidateId=candidate_id, route=LegalRoute.REDESIGN_WITHIN_LINEAGE,
                               productionStatus="REDESIGNABLE",
                               sourceStatus="MOCK_OFFICIAL_EVIDENCE", safeSummary="동일 lineage 내 통제 보완이 필요합니다.",
                               redesignRequirements=["인허가 보유 파트너로 제공 주체를 제한"])
        if fixture_name == "legal_replan" and candidate_id == "C1":
            return LegalReview(candidateId=candidate_id, route=LegalRoute.REPLAN_REQUIRED,
                               productionStatus="REJECTED",
                               sourceStatus="MOCK_OFFICIAL_EVIDENCE", safeSummary="핵심 거래 구조를 유지할 수 없습니다.",
                               prohibitedVariants=[candidate.solutionMechanism])
        if fixture_name == "lock_legal_conflict" and candidate_id == "C1":
            return LegalReview(candidateId=candidate_id, route=LegalRoute.NEEDS_INPUT,
                               productionStatus="NEEDS_FACTS",
                               sourceStatus="MOCK_OFFICIAL_EVIDENCE", safeSummary="법률 통제와 사용자 LOCK이 충돌합니다.",
                               conflictingLock="channels", currentValue=candidate.channels,
                               requiredLegalChange="자격 보유 파트너 채널로 변경", reason="LOCKED 채널은 엔진이 변경할 수 없습니다.",
                               possibleUserAction="채널 LOCK을 해제하거나 자격 보유 파트너 채널을 확정하세요.")
        return LegalReview(candidateId=candidate_id, route=LegalRoute.ACCEPT,
                           productionStatus="IMPLEMENTABLE_WITH_CONTROLS",
                           sourceStatus="MOCK_OFFICIAL_EVIDENCE", safeSummary="구조화된 MOCK 공식근거 계약상 수용 가능합니다.",
                           requiredControls=["표시·거래 조건을 명확히 고지"],
                           officialEvidenceReferences=[{"sourceType": "OFFICIAL_LAW", "officialIdentifier": "MOCK-LAW",
                                                        "articleReference": "제1조", "officialSourceUri": "https://www.law.go.kr/mock",
                                                        "contentHash": "sha256:" + "0" * 64}])

    async def redesign(self, seed: CanonicalSeed, plan: PortfolioPlan, candidate: ConceptCandidateResult,
                       requirements: list[str], candidate_index: int) -> ConceptCandidateResult:
        return _candidate(seed, plan, candidate_index, redesigned=True)


class LivePortfolioProvider(PortfolioProvider):
    async def plan_pool(self, seed: CanonicalSeed, design: DesignSpaceAnalysis, pool_size: int) -> list[PortfolioPlan]:
        prompt = """같은 opportunity를 해결하되 business mechanics가 실질적으로 다른 동적 PortfolioPlan pool을 만든다.
고정 lens나 정확히 5개를 강제하지 않는다. hard lock과 semantic anchor를 그대로 보존하고 strict schema만 반환한다."""
        raw = await execute_structured_prompt(
            prompt, json.dumps({"promptVersion": PLAN_PROMPT_VERSION, "seed": seed.model_dump(mode="json"),
                                "designSpace": design.model_dump(mode="json"), "poolSize": pool_size},
                               ensure_ascii=False, sort_keys=True),
            response_schema=PlanPool.model_json_schema(), schema_name="concept_portfolio_plan_v2",
            task_type="CONCEPT_PORTFOLIO_V2_PLAN")
        return PlanPool.model_validate(raw).plans

    async def expand(self, seed: CanonicalSeed, plan: PortfolioPlan, candidate_index: int) -> ConceptCandidateResult:
        prompt = """통과된 PortfolioPlan 하나를 현행 ConceptCandidateDraft 계약으로 확장한다.
plan의 business mechanics와 사용자 lock/anchor를 보존한다. 법률 결론을 만들지 말고 strict schema만 반환한다."""
        raw = await execute_structured_prompt(
            prompt, json.dumps({"promptVersion": CANDIDATE_PROMPT_VERSION, "seed": seed.model_dump(mode="json"),
                                "plan": plan.model_dump(mode="json")}, ensure_ascii=False, sort_keys=True),
            response_schema=ConceptCandidateDraft.model_json_schema(), schema_name="concept_portfolio_candidate_v2",
            task_type="CONCEPT_PORTFOLIO_V2_CANDIDATE")
        draft = ConceptCandidateDraft.model_validate(raw)
        return ConceptCandidateResult.model_validate({**draft.model_dump(mode="json"), "schemaVersion": "2.0",
            "generationStrategy": "REFINE", "candidateIndex": candidate_index, "originalCandidate": False,
            "valueSemantics": [item.model_dump(mode="json") for item in _semantics(seed)]})

    async def review_legal(self, candidate_id: str, candidate: ConceptCandidateResult,
                           fixture_name: str) -> LegalReview:
        return await CurrentLegalAdapter().review(candidate_id, candidate)

    async def redesign(self, seed: CanonicalSeed, plan: PortfolioPlan, candidate: ConceptCandidateResult,
                       requirements: list[str], candidate_index: int) -> ConceptCandidateResult:
        from app.tasks.concept_redesign.service import execute_concept_redesign
        legal_input = CurrentLegalAdapter().task_input(candidate)
        raw = await execute_concept_redesign({"candidate": candidate.model_dump(mode="json"),
            "safeConstraints": requirements, "prohibitedVariants": [], "designGaps": requirements,
            "legalFactPattern": legal_input["legalFactPattern"]})
        return ConceptCandidateResult.model_validate(raw)


class ReplayMiss(RuntimeError):
    pass


class ProviderGateway:
    """호출 집계, LIVE 기록, strict REPLAY lookup을 한 곳에서 소유한다."""

    def __init__(self, mode: ProviderMode | str = ProviderMode.MOCK, *,
                 recordings_dir: Path | None = None, provider: PortfolioProvider | None = None):
        self.mode = ProviderMode(mode)
        self.recordings_dir = recordings_dir
        self.provider = provider or (LivePortfolioProvider() if self.mode == ProviderMode.LIVE else MockPortfolioProvider())
        self.usage = ProviderUsage(modeCounts={self.mode.value: 0})

    @staticmethod
    def request_hash(operation: str, payload: Any) -> str:
        canonical = json.dumps({"operation": operation, "payload": payload}, ensure_ascii=False,
                               sort_keys=True, separators=(",", ":"), default=str)
        return hashlib.sha256(canonical.encode("utf-8")).hexdigest()

    async def call(self, stage: str, operation: str, payload: dict[str, Any], fn, response_model=None):
        request_hash = self.request_hash(operation, payload)
        started = time.perf_counter()
        self.usage.totalProviderCalls += 1
        self.usage.callsByStage[stage] = self.usage.callsByStage.get(stage, 0) + 1
        self.usage.modeCounts[self.mode.value] = self.usage.modeCounts.get(self.mode.value, 0) + 1
        try:
            if self.mode == ProviderMode.REPLAY:
                if not self.recordings_dir:
                    raise ReplayMiss("REPLAY_MISS: recordings_dir가 없습니다")
                path = self.recordings_dir / f"{request_hash}.json"
                if not path.exists():
                    raise ReplayMiss(f"REPLAY_MISS: {operation} {request_hash}")
                record = json.loads(path.read_text(encoding="utf-8"))
                if record.get("canonicalRequestHash") != request_hash:
                    raise ReplayMiss(f"REPLAY_MISS: hash mismatch {request_hash}")
                raw = record["providerResponse"]
                return response_model.model_validate(raw) if response_model else raw
            retry_number = 0
            while True:
                try:
                    result = await fn()
                    break
                except ProviderFailure as failure:
                    if self.mode != ProviderMode.LIVE or not failure.retryable or retry_number >= 2:
                        raise
                    retry_number += 1
                    self.usage.retries += 1
                    self.usage.totalProviderCalls += 1
                    self.usage.callsByStage[stage] += 1
                    self.usage.modeCounts[self.mode.value] += 1
                    fallback = (2_000, 5_000)[retry_number - 1]
                    delay_ms = min(15_000, max(1_000, failure.retry_after_ms or fallback))
                    await asyncio.sleep(delay_ms / 1000)
            if self.mode == ProviderMode.LIVE and self.recordings_dir:
                self.recordings_dir.mkdir(parents=True, exist_ok=True)
                raw = result.model_dump(mode="json") if hasattr(result, "model_dump") else result
                record = {"taskType": operation, "schemaVersion": "2.0",
                          "canonicalRequestHash": request_hash, "redactedRequest": payload,
                          "providerResponse": raw, "durationMs": int((time.perf_counter() - started) * 1000),
                          "timestamp": datetime.now(timezone.utc).isoformat(),
                          "providerMetadata": {"mode": "LIVE", "retries": retry_number}}
                (self.recordings_dir / f"{request_hash}.json").write_text(
                    json.dumps(record, ensure_ascii=False, indent=2, default=str), encoding="utf-8")
            return result
        except ProviderFailure:
            raise
        finally:
            duration = int((time.perf_counter() - started) * 1000)
            self.usage.durationMs += duration

    async def plan_pool(self, seed, design, pool_size):
        payload = {"seed": seed.model_dump(mode="json"), "design": design.model_dump(mode="json"), "poolSize": pool_size}
        raw = await self.call("PLANNING", "PLAN_POOL", payload,
                              lambda: self.provider.plan_pool(seed, design, pool_size))
        return [item if isinstance(item, PortfolioPlan) else PortfolioPlan.model_validate(item) for item in raw]

    async def expand(self, seed, plan, index):
        payload = {"seed": seed.model_dump(mode="json"), "plan": plan.model_dump(mode="json"), "index": index}
        return await self.call("EXPANDING", "EXPAND", payload,
                               lambda: self.provider.expand(seed, plan, index), ConceptCandidateResult)

    async def review_legal(self, candidate_id, candidate, fixture_name):
        payload = {"candidateId": candidate_id, "candidate": candidate.model_dump(mode="json"),
                   "fixtureName": fixture_name}
        return await self.call("LEGAL_REVIEWING", "LEGAL_REVIEW", payload,
                               lambda: self.provider.review_legal(candidate_id, candidate, fixture_name), LegalReview)

    async def redesign(self, seed, plan, candidate, requirements, index):
        payload = {"seed": seed.model_dump(mode="json"), "plan": plan.model_dump(mode="json"),
                   "candidate": candidate.model_dump(mode="json"), "requirements": requirements, "index": index}
        return await self.call("LEGAL_RECOVERING", "REDESIGN", payload,
                               lambda: self.provider.redesign(seed, plan, candidate, requirements, index),
                               ConceptCandidateResult)
