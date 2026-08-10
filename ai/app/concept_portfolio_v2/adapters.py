"""현재 production 계약을 V2 Lab에서 재사용하기 위한 얇은 adapter."""

from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any

from app.contracts.concept_fingerprint import BUSINESS_FINGERPRINT_FIELDS, BusinessFingerprint
from app.legal.registry import LegalRegistry
from app.tasks.concept_candidate.models import ConceptCandidateResult
from app.tasks.concept_legal_review.service import execute_concept_legal_review
from app.tasks.idea_brief.models import FieldKey, IdeaBriefDerivationInput

from .models import (
    CanonicalSeed, DownstreamHandoff, FieldMapping, HypothesisDecision, IdeaBriefLabContext,
    LegalReview, LegalRoute, ProviderMode, SafetyResult, SeedField,
)
from .hypothesis_validation import assess_hypothesis_value
from .snapshot_hash import production_compatible_snapshot_hash


REQUIRED_SEED = ("ideaOverview", "problem", "targetUsers")
OPTIONAL_FIELDS = tuple(key for key in FieldKey.__args__ if key not in REQUIRED_SEED)
HYPOTHESIS_FIELDS = {
    "TARGET_REGION": "targetRegion", "REVENUE_MODEL": "revenueModel", "PRICE": "price",
    "CHANNELS": "channels", "DIFFERENTIATORS": "differentiators",
    "PRE_MARKET_SOM_SHARE": "preMarketSomShareHypothesis",
    "PRE_MARKET_SOM": "preMarketSomHypothesis",
}


def _hash(value: Any) -> str:
    return production_compatible_snapshot_hash(value)


class CurrentIdeaBriefAdapter:
    """현행 13개 FieldKey와 LOCKED/OPEN 의미를 보존한다."""

    def adapt(self, payload: dict[str, Any]) -> CanonicalSeed:
        fixture_name = str(payload.get("fixtureName") or "custom")
        if "fields" in payload and "mode" in payload:
            current = IdeaBriefDerivationInput.model_validate(payload)
            values = {item.fieldKey: item for item in current.fields}
            overview = current.ideaOverview
            fields = [SeedField(fieldKey=item.fieldKey, value=item.value,
                                source="USER_INPUT" if item.value.strip() else "MISSING",
                                decisionState=item.decisionState) for item in current.fields]
        else:
            overview = str(payload.get("ideaOverview") or "").strip()
            fields = []
            values = {}
            for key in FieldKey.__args__:
                raw = payload.get(key, "")
                if isinstance(raw, dict):
                    value = str(raw.get("value") or "").strip()
                    decision = str(raw.get("decisionState") or ("LOCKED" if value else "OPEN"))
                    source = str(raw.get("source") or raw.get("provenance") or
                                 ("USER_INPUT" if value else "MISSING"))
                else:
                    value = str(raw or "").strip()
                    decision = "LOCKED" if value else "OPEN"
                    source = "USER_INPUT" if value else "MISSING"
                if key == "ideaOverview" and not value:
                    value = overview
                item = SeedField(fieldKey=key, value=value, source=source, decisionState=decision)
                fields.append(item)
                values[key] = item
        by_key = {item.fieldKey: item for item in fields}
        missing = [key for key in REQUIRED_SEED if not by_key.get(key) or not by_key[key].value.strip()]
        if missing:
            raise ValueError("필수 Idea Brief 필드가 비었습니다: " + ", ".join(missing))
        return CanonicalSeed(
            ideaBriefSnapshotId=str(payload.get("ideaBriefSnapshotId") or "lab-idea-brief"),
            ideaOverview=overview or by_key["ideaOverview"].value,
            problem=by_key["problem"].value,
            targetUsers=by_key["targetUsers"].value,
            fields=fields,
            interpretation=dict(payload.get("interpretation") or {}), fixtureName=fixture_name,
        )

    def current_payload(self, seed: CanonicalSeed, mode: str = "FINAL_SYNTHESIS") -> dict[str, Any]:
        return IdeaBriefDerivationInput(
            mode=mode, ideaOverview=seed.ideaOverview,
            fields=[{"fieldKey": item.fieldKey, "value": item.value,
                     "decisionState": item.decisionState} for item in seed.fields],
            attachmentFileIds=[],
            fieldMetadata=[{"fieldKey": key, "requiredForConcept": key in REQUIRED_SEED,
                            "regulatorySensitive": key in {"targetRegion", "revenueModel", "price", "channels"}}
                           for key in FieldKey.__args__],
        ).model_dump(mode="json")

    @staticmethod
    def lab_context(raw: dict[str, Any]) -> IdeaBriefLabContext:
        return IdeaBriefLabContext.model_validate(raw)

    @staticmethod
    def local_context(seed: CanonicalSeed) -> IdeaBriefLabContext:
        unsafe = any(word in (seed.ideaOverview + " " + seed.problem).casefold()
                     for word in ("피싱", "불법 무기", "아동 성착취"))
        interpretation = seed.interpretation or {
            "interpretedProblem": seed.problem,
            "interpretedTargetUsers": seed.targetUsers,
            "usageContext": "사용자가 입력한 문제 상황에서 이용",
            "industryCategory": "푸드테크",
            "researchScope": "대한민국 내 관련 시장과 운영 구조",
            "conciseIdeaDefinition": seed.ideaOverview,
            "targetRegionInterpretation": next((item.value for item in seed.fields
                if item.fieldKey == "targetRegion" and item.value.strip()), "대한민국 가설"),
            "relevantKnownCompetitorContext": next((item.value for item in seed.fields
                if item.fieldKey == "knownCompetitors" and item.value.strip()), "확인 필요"),
        }
        return IdeaBriefLabContext(
            safetyReview=SafetyResult(
                decision="BLOCK_OR_REFRAME" if unsafe else "ALLOW",
                categories=["DANGEROUS_OR_ILLEGAL_DISTRIBUTION"] if unsafe else [], restrictions=[],
                userFacingReason="안전한 방향으로 재구성이 필요합니다." if unsafe else "안전한 사업 아이디어로 확인했습니다."),
            interpretation=interpretation, commitmentCandidates=[],
            readiness={"status": "READY_FOR_REVIEW", "score": 100, "missingFieldKeys": []},
            userFacingSummary="입력된 아이디어의 핵심 문제·대상·의도를 구조화했습니다.",
            contradictions=[], questions=[])


