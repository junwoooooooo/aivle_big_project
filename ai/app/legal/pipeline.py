import json
import logging
import re
from collections import defaultdict
from datetime import datetime, timezone
from typing import Any

from pydantic import ValidationError

from app.legal.moleg import MolegClient, MolegFailure
from app.legal.registry import LegalRegistry, RegistryError
from app.models.legal_source import LegalSourcePipelineResult, RoutingResult, ScreeningResult
from app.services.journey_provider import ProviderFailure, execute_structured_prompt


ROUTING_SYSTEM = """당신은 사업 설명에서 조사할 규제 경로를 고르는 라우터다.
법령명이나 조문을 만들지 말고 제공된 routeId만 사용한다. APPLIES/POSSIBLE 판단의 evidenceQuotes는
입력 원문에서 글자 그대로 복사한다. 정보가 부족하면 UNKNOWN과 missingInformation을 반환한다.
입력의 [확정 정보]에 이미 답이 있는 질문을 missingInformation으로 반복하지 말고 route 판단에 반영한다.
JSON 외의 설명을 반환하지 않는다."""

SCREENING_SYSTEM = """당신은 법제처에서 조회된 실제 조문 후보를 분류한다.
제공된 citationId를 하나도 추가하거나 누락하지 말고 각 ID를 정확히 한 번 반환한다.
관련 조문은 screenings에 상세 판정을 반환하고, 무관 조문은 상세 객체를 만들지 말고
excludedCitationIds에 ID만 반환한다. 두 목록에 같은 ID를 중복하지 않는다.
REQUIREMENT는 사업자 의무·금지, SANCTION은 제재, SCOPE는 적용 범위, SUPPORTING은 정의·보조,
EXCLUDE는 무관 조문이다. 법령명·조문번호·의무를 새로 만들지 않는다. JSON만 반환한다."""
logger = logging.getLogger(__name__)
CONTRACT_REPAIR_SYSTEM = """당신은 법률 판단자가 아니라 JSON Contract 복구기다.
invalidResult의 의미, citationId, routeId를 바꾸거나 새 법률 사실을 만들지 말고 requiredSchema와
requiredIds에 정확히 맞는 JSON 객체 하나만 반환한다. 누락 ID는 repairContext에 포함된
원 요청의 후보만 사용해 보완하고, 설명이나 Markdown을 출력하지 않는다."""
SCREENING_BATCH_SIZE = 24
MAX_SCREENING_CANDIDATES = 24


def _normalized(value: str) -> str:
    return re.sub(r"\s+", "", value or "")


def _task_context(text: str, task_input: dict[str, Any]) -> str:
    required = {"mode", "rerunCategories", "confirmedFacts", "registryVersion"}
    if not required.issubset(task_input):
        raise ProviderFailure("INVALID_REQUEST", "LEGAL_INPUT_CONTRACT_INCOMPLETE", 400, False)
    mode = task_input["mode"]
    if mode not in {"FULL", "INCREMENTAL"}:
        raise ProviderFailure("INVALID_REQUEST", "LEGAL_MODE_INVALID", 400, False)
    rerun = task_input["rerunCategories"]
    facts = task_input["confirmedFacts"]
    if not isinstance(rerun, list) or not all(isinstance(value, str) for value in rerun):
        raise ProviderFailure("INVALID_REQUEST", "LEGAL_RERUN_CATEGORIES_INVALID", 400, False)
    if not isinstance(facts, list) or not all(isinstance(value, dict) for value in facts):
        raise ProviderFailure("INVALID_REQUEST", "LEGAL_CONFIRMED_FACTS_INVALID", 400, False)
    return text + ("\n\n[확정 정보]\n" + json.dumps(facts, ensure_ascii=False, sort_keys=True) if facts else "")


