import hashlib
import json
import re
from typing import Any

from pydantic import ValidationError

from app.legal.pipeline import execute_legal_source_pipeline
from app.models.legal_source import BoundaryNormalizationResult, RegulatoryBoundaryResult
from app.providers import ProviderFailure, execute_structured_prompt


BOUNDARY_SYSTEM = """당신은 공식 법령 Evidence를 사업 구조의 실행 가능한 Regulatory Boundary로 변환한다.
제공된 evidenceId만 인용하고 법령, 조문, citation을 만들지 않는다. 법률명·조문 제목이나 plainSummary를
normalizedRequirement에 복사하지 말고 역할·활동·통제·파트너·고지 관점의 실행 문장으로 변환한다.
정보 부족은 질문 또는 UNRESOLVED_FACT로, 사용자가 LOCKED로 확정한 조건과 공식 근거 기반 Rule의
직접 충돌만 conflicts로 반환한다. Brief를 수정하지 말고 선택지만 제안한다. JSON 외 설명을 반환하지 않는다."""


def _canonical(value: str) -> str:
    return re.sub(r"\s+", " ", value.strip()).casefold()


def _content_hash(value: str) -> str:
    return "sha256:" + hashlib.sha256(value.encode("utf-8")).hexdigest()


def _dedupe_evidence(source: dict[str, Any]) -> tuple[list[dict[str, Any]], dict[str, str]]:
    values: list[dict[str, Any]] = []
    key_to_id: dict[tuple[str, str, str, str], str] = {}
    aliases: dict[str, str] = {}
    source_status = {
        "SOURCE_COMPLETE": "COMPLETE", "SOURCE_PARTIAL": "PARTIAL", "REGISTRY_GAP": "WARNING"
    }[source["sourceStatus"]]
    for item in source["evidence"]:
        content_hash = _content_hash(item["excerpt"])
        key = (item["lawName"], item["article"], item.get("effectiveDate") or "", content_hash)
        if key not in key_to_id:
            evidence_id = f"EVD-{len(values) + 1:03d}"
            key_to_id[key] = evidence_id
            values.append({
                "evidenceId": evidence_id, "sourceType": "OFFICIAL_LAW",
                "lawName": item["lawName"], "article": item.get("article"), "title": item.get("title"),
                "effectiveDate": item.get("effectiveDate"), "officialUrl": item["lawUrl"],
                "excerpt": item["excerpt"], "plainSummary": item["plainSummary"],
                "whyRelevant": item["whyRelevant"], "sourceStatus": "COMPLETE",
                "retrievedAt": item["verifiedAt"], "contentHash": content_hash,
            })
        aliases[item["evidenceId"]] = key_to_id[key]
    return values, aliases


def _normalization_prompt(task_input: dict[str, Any], source: dict[str, Any],
                          evidence: list[dict[str, Any]]) -> str:
    return json.dumps({
        "confirmedBriefVersionId": task_input["confirmedBriefVersionId"],
        "confirmedBriefHash": task_input["confirmedBriefHash"],
        "briefFields": task_input["briefFields"],
        "routes": source["routes"],
        "officialEvidence": evidence,
        "requiredUserInputs": source["requiredUserInputs"],
        "sourceWarnings": source["sourceWarnings"],
        "requiredSchema": BoundaryNormalizationResult.model_json_schema(),
        "deduplicationKey": "ruleType+structureKey+canonical(normalizedRequirement)+canonical(appliesWhen)",
    }, ensure_ascii=False, separators=(",", ":"))


