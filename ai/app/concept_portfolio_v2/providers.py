"""Generic Concept Portfolio V2의 MOCK / REPLAY / LIVE Gateway."""

from __future__ import annotations

import asyncio
import hashlib
import json
import time
from abc import ABC, abstractmethod
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from pydantic import ValidationError

from app.canonical_json import canonical_json
from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.concept_candidate.models import ConceptCandidateDraft, ConceptCandidateResult

from .adapters import CurrentLegalAdapter
from .language_policy import candidate_language_failures, plan_language_failures
from .models import (
    BusinessRoleSemanticBatch, BusinessRoleSemanticItem,
    CanonicalSeed, DesignSpaceAnalysis, ExplorationBreadth, LegalReview, LegalRoute,
    LegalFactCompletionPatch, LegalFactCompletionRequirement,
    LegalFactDependencySemanticBatch, LegalFactDependencySemanticItem,
    PlanDraftPool, PortfolioPlan, PortfolioPlanDraft, ProviderMode, ProviderUsage,
    SemanticArchitectureBatch, SemanticArchitectureClassification,
    SemanticDistinctnessResult, SemanticFidelityResult, SemanticHypothesisBatch,
    SemanticHypothesisResult,
)
from .schema_preflight import assert_strict_compatible


PLAN_PROMPT_VERSION = "concept-portfolio-generic-plan-v3"
CANDIDATE_PROMPT_VERSION = "concept-portfolio-generic-candidate-v3"
DISTINCTNESS_PROMPT_VERSION = "concept-portfolio-relation-v3"
FIDELITY_PROMPT_VERSION = "concept-portfolio-fidelity-v3"
FIDELITY_REGEN_PROMPT_VERSION = "concept-portfolio-fidelity-regeneration-v1"
ARCHITECTURE_PROMPT_VERSION = "concept-portfolio-architecture-classifier-v2"
HYPOTHESIS_SEMANTIC_PROMPT_VERSION = "concept-portfolio-hypothesis-semantic-v1"
BUSINESS_ROLE_SEMANTIC_PROMPT_VERSION = "concept-business-role-semantic-v2"
LEGAL_FACT_DEPENDENCY_PROMPT_VERSION = "concept-legal-fact-dependency-v2"
LEGAL_OPERATION_VERSION = "concept-legal-review-v3"
LEGAL_FACT_COMPLETION_PROMPT_VERSION = "concept-legal-fact-completion-dynamic-patch-v3"
LEGAL_REDESIGN_REPAIR_PROMPT_VERSION = "concept-legal-redesign-compliance-repair-v1"


_COMPLETION_LIST_FIELDS = {
    "transactionFlow", "paymentFlow", "personalDataUsage",
    "physicalActivities", "partnerRequirements",
}


def completion_field_names(requirements: list[LegalFactCompletionRequirement]) -> list[str]:
    return list(dict.fromkeys(item.field for item in requirements))


def legal_fact_completion_schema(requirements: list[LegalFactCompletionRequirement]) -> dict[str, Any]:
    """요청된 fact field만 존재하는 strict Provider 응답 schema를 만든다."""
    fields = completion_field_names(requirements)
    if not fields:
        raise ProviderFailure("INVALID_REQUEST", "LEGAL_FACT_COMPLETION_FIELDS_EMPTY", 400, False,
                              schema_name="concept_legal_fact_completion_dynamic_v3")
    properties = {}
    for field in fields:
        if field in _COMPLETION_LIST_FIELDS:
            properties[field] = {
                "title": field, "type": "array", "minItems": 1, "maxItems": 12,
                "items": {"type": "string", "minLength": 1, "maxLength": 500},
            }
        else:
            properties[field] = {"title": field, "type": "string", "minLength": 1, "maxLength": 1000}
    return {"title": "LegalFactCompletionDynamicPatch", "type": "object",
            "properties": properties, "required": fields, "additionalProperties": False}


class PortfolioProvider(ABC):
    @abstractmethod
    async def plan_pool(self, seed: CanonicalSeed, design: DesignSpaceAnalysis,
                        pool_size: int) -> list[PortfolioPlanDraft]: ...

    @abstractmethod
    async def replenish_plans(self, seed: CanonicalSeed, design: DesignSpaceAnalysis,
                              existing_plans: list[PortfolioPlan], rejected: list[dict[str, Any]],
                              count: int, round_number: int) -> list[PortfolioPlanDraft]: ...

    @abstractmethod
    async def expand(self, seed: CanonicalSeed, plan: PortfolioPlan,
                     candidate_index: int) -> ConceptCandidateDraft: ...

    @abstractmethod
    async def regenerate_candidate(self, seed: CanonicalSeed, design: DesignSpaceAnalysis,
                                   plan: PortfolioPlan, previous: ConceptCandidateResult,
                                   failure_summary: str, missing_identity: list[str],
                                   candidate_index: int) -> ConceptCandidateDraft: ...

    @abstractmethod
    async def review_legal(self, candidate_id: str, candidate: ConceptCandidateResult,
                           seed: CanonicalSeed) -> LegalReview: ...

    @abstractmethod
    async def redesign(self, seed: CanonicalSeed, plan: PortfolioPlan, candidate: ConceptCandidateResult,
                       requirements: list[str], candidate_index: int) -> ConceptCandidateResult: ...

    @abstractmethod
    async def complete_legal_facts(self, seed: CanonicalSeed, plan: PortfolioPlan,
                                   candidate: ConceptCandidateResult,
                                   requirements: list[LegalFactCompletionRequirement],
                                   candidate_index: int) -> dict[str, Any]: ...

    @abstractmethod
    async def repair_redesign_compliance(self, seed: CanonicalSeed, plan: PortfolioPlan,
                                         parent: ConceptCandidateResult, child: ConceptCandidateResult,
                                         requirements: list[str], candidate_index: int) -> ConceptCandidateDraft: ...

    @abstractmethod
    async def judge_distinctness(self, kind: str, left: dict[str, Any],
                                 right: dict[str, Any]) -> SemanticDistinctnessResult: ...

    @abstractmethod
    async def judge_fidelity(self, plan: PortfolioPlan,
                             candidate: ConceptCandidateResult) -> SemanticFidelityResult: ...

    @abstractmethod
    async def classify_architectures(self, items: list[dict[str, Any]]) -> list[SemanticArchitectureClassification]: ...

    @abstractmethod
    async def classify_hypotheses(self, items: list[dict[str, Any]]) -> list[SemanticHypothesisResult]: ...

    @abstractmethod
    async def classify_business_roles(self, items: list[dict[str, Any]]) -> list[BusinessRoleSemanticItem]: ...

    @abstractmethod
    async def classify_legal_fact_dependencies(
        self, items: list[dict[str, Any]],
    ) -> list[LegalFactDependencySemanticItem]: ...

    @abstractmethod
    async def replacement_plans(self, seed: CanonicalSeed, design: DesignSpaceAnalysis,
                                existing_plans: list[PortfolioPlan], count: int) -> list[PortfolioPlanDraft]: ...