async def _route(source_text: str, registry: LegalRegistry) -> RoutingResult:
    prompt = {"source": source_text, "routeCatalog": registry.route_catalog_for_prompt(), "output": {
        "routes": [{"routeId": "string", "status": "APPLIES|POSSIBLE|NOT_APPLICABLE|UNKNOWN",
            "evidenceQuotes": ["verbatim source quote"], "reason": "string", "confidence": 0.0}],
        "additionalRouteCandidates": ["string"],
        "missingInformation": [{"question": "string", "relatedRouteIds": ["string"]}],
    }}
    prompt_json = json.dumps(prompt, ensure_ascii=False)
    raw = await _execute_json_with_retry(ROUTING_SYSTEM, prompt_json, "LEGAL_ROUTING_JSON_INVALID")
    try:
        result = RoutingResult.model_validate(raw)
    except ValidationError as failure:
        repaired = await _repair_result(raw, RoutingResult, [])
        try:
            result = RoutingResult.model_validate(repaired)
        except ValidationError as repair_failure:
            logger.warning("Legal routing contract repair failed errorTypes=%s",
                sorted({item["type"] for item in repair_failure.errors(include_input=False)}))
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "LEGAL_ROUTING_CONTRACT_INVALID", 502, False) from repair_failure
    known = set(registry.routes)
    seen: set[str] = set()
    cleaned = []
    unknown_routes: list[str] = []
    source_normalized = _normalized(source_text)
    for route in result.routes:
        if route.routeId not in known:
            unknown_routes.append(route.routeId)
            continue
        if route.routeId in seen:
            continue
        seen.add(route.routeId)
        quotes = [quote for quote in route.evidenceQuotes if _normalized(quote)
            and _normalized(quote) in source_normalized]
        status = route.status
        if status == "APPLIES" and not quotes:
            status = "POSSIBLE"
        cleaned.append(route.model_copy(update={"status": status, "evidenceQuotes": quotes}))
    returned_route_ids = {route.routeId for route in cleaned}
    missing = [item.model_copy(update={"relatedRouteIds": [route_id for route_id in item.relatedRouteIds if route_id in returned_route_ids]})
        for item in result.missingInformation]
    return RoutingResult(routes=cleaned,
        additionalRouteCandidates=list(dict.fromkeys(result.additionalRouteCandidates + unknown_routes)),
        missingInformation=missing)


def _filter_articles(articles: list[dict[str, str]], keywords: list[str], limit: int = 4) -> list[dict[str, str]]:
    if not articles:
        return []
    ranked = []
    for index, article in enumerate(articles):
        title_match = any(keyword in article["title"] for keyword in keywords)
        body_match = any(keyword in article["text"][:500] for keyword in keywords)
        ranked.append((0 if title_match else 1 if body_match else 2, index, article))
    matched = [item for item in ranked if item[0] < 2]
    selected = matched if len(matched) >= 3 else ranked
    selected = sorted(selected, key=lambda item: (item[0], item[1]))[:limit]
    return [item[2] for item in sorted(selected, key=lambda item: item[1])]


async def _screen(source_text: str, candidates: list[dict[str, Any]]) -> ScreeningResult:
    screenings = []
    excluded = []
    coverage_inferred = False
    for start in range(0, len(candidates), SCREENING_BATCH_SIZE):
        batch = candidates[start:start + SCREENING_BATCH_SIZE]
        result = await _screen_batch(source_text, batch)
        screenings.extend(result.screenings)
        excluded.extend(result.excludedCitationIds)
        coverage_inferred = coverage_inferred or result.coverageInferred
    return ScreeningResult(screenings=screenings, excludedCitationIds=excluded,
        coverageInferred=coverage_inferred)


async def _screen_batch(source_text: str, candidates: list[dict[str, Any]]) -> ScreeningResult:
    payload = {"source": source_text, "candidates": [{"citationId": value["citationId"],
        "routeId": value["routeId"], "lawName": value["lawName"], "article": value["article"],
        "title": value["title"], "excerpt": value["excerpt"]} for value in candidates],
        "output": {"screenings": [{"citationId": "CIT-001",
            "role": "REQUIREMENT|SANCTION|SCOPE|SUPPORTING|EXCLUDE",
            "plainSummary": "string", "whyRelevant": "string"}],
            "excludedCitationIds": ["CIT-002"]}}
    expected = {value["citationId"] for value in candidates}
    raw = await _execute_json_with_retry(SCREENING_SYSTEM,
        json.dumps(payload, ensure_ascii=False), "LEGAL_SCREENING_JSON_INVALID")
    result = _sanitize_screening_result(_screening_result(raw), expected)
    if result is None:
        repaired = await _repair_result(raw, ScreeningResult, sorted(expected), payload)
        result = _sanitize_screening_result(_screening_result(repaired), expected)
        if result is None:
            logger.warning("Legal screening contract repair failed expectedIds=%s actualIds=%s",
                len(expected), 0)
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "LEGAL_CITATION_COVERAGE_INVALID", 502, False)
    result = _infer_omitted_screening_ids(result, expected)
    for value in result.screenings:
        if value.role != "EXCLUDE" and (not value.plainSummary.strip() or not value.whyRelevant.strip()):
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "LEGAL_SCREENING_FIELD_INVALID", 502, False)
    return result


async def _execute_json_with_retry(system: str, user: str, reason: str) -> dict[str, Any]:
    try:
        return await execute_structured_prompt(system, user)
    except ProviderFailure as failure:
        if failure.code != "RESULT_SCHEMA_INVALID":
            raise
        logger.warning("Legal provider JSON regeneration reason=%s", reason)
        return await execute_structured_prompt(system, user)