class CurrentSafetyAdapter:
    async def evaluate(self, seed: CanonicalSeed, mode: ProviderMode) -> SafetyResult:
        return CurrentIdeaBriefAdapter.local_context(seed).safetyReview


def business_fingerprint(candidate: ConceptCandidateResult) -> BusinessFingerprint:
    return BusinessFingerprint.model_validate({key: getattr(candidate, key) for key in BUSINESS_FINGERPRINT_FIELDS})


class CurrentLegalAdapter:
    """현행 공식 근거/MOLEG legal task와 동일한 입력 계약을 만든다."""

    @staticmethod
    def _governed(candidate: ConceptCandidateResult, key: str) -> dict[str, Any]:
        semantics = {item.fieldKey: item for item in candidate.valueSemantics}
        semantic = semantics[key]
        return {"value": getattr(candidate, key), "source": semantic.source,
                "authority": semantic.authority, "decision": semantic.decision}

    def task_input(self, candidate: ConceptCandidateResult, seed: CanonicalSeed | None = None) -> dict[str, Any]:
        text = lambda key: self._governed(candidate, key)
        listed = lambda key: self._governed(candidate, key)
        sensitive = lambda key: {**text(key), "legalSensitivity": "LEGAL_SENSITIVE"}
        pattern = {
            "schemaVersion": "2.0", "jurisdiction": "KR", "actorRoles": listed("actorRoles"),
            "platformRole": text("platformRole"),
            "commercialRoles": {"providerRole": text("providerRole"), "sellerRole": text("sellerRole"),
                                "intermediaryRole": text("intermediaryRole")},
            "transactionFlow": listed("transactionFlow"), "paymentFlow": listed("paymentFlow"),
            "personalDataUsage": listed("personalDataUsage"), "physicalActivities": listed("physicalActivities"),
            "partnerRoles": {"partnerModel": text("partnerModel"),
                             "partnerRequirements": listed("partnerRequirements")},
            "qualificationRequirements": listed("qualificationRequirements"),
            "advertisingClaims": listed("advertisingClaims"), "operatingModel": text("operatingModel"),
            "hypotheses": {key: sensitive(key) for key in
                           ("targetRegion", "revenueModel", "price", "channels", "differentiators")},
        }
        facts = []
        if seed:
            region = seed.by_key().get("targetRegion")
            if (region and region.value.strip() and region.decisionState == "LOCKED"
                    and region.source in {"USER_INPUT", "USER_CONFIRMED"}):
                facts.append({"factKey": "fixedJurisdiction", "value": region.value,
                              "source": "USER_INPUT", "authority": "LOCKED"})
        registry_version = LegalRegistry().version
        return {"legalFactPattern": pattern, "factPatternHash": _hash(pattern),
                "externalFactContext": {"sourceSnapshotHash": _hash(facts),
                                        "registryVersion": registry_version, "facts": facts}}

    async def review(self, candidate_id: str, candidate: ConceptCandidateResult,
                     seed: CanonicalSeed | None = None) -> LegalReview:
        raw = await execute_concept_legal_review(self.task_input(candidate, seed))
        route = {
            "IMPLEMENTABLE": LegalRoute.ACCEPT, "IMPLEMENTABLE_WITH_CONTROLS": LegalRoute.ACCEPT,
            "REDESIGNABLE": LegalRoute.REDESIGN_WITHIN_LINEAGE,
            "REJECTED": LegalRoute.REPLAN_REQUIRED, "NEEDS_FACTS": LegalRoute.NEEDS_INPUT,
        }[raw["status"]]
        coverage_message = ("공식 근거를 바탕으로 구현 가능성을 검토했으나, 일부 법률 소스의 조회 범위에는 제한이 있습니다."
                            if raw.get("legalSourceStatus") == "SOURCE_PARTIAL" else None)
        return LegalReview(
            candidateId=candidate_id, route=route, productionStatus=raw["status"], sourceStatus="OFFICIAL_EVIDENCE",
            safeSummary=raw["safeUserSummary"], requiredControls=raw["requiredControls"],
            requiredPartnersAndQualifications=raw["requiredPartnersAndQualifications"],
            redesignRequirements=raw["redesignRequirements"], prohibitedVariants=raw["prohibitedVariants"],
            requiredDisclosures=raw["requiredDisclosures"],
            officialEvidenceReferences=raw["officialEvidence"],
            reviewPhase=raw.get("reviewPhase"), factCompletenessStatus=raw.get("factCompletenessStatus"),
            legalSourceStatus=raw.get("legalSourceStatus"),
            finalEvidenceJudgmentExecuted=raw.get("finalEvidenceJudgmentExecuted"),
            recoveryResolution=raw.get("recoveryResolution"),
            unknownFacts=list(raw.get("unknownFacts") or []),
            sourceQuestionCount=raw.get("sourceQuestionCount", 0),
            resolvedByFactPatternCount=raw.get("resolvedByFactPatternCount", 0),
            designGapCount=raw.get("designGapCount", 0),
            externalFactCount=raw.get("externalFactCount", 0),
            controlConvertibleCount=raw.get("controlConvertibleCount", 0),
            legalClarificationCount=raw.get("legalClarificationCount", 0),
            evidenceDiagnostics=({"coverageStatus": raw.get("legalSourceStatus"),
                                  "coverageMessage": coverage_message}
                                 if raw.get("legalSourceStatus") else {}),
        )