GENERIC_TEMPLATES = [
    {
        "name": "직접 운영 핵심형", "target": "핵심 대상 전체", "use": "핵심 문제가 반복되는 일상 상황",
        "value": "일관된 품질과 명확한 책임 주체", "offer": "핵심 기능을 묶은 직접 제공형 패키지",
        "solution": "운영사가 핵심 해결 과정을 직접 설계하고 제공",
        "operation": "운영사가 품질과 이행을 직접 운영", "partner": "필요 기능만 제한적으로 제휴",
        "transaction": "사용자가 운영사와 건별로 직접 거래", "commercial": "서비스 건별 직접 판매",
        "delivery": "운영사가 디지털 또는 직접 이행", "interaction": "모바일 앱과 웹 기반 직접 접점",
    },
    {
        "name": "우선 세그먼트 특화형", "target": "구체적인 우선 하위 세그먼트", "use": "특정 빈도와 제약이 강한 사용 상황",
        "value": "세그먼트 맥락에 맞춘 낮은 도입 부담", "offer": "하위 세그먼트 전용 구성과 경험",
        "solution": "동일 핵심 해결책을 우선 세그먼트의 맥락에 맞게 특화",
        "operation": "운영사가 특화 경험을 직접 운영", "partner": "필요 기능만 제한적으로 제휴",
        "transaction": "사용자가 운영사와 건별로 직접 거래", "commercial": "서비스 건별 직접 판매",
        "delivery": "디지털 중심 직접 이행", "interaction": "모바일 앱 기반 셀프서비스",
    },
    {
        "name": "파트너 마켓플레이스형", "target": "선택 폭을 중시하는 핵심 대상", "use": "여러 공급 대안을 비교하는 상황",
        "value": "다양한 대안을 한 곳에서 비교하고 선택", "offer": "검증된 파트너 offer의 통합 목록",
        "solution": "수요와 검증된 파트너를 매칭하는 마켓플레이스",
        "operation": "플랫폼이 거래 기준과 품질 정책을 운영", "partner": "다수의 제휴 파트너 네트워크",
        "transaction": "플랫폼이 사용자와 파트너의 거래를 매칭", "commercial": "거래 성사 수수료",
        "delivery": "파트너가 최종 서비스를 이행", "interaction": "웹과 앱을 통한 비교·매칭",
    },
    {
        "name": "자동화 SaaS 도구형", "target": "직접 해결 과정을 관리하려는 핵심 대상", "use": "반복 업무를 스스로 처리하는 상황",
        "value": "반복 판단과 실행을 자동화해 시간 절감", "offer": "셀프서비스 자동화 도구와 분석 화면",
        "solution": "소프트웨어와 AI가 핵심 해결 과정을 자동화",
        "operation": "자동화 디지털 SaaS로 운영", "partner": "핵심 기능은 자체 운영하고 API만 연계",
        "transaction": "정기 이용권으로 소프트웨어 접근 권한 제공", "commercial": "월 구독료",
        "delivery": "온라인 디지털 제공", "interaction": "웹·앱 셀프서비스",
    },
    {
        "name": "전문가 지원형", "target": "높은 확신과 도움을 원하는 핵심 대상", "use": "복잡하거나 실패 비용이 큰 상황",
        "value": "전문가 판단과 개인 맥락을 결합한 높은 신뢰", "offer": "진단·상담·실행 지원 패키지",
        "solution": "자격 또는 경험을 갖춘 전문가가 해결 과정을 지원",
        "operation": "전문가 네트워크와 운영사가 하이브리드 운영", "partner": "검증된 전문가 네트워크",
        "transaction": "상담 예약 후 서비스 요금 결제", "commercial": "상담 및 서비스 요금",
        "delivery": "온라인 상담과 현장 지원의 하이브리드", "interaction": "전문가가 지원하는 예약형 접점",
    },
    {
        "name": "커뮤니티 매칭형", "target": "경험 공유와 상호 도움을 원하는 핵심 대상", "use": "유사한 경험을 가진 사람과 연결되는 상황",
        "value": "검증된 경험과 상호 지원을 통한 실행 장벽 감소", "offer": "커뮤니티 콘텐츠와 개인 간 매칭",
        "solution": "커뮤니티 참여자 간 경험과 자원을 매칭",
        "operation": "개인 간 네트워크를 플랫폼이 운영", "partner": "커뮤니티 기여자와 운영 파트너",
        "transaction": "필요 시 참여자 간 매칭 거래", "commercial": "프리미엄 기능과 서비스 수수료",
        "delivery": "커뮤니티 기반 디지털 셀프서비스", "interaction": "커뮤니티 앱",
    },
    {
        "name": "기업 계약형", "target": "핵심 대상에게 서비스를 제공하는 조직", "use": "조직 단위로 반복 도입하는 상황",
        "value": "조직 단위 표준화와 관리 효율", "offer": "관리자 기능과 조직용 운영 패키지",
        "solution": "조직이 핵심 대상에게 해결책을 배포하도록 지원",
        "operation": "운영사와 고객 조직이 공동 운영", "partner": "고객 조직과 구현 파트너 네트워크",
        "transaction": "기업 연간 계약", "commercial": "B2B 연간 계약",
        "delivery": "API와 웹 기반 디지털 제공", "interaction": "관리자 웹과 API",
    },
    {
        "name": "플랫폼 인프라형", "target": "핵심 문제를 해결하는 외부 사업자", "use": "기존 제품에 해결 기능을 내장하는 상황",
        "value": "새 기능을 빠르고 안정적으로 통합", "offer": "API와 운영 인프라",
        "solution": "외부 사업자가 핵심 해결 기능을 호출할 수 있는 플랫폼 인프라",
        "operation": "자동화 디지털 인프라 운영", "partner": "구현 및 채널 파트너",
        "transaction": "사용량 기반 API 거래", "commercial": "사용량 기반 요금",
        "delivery": "API 디지털 제공", "interaction": "API와 개발자 웹",
    },
]


def _locks(seed: CanonicalSeed) -> dict[str, str]:
    return {item.fieldKey: item.value for item in seed.fields
            if item.decisionState == "LOCKED" and item.value.strip()}


def _fidelity_context(plan: PortfolioPlan, candidate: ConceptCandidateResult) -> dict[str, Any]:
    plan_fields = ("targetSegment", "problemFocus", "useContext", "valueProposition",
                   "offerThesis", "solutionThesis", "differentiatingMechanics")
    candidate_fields = ("targetUsers", "problemScenario", "coreValue", "conceptDefinition",
                        "solutionMechanism", "featureSet", "operatingModel")
    return {
        "plan": {key: getattr(plan, key) for key in plan_fields},
        "candidate": {key: getattr(candidate, key) for key in candidate_fields},
        "planDescriptorSummary": plan.descriptor.model_dump(mode="json"),
    }


def _plan(seed: CanonicalSeed, index: int, template_index: int | None = None) -> PortfolioPlanDraft:
    template = GENERIC_TEMPLATES[(template_index if template_index is not None else index) % len(GENERIC_TEMPLATES)]
    locks = _locks(seed)
    target = seed.targetUsers if index == 0 else f"{seed.targetUsers} 중 {template['target']}"
    solution = f"{seed.ideaOverview}의 핵심 의도를 유지하면서 {template['solution']}"
    commercial = " · ".join(item for item in (
        locks.get("revenueModel") or template["commercial"], locks.get("price")) if item)
    interaction = locks.get("channels") or template["interaction"]
    differentiators = [template["target"], template["offer"], template["solution"]]
    if locks.get("differentiators"):
        differentiators.insert(0, locks["differentiators"])
    return PortfolioPlanDraft(
        title=template["name"], oneLineConcept=f"{seed.ideaOverview}를 {template['name']}으로 구현",
        targetSegment=target, problemFocus=seed.problem, useContext=template["use"],
        valueProposition=f"{seed.problem}을 줄이면서 {template['value']}을 제공",
        offerThesis=template["offer"], solutionThesis=solution,
        coreMechanism=solution, customerInteraction=interaction,
        valueDelivery=template["value"], operatingApproach=template["operation"],
        partnerApproach=template["partner"], transactionApproach=template["transaction"],
        commercialApproach=commercial, fulfillmentApproach=template["delivery"],
        differentiatingMechanics=differentiators,
        mainChanges=["conceptThesis", "businessArchitecture"], secondaryChanges=["customerInteraction"],
        legalRiskHints=[], reasonForPortfolioRole=f"사용자가 비교할 수 있는 {template['name']} 대안",
    )


def _candidate(seed: CanonicalSeed, plan: PortfolioPlan, index: int, *, redesigned: bool = False) -> ConceptCandidateDraft:
    locks = _locks(seed)
    value = lambda key, default: locks.get(key) or default
    partner = plan.partnerApproach + (" · 필요한 자격을 보유한 파트너로 제한" if redesigned else "")
    physical = [] if any(word in plan.fulfillmentApproach for word in ("디지털", "온라인", "API")) else [plan.fulfillmentApproach]
    return ConceptCandidateDraft(
        conceptName=plan.title, conceptDefinition=plan.oneLineConcept, introduction=plan.reasonForPortfolioRole,
        coreValue=plan.valueProposition, targetUsers=plan.targetSegment,
        industryCategory="해당 아이디어 관련 산업", researchScope="대한민국 내 관련 시장과 운영 구조",
        targetRegion=value("targetRegion", "대한민국"),
        revenueModel=value("revenueModel", plan.commercialApproach),
        price=value("price", "서비스 범위별 건당 1만~3만원 가설"),
        channels=value("channels", plan.customerInteraction),
        differentiators=value("differentiators", ", ".join(plan.differentiatingMechanics)),
        preMarketSomShareHypothesis={"targetSharePercent": 1.0 + index, "horizonYears": 3,
                                     "rationale": "시장 분석 전 검증할 가설", "assumptions": ["초기 가설"]},
        preMarketSomHypothesis={"amount": float(100_000_000 * index), "currency": "KRW", "period": "연간",
                                "calculationBasis": "시장 분석 전 임시 산식", "assumptions": ["초기 가설"],
                                "confidence": "LOW"},
        problemScenario=plan.problemFocus, solutionMechanism=plan.solutionThesis,
        featureSet=[plan.offerThesis, plan.coreMechanism], actorRoles=["사용자", "운영사", "파트너"],
        platformRole=plan.operatingApproach, operatingModel=plan.operatingApproach,
        partnerModel=partner, providerRole=plan.operatingApproach,
        sellerRole="계약에 따라 offer를 제공하는 주체",
        intermediaryRole="거래 연결" if any(x in plan.partnerApproach for x in ("매칭", "마켓", "네트워크")) else "직접 제공",
        transactionFlow=["사용자 요청", plan.transactionApproach, plan.fulfillmentApproach],
        paymentFlow=["사용자 결제", plan.commercialApproach, "필요 시 파트너 정산"],
        personalDataUsage=["서비스 이행을 위한 최소 정보"], physicalActivities=physical,
        partnerRequirements=["품질 기준 준수"] + (["필요한 자격 보유"] if redesigned else []),
        qualificationRequirements=["해당 활동에 필요한 자격"] if redesigned else [],
        advertisingClaims=["검증 가능한 효율과 편의성"], constraintCompliance=[])


