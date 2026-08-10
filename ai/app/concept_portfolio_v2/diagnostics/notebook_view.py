"""Notebook용 사람이 읽을 수 있는 표/요약 helper."""

from __future__ import annotations

import json
from typing import Any

try:
    import pandas as pd
except ImportError:  # Core import는 pandas 없이도 동작한다.
    pd = None


def _dump(value: Any) -> Any:
    return value.model_dump(mode="json") if hasattr(value, "model_dump") else value


def _table(rows: list[dict[str, Any]]):
    return pd.DataFrame(rows) if pd is not None else rows


def show_seed_input(seed):
    return _table([_dump(item) for item in seed.fields])


def show_seed_analysis(analysis):
    return _table([{"구분": "탐색 폭", "값": analysis.explorationBreadth.value},
                   {"구분": "다양성 수용량", "값": analysis.diversityCapacity},
                   {"구분": "설명", "값": analysis.rationaleSummary}])


def show_idea_interpretation(context):
    keys = ("interpretedProblem", "interpretedTargetUsers", "usageContext", "industryCategory",
            "researchScope", "conciseIdeaDefinition", "targetRegionInterpretation",
            "relevantKnownCompetitorContext")
    return _table([{"항목": key, "AI 이해 결과": context.interpretation.get(key)} for key in keys])


def show_idea_readiness(context):
    inconsistent = (context.readiness.get("status") == "READY_FOR_REVIEW"
                    and not context.readiness.get("missingFieldKeys")
                    and context.readiness.get("score") == 0)
    return {"readiness": context.readiness,
            "readinessDiagnostic": "READINESS_INCONSISTENT" if inconsistent else "CONSISTENT",
            "userFacingSummary": context.userFacingSummary,
            "commitmentCandidates": context.commitmentCandidates,
            "contradictions": context.contradictions, "questions": context.questions}


def show_design_space(analysis):
    rows = ([{"분류": "SOURCE_LOCK", "필드": key, "값": value} for key, value in analysis.sourceLocks.items()]
            + [{"분류": "BUSINESS_LOCK", "필드": key, "값": value}
               for key, value in analysis.explicitBusinessLocks.items()]
            + [{"분류": "SEMANTIC_ANCHOR", "필드": key, "값": value}
               for key, value in analysis.semanticAnchors.items()]
            + [{"분류": "OPEN", "필드": value, "값": "변경 가능"} for value in analysis.openDimensions])
    return _table(rows)


def show_portfolio_plans(plans):
    return _table([{"planId": p.planId, "제목": p.title,
                    "선택 상태": p.selectionStatus, "selectionScore": p.selectionScore,
                    "selectionReason": p.selectionReason,
                    "relationToPortfolio": p.relationToPortfolio,
                    "Concept Family": p.descriptor.familyLabelKo,
                    "Target Thesis": p.targetSegment, "Use Context": p.useContext,
                    "Value Thesis": p.valueProposition, "Offer Thesis": p.offerThesis,
                    "Solution Thesis": p.solutionThesis,
                    "Architecture": p.descriptor.architecture.model_dump(mode="json"),
                    "비교 가치": p.reasonForPortfolioRole} for p in plans])


def show_plan_pool_status(status):
    return _table([_dump(status)]) if status else _table([])


def show_mechanics(items):
    rows = []
    for item in items:
        descriptor = item.descriptor
        for field, value in descriptor.architecture:
            diagnostic = descriptor.architectureDiagnostics.get(field)
            rows.append({"entityId": getattr(item, "candidateId", getattr(item, "planId", "")),
                         "family": descriptor.familyId, "dimension": field, "code": value,
                         "confidence": diagnostic.confidence if diagnostic else None,
                         "source": diagnostic.source if diagnostic else None})
    return _table(rows)


show_concept_descriptors = show_mechanics


def compare_plans(assessments):
    return show_plan_diversity(assessments)


def show_plan_diversity(assessments):
    return _table([{"A": item.entityA, "B": item.entityB, "판정": item.decision,
                    "Family A": item.familyA, "Family B": item.familyB,
                    "겹침": ", ".join(item.overlap), "실질 차이": ", ".join(item.materialDifferences),
                    "단계": item.deterministicLevel, "semantic judge": item.semanticJudgeUsed,
                    "관계 설명": item.whyDistinct} for item in assessments])