async def _repair_result(raw: dict[str, Any], model_type, required_ids: list[str],
        repair_context: dict[str, Any] | None = None) -> dict[str, Any]:
    payload = {
        "requiredSchema": model_type.model_json_schema(),
        "requiredIds": required_ids,
        "invalidResult": raw,
    }
    if repair_context is not None:
        payload["repairContext"] = repair_context
    return await _execute_json_with_retry(CONTRACT_REPAIR_SYSTEM,
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
        "LEGAL_CONTRACT_REPAIR_JSON_INVALID")


def _screening_result(raw: dict[str, Any]) -> ScreeningResult | None:
    try:
        result = ScreeningResult.model_validate(raw)
    except ValidationError:
        return None
    if any(value.role != "EXCLUDE" and
           (not value.plainSummary.strip() or not value.whyRelevant.strip())
           for value in result.screenings):
        return None
    return result


def _screened_ids(result: ScreeningResult | None) -> list[str]:
    if result is None:
        return []
    return [value.citationId for value in result.screenings] + result.excludedCitationIds


def _sanitize_screening_result(result: ScreeningResult | None,
        expected: set[str]) -> ScreeningResult | None:
    if result is None:
        return None
    detailed = []
    seen: set[str] = set()
    for value in result.screenings:
        if value.citationId not in expected or value.citationId in seen:
            continue
        detailed.append(value)
        seen.add(value.citationId)
    excluded = []
    for citation_id in result.excludedCitationIds:
        if citation_id not in expected or citation_id in seen:
            continue
        excluded.append(citation_id)
        seen.add(citation_id)
    return ScreeningResult(screenings=detailed, excludedCitationIds=excluded,
        coverageInferred=result.coverageInferred)


def _infer_omitted_screening_ids(result: ScreeningResult,
        expected: set[str]) -> ScreeningResult:
    present = set(_screened_ids(result))
    missing = sorted(expected - present)
    if not missing:
        return result
    logger.info("Legal screening omitted candidates recorded as unselected omittedIds=%s",
        len(missing))
    return ScreeningResult(
        screenings=result.screenings,
        excludedCitationIds=result.excludedCitationIds + missing,
        coverageInferred=True,
    )


def _reasoning(category: str, label: str, route_quotes: list[str], evidence: list[dict[str, Any]]) -> dict[str, Any]:
    requirements = [item for item in evidence if item["role"] in {"REQUIREMENT", "SCOPE"}]
    sanctions = [item for item in evidence if item["role"] == "SANCTION"]
    obligations = [item["plainSummary"] for item in requirements[:3]]
    consequences = [item["plainSummary"] for item in sanctions[:2]]
    return {"category": category, "inputBasis": route_quotes[:5], "regulatoryArea": label,
        "obligation": " ".join(obligations) if obligations else "적용 범위와 직접 의무를 추가 확인해야 합니다.",
        "consequence": " ".join(consequences) if consequences else "확인된 제재 조문이 없거나 추가 검토가 필요합니다.",
        "requiredAction": obligations[0] if obligations else "관련 조건과 사업자 역할을 확인하세요.",
        "evidenceIds": [item["evidenceId"] for item in evidence]}