class MockPortfolioProvider(PortfolioProvider):
    async def plan_pool(self, seed, design, pool_size):
        if seed.fixtureName == "limited_unique_plans":
            count = 3
        elif seed.fixtureName in {"plan_pool_shortfall", "no_reserve_legal_replan"}:
            count = min(5, pool_size)
        else:
            count = pool_size
        plans = [_plan(seed, index, index) for index in range(count)]
        if seed.fixtureName in {"duplicate_plans", "near_duplicate_paraphrase"} and len(plans) >= 2:
            plans[1] = plans[0].model_copy(update={
                "title": "표현만 바꾼 동일 사업", "oneLineConcept": plans[0].oneLineConcept + " 스마트형"})
        return plans

    async def replenish_plans(self, seed, design, existing_plans, rejected, count, round_number):
        if seed.fixtureName == "limited_unique_plans":
            return []
        used_titles = {item.title for item in existing_plans}
        result = []
        for index in range(len(GENERIC_TEMPLATES)):
            draft = _plan(seed, index + round_number, index)
            if draft.title not in used_titles:
                result.append(draft)
            if len(result) >= count:
                break
        return result

    async def expand(self, seed, plan, candidate_index):
        draft = _candidate(seed, plan, candidate_index)
        if seed.fixtureName == "replan_anchor_drift" and (
                plan.planId in {"P6", "P7"} or plan.planId.startswith("RP")):
            draft = draft.model_copy(update={"targetUsers": "원 대상과 무관한 고객군",
                                             "problemScenario": "원 문제와 무관한 별도 문제"})
        if seed.fixtureName == "provider_wrong_lock":
            draft = draft.model_copy(update={"price": "Provider 임의 가격", "channels": "Provider 임의 채널"})
        return draft

    async def regenerate_candidate(self, seed, design, plan, previous, failure_summary,
                                   missing_identity, candidate_index):
        return _candidate(seed, plan, candidate_index)

    async def review_legal(self, candidate_id, candidate, seed):
        name = seed.fixtureName
        root_id = candidate_id.split("-")[0]
        is_redesign_child = "-R" in candidate_id
        if ((name == "legal_redesign" and root_id == "C1" and not is_redesign_child)
                or (name == "two_legal_redesigns" and root_id in {"C1", "C3"} and not is_redesign_child)
                or (name == "second_redesign" and root_id == "C1")):
            return LegalReview(candidateId=candidate_id, route=LegalRoute.REDESIGN_WITHIN_LINEAGE,
                productionStatus="REDESIGNABLE", sourceStatus="MOCK_OFFICIAL_EVIDENCE",
                safeSummary="동일 lineage 내 통제 보완이 필요합니다.", redesignRequirements=["자격 보유 주체로 제한"])
        if name in {"legal_replan", "replan_anchor_drift", "no_reserve_legal_replan"} and root_id == "C1" and "REPLAN" not in candidate_id:
            return LegalReview(candidateId=candidate_id, route=LegalRoute.REPLAN_REQUIRED,
                productionStatus="REJECTED", sourceStatus="MOCK_OFFICIAL_EVIDENCE",
                safeSummary="핵심 거래 구조를 유지할 수 없습니다.", prohibitedVariants=[candidate.solutionMechanism])
        if name == "lock_legal_conflict" and root_id == "C1":
            return LegalReview(candidateId=candidate_id, route=LegalRoute.NEEDS_INPUT,
                productionStatus="NEEDS_FACTS", sourceStatus="MOCK_OFFICIAL_EVIDENCE",
                safeSummary="법률 통제와 사용자 LOCK이 충돌합니다.", conflictingLock="channels",
                currentValue=candidate.channels, requiredLegalChange="자격 보유 파트너 채널로 변경",
                reason="LOCKED 채널은 엔진이 변경할 수 없습니다.",
                possibleUserAction="채널 LOCK을 해제하거나 적법한 채널을 확정하세요.")
        if name == "candidate_needs_input" and root_id == "C1":
            return LegalReview(candidateId=candidate_id, route=LegalRoute.NEEDS_INPUT,
                productionStatus="NEEDS_FACTS", inputScope="CANDIDATE", sourceStatus="MOCK_OFFICIAL_EVIDENCE",
                safeSummary="이 후보에만 필요한 외부 사실 확인이 남아 있습니다.",
                reason="후보별 자격 보유 여부를 확인해야 합니다.", possibleUserAction="외부 사실을 입력하세요.")
        return LegalReview(candidateId=candidate_id, route=LegalRoute.ACCEPT,
            productionStatus="IMPLEMENTABLE_WITH_CONTROLS", sourceStatus="MOCK_OFFICIAL_EVIDENCE",
            safeSummary="구조화된 MOCK 공식근거 계약상 수용 가능합니다.", requiredControls=["조건을 명확히 고지"],
            requiredPartnersAndQualifications=["필요 시 자격 보유 파트너 사용"],
            officialEvidenceReferences=[{"sourceType": "OFFICIAL_LAW", "officialIdentifier": "MOCK-LAW",
                "articleReference": "제1조", "officialSourceUri": "https://www.law.go.kr/mock",
                "contentHash": "sha256:" + "0" * 64}])

    async def redesign(self, seed, plan, candidate, requirements, candidate_index):
        from .candidate_governance import candidate_result_to_draft, normalize_candidate_draft
        draft = candidate_result_to_draft(candidate).model_copy(update={
            "partnerModel": candidate.partnerModel + " · Legal 요구를 반영한 제한 조건",
            "partnerRequirements": list(dict.fromkeys([
                *candidate.partnerRequirements, "Legal redesign 요구에 맞는 파트너만 사용",
            ])),
            "qualificationRequirements": list(dict.fromkeys([
                *candidate.qualificationRequirements, "설계에서 정한 자격 보유 주체만 참여",
            ])),
        })
        return normalize_candidate_draft(draft, seed,
                                         ExplorationBreadth(candidate.generationStrategy), candidate_index)

    async def complete_legal_facts(self, seed, plan, candidate, requirements, candidate_index):
        requested = {item.field: item for item in requirements}
        patch: dict[str, Any] = {}
        if "platformRole" in requested:
            patch["platformRole"] = "운영사가 고객 접점과 거래 기준을 운영"
        if "providerRole" in requested:
            patch["providerRole"] = "운영사 또는 명시된 외부 주체가 서비스 이행 책임을 부담"
        if "sellerRole" in requested:
            patch["sellerRole"] = "운영사가 고객과 계약하고 판매 책임을 부담"
        if "intermediaryRole" in requested:
            transaction = canonical_json(candidate.transactionFlow).casefold()
            patch["intermediaryRole"] = (
                "플랫폼이 수요자와 제공자의 거래를 연결·중개"
                if any(marker in transaction for marker in ("중개", "매칭", "연결", "판매자", "제공자"))
                else "제3자 거래를 중개하지 않음")
        if "transactionFlow" in requested:
            patch["transactionFlow"] = ["고객이 운영사와 계약하고 운영사가 서비스를 제공"]
        if "paymentFlow" in requested:
            patch["paymentFlow"] = ["고객이 운영사에 결제하고 운영사가 대금을 수취"]
        if "personalDataUsage" in requested:
            physical = canonical_json([candidate.solutionMechanism, candidate.physicalActivities,
                                       candidate.transactionFlow]).casefold()
            patch["personalDataUsage"] = (["서비스 담당자 이름과 연락처를 배송·방문·회수 일정 안내 목적으로 처리"]
                                           if any(marker in physical for marker in
                                                  ("배송", "방문", "회수", "설치", "픽업"))
                                           else ["사용자 계정 이메일을 로그인과 서비스 제공 목적으로 처리"])
        if "physicalActivities" in requested:
            if requested["physicalActivities"].reasonType == "FACT_CONSISTENCY_REPAIR":
                patch["physicalActivities"] = ["물리적 이행 없음"]
            else:
                patch["physicalActivities"] = [
                    f"운영사가 {plan.fulfillmentApproach}에 필요한 배송·방문·설치 등 실제 물리 이행을 수행"]
        if "partnerRequirements" in requested:
            patch["partnerRequirements"] = (
                ["외부 사업 파트너를 사용하지 않음"]
                if requested["partnerRequirements"].reasonType == "FACT_CONSISTENCY_REPAIR"
                else [f"사업 이행을 맡는 외부 계약 파트너가 {plan.partnerApproach} 역할을 수행"])
        if "targetRegion" in requested:
            patch["targetRegion"] = "대한민국"
        if "channels" in requested:
            patch["channels"] = "운영사가 관리하는 웹·앱 고객 접점"
        return patch

    async def repair_redesign_compliance(self, seed, plan, parent, child, requirements, candidate_index):
        from .candidate_governance import candidate_result_to_draft
        field_markers = {
            "platformRole": ("platformRole", "플랫폼"),
            "providerRole": ("providerRole", "제공", "이행"),
            "sellerRole": ("sellerRole", "판매"),
            "intermediaryRole": ("intermediaryRole", "중개"),
            "transactionFlow": ("transactionFlow", "거래", "주문", "계약"),
            "paymentFlow": ("paymentFlow", "결제", "정산"),
            "personalDataUsage": ("personalDataUsage", "개인정보", "데이터"),
            "physicalActivities": ("physicalActivities", "물리", "배송", "현장"),
            "partnerRequirements": ("partnerRequirements", "파트너", "제휴"),
            "targetRegion": ("targetRegion", "지역"),
            "channels": ("channels", "채널", "접점"),
        }
        structured = []
        for field, markers in field_markers.items():
            if any(any(marker in requirement for marker in markers) for requirement in requirements):
                structured.append(LegalFactCompletionRequirement(
                    field=field, reasonType=("ROLE_MISMATCH" if field.endswith("Role") else
                        "TRANSACTION_INCOMPLETE" if field == "transactionFlow" else
                        "PAYMENT_INCOMPLETE" if field == "paymentFlow" else
                        "GENERAL_FACT_INCOMPLETE"),
                    dependencyType=None, instruction=f"redesign 요구에 따라 {field}를 보완"))
        patch = await self.complete_legal_facts(seed, plan, child, structured, candidate_index)
        return candidate_result_to_draft(child).model_copy(update={
            field: value for field, value in patch.items()})

    async def judge_distinctness(self, kind, left, right):
        if kind == "OPPORTUNITY_SCOPE" and "무관" in canonical_json(right):
            return SemanticDistinctnessResult(
                decision="OUT_OF_SCOPE", overlappingMechanics=[], materiallyDifferentMechanics=["opportunity"],
                safeSummary="명시적으로 원 Opportunity와 무관한 대상/문제로 이동했습니다.")
        same = canonical_json(left) == canonical_json(right)
        return SemanticDistinctnessResult(
            decision="DUPLICATE" if same else "VARIANT",
            overlappingMechanics=["semantic_thesis"] if same else ["business_family"],
            materiallyDifferentMechanics=[] if same else ["user_visible_thesis"],
            safeSummary="의미상 같은 Concept입니다." if same else "같은 Family의 의미 있는 Variant입니다.")

    async def judge_fidelity(self, plan, candidate):
        return SemanticFidelityResult(decision="ADAPTED", matchedMechanics=["conceptThesis"],
                                      missingMechanics=[], safeSummary="Plan identity를 유지한 구체화입니다.")

    async def classify_architectures(self, items):
        results = []
        for item in items:
            architecture = dict(item["currentArchitecture"])
            text = item.get("businessText", "").casefold()
            if any(value in text for value in ("marketplace", "마켓플레이스", "양면시장")):
                architecture["businessRole"] = "MARKETPLACE"
            elif any(value in text for value in ("saas", "소프트웨어", "업무 도구")):
                architecture["businessRole"] = "SAAS_TOOL"
            elif any(value in text for value in ("중개", "매칭", "연결")):
                architecture["businessRole"] = "INTERMEDIARY"
            elif any(value in text for value in ("직접 운영", "직접 제공", "운영사")):
                architecture["businessRole"] = "PRINCIPAL_OPERATOR"
            results.append(SemanticArchitectureClassification.model_validate({
                "entityId": item["entityId"],
                "architecture": architecture,
                "confidence": {key: ("LOW" if value == "OTHER" else "HIGH")
                               for key, value in architecture.items()
                               if key not in {"dataDependency", "physicalDependency"}},
                "safeSummary": "규칙 근거가 없는 축은 OTHER로 보존했습니다.",
            }))
        return results

    async def classify_hypotheses(self, items):
        results = []
        for item in items:
            text = str(item.get("value") or "").casefold()
            cross_field = (item["hypothesisType"] == "TARGET_REGION"
                           and any(marker in text for marker in
                                   ("가격", "견적", "구독", "수수료", "채널", "앱", "웹")))
            results.append(SemanticHypothesisResult(
                hypothesisType=item["hypothesisType"], decision="INVALID" if cross_field else "VALID",
                safeReason=("다른 가설 필드의 값으로 판단했습니다." if cross_field else
                            "placeholder가 아닌 구체적 후보값으로 판단했습니다.")))
        return results

    async def classify_business_roles(self, items):
        markers = {
            "platformRole": ("운영", "관리", "접점", "기준", "플랫폼"),
            "providerRole": ("제공", "수행", "이행", "공급", "전달", "전문가", "파트너"),
            "sellerRole": ("판매", "계약", "청구", "수취", "과금", "요금"),
            "intermediaryRole": ("중개", "연결", "매칭", "알선", "거래 성사"),
        }
        results = []
        for item in items:
            field = item["field"]
            text = str(item.get("value") or "").casefold()
            decision = "MATCH" if any(marker in text for marker in markers[field]) else "UNKNOWN"
            results.append(BusinessRoleSemanticItem(
                candidateId=item["candidateId"], field=field, decision=decision,
                safeReason=("요청된 역할의 주체 또는 책임을 의미상 확인했습니다."
                            if decision == "MATCH" else "제공된 맥락만으로 해당 역할을 확정할 수 없습니다.")))
        return results

    async def classify_legal_fact_dependencies(self, items):
        results = []
        for item in items:
            dependency_type = item["dependencyType"]
            text = canonical_json(item).casefold()
            if dependency_type == "PERSONAL_DATA":
                if any(marker in text for marker in ("개인정보를 처리하지 않", "익명", "로그인 없음")):
                    decision, reason = "NOT_REQUIRED", "개인 단위 식별정보를 처리하지 않는 구조가 명시되었습니다."
                elif any(marker in text for marker in (
                        "이메일", "전화번호", "연락처", "주소", "예약자", "회원", "사용자 계정")):
                    decision, reason = "REQUIRED", "서비스 이행에 개인 단위 정보 처리가 필요합니다."
                else:
                    decision, reason = "NOT_REQUIRED", "입력 맥락에 개인 단위 정보 처리 구조가 없습니다."
            elif dependency_type == "PHYSICAL_ACTIVITY":
                if any(marker in text for marker in ("배송", "방문", "픽업", "설치", "수거", "현장")):
                    decision, reason = "REQUIRED", "사업 이행에 물리 활동이 포함됩니다."
                elif any(marker in text for marker in (
                        "디지털", "digital", "온라인", "saas", "api", "소프트웨어")):
                    decision, reason = "NOT_REQUIRED", "확인된 제공 구조는 디지털 이행입니다."
                else:
                    decision, reason = "UNKNOWN", "물리 활동 포함 여부를 확정할 근거가 부족합니다."
            else:
                architecture = item.get("descriptor", {}).get("architecture", {})
                p2p = (architecture.get("operatingModel") == "PEER_TO_PEER"
                       and architecture.get("businessRole") in {"MARKETPLACE", "INTERMEDIARY"})
                if p2p and not any(marker in text for marker in (
                        "외부 공급업체", "제휴 업체", "배송 파트너", "운영 위탁")):
                    decision, reason = "NOT_REQUIRED", "판매자와 구매자는 P2P 참가자이며 별도 사업 파트너가 아닙니다."
                elif (architecture.get("partnerModel") in {"PARTNER_NETWORK", "EXPERT_NETWORK"}
                      or any(marker in text for marker in (
                          "외부 공급업체", "제휴 업체", "배송 파트너", "운영 위탁", "파트너 네트워크"))):
                    decision, reason = "REQUIRED", "사업 이행에 외부 계약·운영 파트너가 필요합니다."
                elif (architecture.get("partnerModel") == "OWN_OPERATED"
                      or any(marker in text for marker in ("직접 운영", "외부 파트너 없음"))):
                    decision, reason = "NOT_REQUIRED", "직접 운영 구조이며 외부 사업 파트너가 없습니다."
                else:
                    decision, reason = "UNKNOWN", "참여자와 사업 파트너를 구분할 근거가 부족합니다."
            results.append(LegalFactDependencySemanticItem(
                candidateId=item["candidateId"], dependencyType=dependency_type,
                decision=decision, safeReason=reason))
        return results

    async def replacement_plans(self, seed, design, existing_plans, count):
        return await self.replenish_plans(seed, design, existing_plans, [], count, 1)


