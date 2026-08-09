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
from app.canonical_json import canonical_json
from app.tasks.concept_candidate.models import (
    ConceptCandidateDraft, ConceptCandidateResult,
)

from .adapters import CurrentLegalAdapter
from .models import (
    CanonicalSeed, DesignSpaceAnalysis, ExplorationBreadth, LegalReview, LegalRoute, MechanicsDescriptor,
    PlanDraftPool, PortfolioPlan, PortfolioPlanDraft, ProviderMode, ProviderUsage,
    SemanticDistinctnessResult, SemanticFidelityResult,
)
from .schema_preflight import assert_strict_compatible


PLAN_PROMPT_VERSION = "concept-portfolio-plan-v2.0"
CANDIDATE_PROMPT_VERSION = "concept-portfolio-candidate-v2.0"


class PortfolioProvider(ABC):
    @abstractmethod
    async def plan_pool(self, seed: CanonicalSeed, design: DesignSpaceAnalysis,
                        pool_size: int) -> list[PortfolioPlanDraft]: ...

    @abstractmethod
    async def expand(self, seed: CanonicalSeed, plan: PortfolioPlan,
                     candidate_index: int) -> ConceptCandidateDraft: ...

    @abstractmethod
    async def review_legal(self, candidate_id: str, candidate: ConceptCandidateResult,
                           fixture_name: str) -> LegalReview: ...

    @abstractmethod
    async def redesign(self, seed: CanonicalSeed, plan: PortfolioPlan, candidate: ConceptCandidateResult,
                       requirements: list[str], candidate_index: int) -> ConceptCandidateResult: ...

    @abstractmethod
    async def judge_distinctness(self, kind: str, left: dict[str, Any],
                                 right: dict[str, Any]) -> SemanticDistinctnessResult: ...

    @abstractmethod
    async def judge_fidelity(self, plan: PortfolioPlan,
                             candidate: ConceptCandidateResult) -> SemanticFidelityResult: ...


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

MECHANIC_KEYS = [
    ("SUBSCRIPTION_PORTIONING", "CENTRAL_PORTIONING", "RECURRING_DELIVERY", "DIRECT_OPERATOR", "LOCAL_CENTER", "SUBSCRIPTION", "SUBSCRIPTION_FEE", "MOBILE_DIRECT"),
    ("GROUP_ORDER", "LOCAL_RETAILER_NETWORK", "PICKUP_POINT", "MARKETPLACE", "LOCAL_RETAILER_NETWORK", "AGGREGATED_ORDER", "COMMISSION", "COMMUNITY_APP"),
    ("RECIPE_BUNDLE", "CURATED_SUPPLIER", "ON_DEMAND_DELIVERY", "CURATION_MARKET", "CHEF_PRODUCER", "BUNDLE_PURCHASE", "BUNDLE_SALE", "CONTENT_COMMERCE"),
    ("INVENTORY_COACH", "RETAIL_DATA_NETWORK", "STORE_PICKUP", "DIGITAL_COACH", "RETAIL_DATA_PARTNER", "RECOMMENDATION_ORDER", "APP_SUBSCRIPTION", "MOBILE_ASSISTANT"),
    ("EMPLOYEE_FOOD_KIT", "B2B_SUPPLY", "OFFICE_BULK_DELIVERY", "B2B_OPERATOR", "EMPLOYER_CATERING", "ENTERPRISE_ORDER", "B2B_CONTRACT", "EMPLOYEE_PORTAL"),
    ("PRODUCTION_RESERVATION", "PRODUCER_NETWORK", "PRODUCER_DIRECT_DELIVERY", "MARKETPLACE", "PRODUCER_NETWORK", "PREORDER", "TRANSACTION_COMMISSION", "RESERVATION_MARKET"),
    ("UNMANNED_PORTIONING", "LOCAL_STATION_NETWORK", "STATION_PICKUP", "STATION_OPERATOR", "SPACE_LOGISTICS", "METERED_USE", "USAGE_FEE", "KIOSK_APP"),
    ("EXPERT_MEAL_GUIDANCE", "QUALIFIED_EXPERT_NETWORK", "SCHEDULED_DELIVERY", "GUIDANCE_PLATFORM", "QUALIFIED_EXPERT", "CONSULTATION_ORDER", "SERVICE_SUBSCRIPTION", "APPOINTMENT_APP"),
]