class CurrentDownstreamAdapter:
    """market-analysis-seed-snapshot-v1과 marketing-source-snapshot-v1을 그대로 투영한다."""

    def build(self, seed: CanonicalSeed, candidate_id: str, candidate: ConceptCandidateResult,
              hypotheses: list[HypothesisDecision], legal: LegalReview) -> DownstreamHandoff:
        errors: list[str] = []
        by_type = {item.hypothesisType: item for item in hypotheses}
        missing = [key for key in HYPOTHESIS_FIELDS if key not in by_type or not by_type[key].accepted]
        if missing:
            errors.append("확정되지 않은 hypothesis: " + ", ".join(missing))
        invalid_semantics = []
        for key in HYPOTHESIS_FIELDS:
            item = by_type.get(key)
            if not item:
                continue
            assessment = assess_hypothesis_value(key, item.finalValue if item.finalValue is not None else item.proposedValue)
            if assessment.status in {"UNRESOLVED", "INVALID"} or (
                    assessment.status == "AMBIGUOUS" and item.semanticStatus != "VALID"):
                invalid_semantics.append(key)
        if invalid_semantics:
            errors.append("UNRESOLVED_HYPOTHESES: " + ", ".join(invalid_semantics))
        delta_pending = [item.hypothesisType for item in hypotheses
                         if item.deltaLegalRequired and item.legalReviewStatus not in {
                             "IMPLEMENTABLE", "IMPLEMENTABLE_WITH_CONTROLS", "PASSED"}]
        if delta_pending:
            errors.append("Delta Legal 미완료 hypothesis: " + ", ".join(delta_pending))
        if legal.route != LegalRoute.ACCEPT:
            errors.append("선택 Concept Legal 결과가 ACCEPT가 아닙니다.")
        if not seed.interpretation:
            errors.append("Idea Brief AI interpretation이 비어 있습니다.")
        final_hypotheses: dict[str, Any] = {}
        output_keys = {"PRE_MARKET_SOM_SHARE": "preMarketSomShare", "PRE_MARKET_SOM": "preMarketSom"}
        for hypothesis_type, field in HYPOTHESIS_FIELDS.items():
            item = by_type.get(hypothesis_type)
            if not item:
                continue
            final_hypotheses[output_keys.get(hypothesis_type, field)] = {
                "value": item.finalValue, "source": item.source, "decisionStatus": item.decisionStatus,
                "proposalVersion": item.proposalVersion, "legalImpact": item.legalImpact,
                "legalReviewStatus": item.legalReviewStatus,
            }
        now = datetime.now(timezone.utc).isoformat()
        semantics = [item.model_dump(mode="json") for item in candidate.valueSemantics]
        selected = {
            "identity": {key: getattr(candidate, key) for key in
                         ("conceptName", "conceptDefinition", "introduction", "coreValue", "targetUsers",
                          "industryCategory", "researchScope")},
            "solution": {key: getattr(candidate, key) for key in
                         ("problemScenario", "solutionMechanism", "featureSet")},
            "operation": {key: getattr(candidate, key) for key in
                          ("actorRoles", "platformRole", "operatingModel", "partnerModel", "providerRole",
                           "sellerRole", "intermediaryRole", "transactionFlow", "paymentFlow", "personalDataUsage",
                           "physicalActivities", "partnerRequirements", "qualificationRequirements")},
            "valueSemantics": semantics, "canonicalHash": _hash(candidate.model_dump(mode="json")),
        }
        original_fields = {item.fieldKey: {"value": item.value, "source": item.source,
                                           "decisionState": item.decisionState}
                           for item in seed.fields if item.fieldKey in REQUIRED_SEED or item.decisionState == "LOCKED"}
        production_status = legal.productionStatus or {
            LegalRoute.ACCEPT: "IMPLEMENTABLE_WITH_CONTROLS",
            LegalRoute.REDESIGN_WITHIN_LINEAGE: "REDESIGNABLE",
            LegalRoute.REPLAN_REQUIRED: "REJECTED",
            LegalRoute.NEEDS_INPUT: "NEEDS_FACTS",
            LegalRoute.SYSTEM_FAILURE: "REJECTED",
        }[legal.route]
        legal_result = {"legalStatus": production_status, "safeSummary": legal.safeSummary,
                        "requiredControls": legal.requiredControls,
                        "requiredPartnersAndQualifications": legal.requiredPartnersAndQualifications,
                        "prohibitedVariants": legal.prohibitedVariants,
                        "requiredDisclosures": legal.requiredDisclosures,
                        "officialEvidenceReferences": legal.officialEvidenceReferences,
                        "deltaLegalReviews": legal.deltaLegalReviews}
        market = {"contract": "market-analysis-seed-snapshot-v1", "schemaVersion": "2.0",
                  "snapshotId": "lab-market-seed", "projectId": 0, "selectionId": 0,
                  "conceptId": candidate_id, "createdAt": now, "sourceSnapshotHash": _hash(seed.model_dump()),
                  "originalSeed": {"ideaOverview": seed.ideaOverview, "fields": original_fields},
                  "aiInterpretation": seed.interpretation, "selectedConcept": selected,
                  "finalHypotheses": final_hypotheses, "legalResult": legal_result}
        value = lambda key: final_hypotheses.get(key, {}).get("value")
        evidence_fields = ("referenceIndex", "sourceType", "lawId", "officialIdentifier", "lawName",
                           "articleReference", "title", "officialSourceUri", "jurisdiction", "promulgationDate",
                           "effectiveDate", "retrievedAt", "contentHash", "registryVersion")
        marketing_evidence = [{key: item[key] for key in evidence_fields if key in item}
                              for item in legal.officialEvidenceReferences if isinstance(item, dict)]
        marketing = {"contract": "marketing-source-snapshot-v1", "schemaVersion": "2.0",
                     "snapshotId": "lab-marketing-source", "projectId": 0, "selectionId": 0,
                     "conceptId": candidate_id, "marketAnalysisSeedSnapshotId": market["snapshotId"],
                     "marketAnalysisSeedSnapshotHash": _hash(market), "createdAt": now,
                     "conceptName": candidate.conceptName, "targetSegment": candidate.targetUsers,
                     "problem": candidate.problemScenario, "valueProposition": candidate.coreValue,
                     "positioning": candidate.conceptDefinition, "keyFeatures": candidate.featureSet,
                     "targetRegion": value("targetRegion"), "revenueModel": value("revenueModel"),
                     "price": value("price"), "pricing": f'{value("revenueModel")} · {value("price")}',
                     "channels": [value("channels")] if isinstance(value("channels"), str) else value("channels"),
                     "competitorDifferentiators": [value("differentiators")] if isinstance(value("differentiators"), str) else value("differentiators"),
                     "preMarketSomShare": value("preMarketSomShare"), "preMarketSom": value("preMarketSom"),
                     "legalStatus": production_status, "allowedClaims": candidate.advertisingClaims,
                     "prohibitedClaims": legal.prohibitedVariants, "requiredDisclosures": legal.requiredDisclosures,
                     "requiredControls": legal.requiredControls,
                     "communicationRequiredControls": legal.requiredControls,
                     "officialEvidenceReferences": marketing_evidence}
        marketing["hash"] = _hash(marketing)
        marketing["sourceSnapshotHash"] = marketing["hash"]
        mappings = [
            FieldMapping(v2Field="candidate.conceptName", downstreamField="selectedConcept.identity.conceptName",
                         source="CONCEPT_GENERATED", transformed=False, required=True),
            FieldMapping(v2Field="candidate.solutionMechanism", downstreamField="selectedConcept.solution.solutionMechanism",
                         source="CONCEPT_GENERATED", transformed=False, required=True),
            FieldMapping(v2Field="hypotheses[*]", downstreamField="finalHypotheses", source="USER_CONFIRMED",
                         transformed=True, required=True),
            FieldMapping(v2Field="legal", downstreamField="legalResult", source="OFFICIAL_EVIDENCE",
                         transformed=True, required=True),
        ]
        required_market = {"contract", "schemaVersion", "snapshotId", "projectId", "selectionId", "conceptId",
                           "createdAt", "sourceSnapshotHash", "originalSeed", "aiInterpretation",
                           "selectedConcept", "finalHypotheses", "legalResult"}
        required_marketing = {"contract", "schemaVersion", "snapshotId", "projectId", "selectionId", "conceptId",
                              "marketAnalysisSeedSnapshotId", "marketAnalysisSeedSnapshotHash", "createdAt",
                              "conceptName", "targetSegment", "problem", "valueProposition", "positioning",
                              "keyFeatures", "targetRegion", "revenueModel", "price", "pricing", "channels",
                              "competitorDifferentiators", "preMarketSomShare", "preMarketSom", "legalStatus",
                              "allowedClaims", "prohibitedClaims", "requiredDisclosures", "requiredControls",
                              "communicationRequiredControls", "officialEvidenceReferences", "hash", "sourceSnapshotHash"}
        structure_errors = []
        if not required_market <= market.keys(): structure_errors.append("Market Seed required field 누락")
        if not required_marketing <= marketing.keys(): structure_errors.append("Marketing required field 누락")
        if not {"requiredControls", "requiredPartnersAndQualifications", "prohibitedVariants",
                "requiredDisclosures", "officialEvidenceReferences", "deltaLegalReviews"} <= legal_result.keys():
            structure_errors.append("Market Seed legalResult required field 누락")
        errors = structure_errors + errors
        structure_status = "STRUCTURE_PASS" if not structure_errors else "STRUCTURE_FAIL"
        contract_status = "CONTRACT_PASS" if not errors else "CONTRACT_FAIL"
        return DownstreamHandoff(
            compatibility="PASS" if contract_status == "CONTRACT_PASS" else "FAIL",
            structureStatus=structure_status, contractStatus=contract_status,
            marketAnalysisSeedSnapshot=market,
            marketingSourceSnapshot=marketing,
            sourceProvenance={"ideaBriefSnapshotId": seed.ideaBriefSnapshotId,
                              "candidateHash": selected["canonicalHash"], "generatedAt": now},
            fieldMapping=mappings, validationErrors=errors,
        )