class LivePortfolioProvider(PortfolioProvider):
    async def plan_pool(self, seed, design, pool_size):
        prompt = """같은 OpportunityKernel 안에서 사용자가 비교할 가치가 있는 Plan Business Draft를 요청 수만큼 만든다.
각 Plan은 targetSegment, problemFocus, useContext, valueProposition, offerThesis, solutionThesis를 명시한다.
운영 주체, 판매·계약 주체, 파트너 역할, 고객 거래 방식, 수익 방식, 이행 방식, 고객 접점을
각각 실제 business sentence로 구체적으로 작성한다.
canonical code, familyId, mechanics enum을 생성하지 않는다. 같은 Architecture라도 Thesis가 의미 있게 다른 Variant는 허용한다.
이름만 다른 중복은 만들지 않는다. JSON key를 제외한 사용자-facing 내용은 한국어(ko-KR)로 작성한다. strict schema만 반환한다."""
        schema = PlanDraftPool.model_json_schema(); assert_strict_compatible(schema, "concept_portfolio_plan_draft_v3")
        raw = await execute_structured_prompt(prompt, json.dumps({
            "promptVersion": PLAN_PROMPT_VERSION, "seed": seed.model_dump(mode="json"),
            "opportunityKernel": design.opportunityKernel.model_dump(mode="json"), "requestedPoolSize": pool_size,
        }, ensure_ascii=False, sort_keys=True), response_schema=schema,
            schema_name="concept_portfolio_plan_draft_v3", task_type="CONCEPT_PORTFOLIO_V2_PLAN")
        result = PlanDraftPool.model_validate(raw)
        failures = sorted({field for plan in result.plans for field in plan_language_failures(plan)})
        if failures:
            correction = await execute_structured_prompt(
                "사업 의미를 바꾸지 말고 사용자-facing 문구만 한국어로 1회 교정한다. canonical code는 추가하지 않는다.",
                json.dumps({"previousResult": result.model_dump(mode="json"), "languageFailures": failures}, ensure_ascii=False),
                response_schema=schema, schema_name="concept_portfolio_plan_draft_v3",
                task_type="CONCEPT_PORTFOLIO_V2_PLAN_LANGUAGE_CORRECTION")
            result = PlanDraftPool.model_validate(correction)
        return result.plans

    async def replenish_plans(self, seed, design, existing_plans, rejected, count, round_number):
        schema = PlanDraftPool.model_json_schema(); assert_strict_compatible(schema, "concept_portfolio_replenishment_v3")
        raw = await execute_structured_prompt(
            "기존 Concept과 사용자 관점에서 의미 있게 다른 새 Thesis 또는 Architecture Plan을 요청 수만큼 제안한다. 모든 Architecture가 달라야 한다고 강제하지 않는다. 사용자-facing 문구는 한국어이며 canonical code는 생성하지 않는다.",
            json.dumps({"seed": seed.model_dump(mode="json"), "opportunityKernel": design.opportunityKernel.model_dump(mode="json"),
                        "existingPlans": [p.model_dump(mode="json") for p in existing_plans],
                        "rejectedPlans": rejected, "missingPortfolioCoverage": count,
                        "round": round_number, "requestedPoolSize": count}, ensure_ascii=False, sort_keys=True),
            response_schema=schema, schema_name="concept_portfolio_replenishment_v3",
            task_type="CONCEPT_PORTFOLIO_V2_REPLENISHMENT")
        return PlanDraftPool.model_validate(raw).plans

    async def expand(self, seed, plan, candidate_index):
        prompt = """통과된 generic PortfolioPlan을 ConceptCandidateDraft로 확장한다.
Plan의 target/use/value/offer/solution thesis와 핵심 differentiator, 사용자 LOCK을 보존한다.
세부 Architecture 구체화는 가능하지만 다른 Concept으로 교체하지 않는다. governance state를 사업값으로 쓰지 않는다.
platformRole, providerRole, sellerRole, intermediaryRole에는 실제 주체와 책임을 명시하고,
transactionFlow와 paymentFlow에는 주문·계약·제공·결제 수취·정산 주체를 구체적으로 작성한다.
personalDataUsage, physicalActivities, partnerRequirements는 실제 사업 흐름상 해당할 때 처리 목적·수행 주체·파트너 기능을 작성한다.
physicalActivities는 배송·포장·현장 방문·설치처럼 물리 세계에서 수행되는 활동만 포함한다.
AI 분석, 디지털 피드백, 화면 처리, 온라인 상호작용은 physicalActivities가 아니며 순수 디지털이면 빈 배열로 둔다.
qualificationRequirements는 Concept가 애초에 특정 자격 보유 주체를 사용하도록 설계한 경우에만 작성한다.
targetRegion, revenueModel, price, channels, differentiators에는 '미제공', '검증 필요', '추후 결정'이 아니라
사용자가 검토할 수 있는 실제 geography·수익 방식·가격/범위 가설·채널·차별화 proposal을 작성한다.
'필요 시 확인', '필요한 자격', '관련 자격' 같은 generic placeholder를 넣지 않는다.
JSON key를 제외한 사용자-facing 내용은 한국어(ko-KR)로 작성하고 strict schema만 반환한다."""
        schema = ConceptCandidateDraft.model_json_schema(); assert_strict_compatible(schema, "concept_portfolio_candidate_v3")
        raw = await execute_structured_prompt(prompt, json.dumps({
            "promptVersion": CANDIDATE_PROMPT_VERSION, "seed": seed.model_dump(mode="json"),
            "plan": plan.model_dump(mode="json", exclude={"descriptor"})}, ensure_ascii=False, sort_keys=True),
            response_schema=schema, schema_name="concept_portfolio_candidate_v3",
            task_type="CONCEPT_PORTFOLIO_V2_CANDIDATE")
        result = ConceptCandidateDraft.model_validate(raw)
        failures = candidate_language_failures(result)
        if failures:
            correction = await execute_structured_prompt(
                "사업 의미와 LOCK을 바꾸지 말고 사용자-facing 문구만 한국어로 1회 교정한다.",
                json.dumps({"previousResult": result.model_dump(mode="json"), "languageFailures": failures}, ensure_ascii=False),
                response_schema=schema, schema_name="concept_portfolio_candidate_v3",
                task_type="CONCEPT_PORTFOLIO_V2_CANDIDATE_LANGUAGE_CORRECTION")
            result = ConceptCandidateDraft.model_validate(correction)
        return result

    async def regenerate_candidate(self, seed, design, plan, previous, failure_summary,
                                   missing_identity, candidate_index):
        prompt = """이전 Candidate에서 Plan fidelity를 잃은 부분만 수정해 동일 Plan을 다시 구현한다.
새 Concept을 만들지 않는다. target/use context, value proposition, offer thesis, solution thesis,
differentiating mechanics와 사용자 LOCK을 반드시 보존한다. 사용자-facing 내용은 한국어로 작성한다.
qualificationRequirements 등 Legal fact 필드는 구체적 설계상 실제 근거가 있을 때만 작성하며
'필요 시 확인', '필요한 자격' 같은 placeholder를 넣지 않는다. strict schema만 반환한다."""
        schema = ConceptCandidateDraft.model_json_schema()
        assert_strict_compatible(schema, "concept_portfolio_candidate_fidelity_regeneration_v1")
        candidate_fields = ("targetUsers", "problemScenario", "coreValue", "conceptDefinition",
                            "solutionMechanism", "featureSet", "operatingModel")
        raw = await execute_structured_prompt(prompt, json.dumps({
            "seed": seed.model_dump(mode="json"),
            "opportunityKernel": design.opportunityKernel.model_dump(mode="json"),
            "plan": plan.model_dump(mode="json", exclude={"descriptor"}),
            "previousCandidate": {key: getattr(previous, key) for key in candidate_fields},
            "fidelityFailureSummary": failure_summary,
            "missingIdentityComponents": missing_identity,
            "candidateIndex": candidate_index,
        }, ensure_ascii=False, sort_keys=True), response_schema=schema,
            schema_name="concept_portfolio_candidate_fidelity_regeneration_v1",
            task_type="CONCEPT_PORTFOLIO_V2_CANDIDATE_FIDELITY_REGENERATION")
        result = ConceptCandidateDraft.model_validate(raw)
        failures = candidate_language_failures(result)
        if failures:
            correction = await execute_structured_prompt(
                "사업 의미와 LOCK을 바꾸지 말고 사용자-facing 문구만 한국어로 1회 교정한다.",
                json.dumps({"previousResult": result.model_dump(mode="json"),
                            "languageFailures": failures}, ensure_ascii=False),
                response_schema=schema,
                schema_name="concept_portfolio_candidate_fidelity_regeneration_v1",
                task_type="CONCEPT_PORTFOLIO_V2_CANDIDATE_LANGUAGE_CORRECTION")
            result = ConceptCandidateDraft.model_validate(correction)
        return result

    async def review_legal(self, candidate_id, candidate, seed):
        return await CurrentLegalAdapter().review(candidate_id, candidate, seed)

    async def redesign(self, seed, plan, candidate, requirements, candidate_index):
        from app.tasks.concept_redesign.service import execute_concept_redesign
        legal_input = CurrentLegalAdapter().task_input(candidate, seed)
        raw = await execute_concept_redesign({"candidate": candidate.model_dump(mode="json"),
            "safeConstraints": requirements, "prohibitedVariants": [], "designGaps": requirements,
            "legalFactPattern": legal_input["legalFactPattern"]})
        return ConceptCandidateResult.model_validate(raw)

    async def complete_legal_facts(self, seed, plan, candidate, requirements, candidate_index):
        allowed_fields = completion_field_names(requirements)
        prompt = """Candidate의 정체성과 요청되지 않은 필드는 절대 수정하지 않는다.
completionRequirements에 열거되어 schema에 존재하는 필드만 구체적인 값으로 보완한다.
요청되지 않은 필드는 schema에 존재하지 않으며 절대 반환하지 않는다.
필요한 dependency가 실제로 없으면 새 역할·파트너·활동을 발명하지 말고 명시적 부재만 작성한다.
법률 판단, 법령명, 면허·허가 보유, 합법성 결론은 생성하지 않는다. 사용자-facing 값은 한국어다.
요청 필드만 가진 strict JSON object를 반환한다."""
        schema = legal_fact_completion_schema(requirements)
        schema_name = "concept_legal_fact_completion_dynamic_v3_" + "_".join(allowed_fields)
        assert_strict_compatible(schema, schema_name)
        raw = await execute_structured_prompt(prompt, json.dumps({
            "seed": seed.model_dump(mode="json"), "plan": plan.model_dump(mode="json"),
            "candidate": candidate.model_dump(mode="json"),
            "completionRequirements": [item.model_dump(mode="json") for item in requirements],
            "allowedFields": allowed_fields,
            "candidateIndex": candidate_index,
        }, ensure_ascii=False, sort_keys=True), response_schema=schema,
            schema_name=schema_name,
            task_type="COMPLETE_CANDIDATE_LEGAL_FACTS_PATCH")
        return raw

    async def repair_redesign_compliance(self, seed, plan, parent, child, requirements, candidate_index):
        prompt = """이미 수행된 Legal redesign에서 미충족 요구만 보완한다. 새 Concept을 만들거나
target/value/offer/solution/Plan identity를 바꾸지 않는다. 법령명·법률결론·보유하지 않은 자격이나
계약을 만들지 않는다. 실제 사업 역할과 거래 흐름만 한국어로 구체화하고 strict schema만 반환한다."""
        schema = ConceptCandidateDraft.model_json_schema()
        assert_strict_compatible(schema, "concept_legal_redesign_compliance_repair_v1")
        raw = await execute_structured_prompt(prompt, json.dumps({
            "seed": seed.model_dump(mode="json"), "plan": plan.model_dump(mode="json"),
            "parentCandidate": parent.model_dump(mode="json"),
            "redesignedCandidate": child.model_dump(mode="json"),
            "unsatisfiedRequirements": requirements, "candidateIndex": candidate_index,
        }, ensure_ascii=False, sort_keys=True), response_schema=schema,
            schema_name="concept_legal_redesign_compliance_repair_v1",
            task_type="LEGAL_REDESIGN_COMPLIANCE_REPAIR")
        return ConceptCandidateDraft.model_validate(raw)

    async def judge_distinctness(self, kind, left, right):
        schema = SemanticDistinctnessResult.model_json_schema(); assert_strict_compatible(schema, "concept_relation_v3")
        raw = await execute_structured_prompt(
            "Concept Thesis와 Business Architecture를 분리해 DUPLICATE, VARIANT, DISTINCT 중 하나로 판정한다. VARIANT는 정상 후보이며 이름만 다른 결과만 DUPLICATE다.",
            json.dumps({"kind": kind, "left": left, "right": right}, ensure_ascii=False, sort_keys=True),
            response_schema=schema, schema_name="concept_relation_v3", task_type="CONCEPT_PORTFOLIO_V2_RELATION")
        return SemanticDistinctnessResult.model_validate(raw)

    async def judge_fidelity(self, plan, candidate):
        schema = SemanticFidelityResult.model_json_schema(); assert_strict_compatible(schema, "concept_fidelity_v3")
        raw = await execute_structured_prompt(
            "Candidate가 Plan의 Opportunity, core value, solution thesis, 핵심 differentiator를 보존했는지 PASS/ADAPTED/FAIL로 판정한다. 세부 운영 구체화는 ADAPTED로 허용한다.",
            json.dumps(_fidelity_context(plan, candidate),
                       ensure_ascii=False, sort_keys=True), response_schema=schema,
            schema_name="concept_fidelity_v3", task_type="CONCEPT_PORTFOLIO_V2_PLAN_FIDELITY")
        return SemanticFidelityResult.model_validate(raw)

    async def classify_architectures(self, items):
        schema = SemanticArchitectureBatch.model_json_schema()
        assert_strict_compatible(schema, "concept_architecture_classifier_v2")
        raw = await execute_structured_prompt(
            "사업 설명을 system enum으로만 분류한다. 근거가 부족하면 OTHER를 사용한다. 임의 code를 만들지 않는다. 각 축 confidence를 HIGH/MEDIUM/LOW로 반환하고 입력 entityId를 그대로 보존한다.",
            json.dumps({"items": items}, ensure_ascii=False, sort_keys=True),
            response_schema=schema, schema_name="concept_architecture_classifier_v2",
            task_type="CONCEPT_PORTFOLIO_V2_ARCHITECTURE_CLASSIFICATION")
        return SemanticArchitectureBatch.model_validate(raw).results

    async def classify_hypotheses(self, items):
        schema = SemanticHypothesisBatch.model_json_schema()
        assert_strict_compatible(schema, "concept_hypothesis_semantic_v1")
        raw = await execute_structured_prompt(
            "각 값이 해당 사업 가설 필드에 실제로 답하는 값인지 VALID/INVALID로만 판정한다. 새 가설을 생성하거나 값을 수정하지 않는다.",
            json.dumps({"items": items}, ensure_ascii=False, sort_keys=True),
            response_schema=schema, schema_name="concept_hypothesis_semantic_v1",
            task_type="CONCEPT_PORTFOLIO_V2_HYPOTHESIS_SEMANTIC")
        return SemanticHypothesisBatch.model_validate(raw).results

    async def classify_business_roles(self, items):
        schema = BusinessRoleSemanticBatch.model_json_schema()
        assert_strict_compatible(schema, "concept_business_role_semantic_v2")
        raw = await execute_structured_prompt(
            """각 item의 field가 묻는 사업 역할에 value가 실제로 답하는지만 판정한다.
다른 role fields, actorRoles, 거래·결제 흐름, 운영·파트너 모델, solution과 system descriptor는
해석 맥락일 뿐 새 사실을 만들 근거가 아니다. 역할이 의미상 있으면 MATCH, 명시적으로 없으면
EXPLICIT_ABSENCE, 다른 역할 설명이면 MISMATCH, 근거 부족이면 UNKNOWN을 반환한다.
법률 판단, 사업 설계, 문구 보완은 하지 않는다. candidateId/field identity를 정확히 보존한다.""",
            json.dumps({"items": items}, ensure_ascii=False, sort_keys=True),
            response_schema=schema, schema_name="concept_business_role_semantic_v2",
            task_type="CONCEPT_PORTFOLIO_V2_BUSINESS_ROLE_SEMANTIC_CLASSIFICATION")
        return BusinessRoleSemanticBatch.model_validate(raw).results

    async def classify_legal_fact_dependencies(self, items):
        schema = LegalFactDependencySemanticBatch.model_json_schema()
        assert_strict_compatible(schema, "concept_legal_fact_dependency_v2")
        raw = await execute_structured_prompt(
            """각 item의 사업 사실만 보고 PERSONAL_DATA, PHYSICAL_ACTIVITY, BUSINESS_PARTNER
dependency가 실제로 필요한지 REQUIRED/NOT_REQUIRED/UNKNOWN 중 하나로 판정한다.
법률 적용 여부를 판단하거나 새 사업 사실을 만들지 않는다. P2P 판매자·구매자 같은 거래 참가자를
별도 계약·운영 파트너로 자동 간주하지 않는다. candidateId/dependencyType identity를 정확히 보존한다.""",
            json.dumps({"items": items}, ensure_ascii=False, sort_keys=True),
            response_schema=schema, schema_name="concept_legal_fact_dependency_v2",
            task_type="CONCEPT_PORTFOLIO_V2_LEGAL_FACT_DEPENDENCY_CLASSIFICATION")
        return LegalFactDependencySemanticBatch.model_validate(raw).results

    async def replacement_plans(self, seed, design, existing_plans, count):
        return await self.replenish_plans(seed, design, existing_plans, [], count, 1)