def _locked(seed: CanonicalSeed) -> dict[str, str]:
    return {item.fieldKey: item.value for item in seed.fields
            if item.decisionState == "LOCKED" and item.value.strip()}


def _plan(seed: CanonicalSeed, index: int, mechanics_index: int | None = None) -> PortfolioPlanDraft:
    name, mechanism, commercial, partner, fulfillment = MECHANICS[mechanics_index if mechanics_index is not None else index]
    keys = MECHANIC_KEYS[mechanics_index if mechanics_index is not None else index]
    return PortfolioPlanDraft(
        title=name, oneLineConcept=f"{seed.targetUsers}를 위한 {name}",
        coreMechanism=mechanism, customerInteraction=f"{name} 전용 모바일·현장 접점",
        valueDelivery=f"{seed.problem}을 줄이는 {mechanism}", operatingApproach=mechanism,
        partnerApproach=partner, transactionApproach=commercial, commercialApproach=commercial,
        fulfillmentApproach=fulfillment,
        mechanics=MechanicsDescriptor(
            solutionMechanismType=keys[0], supplyModel=keys[1], fulfillmentModel=keys[2],
            platformRoleType=keys[3], partnerStructureType=keys[4], transactionModel=keys[5],
            commercialModel=keys[6], customerInteractionModel=keys[7]),
        differentiatingMechanics=[mechanism, partner, commercial, fulfillment],
        mainChanges=["solutionMechanism", "operatingModel", "partnerModel"],
        secondaryChanges=["transactionFlow", "fulfillment"], legalRiskHints=[],
        reasonForPortfolioRole=f"사업 작동방식 {index + 1}의 독립 대안",
    )


def _candidate(seed: CanonicalSeed, plan: PortfolioPlan, index: int, *, redesigned: bool = False) -> ConceptCandidateDraft:
    locks = _locked(seed)
    get = lambda key, default: locks.get(key) or default
    mechanism = plan.coreMechanism + ("(인허가 보유 파트너가 제공)" if redesigned else "")
    values = dict(
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
        advertisingClaims=["검증 가능한 편의성"], constraintCompliance=[],
    )
    return ConceptCandidateDraft.model_validate(values)