def show_candidates(candidates):
    return _table([{"candidateId": item.candidateId, "lineageId": item.lineageId,
                    "parentCandidateId": item.parentCandidateId, "이름": item.candidate.conceptName,
                    "핵심 작동방식": item.candidate.solutionMechanism,
                    "family": item.descriptor.familyLabelKo,
                    "descriptor": item.descriptor.model_dump(mode="json"),
                    "수익": item.candidate.revenueModel, "운영": item.candidate.operatingModel}
                   for item in candidates])


def compare_candidates(assessments):
    return show_plan_diversity(assessments)


def show_candidate_validation(reports):
    return _table([_dump(item) for item in reports])


def show_candidate_recovery(preparation):
    summary = {key: getattr(preparation, key) for key in (
        "candidateGenerated", "candidateAcceptedInitially", "candidateRegenerated",
        "candidateRecovered", "reservePlansActivated", "candidateRecoveryReplans")}
    summary["finalCandidatePortfolio"] = len(preparation.candidates)
    return {"summary": _table([summary]), "attempts": show_candidate_validation(preparation.reports),
            "finalCandidates": show_candidates(preparation.candidates)}


def show_legal_precheck(results):
    return _table([_dump(item) for item in results])


def show_legal_fact_completeness(preparation):
    summary = {key: getattr(preparation, key) for key in (
        "completionAttempted", "completionValidated", "completionAccepted", "completionExhausted",
        "roleSemanticBatchCalls", "dependencySemanticBatchCalls", "consistencyRepairAttempted",
        "consistencyRepairAccepted", "consistencyRepairExhausted")}
    summary["preparedForLegal"] = len(preparation.candidates)
    return {"summary": _table([summary]),
            "reports": _table([_dump(item) for item in preparation.reports]),
            "consistencyReports": _table([_dump(item) for item in preparation.consistencyReports]),
            "completionCompliance": _table([_dump(item) for item in preparation.completionCompliance]),
            "excludedCandidates": _table(preparation.excludedCandidates)}


def show_legal_fact_pattern(candidate, seed=None):
    from ..adapters import CurrentLegalAdapter
    pattern = CurrentLegalAdapter().task_input(candidate, seed)["legalFactPattern"]
    roles = pattern["commercialRoles"]
    partners = pattern["partnerRoles"]
    rows = [
        ("platformRole", pattern["platformRole"]),
        ("providerRole", roles["providerRole"]),
        ("sellerRole", roles["sellerRole"]),
        ("intermediaryRole", roles["intermediaryRole"]),
        ("transactionFlow", pattern["transactionFlow"]),
        ("paymentFlow", pattern["paymentFlow"]),
        ("personalDataUsage", pattern["personalDataUsage"]),
        ("physicalActivities", pattern["physicalActivities"]),
        ("partnerRequirements", partners["partnerRequirements"]),
        ("qualificationRequirements", pattern["qualificationRequirements"]),
        ("advertisingClaims", pattern["advertisingClaims"]),
    ]
    return _table([{"field": key, "legalFact": value["value"],
                    "source": value["source"], "authority": value["authority"],
                    "decision": value["decision"]} for key, value in rows])


def show_legal_result(results):
    return _table([{"candidateId": item.candidateId, "route": item.route.value,
                    "productionStatus": item.productionStatus,
                    "sourceStatus": item.legalSourceStatus or item.sourceStatus,
                    "evidenceCoverage": item.evidenceDiagnostics.get("coverageMessage"),
                    "reviewPhase": item.reviewPhase,
                    "factCompletenessStatus": item.factCompletenessStatus,
                    "legalSourceStatus": item.legalSourceStatus,
                    "finalEvidenceJudgmentExecuted": item.finalEvidenceJudgmentExecuted,
                    "recoveryResolution": item.recoveryResolution,
                    "sourceQuestionCount": item.sourceQuestionCount,
                    "resolvedByFactPatternCount": item.resolvedByFactPatternCount,
                    "designGapCount": item.designGapCount,
                    "externalFactCount": item.externalFactCount,
                    "controlConvertibleCount": item.controlConvertibleCount,
                    "legalClarificationCount": item.legalClarificationCount,
                    "safeSummary": item.safeSummary,
                    "unknownFacts": item.unknownFacts,
                    "requiredControls": item.requiredControls,
                    "requiredPartnersAndQualifications": item.requiredPartnersAndQualifications,
                    "requiredDisclosures": item.requiredDisclosures,
                    "prohibitedVariants": item.prohibitedVariants,
                    "evidenceCount": len(item.officialEvidenceReferences),
                    "evidenceRefs": item.officialEvidenceReferences,
                    "evidenceDiagnostics": item.evidenceDiagnostics} for item in results])