async def execute_legal_source_pipeline(task_type: str, text: str,
        task_input: dict[str, Any]) -> dict[str, Any]:
    if task_type not in {"IDEA_LEGAL_PRECHECK", "CONCEPT_LEGAL_VALIDATION"}:
        raise ProviderFailure("UNSUPPORTED_TASK_TYPE", "TASK_TYPE_UNSUPPORTED", 422, False)
    source_text = _task_context(text, task_input)
    try:
        registry = LegalRegistry()
    except RegistryError as failure:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "LEGAL_CONFIGURATION_INVALID", 503, False) from failure
    requested_registry = task_input["registryVersion"]
    if requested_registry != registry.version:
        raise ProviderFailure("INVALID_REQUEST", "LEGAL_REGISTRY_VERSION_MISMATCH", 400, False)
    routing = await _route(source_text, registry)
    route_by_id = {value.routeId: value for value in routing.routes}
    active = [value for value in routing.routes if value.status in {"APPLIES", "POSSIBLE"}]
    rerun = set(task_input["rerunCategories"])
    if task_input["mode"] == "INCREMENTAL" and rerun:
        active = [value for value in active if set(registry.categories_for_route(value.routeId)) & rerun]
    registry_gap = bool(routing.additionalRouteCandidates)
    warnings: list[str] = []
    candidates: list[dict[str, Any]] = []
    retryable_source_failure: str | None = None
    client: MolegClient | None = None
    if active:
        try:
            client = MolegClient()
        except MolegFailure as failure:
            raise ProviderFailure("DEPENDENCY_UNAVAILABLE", failure.reason, 503, failure.retryable) from failure
    citation_number = 0
    for decision in active:
        config = registry.routes.get(decision.routeId)
        if not config:
            registry_gap = True
            warnings.append(f"REGISTRY_GAP:{decision.routeId}")
            continue
        if not config["laws"]:
            registry_gap = True
            warnings.append(f"REGISTRY_GAP:{decision.routeId}")
            continue
        for law in config["laws"]:
            try:
                metadata = await client.search_exact(law["name"]) if client else None
                if metadata is None:
                    warnings.append(f"SOURCE_NOT_FOUND:{decision.routeId}:{law['name']}")
                    continue
                articles = _filter_articles(await client.articles(metadata), law.get("focusKeywords") or [])
            except MolegFailure as failure:
                if failure.reason in {"MOLEG_AUTHENTICATION_FAILED", "LEGAL_CONFIGURATION_INVALID"}:
                    raise ProviderFailure("DEPENDENCY_UNAVAILABLE", failure.reason, 503, False) from failure
                if failure.retryable:
                    retryable_source_failure = failure.reason
                warnings.append(f"SOURCE_ERROR:{decision.routeId}:{law['name']}:{failure.reason}")
                continue
            for article in articles:
                citation_number += 1
                categories, fallback = registry.categories_for_article(decision.routeId, law["name"], article["title"])
                if fallback:
                    warnings.append(f"CATEGORY_RULE_FALLBACK:{law['name']}")
                candidates.append({"citationId": f"CIT-{citation_number:03d}", "routeId": decision.routeId,
                    "categories": categories, "lawName": law["name"], "article": article["article"],
                    "title": article["title"], "excerpt": article["text"][:700],
                    "effectiveDate": metadata.effective_date, "lawUrl": metadata.law_url})
    if not candidates and retryable_source_failure:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", retryable_source_failure, 503, True)
    if len(candidates) > MAX_SCREENING_CANDIDATES:
        candidates = candidates[:MAX_SCREENING_CANDIDATES]
        warnings.append("SOURCE_CANDIDATE_LIMIT_APPLIED")
    screening = await _screen(source_text, candidates) if candidates else ScreeningResult(screenings=[])
    if screening.coverageInferred:
        warnings.append("SCREENING_COVERAGE_INFERRED")
    screen_by_id = {value.citationId: value for value in screening.screenings}
    verified_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    evidence: list[dict[str, Any]] = []
    for candidate in candidates:
        screened = screen_by_id.get(candidate["citationId"])
        if screened is None:
            continue
        if screened.role == "EXCLUDE":
            continue
        for category in candidate["categories"]:
            evidence_id = f"EVD-{len(evidence) + 1:03d}"
            evidence.append({"evidenceId": evidence_id, "routeId": candidate["routeId"],
                "category": category, "registryVersion": registry.version,
                "lawName": candidate["lawName"], "article": candidate["article"],
                "title": candidate["title"], "role": screened.role,
                "plainSummary": screened.plainSummary, "whyRelevant": screened.whyRelevant,
                "excerpt": candidate["excerpt"], "effectiveDate": candidate["effectiveDate"],
                "lawUrl": candidate["lawUrl"], "verifiedAt": verified_at})
    by_category: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for item in evidence:
        by_category[item["category"]].append(item)
    findings = []
    for category, values in by_category.items():
        route_ids = {item["routeId"] for item in values}
        decisions = [route_by_id[value] for value in route_ids if value in route_by_id]
        applicability = "APPLIES" if any(value.status == "APPLIES" for value in decisions) else "POSSIBLE"
        quotes = [quote for value in decisions for quote in value.evidenceQuotes]
        reasoning = _reasoning(category, registry.category_labels.get(category, category), quotes, values)
        findings.append({"category": category, "applicability": applicability,
            "summary": reasoning["requiredAction"], "evidenceIds": reasoning["evidenceIds"],
            "reasoning": reasoning})
    source_partial = bool(warnings) or (bool(active) and not evidence)
    source_status = "REGISTRY_GAP" if registry_gap else "SOURCE_PARTIAL" if source_partial else "SOURCE_COMPLETE"
    result = LegalSourcePipelineResult(taskType=task_type, sourceStatus=source_status,
        registryVersion=registry.version,
        routes=[{"routeId": value.routeId, "topic": registry.routes[value.routeId]["topic"],
            "status": value.status, "evidenceQuotes": value.evidenceQuotes, "reason": value.reason,
            "categories": registry.categories_for_route(value.routeId)} for value in routing.routes],
        findings=findings, evidence=evidence, requiredUserInputs=routing.missingInformation,
        sourceWarnings=sorted(set(warnings + [f"REGISTRY_CANDIDATE:{value}" for value in routing.additionalRouteCandidates])))
    return result.model_dump()
