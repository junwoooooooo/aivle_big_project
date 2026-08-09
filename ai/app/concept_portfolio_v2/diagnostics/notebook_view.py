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
    return {"readiness": context.readiness, "userFacingSummary": context.userFacingSummary,
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
    return _table([{"planId": p.planId, "제목": p.title, "핵심 mechanics": p.coreMechanism,
                    "운영": p.operatingApproach, "파트너": p.partnerApproach,
                    "거래": p.transactionApproach, "이행": p.fulfillmentApproach} for p in plans])


def show_plan_pool_status(status):
    return _table([_dump(status)]) if status else _table([])


def show_mechanics(items):
    rows = []
    for item in items:
        descriptor = item.mechanics
        for field, value in descriptor:
            rows.append({"entityId": getattr(item, "candidateId", getattr(item, "planId", "")),
                         "dimension": field, "code": value.code,
                         "labelKo": value.labelKo, "detailKo": value.detailKo})
    return _table(rows)


def compare_plans(assessments):
    return show_plan_diversity(assessments)


def show_plan_diversity(assessments):
    return _table([{"A": item.entityA, "B": item.entityB, "판정": item.decision,
                    "겹침": ", ".join(item.overlap), "실질 차이": ", ".join(item.materialDifferences),
                    "단계": item.deterministicLevel, "semantic judge": item.semanticJudgeUsed,
                    "설명": item.whyDistinct} for item in assessments])


def show_candidates(candidates):
    return _table([{"candidateId": item.candidateId, "lineageId": item.lineageId,
                    "parentCandidateId": item.parentCandidateId, "이름": item.candidate.conceptName,
                    "핵심 작동방식": item.candidate.solutionMechanism,
                    "mechanics": item.mechanics.model_dump(mode="json"),
                    "수익": item.candidate.revenueModel, "운영": item.candidate.operatingModel}
                   for item in candidates])


def compare_candidates(assessments):
    return show_plan_diversity(assessments)


def show_candidate_validation(reports):
    return _table([_dump(item) for item in reports])


def show_legal_precheck(results):
    return _table([_dump(item) for item in results])


def show_legal_result(results):
    return _table([{"candidateId": item.candidateId, "route": item.route.value,
                    "source": item.sourceStatus, "요약": item.safeSummary,
                    "통제": ", ".join(item.requiredControls)} for item in results])


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
    return _table([_dump(item) for item in hypotheses])


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


def show_run_summary(result):
    return _table([_dump(result.runSummary) if result.runSummary else {
        "portfolioStatus": result.runStatus.value, "finalPortfolio": result.producedConceptCount,
        "downstreamHandoff": result.downstreamReadiness}])


def show_raw_json(value):
    return json.dumps(_dump(value), ensure_ascii=False, indent=2, default=str)