class MockPortfolioProvider(PortfolioProvider):
    async def plan_pool(self, seed: CanonicalSeed, design: DesignSpaceAnalysis,
                        pool_size: int) -> list[PortfolioPlanDraft]:
        count = 3 if seed.fixtureName == "limited_unique_plans" else pool_size
        plans = [_plan(seed, index, index % len(MECHANICS)) for index in range(count)]
        if seed.fixtureName == "duplicate_plans" and len(plans) >= 2:
            plans[1] = _plan(seed, 1, 0)
        if seed.fixtureName == "near_duplicate_paraphrase" and len(plans) >= 2:
            plans[1] = plans[0].model_copy(update={
                "title": "제휴 소매점 고객 직배송 플랫폼",
                "coreMechanism": "제휴 소매점이 고객에게 바로 배송",
                "operatingApproach": "지역 판매점 주문 연계 운영",
            })
        return plans

    async def expand(self, seed: CanonicalSeed, plan: PortfolioPlan,
                     candidate_index: int) -> ConceptCandidateDraft:
        draft = _candidate(seed, plan, candidate_index)
        if seed.fixtureName == "replan_anchor_drift" and plan.planId in {"P6", "P7"}:
            draft = draft.model_copy(update={"targetUsers": "기업 구내식당 담당자",
                                             "problemScenario": "대기업 급식 운영 효율화"})
        if seed.fixtureName == "provider_wrong_lock":
            draft = draft.model_copy(update={"price": "Provider 임의 가격", "channels": "Provider 임의 채널"})
        return draft

    async def review_legal(self, candidate_id: str, candidate: ConceptCandidateResult,
                           fixture_name: str) -> LegalReview:
        first = candidate_id in {"C1", "C1-R1", "C1-REPLAN"}
        if ((fixture_name == "legal_redesign" and candidate_id == "C1")
                or (fixture_name == "two_legal_redesigns" and candidate_id in {"C1", "C3"})
                or (fixture_name == "second_redesign" and candidate_id in {"C1", "C1-R1"})):
            return LegalReview(candidateId=candidate_id, route=LegalRoute.REDESIGN_WITHIN_LINEAGE,
                               productionStatus="REDESIGNABLE",
                               sourceStatus="MOCK_OFFICIAL_EVIDENCE", safeSummary="동일 lineage 내 통제 보완이 필요합니다.",
                               redesignRequirements=["인허가 보유 파트너로 제공 주체를 제한"])
        if fixture_name in {"legal_replan", "replan_anchor_drift"} and candidate_id == "C1":
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
                           requiredPartnersAndQualifications=["필요 시 자격 보유 파트너 사용"],
                           officialEvidenceReferences=[{"sourceType": "OFFICIAL_LAW", "officialIdentifier": "MOCK-LAW",
                                                        "articleReference": "제1조", "officialSourceUri": "https://www.law.go.kr/mock",
                                                        "contentHash": "sha256:" + "0" * 64}])

    async def redesign(self, seed: CanonicalSeed, plan: PortfolioPlan, candidate: ConceptCandidateResult,
                       requirements: list[str], candidate_index: int) -> ConceptCandidateResult:
        draft = _candidate(seed, plan, candidate_index, redesigned=True)
        from .candidate_governance import normalize_candidate_draft
        return normalize_candidate_draft(
            draft, seed, ExplorationBreadth(candidate.generationStrategy), candidate_index)

    async def judge_distinctness(self, kind: str, left: dict[str, Any],
                                 right: dict[str, Any]) -> SemanticDistinctnessResult:
        left_text = json.dumps(left, ensure_ascii=False, sort_keys=True)
        right_text = json.dumps(right, ensure_ascii=False, sort_keys=True)
        duplicate = left_text == right_text or ("지역 식료품점" in left_text and "제휴 소매점" in right_text)
        return SemanticDistinctnessResult(
            decision="DUPLICATE" if duplicate else "DISTINCT",
            overlappingMechanics=["semantic_business_mechanics"] if duplicate else [],
            materiallyDifferentMechanics=[] if duplicate else ["business_mechanics"],
            safeSummary="의미상 같은 사업 구조입니다." if duplicate else "의미상 다른 사업 구조입니다.")

    async def judge_fidelity(self, plan: PortfolioPlan,
                             candidate: ConceptCandidateResult) -> SemanticFidelityResult:
        plan_tokens = set(plan.coreMechanism.split())
        candidate_tokens = set(candidate.solutionMechanism.split())
        passed = bool(plan_tokens & candidate_tokens)
        return SemanticFidelityResult(decision="PASS" if passed else "FAIL",
            matchedMechanics=["solutionMechanism"] if passed else [],
            missingMechanics=[] if passed else ["solutionMechanism"],
            safeSummary="Plan mechanics를 의미상 구현합니다." if passed else "Plan 핵심 mechanism이 구현되지 않았습니다.")