def show_legal_failure(candidate_id, gateway, official_evidence_count=0):
    failure = gateway.last_failure or {}
    allowed = failure.get("allowedEvidenceReferenceIndexes", [])
    invalid = failure.get("invalidIndexes", [])
    return _table([{"Legal Candidate": candidate_id, "status": "FAILED",
                    "failureCode": failure.get("safeProviderMessage") or failure.get("providerErrorType"),
                    "officialEvidenceCount": official_evidence_count,
                    "allowedIndexes": allowed, "invalidReturnedIndexes": invalid,
                    "findingType": failure.get("findingType"),
                    "findingIndex": failure.get("findingIndex"),
                    "nextAction": "LEGAL CONTRACT FIX REQUIRED"}])


def show_redesign_diff(parent, child):
    fields = ("conceptName", "targetUsers", "problemScenario", "solutionMechanism", "operatingModel",
              "partnerModel", "qualificationRequirements")
    return _table([{"필드": field, "이전": getattr(parent.candidate, field),
                    "이후": getattr(child.candidate, field),
                    "변경": getattr(parent.candidate, field) != getattr(child.candidate, field)} for field in fields])


def show_replan(result):
    return _table([{"candidateId": item.candidateId, "planId": item.planId, "lineageId": item.lineageId}
                   for item in result.concepts if "REPLAN" in item.candidateId])


def show_final_portfolio(result):
    return show_candidates(result.concepts)


def show_hypotheses(hypotheses):
    return _table([{"HypothesisType": item.hypothesisType,
                    "ProposedValue": item.proposedValue,
                    "FinalValue": item.finalValue,
                    "SemanticStatus": item.semanticStatus,
                    "SemanticReason": item.semanticReason,
                    "Locked": item.locked,
                    "DecisionStatus": item.decisionStatus,
                    "LegalImpact": item.legalImpact} for item in hypotheses])


def show_hypothesis_readiness(hypotheses):
    if not hypotheses:
        return {"All Values Semantically Valid": False,
                "All Decisions Confirmed": False,
                "Ready For Handoff": False,
                "status": "NOT_READY",
                "reason": "NO_SELECTED_CONCEPT_OR_HYPOTHESES",
                "unresolvedHypotheses": []}
    unresolved = [item.hypothesisType for item in hypotheses
                  if item.semanticStatus != "VALID" or not item.accepted]
    all_valid = all(item.semanticStatus == "VALID" for item in hypotheses)
    all_confirmed = all(item.accepted for item in hypotheses)
    return {"All Values Semantically Valid": all_valid,
            "All Decisions Confirmed": all_confirmed,
            "Ready For Handoff": all_valid and all_confirmed,
            "status": "READY" if not unresolved else "NOT_READY",
            "reason": None if not unresolved else "UNRESOLVED_HYPOTHESES",
            "unresolvedHypotheses": unresolved}


def show_downstream_handoff(handoff):
    return {"호환성": handoff.compatibility, "구조": handoff.structureStatus,
            "계약": handoff.contractStatus,
            "필드 매핑": _table([_dump(item) for item in handoff.fieldMapping]),
            "Market payload": handoff.marketAnalysisSeedSnapshot,
            "Marketing payload": handoff.marketingSourceSnapshot,
            "오류": handoff.validationErrors}


def show_trace(events, stage: str | None = None):
    selected = [item for item in events if stage is None or item.stage.value == stage]
    return _table([{"순서": index, "시각": item.timestamp.isoformat(), "stage": item.stage.value,
                    "action": item.action, "entity": item.entityId, "parent": item.parentId,
                    "status": item.status, "mode": item.providerMode.value, "호출": item.providerCallNumber,
                    "요약": item.safeSummary, "decision": item.decision,
                    "reasonCode": item.reasonCode.value if item.reasonCode else None}
                   for index, item in enumerate(selected, 1)])


def show_provider_usage(usage):
    return _table([{"논리 작업": usage.logicalOperations,
                    "상위 외부 작업": usage.topLevelExternalOperations,
                    "논리 stage별": usage.callsByStage,
                    "상위 외부 작업 stage별": usage.topLevelOperationsByStage,
                    "재시도": usage.retries, "소요(ms)": usage.durationMs,
                    "모드별": usage.modeCounts, "token": usage.tokenUsage, "보고 비용": usage.reportedCost}])


def show_replay_manifest(gateway):
    manifest = gateway.replay_manifest()
    return {"status": manifest["status"], "entries": _table(manifest["entries"])}


def show_schema_preflight(report):
    return _table([{"스키마": item.schemaName, "상태": item.status,
                    "실패": item.failures, "Provider 호출": report.providerCalls}
                   for item in report.schemas])