class ReplayMiss(RuntimeError):
    pass


class ProviderGateway:
    """Core business policy와 분리된 외부 작업/record/replay 경계."""

    def __init__(self, mode: ProviderMode | str = ProviderMode.MOCK, *, recordings_dir: Path | None = None,
                 provider: PortfolioProvider | None = None, record_mock_fixtures: bool = False):
        self.mode = ProviderMode(mode)
        self.recordings_dir = recordings_dir
        self.provider = provider or (LivePortfolioProvider() if self.mode == ProviderMode.LIVE else MockPortfolioProvider())
        self.record_mock_fixtures = record_mock_fixtures
        self.usage = ProviderUsage(modeCounts={self.mode.value: 0})
        self.last_failure: dict[str, Any] | None = None

    @staticmethod
    def request_hash(operation, payload, *, operation_version="v1", prompt_version="unversioned", schema_version="2.0"):
        input_hash = "sha256:" + hashlib.sha256(canonical_json(payload).encode()).hexdigest()
        value = canonical_json({"operation": operation, "operationVersion": operation_version,
                                "promptVersion": prompt_version, "schemaVersion": schema_version,
                                "canonicalInputHash": input_hash})
        return hashlib.sha256(value.encode()).hexdigest()

    @staticmethod
    def redact_secrets(value):
        markers = ("secret", "password", "authorization", "api_key", "apikey", "token", "moleg_key")
        if isinstance(value, dict):
            return {key: "[REDACTED]" if any(m in key.casefold() for m in markers)
                    else ProviderGateway.redact_secrets(item) for key, item in value.items()}
        if isinstance(value, list): return [ProviderGateway.redact_secrets(item) for item in value]
        return value

    @staticmethod
    def json_value(value):
        if hasattr(value, "model_dump"): return ProviderGateway.json_value(value.model_dump(mode="json"))
        if isinstance(value, dict): return {key: ProviderGateway.json_value(item) for key, item in value.items()}
        if isinstance(value, (list, tuple)): return [ProviderGateway.json_value(item) for item in value]
        return value

    def _validate_keyed_batch(self, expected_keys, results, key_fn, contract_name, schema_name, *,
                              safe_extra_values: set[str] | None = None):
        """배열 순서가 아니라 business identity key의 완전한 일대일 대응을 검증한다."""
        expected = list(expected_keys)
        actual = [key_fn(item) for item in results]
        expected_set, actual_set = set(expected), set(actual)
        if len(expected_set) != len(expected):
            raise ProviderFailure(
                "INVALID_REQUEST", f"{contract_name}_REQUEST_DUPLICATE_IDENTITY",
                400, False, schema_name=schema_name)
        duplicate = sorted({key for key in actual if actual.count(key) > 1})
        missing = sorted(expected_set - actual_set)
        extra = sorted(actual_set - expected_set)
        requested_candidates = {key[0] for key in expected if len(key) == 2}
        safe_extra = []
        unsafe_extra = list(extra)
        if safe_extra_values is not None:
            safe_extra = [key for key in extra
                          if len(key) == 2 and key[0] in requested_candidates
                          and key[1] in safe_extra_values]
            unsafe_extra = [key for key in extra if key not in safe_extra]
        if duplicate or missing or unsafe_extra:
            reason = (
                f"{contract_name}_BATCH_IDENTITY_MISMATCH:"
                f"missing={missing};duplicate={duplicate};extra={unsafe_extra}")
            raise ProviderFailure(
                "RESULT_SCHEMA_INVALID", reason, 502, False, schema_name=schema_name,
                safe_diagnostics={"missingKeys": missing, "duplicateKeys": duplicate,
                                  "extraKeys": unsafe_extra})
        if safe_extra:
            diagnostic = {
                "action": "BATCH_EXTRA_RESULTS_IGNORED",
                "contract": contract_name,
                "ignoredKeys": [list(key) for key in safe_extra],
                "safeSummary": "요청하지 않은 추가 의미판정 결과를 사용하지 않고 폐기했습니다.",
            }
            self.usage = self.usage.model_copy(update={
                "batchDiagnostics": [*self.usage.batchDiagnostics, diagnostic],
            })
        by_key = {key_fn(item): item for item in results}
        return [by_key[key] for key in expected]

    def note_external_call(self, stage):
        self.usage.topLevelExternalOperations += 1
        self.usage.topLevelOperationsByStage[stage] = self.usage.topLevelOperationsByStage.get(stage, 0) + 1
        self.usage.externalProviderCalls += 1; self.usage.totalProviderCalls = self.usage.externalProviderCalls
        self.usage.externalCallsByStage[stage] = self.usage.externalCallsByStage.get(stage, 0) + 1

    async def call(self, stage, operation, payload, fn, response_model=None, *, operation_version="v1",
                   prompt_version="unversioned", schema_version="2.0"):
        input_hash = "sha256:" + hashlib.sha256(canonical_json(payload).encode()).hexdigest()
        request_hash = self.request_hash(operation, payload, operation_version=operation_version,
                                         prompt_version=prompt_version, schema_version=schema_version)
        started = time.perf_counter(); self.usage.logicalOperations += 1
        self.usage.callsByStage[stage] = self.usage.callsByStage.get(stage, 0) + 1
        self.usage.modeCounts[self.mode.value] = self.usage.modeCounts.get(self.mode.value, 0) + 1
        try:
            if self.mode == ProviderMode.REPLAY:
                if not self.recordings_dir: raise ReplayMiss("REPLAY_MISS: recordings_dir가 없습니다")
                path = self.recordings_dir / f"{request_hash}.json"
                if not path.exists(): raise ReplayMiss(f"REPLAY_MISS: {operation} {request_hash}")
                record = json.loads(path.read_text(encoding="utf-8"))
                if (record.get("canonicalRequestHash") != request_hash
                        or record.get("operationVersion") != operation_version
                        or record.get("promptVersion") != prompt_version
                        or record.get("schemaVersion") != schema_version
                        or record.get("canonicalInputHash") != input_hash):
                    raise ReplayMiss(f"REPLAY_MISS: hash mismatch {request_hash}")
                raw = record["providerResponse"]
                return response_model.model_validate(raw) if response_model else raw
            retry = 0
            while True:
                try:
                    if self.mode == ProviderMode.LIVE: self.note_external_call(stage)
                    result = await fn(); break
                except ProviderFailure as failure:
                    if self.mode != ProviderMode.LIVE or not failure.retryable or retry >= 2: raise
                    retry += 1; self.usage.retries += 1
                    await asyncio.sleep(min(15_000, max(1_000, failure.retry_after_ms or (2_000, 5_000)[retry - 1])) / 1000)
            if (self.mode == ProviderMode.LIVE or self.record_mock_fixtures) and self.recordings_dir:
                self.recordings_dir.mkdir(parents=True, exist_ok=True)
                record = {"taskType": operation, "operationVersion": operation_version,
                    "promptVersion": prompt_version, "schemaVersion": schema_version,
                    "canonicalInputHash": input_hash, "canonicalRequestHash": request_hash,
                    "redactedRequest": self.redact_secrets(payload), "providerResponse": self.json_value(result),
                    "durationMs": int((time.perf_counter() - started) * 1000),
                    "timestamp": datetime.now(timezone.utc).isoformat(),
                    "providerMetadata": {"mode": "LIVE" if self.mode == ProviderMode.LIVE else "TEST_FIXTURE",
                                         "retries": retry}}
                (self.recordings_dir / f"{request_hash}.json").write_text(
                    json.dumps(record, ensure_ascii=False, indent=2, default=str), encoding="utf-8")
            return result
        except ProviderFailure as failure:
            self.last_failure = {"taskType": operation, "schemaName": failure.schema_name,
                "providerStatus": failure.upstream_status or failure.status_code,
                "providerErrorType": failure.provider_error_type, "providerErrorParam": failure.provider_error_param,
                "safeProviderMessage": getattr(failure, "safe_provider_message", None) or failure.reason,
                "retryable": failure.retryable, **getattr(failure, "safe_diagnostics", {})}
            raise
        finally:
            self.usage.durationMs += int((time.perf_counter() - started) * 1000)

    async def plan_pool(self, seed, design, pool_size):
        payload = {"seed": seed.model_dump(mode="json"), "design": design.model_dump(mode="json"), "poolSize": pool_size}
        raw = await self.call("PLANNING", "PLAN_POOL", payload, lambda: self.provider.plan_pool(seed, design, pool_size),
                              operation_version="v3", prompt_version=PLAN_PROMPT_VERSION)
        return [item if isinstance(item, PortfolioPlanDraft) else PortfolioPlanDraft.model_validate(item) for item in raw]

    async def derive_idea_brief(self, payload, mock_result, live_fn):
        async def selected():
            if self.mode == ProviderMode.MOCK:
                return mock_result
            return await live_fn()
        return await self.call("SAFETY_CHECKING", "IDEA_BRIEF_DERIVATION", {"input": payload}, selected,
                               operation_version="v2", prompt_version="idea-brief-current-v2")

    async def replenish_plans(self, seed, design, existing, rejected, count, round_number):
        payload = {"seed": seed.model_dump(mode="json"), "design": design.model_dump(mode="json"),
            "existingPlans": [item.model_dump(mode="json") for item in existing], "rejectedPlans": rejected,
            "count": count, "round": round_number}
        raw = await self.call("PLANNING", "PLAN_REPLENISHMENT", payload,
            lambda: self.provider.replenish_plans(seed, design, existing, rejected, count, round_number),
            operation_version="v3", prompt_version=PLAN_PROMPT_VERSION)
        return [item if isinstance(item, PortfolioPlanDraft) else PortfolioPlanDraft.model_validate(item) for item in raw]

    async def expand(self, seed, plan, index):
        payload = {"seed": seed.model_dump(mode="json"), "plan": plan.model_dump(mode="json"), "index": index}
        return await self.call("EXPANDING", "EXPAND", payload, lambda: self.provider.expand(seed, plan, index),
                               ConceptCandidateDraft, operation_version="v3", prompt_version=CANDIDATE_PROMPT_VERSION)

    async def regenerate_candidate(self, seed, design, plan, previous, failure_summary,
                                   missing_identity, index):
        payload = {"seed": seed.model_dump(mode="json"), "design": design.model_dump(mode="json"),
                   "plan": plan.model_dump(mode="json"),
                   "previousCandidate": previous.model_dump(mode="json"),
                   "fidelityFailureSummary": failure_summary,
                   "missingIdentityComponents": missing_identity, "index": index}
        return await self.call("CANDIDATE_VALIDATING", "REGENERATE_CANDIDATE_FOR_FIDELITY", payload,
            lambda: self.provider.regenerate_candidate(
                seed, design, plan, previous, failure_summary, missing_identity, index),
            ConceptCandidateDraft, operation_version="v1",
            prompt_version=FIDELITY_REGEN_PROMPT_VERSION)

    async def review_legal(self, candidate_id, candidate, seed):
        payload = {"candidateId": candidate_id, "candidate": candidate.model_dump(mode="json"),
                   "seed": seed.model_dump(mode="json")}
        return await self.call("LEGAL_REVIEWING", "LEGAL_REVIEW", payload,
            lambda: self.provider.review_legal(candidate_id, candidate, seed), LegalReview,
            operation_version="v3", prompt_version=LEGAL_OPERATION_VERSION)

    async def redesign(self, seed, plan, candidate, requirements, index):
        payload = {"seed": seed.model_dump(mode="json"), "plan": plan.model_dump(mode="json"),
                   "candidate": candidate.model_dump(mode="json"), "requirements": requirements, "index": index}
        return await self.call("LEGAL_RECOVERING", "REDESIGN", payload,
            lambda: self.provider.redesign(seed, plan, candidate, requirements, index), ConceptCandidateResult,
            operation_version="v3", prompt_version="concept-redesign-v3")

    async def complete_legal_facts(self, seed, plan, candidate, requirements, index):
        payload = {"seed": seed.model_dump(mode="json"), "plan": plan.model_dump(mode="json"),
                   "candidate": candidate.model_dump(mode="json"),
                   "requirements": [item.model_dump(mode="json") for item in requirements], "index": index}
        raw = await self.call("LEGAL_RECOVERING", "LEGAL_FACT_COMPLETION", payload,
            lambda: self.provider.complete_legal_facts(seed, plan, candidate, requirements, index),
            operation_version="v3", prompt_version=LEGAL_FACT_COMPLETION_PROMPT_VERSION)
        values = self.json_value(raw)
        if not isinstance(values, dict):
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "LEGAL_FACT_COMPLETION_PATCH_NOT_OBJECT",
                                  502, False, schema_name="concept_legal_fact_completion_dynamic_v3")
        unsupported = sorted(set(values) - set(LegalFactCompletionPatch.model_fields))
        if unsupported:
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "LEGAL_FACT_COMPLETION_PATCH_UNSUPPORTED_FIELDS",
                                  502, False, schema_name="concept_legal_fact_completion_dynamic_v3",
                                  safe_diagnostics={"unsupportedFields": unsupported})
        full = {field: None for field in LegalFactCompletionPatch.model_fields}
        full.update(values)
        try:
            return LegalFactCompletionPatch.model_validate(full)
        except ValidationError as failure:
            raise ProviderFailure(
                "RESULT_SCHEMA_INVALID", "LEGAL_FACT_COMPLETION_PATCH_VALUE_INVALID",
                502, False, schema_name="concept_legal_fact_completion_dynamic_v3",
                validation_fields=[{"path": ".".join(str(part) for part in item["loc"]),
                                    "reason": item["type"]}
                                   for item in failure.errors()[:12]]) from failure

    async def repair_redesign_compliance(self, seed, plan, parent, child, requirements, index):
        payload = {"seed": seed.model_dump(mode="json"), "plan": plan.model_dump(mode="json"),
                   "parentCandidate": parent.model_dump(mode="json"),
                   "redesignedCandidate": child.model_dump(mode="json"),
                   "requirements": requirements, "index": index}
        return await self.call("LEGAL_RECOVERING", "LEGAL_REDESIGN_COMPLIANCE_REPAIR", payload,
            lambda: self.provider.repair_redesign_compliance(seed, plan, parent, child, requirements, index),
            ConceptCandidateDraft, operation_version="v1", prompt_version=LEGAL_REDESIGN_REPAIR_PROMPT_VERSION)

    async def judge_distinctness(self, kind, left, right):
        payload = {"kind": kind, "left": left, "right": right}
        return await self.call("DISTINCTNESS", "SEMANTIC_RELATION", payload,
            lambda: self.provider.judge_distinctness(kind, left, right), SemanticDistinctnessResult,
            operation_version="v3", prompt_version=DISTINCTNESS_PROMPT_VERSION)

    async def judge_fidelity(self, plan, candidate):
        payload = _fidelity_context(plan, candidate)
        return await self.call("CANDIDATE_VALIDATING", "PLAN_FIDELITY", payload,
            lambda: self.provider.judge_fidelity(plan, candidate), SemanticFidelityResult,
            operation_version="v3", prompt_version=FIDELITY_PROMPT_VERSION)

    async def classify_architectures(self, items):
        raw = await self.call("NORMALIZING", "NORMALIZE_ARCHITECTURES", {"items": items},
            lambda: self.provider.classify_architectures(items),
            operation_version="v1", prompt_version=ARCHITECTURE_PROMPT_VERSION)
        results = [item if isinstance(item, SemanticArchitectureClassification)
                   else SemanticArchitectureClassification.model_validate(item) for item in raw]
        return self._validate_keyed_batch(
            [(item["entityId"],) for item in items], results,
            lambda item: (item.entityId,), "ARCHITECTURE",
            "concept_architecture_classifier_v2")

    async def classify_hypotheses(self, items):
        raw = await self.call("PORTFOLIO_VALIDATING", "VALIDATE_HYPOTHESES", {"items": items},
            lambda: self.provider.classify_hypotheses(items),
            operation_version="v1", prompt_version=HYPOTHESIS_SEMANTIC_PROMPT_VERSION)
        results = [item if isinstance(item, SemanticHypothesisResult)
                   else SemanticHypothesisResult.model_validate(item) for item in raw]
        return self._validate_keyed_batch(
            [(item["hypothesisType"],) for item in items], results,
            lambda item: (item.hypothesisType,), "HYPOTHESIS",
            "concept_hypothesis_semantic_v1")

    async def classify_business_roles(self, items):
        raw = await self.call("LEGAL_RECOVERING", "CLASSIFY_BUSINESS_ROLE_SEMANTICS", {"items": items},
            lambda: self.provider.classify_business_roles(items),
            operation_version="v1", prompt_version=BUSINESS_ROLE_SEMANTIC_PROMPT_VERSION)
        try:
            results = [item if isinstance(item, BusinessRoleSemanticItem)
                       else BusinessRoleSemanticItem.model_validate(item) for item in raw]
        except ValidationError as failure:
            raise ProviderFailure(
                "RESULT_SCHEMA_INVALID", "BUSINESS_ROLE_BATCH_ITEM_INVALID",
                502, False, schema_name="concept_business_role_semantic_v2") from failure
        return self._validate_keyed_batch(
            [(item["candidateId"], item["field"]) for item in items], results,
            lambda item: (item.candidateId, item.field), "BUSINESS_ROLE",
            "concept_business_role_semantic_v2",
            safe_extra_values={"platformRole", "providerRole", "sellerRole", "intermediaryRole"})

    async def classify_legal_fact_dependencies(self, items):
        raw = await self.call(
            "LEGAL_RECOVERING", "CLASSIFY_LEGAL_FACT_DEPENDENCIES", {"items": items},
            lambda: self.provider.classify_legal_fact_dependencies(items),
            operation_version="v1", prompt_version=LEGAL_FACT_DEPENDENCY_PROMPT_VERSION)
        try:
            results = [item if isinstance(item, LegalFactDependencySemanticItem)
                       else LegalFactDependencySemanticItem.model_validate(item) for item in raw]
        except ValidationError as failure:
            raise ProviderFailure(
                "RESULT_SCHEMA_INVALID", "LEGAL_FACT_DEPENDENCY_BATCH_ITEM_INVALID",
                502, False, schema_name="concept_legal_fact_dependency_v2") from failure
        return self._validate_keyed_batch(
            [(item["candidateId"], item["dependencyType"]) for item in items], results,
            lambda item: (item.candidateId, item.dependencyType), "LEGAL_FACT_DEPENDENCY",
            "concept_legal_fact_dependency_v2",
            safe_extra_values={"PERSONAL_DATA", "PHYSICAL_ACTIVITY", "BUSINESS_PARTNER"})

    async def replacement_plans(self, seed, design, existing_plans, count=2):
        payload = {"seed": seed.model_dump(mode="json"), "design": design.model_dump(mode="json"),
                   "existingPlans": [item.model_dump(mode="json") for item in existing_plans], "count": count}
        raw = await self.call("LEGAL_RECOVERING", "REPLACEMENT_PLAN", payload,
            lambda: self.provider.replacement_plans(seed, design, existing_plans, count),
            operation_version="v3", prompt_version=PLAN_PROMPT_VERSION)
        return [item if isinstance(item, PortfolioPlanDraft) else PortfolioPlanDraft.model_validate(item) for item in raw]

    def replay_manifest(self):
        entries = []
        if self.recordings_dir and self.recordings_dir.exists():
            for path in sorted(self.recordings_dir.glob("*.json")):
                try: record = json.loads(path.read_text(encoding="utf-8"))
                except (OSError, ValueError): continue
                versioned = all(record.get(key) for key in ("operationVersion", "promptVersion", "schemaVersion", "canonicalInputHash"))
                entries.append({"operation": record.get("taskType"), "hash": record.get("canonicalRequestHash"),
                    "operationVersion": record.get("operationVersion"), "promptVersion": record.get("promptVersion"),
                    "schemaVersion": record.get("schemaVersion"), "recordExists": True,
                    "timestamp": record.get("timestamp"), "versionCompatible": versioned})
        status = "REPLAY_MISS" if not entries else ("REPLAY_READY" if all(x["versionCompatible"] for x in entries) else "REPLAY_PARTIAL")
        return {"status": status, "entries": entries}