class LivePortfolioProvider(PortfolioProvider):
    async def plan_pool(self, seed: CanonicalSeed, design: DesignSpaceAnalysis,
                        pool_size: int) -> list[PortfolioPlanDraft]:
        prompt = """같은 opportunity를 해결하되 business mechanics가 실질적으로 다른 동적 PortfolioPlan pool을 만든다.
고정 lens나 정확히 5개를 강제하지 않는다. mechanics에는 각 구조를 비교할 짧은 normalized semantic label을 넣는다.
planId, preservedAnchors, preservedLocks 같은 system metadata는 생성하지 않는다. strict schema만 반환한다."""
        schema = PlanDraftPool.model_json_schema()
        assert_strict_compatible(schema, "concept_portfolio_plan_draft_v2")
        raw = await execute_structured_prompt(
            prompt, json.dumps({"promptVersion": PLAN_PROMPT_VERSION, "seed": seed.model_dump(mode="json"),
                                "designSpace": design.model_dump(mode="json"), "poolSize": pool_size},
                               ensure_ascii=False, sort_keys=True),
            response_schema=schema, schema_name="concept_portfolio_plan_draft_v2",
            task_type="CONCEPT_PORTFOLIO_V2_PLAN")
        return PlanDraftPool.model_validate(raw).plans

    async def expand(self, seed: CanonicalSeed, plan: PortfolioPlan,
                     candidate_index: int) -> ConceptCandidateDraft:
        prompt = """통과된 PortfolioPlan 하나를 현행 ConceptCandidateDraft 계약으로 확장한다.
plan의 business mechanics와 사용자 lock/anchor를 보존한다. 법률 결론을 만들지 말고 strict schema만 반환한다."""
        schema = ConceptCandidateDraft.model_json_schema()
        assert_strict_compatible(schema, "concept_portfolio_candidate_v2")
        raw = await execute_structured_prompt(
            prompt, json.dumps({"promptVersion": CANDIDATE_PROMPT_VERSION, "seed": seed.model_dump(mode="json"),
                                "plan": plan.model_dump(mode="json")}, ensure_ascii=False, sort_keys=True),
            response_schema=schema, schema_name="concept_portfolio_candidate_v2",
            task_type="CONCEPT_PORTFOLIO_V2_CANDIDATE")
        return ConceptCandidateDraft.model_validate(raw)

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

    async def judge_distinctness(self, kind: str, left: dict[str, Any],
                                 right: dict[str, Any]) -> SemanticDistinctnessResult:
        schema = SemanticDistinctnessResult.model_json_schema()
        assert_strict_compatible(schema, "concept_portfolio_semantic_distinctness_v2")
        raw = await execute_structured_prompt(
            "두 사업의 problem/target 공통점이 아니라 business mechanics의 실질적 동일성을 판정한다. strict schema만 반환한다.",
            json.dumps({"kind": kind, "left": left, "right": right}, ensure_ascii=False, sort_keys=True),
            response_schema=schema, schema_name="concept_portfolio_semantic_distinctness_v2",
            task_type="CONCEPT_PORTFOLIO_V2_DISTINCTNESS")
        return SemanticDistinctnessResult.model_validate(raw)

    async def judge_fidelity(self, plan: PortfolioPlan,
                             candidate: ConceptCandidateResult) -> SemanticFidelityResult:
        schema = SemanticFidelityResult.model_json_schema()
        assert_strict_compatible(schema, "concept_portfolio_plan_fidelity_v2")
        raw = await execute_structured_prompt(
            "Candidate가 Plan 문장을 복사했는지가 아니라 핵심 business mechanics를 실제 구현하는지 판정한다. strict schema만 반환한다.",
            json.dumps({"plan": plan.model_dump(mode="json"),
                        "candidate": candidate.model_dump(mode="json")}, ensure_ascii=False, sort_keys=True),
            response_schema=schema, schema_name="concept_portfolio_plan_fidelity_v2",
            task_type="CONCEPT_PORTFOLIO_V2_PLAN_FIDELITY")
        return SemanticFidelityResult.model_validate(raw)


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
        self.last_failure: dict[str, Any] | None = None

    @staticmethod
    def request_hash(operation: str, payload: Any) -> str:
        canonical = canonical_json({"operation": operation, "payload": payload})
        return hashlib.sha256(canonical.encode("utf-8")).hexdigest()

    @staticmethod
    def redact_secrets(value: Any) -> Any:
        secret_markers = ("secret", "password", "authorization", "api_key", "apikey", "token", "moleg_key")
        if isinstance(value, dict):
            return {key: "[REDACTED]" if any(marker in key.casefold() for marker in secret_markers)
                    else ProviderGateway.redact_secrets(item) for key, item in value.items()}
        if isinstance(value, list):
            return [ProviderGateway.redact_secrets(item) for item in value]
        return value

    def note_external_call(self, stage: str):
        self.usage.externalProviderCalls += 1
        self.usage.totalProviderCalls = self.usage.externalProviderCalls
        self.usage.externalCallsByStage[stage] = self.usage.externalCallsByStage.get(stage, 0) + 1

    async def call(self, stage: str, operation: str, payload: dict[str, Any], fn, response_model=None):
        request_hash = self.request_hash(operation, payload)
        started = time.perf_counter()
        self.usage.logicalOperations += 1
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
                    if self.mode == ProviderMode.LIVE:
                        self.note_external_call(stage)
                    result = await fn()
                    break
                except ProviderFailure as failure:
                    if self.mode != ProviderMode.LIVE or not failure.retryable or retry_number >= 2:
                        raise
                    retry_number += 1
                    self.usage.retries += 1
                    fallback = (2_000, 5_000)[retry_number - 1]
                    delay_ms = min(15_000, max(1_000, failure.retry_after_ms or fallback))
                    await asyncio.sleep(delay_ms / 1000)
            if self.mode == ProviderMode.LIVE and self.recordings_dir:
                self.recordings_dir.mkdir(parents=True, exist_ok=True)
                raw = result.model_dump(mode="json") if hasattr(result, "model_dump") else result
                record = {"taskType": operation, "schemaVersion": "2.0",
                          "canonicalRequestHash": request_hash,
                          "redactedRequest": self.redact_secrets(payload),
                          "providerResponse": raw, "durationMs": int((time.perf_counter() - started) * 1000),
                          "timestamp": datetime.now(timezone.utc).isoformat(),
                          "providerMetadata": {"mode": "LIVE", "retries": retry_number}}
                (self.recordings_dir / f"{request_hash}.json").write_text(
                    json.dumps(record, ensure_ascii=False, indent=2, default=str), encoding="utf-8")
            return result
        except ProviderFailure as failure:
            self.last_failure = {
                "taskType": operation, "schemaName": failure.schema_name,
                "providerStatus": failure.upstream_status or failure.status_code,
                "providerErrorType": failure.provider_error_type,
                "providerErrorParam": failure.provider_error_param,
                "safeProviderMessage": getattr(failure, "safe_provider_message", None) or failure.reason,
                "retryable": failure.retryable,
            }
            raise
        finally:
            duration = int((time.perf_counter() - started) * 1000)
            self.usage.durationMs += duration

    async def plan_pool(self, seed, design, pool_size):
        payload = {"seed": seed.model_dump(mode="json"), "design": design.model_dump(mode="json"), "poolSize": pool_size}
        raw = await self.call("PLANNING", "PLAN_POOL", payload,
                              lambda: self.provider.plan_pool(seed, design, pool_size))
        return [item if isinstance(item, PortfolioPlanDraft) else PortfolioPlanDraft.model_validate(item) for item in raw]

    async def expand(self, seed, plan, index):
        payload = {"seed": seed.model_dump(mode="json"), "plan": plan.model_dump(mode="json"), "index": index}
        return await self.call("EXPANDING", "EXPAND", payload,
                               lambda: self.provider.expand(seed, plan, index), ConceptCandidateDraft)

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

    async def judge_distinctness(self, kind, left, right):
        payload = {"kind": kind, "left": left, "right": right}
        return await self.call("DISTINCTNESS", "SEMANTIC_DISTINCTNESS", payload,
                               lambda: self.provider.judge_distinctness(kind, left, right),
                               SemanticDistinctnessResult)

    async def judge_fidelity(self, plan, candidate):
        payload = {"plan": plan.model_dump(mode="json"),
                   "candidate": candidate.model_dump(mode="json")}
        return await self.call("CANDIDATE_VALIDATING", "PLAN_FIDELITY", payload,
                               lambda: self.provider.judge_fidelity(plan, candidate),
                               SemanticFidelityResult)