def show_provider_failure(gateway):
    return gateway.last_failure or {"상태": "기록된 Provider 실패 없음"}


def show_run_failure(result):
    if not result or not result.failureDiagnostics:
        return {"상태": "기록된 run failure 없음"}
    value = _dump(result.failureDiagnostics)
    value["lastTraceEvents"] = len(value.get("lastTraceEvents", []))
    return _table([value])


def show_required_inputs(result_or_items):
    items = (result_or_items.requiredInputs if hasattr(result_or_items, "requiredInputs")
             else list(result_or_items or []))
    keys = ("candidateId", "scope", "unknownFacts", "reason", "possibleUserAction",
            "currentValue", "requiredLegalChange", "safeSummary")
    return _table([{key: item.get(key) for key in keys} for item in items])


def show_pre_legal_exclusions(result_or_items):
    items = (result_or_items.preLegalExclusions if hasattr(result_or_items, "preLegalExclusions")
             else list(result_or_items or []))
    keys = ("candidateId", "scope", "reasonCode", "affectedFields", "consistencyReport", "dependencyDecisions",
            "completionRequirements", "patchChangedFields", "completionCompliance", "recheckStatus",
            "recoveryAttempted", "recoveryResolution", "safeSummary")
    return _table([{key: item.get(key) for key in keys} for item in items])


def show_legal_resolutions(result_or_items):
    items = (result_or_items.legalResolutions if hasattr(result_or_items, "legalResolutions")
             else list(result_or_items or []))
    return _table([_dump(item) for item in items])


def show_live_validation_summary(scenario_id, result):
    legal = result.legalSummaries if result else []
    summary = result.runSummary if result else None
    return _table([{
        "Scenario": scenario_id,
        "Plan returned": summary.planned if summary else 0,
        "Plan selected": summary.planSelected if summary else 0,
        "Candidate generated": summary.candidateGenerated if summary else 0,
        "Candidate valid initially": summary.candidateAcceptedInitially if summary else 0,
        "Candidate regenerated": summary.candidateRegenerated if summary else 0,
        "Candidate recovered": summary.candidateRecovered if summary else 0,
        "Fact completion attempted": summary.legalFactCompletionAttempted if summary else 0,
        "Fact completion validated": summary.legalFactCompletionValidated if summary else 0,
        "Fact completion accepted": summary.legalFactCompletionAccepted if summary else 0,
        "Dependency semantic calls": summary.legalFactDependencySemanticCalls if summary else 0,
        "Completion compliance PASS": summary.legalFactCompletionCompliancePassed if summary else 0,
        "Provider noncompliant": summary.legalFactCompletionProviderNoncompliant if summary else 0,
        "Completion recheck failed": summary.legalFactCompletionRecheckFailed if summary else 0,
        "Fact consistency invalid": summary.factConsistencyInvalid if summary else 0,
        "Consistency repair attempted": summary.factConsistencyRepairAttempted if summary else 0,
        "Consistency repair accepted": summary.factConsistencyRepairAccepted if summary else 0,
        "Consistency repair exhausted": summary.factConsistencyRepairExhausted if summary else 0,
        "Legal ready": summary.legalReady if summary else 0,
        "Legal initial reviewed": summary.legalInitialReviewed if summary else 0,
        "Legal recovery reviewed": summary.legalRecoveryReviewed if summary else 0,
        "Total legal review events": summary.totalLegalReviewEvents if summary else 0,
        "Legal ACCEPT": summary.legalAccepted if summary else 0,
        "NEEDS_INPUT": sum(item.route.value == "NEEDS_INPUT" for item in legal),
        "Final portfolio": result.producedConceptCount if result else 0,
        "Hypothesis valid": result.downstreamReadiness if result else "NOT_RUN",
        "Handoff": result.handoff.contractStatus if result and result.handoff else "PENDING",
        "Provider ops": result.providerUsage.topLevelExternalOperations if result else 0,
        "Duration(ms)": summary.totalDurationMs if summary else 0,
    }])


def show_run_summary(result):
    identity = {"runId": result.runId, "runStatus": result.runStatus.value,
                "runtimeStage": result.runtimeStage.value,
                "producedConceptCount": result.producedConceptCount,
                "downstreamReadiness": result.downstreamReadiness}
    return _table([{**identity, **(_dump(result.runSummary) if result.runSummary else {})}])


def show_raw_json(value):
    return json.dumps(_dump(value), ensure_ascii=False, indent=2, default=str)