def _validate_and_dedupe(normalized: BoundaryNormalizationResult,
                         evidence: list[dict[str, Any]], task_input: dict[str, Any]) -> BoundaryNormalizationResult:
    evidence_by_id = {item["evidenceId"]: item for item in evidence}
    locked = {item["fieldKey"] for item in task_input["briefFields"]
              if item["decisionStatus"] == "LOCKED" and item["userConfirmed"] is True}
    rules = []
    dedupe: dict[tuple[str, str, str, str], int] = {}
    for rule in normalized.rules:
        if any(value not in evidence_by_id for value in rule.evidenceIds):
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "BOUNDARY_EVIDENCE_REFERENCE_INVALID", 502, False)
        if rule.ruleType != "UNRESOLVED_FACT" and not rule.evidenceIds:
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "BOUNDARY_EVIDENCE_REQUIRED", 502, False)
        requirement = _canonical(rule.normalizedRequirement)
        if requirement == _canonical(rule.title) or any(
                requirement == _canonical(evidence_by_id[value]["plainSummary"])
                for value in rule.evidenceIds):
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "BOUNDARY_RULE_NOT_NORMALIZED", 502, False)
        key = (rule.ruleType, _canonical(rule.structureKey), requirement,
               json.dumps(rule.appliesWhen, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        if key in dedupe:
            existing = rules[dedupe[key]]
            existing.evidenceIds = sorted(set(existing.evidenceIds) | set(rule.evidenceIds))
        else:
            dedupe[key] = len(rules)
            rules.append(rule)
    rule_ids = {rule.ruleId for rule in rules}
    for question in normalized.questions:
        if not set(question.relatedRuleIds) <= rule_ids or not set(question.relatedEvidenceIds) <= set(evidence_by_id):
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "BOUNDARY_REFERENCE_INVALID", 502, False)
    for conflict in normalized.conflicts:
        if conflict.affectedFieldKey not in locked or not set(conflict.conflictingRuleIds) <= rule_ids:
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "BOUNDARY_LOCKED_CONFLICT_INVALID", 502, False)
    return BoundaryNormalizationResult(rules=rules, questions=normalized.questions,
        conflicts=normalized.conflicts, userActionOptions=normalized.userActionOptions)


async def execute_regulatory_boundary(text: str, task_input: dict[str, Any]) -> dict[str, Any]:
    required = {"confirmedBriefVersionId", "confirmedBriefHash", "briefFields"}
    if not required.issubset(task_input) or not isinstance(task_input["briefFields"], list):
        raise ProviderFailure("INVALID_REQUEST", "BOUNDARY_INPUT_CONTRACT_INCOMPLETE", 400, False)
    source = await execute_legal_source_pipeline("IDEA_LEGAL_PRECHECK", text, task_input)
    evidence, _ = _dedupe_evidence(source)
    try:
        raw = await execute_structured_prompt(BOUNDARY_SYSTEM,
            _normalization_prompt(task_input, source, evidence))
        normalized = BoundaryNormalizationResult.model_validate(raw)
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "BOUNDARY_NORMALIZATION_CONTRACT_INVALID", 502, False) from failure
    normalized = _validate_and_dedupe(normalized, evidence, task_input)
    questions = list(normalized.questions)
    if not evidence and not questions:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "BOUNDARY_EVIDENCE_REQUIRED", 502, False)
    status = "BLOCKED" if normalized.conflicts else (
        "NEEDS_INPUT" if questions or any(rule.ruleType == "UNRESOLVED_FACT" for rule in normalized.rules)
        else "READY")
    result = {
        "taskType": "REGULATORY_BOUNDARY_GENERATION",
        "sourceStatus": {"SOURCE_COMPLETE": "COMPLETE", "SOURCE_PARTIAL": "PARTIAL",
                         "REGISTRY_GAP": "WARNING"}[source["sourceStatus"]],
        "registryVersion": source["registryVersion"], "routes": source["routes"],
        "evidence": evidence,
        "rules": [value.model_dump() for value in normalized.rules],
        "questions": [value.model_dump() for value in questions],
        "conflicts": [value.model_dump() for value in normalized.conflicts],
        "status": status, "userActionOptions": normalized.userActionOptions,
        "sourceWarnings": source["sourceWarnings"],
    }
    return RegulatoryBoundaryResult.model_validate(result).model_dump()
