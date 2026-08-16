#!/usr/bin/env python3
"""Validate P2 public/internal contract fixtures with the Python standard library."""

from __future__ import annotations

import hashlib
import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any


HERE = Path(__file__).resolve().parent
CONTRACTS = HERE.parents[1]
INTERNAL_DOC = CONTRACTS / "INTERNAL_AI_API_V1_CONTRACT.md"
PUBLIC_DOC = CONTRACTS / "PUBLIC_API_V2_CONTRACT.md"
STATUS_DOC = CONTRACTS / "STATUS_AND_ERROR_CONTRACT.md"
MANIFEST_PATH = HERE / "manifest.json"
FIXTURE_NOW = "2030-01-01T00:00:00Z"
MAX_JSON_BYTES = 2 * 1024 * 1024
RAW_BYTE_LENGTHS: dict[str, int] = {}

TASK_TYPES = (
    "IDEA_INTERPRETATION", "LEGAL_REVIEW", "IDEA_LEGAL_PRECHECK",
    "CONCEPT_LEGAL_VALIDATION", "CONCEPT_GENERATION",
    "QUICK_ASSESSMENT", "DETAILED_ANALYSIS", "PERSONA_CARD_GENERATION",
    "PERSONA_INTERVIEW", "INTERVIEW_SYNTHESIS", "MARKETING_GENERATION",
    "MARKETING_COMPARISON", "FINAL_REPORT_GENERATION",
)
INTERNAL_ERRORS = (
    "INVALID_REQUEST", "UNAUTHORIZED_INTERNAL_CALL",
    "UNSUPPORTED_CONTRACT_VERSION", "UNSUPPORTED_TASK_TYPE",
    "UNSUPPORTED_TASK_SCHEMA_VERSION", "PAYLOAD_TOO_LARGE",
    "DEADLINE_EXCEEDED", "DEPENDENCY_UNAVAILABLE", "RATE_LIMITED",
    "EXECUTION_FAILED", "RESULT_SCHEMA_INVALID", "INTERNAL_ERROR",
)
LEGAL_RESULTS = {
    "PASS", "PASS_WITH_CONDITIONS", "REVISION_REQUIRED", "PROHIBITED",
    "INSUFFICIENT_INFORMATION", "EXPERT_REVIEW_REQUIRED",
}
ANALYSIS_TYPES = {"MARKET", "BUSINESS_MODEL", "TECHNICAL_OPERATION", "FINANCIAL"}
REPORT_DECISIONS = {"GO", "CONDITIONAL_GO", "REWORK", "HOLD", "STOP"}
PROVENANCE_CATEGORIES = {
    "USER_INPUT", "EXTERNAL_SOURCE_FACT", "ASSUMPTION", "AI_PROPOSAL", "USER_DECISION"
}
MARKETING_ASSET_TYPES = {"HEADLINE", "BODY_COPY", "CTA", "CAMPAIGN_CONCEPT"}
RESOURCE_TYPES = {
    "SOURCE_EXTRACTION", "SOURCE_STATEMENT", "IDEA_VERSION", "LEGAL_REVIEW_RUN",
    "CONCEPT_VERSION", "SHORTLIST_DECISION", "CONCEPT_SELECTION", "EVIDENCE_ITEM",
    "PERSONA_STUDY", "PERSONA_CARD_VERSION", "PERSONA_INTERVIEW_RESULT",
    "MARKETING_WORKSPACE_VERSION", "MARKETING_ASSET_VERSION", "QUESTION",
    "COMPARISON_DIMENSION", "REPORT_UPSTREAM_RESOURCE",
}
HASH_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
LOCAL_KEY_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
FORBIDDEN_KEYS = {
    "storageUrl", "objectKey", "presignedUrl", "localPath", "fileBytes",
    "fileContentBase64", "binary", "base64", "userJwt", "refreshToken",
    "sessionId", "credential", "providerName", "modelName", "sdkName",
}
MANIFEST_FIELDS = {
    "fixtureId", "path", "category", "contractObject", "taskType", "schemaName",
    "expectedValid", "expectedErrorCode", "expectedReason", "invariants",
    "matchingContractSection", "expectedValidatorRule", "coveredSchemas",
    "primaryInvariant",
}

TASK_SCHEMAS = {
    "IDEA_INTERPRETATION": ("IdeaInterpretationInputV1", "IdeaInterpretationResultV1"),
    "LEGAL_REVIEW": ("LegalReviewInputV1", "LegalReviewResultV1"),
    "IDEA_LEGAL_PRECHECK": ("LegalSourcePipelineInputV1", "LegalSourcePipelineResultV1"),
    "CONCEPT_LEGAL_VALIDATION": ("ConceptLegalValidationBatchInputV1", "ConceptLegalValidationBatchResultV1"),
    "CONCEPT_GENERATION": ("ConceptGenerationInputV1", "ConceptGenerationResultV1"),
    "QUICK_ASSESSMENT": ("QuickAssessmentInputV1", "QuickAssessmentResultV1"),
    "DETAILED_ANALYSIS": ("DetailedAnalysisInputV1", "DetailedAnalysisResultV1"),
    "PERSONA_CARD_GENERATION": ("PersonaCardGenerationInputV1", "PersonaCardGenerationResultV1"),
    "PERSONA_INTERVIEW": ("PersonaInterviewInputV1", "PersonaInterviewResultV1"),
    "INTERVIEW_SYNTHESIS": ("InterviewSynthesisInputV1", "InterviewSynthesisResultV1"),
    "MARKETING_GENERATION": ("MarketingGenerationInputV1", "MarketingGenerationResultV1"),
    "MARKETING_COMPARISON": ("MarketingComparisonInputV1", "MarketingComparisonResultV1"),
    "FINAL_REPORT_GENERATION": ("FinalReportGenerationInputV1", "FinalReportGenerationResultV1"),
}

PREPARSE_REASONS = {
    "JSON_PARSE_FAILED", "REQUEST_BYTES_EXCEEDED", "SERVICE_TOKEN_MISSING",
    "SERVICE_TOKEN_INVALID", "INTERNAL_PRINCIPAL_FORBIDDEN",
}
REQUEST_FIELDS = {
    "contractVersion", "taskType", "taskSchemaVersion", "taskRunId", "taskAttemptId",
    "correlationId", "deadlineAt", "canonicalInputHash", "locale", "input",
}
SUCCESS_FIELDS = {
    "contractVersion", "taskType", "taskSchemaVersion", "taskRunId", "taskAttemptId",
    "correlationId", "canonicalInputHash", "resultSchemaVersion", "result", "warnings",
    "provenance", "usage",
}
ERROR_BODY_FIELDS = {
    "code", "message", "correlationId", "taskRunId", "taskAttemptId", "retryable", "details"
}
DETAIL_FIELDS = {"reason", "field", "limitName", "supportedValues", "retryAfterSeconds"}

TASK_INPUT_FIELDS = {
    "IDEA_INTERPRETATION": {"textContents", "sourceReferences", "normalizationMode", "maxOpenQuestions", "preserveSourceWording"},
    "LEGAL_REVIEW": {"ideaVersionKey", "normalizedDescription", "facts", "assumptions", "constraints", "jurisdiction", "includeRelatedStatutes"},
    "IDEA_LEGAL_PRECHECK": {"mode", "rerunCategories", "confirmedFacts", "registryVersion", "promptVersion", "sourceSchemaVersion", "textContents"},
    "CONCEPT_LEGAL_VALIDATION": {"textContents", "validationMode", "guardrailVersionId"},
    "CONCEPT_GENERATION": {"ideaVersionKey", "legalReviewKey", "normalizedDescription", "facts", "assumptions", "constraints", "legalResult", "legalConditions", "candidateCount", "generationFocuses"},
    "QUICK_ASSESSMENT": {"conceptVersionKey", "concept", "sharedEvidence", "dimensionKeys"},
    "DETAILED_ANALYSIS": {"conceptVersionKey", "shortlistDecisionKey", "analysisType", "sharedEvidence", "marketInput", "businessModelInput", "technicalOperationInput", "financialInput"},
    "PERSONA_CARD_GENERATION": {"personaStudyKey", "conceptSelectionKey", "selectedConceptVersionKey", "selectedConcept", "personaCount", "diversityFocuses"},
    "PERSONA_INTERVIEW": {"personaStudyKey", "personaCardVersionKey", "personaCard", "selectedConceptVersionKey", "questions", "responseStyle"},
    "INTERVIEW_SYNTHESIS": {"personaStudyKey", "includedInterviews", "excludedInterviewKeys", "synthesisFocuses"},
    "MARKETING_GENERATION": {"workspaceVersionKey", "selectedConceptVersionKey", "personaEvidence", "assetType", "targetPersonaKeys", "generationBrief", "tone"},
    "MARKETING_COMPARISON": {"workspaceVersionKey", "assets", "personaEvidence", "comparisonDimensions"},
    "FINAL_REPORT_GENERATION": {"upstreamReferences", "facts", "legalSources", "aiProposals", "assumptions", "researchNeeds", "userDecisions", "reportDecision", "userRationale"},
}
TASK_RESULT_FIELDS = {
    "IDEA_INTERPRETATION": {"originalSourceSummary", "normalizedDescription", "facts", "assumptions", "constraints", "openQuestions", "readiness", "warnings", "evidenceNeeds", "originDraft", "fieldMetadata", "clarificationQuestions"},
    "LEGAL_REVIEW": {"legalResult", "findings", "sourceReferences", "sourceCoverage", "conditions", "warnings", "expertReviewReasons", "provenance"},
    "IDEA_LEGAL_PRECHECK": {"taskType", "sourceStatus", "registryVersion", "routes", "findings", "evidence", "requiredUserInputs", "sourceWarnings"},
    "CONCEPT_LEGAL_VALIDATION": {"validations"},
    "CONCEPT_GENERATION": {"concepts", "warnings", "provenance"},
    "QUICK_ASSESSMENT": {"dimensions", "evidence", "assumptions", "uncertainties", "warnings", "evidenceNeeds", "provenance"},
    "DETAILED_ANALYSIS": {"analysisType", "findings", "marketResult", "businessModelResult", "technicalOperationResult", "financialResult", "warnings", "provenance"},
    "PERSONA_CARD_GENERATION": {"personaCards", "warnings", "provenance"},
    "PERSONA_INTERVIEW": {"responses", "warnings", "syntheticDisclosure", "provenance"},
    "INTERVIEW_SYNTHESIS": {"commonResponses", "conflictingResponses", "unresolvedQuestions", "researchRecommendations", "caveats", "provenance"},
    "MARKETING_GENERATION": {"assets", "warnings", "provenance"},
    "MARKETING_COMPARISON": {"assessments", "overallCaveats", "evidenceNeeds", "provenance"},
    "FINAL_REPORT_GENERATION": {"reportDecision", "executiveSummary", "sections", "supportingFindings", "risks", "unresolvedResearch", "caveats", "provenance"},
}


class ValidationFailure(Exception):
    def __init__(self, path: str, rule: str, expected: str, actual: str):
        super().__init__(rule)
        self.path, self.rule, self.expected, self.actual = path, rule, expected, actual


def fail(path: str, rule: str, expected: Any, actual: Any) -> None:
    raise ValidationFailure(path, rule, str(expected), str(actual))


def section(text: str, start: str, end: str) -> str:
    try:
        return text.split(start, 1)[1].split(end, 1)[0]
    except IndexError as exc:
        raise ValidationFailure(str(INTERNAL_DOC), "DOC_SECTION", f"{start}..{end}", "missing") from exc


def canonical_value(value: Any) -> Any:
    if isinstance(value, str):
        return unicodedata.normalize("NFC", value)
    if isinstance(value, list):
        return [canonical_value(item) for item in value]
    if isinstance(value, dict):
        return {unicodedata.normalize("NFC", key): canonical_value(item) for key, item in value.items()}
    return value


def canonical_json(value: Any) -> str:
    return json.dumps(canonical_value(value), ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def canonical_hash(request: dict[str, Any]) -> tuple[str, str]:
    target = {key: request[key] for key in ("contractVersion", "taskType", "taskSchemaVersion", "locale", "input")}
    encoded = canonical_json(target).encode("utf-8")
    return encoded.decode("utf-8"), "sha256:" + hashlib.sha256(encoded).hexdigest()


def sha256_text(text: str) -> str:
    return "sha256:" + hashlib.sha256(text.encode("utf-8")).hexdigest()


def walk_keys(value: Any):
    if isinstance(value, dict):
        for key, item in value.items():
            yield key
            yield from walk_keys(item)
    elif isinstance(value, list):
        for item in value:
            yield from walk_keys(item)


def _json_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    normalized: dict[str, str] = {}
    for key, value in pairs:
        if key in result:
            fail("<json>", "DUPLICATE_JSON_KEY", "unique object keys", key)
        nfc_key = unicodedata.normalize("NFC", key)
        if nfc_key in normalized and normalized[nfc_key] != key:
            fail("<json>", "NORMALIZED_KEY_COLLISION", "unique NFC keys", nfc_key)
        normalized[nfc_key] = key
        result[key] = value
    return result


def _reject_float(value: str) -> None:
    fail("<json>", "FLOAT_NUMBER_NOT_ALLOWED", "decimal string", value)


def decode_json(raw: bytes, path: str) -> Any:
    if raw.startswith(b"\xef\xbb\xbf"):
        fail(path, "UTF8_BOM", "absent", "present")
    if b"\r\n" in raw or b"\r" in raw:
        fail(path, "LINE_ENDING_INVALID", "LF", "CRLF/CR")
    try:
        return json.loads(
            raw.decode("utf-8"), object_pairs_hook=_json_pairs,
            parse_float=_reject_float,
        )
    except ValidationFailure as exc:
        if exc.path == "<json>":
            exc.path = path
        raise
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        fail(path, "JSON_PARSE", "valid UTF-8 JSON", type(exc).__name__)


def load_json_file(path: Path) -> Any:
    raw = path.read_bytes()
    try:
        key = path.resolve().relative_to(HERE.resolve()).as_posix()
    except ValueError:
        key = path.as_posix()
    RAW_BYTE_LENGTHS[key] = len(raw)
    return decode_json(raw, path.as_posix())


def parse_field_schemas(internal: str) -> dict[str, dict[str, dict[str, str]]]:
    headings = list(re.finditer(r"(?m)^### ([A-Za-z][A-Za-z0-9]+V1)\r?$", internal))
    schemas: dict[str, dict[str, dict[str, str]]] = {}
    header = "| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |"
    for index, heading in enumerate(headings):
        end = headings[index + 1].start() if index + 1 < len(headings) else len(internal)
        lines = internal[heading.end():end].splitlines()
        if header not in lines:
            continue
        cursor = lines.index(header) + 2
        fields: dict[str, dict[str, str]] = {}
        while cursor < len(lines) and lines[cursor].startswith("|"):
            cells = [cell.strip() for cell in lines[cursor].strip("|").split("|")]
            if len(cells) != 6:
                fail(str(INTERNAL_DOC), "SCHEMA_TABLE_ROW", "6 cells", lines[cursor])
            field, json_type, presence, nullable, bounds, semantic = cells
            if field in fields:
                fail(str(INTERNAL_DOC), "SCHEMA_FIELD_DUPLICATE", "unique", f"{heading.group(1)}.{field}")
            if presence not in {"REQUIRED", "OPTIONAL"} or nullable not in {"YES", "NO"}:
                fail(str(INTERNAL_DOC), "SCHEMA_PRESENCE", "REQUIRED/OPTIONAL and YES/NO", f"{presence}/{nullable}")
            fields[field] = {
                "type": json_type, "presence": presence, "nullable": nullable,
                "bounds": bounds, "semantic": semantic,
            }
            cursor += 1
        schemas[heading.group(1)] = fields
    if len(schemas) != 79:
        fail(str(INTERNAL_DOC), "NAMED_SCHEMA_REGISTRY", 79, len(schemas))
    return schemas


def parse_consistency_registry(text: str) -> tuple[dict[str, set[str]], dict[str, str]]:
    try:
        block = text.split("### Public/Internal consistency registry", 1)[1]
    except IndexError:
        fail("contract", "CONSISTENCY_REGISTRY", "named registry section", "missing")
    registries: dict[str, set[str]] = {}
    invariants: dict[str, str] = {}
    registry_names = {"Legal Result", "Analysis Type", "Report Decision", "Provenance Category", "Marketing Asset Type"}
    for name, values in re.findall(r"(?m)^\| ([A-Za-z][A-Za-z ]+) \| ([^|]+) \|$", block):
        if name not in registry_names:
            continue
        found = set(re.findall(r"`([A-Z][A-Z0-9_]+)`", values))
        if found:
            registries[name] = found
    for name, value in re.findall(r"(?m)^\| ([A-Z][A-Z0-9_]+) \| `([A-Z][A-Z0-9_]+)` \|$", block):
        invariants[name] = value
    return registries, invariants


def parse_registries(
    internal: str, public: str, status: str,
) -> tuple[dict[tuple[str, str], dict[str, str]], dict[str, dict[str, dict[str, str]]], set[str], int]:
    banned = (
        "array<object>", "exact fields below", "each exact {", "array of {",
        "policy-dependent", "indicated by response", "six canonical values", "task별 allowlist",
    )
    for marker in banned:
        if marker in internal:
            fail(str(INTERNAL_DOC), "BANNED_MARKER", "0", marker)
    if re.search(r"(?m)^\|[^\n]+\| (same|request value) \|", internal):
        fail(str(INTERNAL_DOC), "AMBIGUOUS_BOUNDS", "0", "same/request value")

    task_text = section(internal, "## 7. Task registry", "### Task-specific collection limits")
    tasks = re.findall(r"(?m)^\| `([A-Z_]+)` \|", task_text)
    if tuple(tasks) != TASK_TYPES:
        fail(str(INTERNAL_DOC), "TASK_REGISTRY", TASK_TYPES, tasks)

    error_text = section(internal, "## 6. Internal error envelope", "## 7. Task registry")
    error_codes = list(dict.fromkeys(re.findall(r"(?m)^\| `([A-Z_]+)` \|", error_text)))
    if tuple(error_codes) != INTERNAL_ERRORS:
        fail(str(INTERNAL_DOC), "ERROR_REGISTRY", INTERNAL_ERRORS, error_codes)

    reason_text = section(internal, "### Internal Error Reason Registry", "### UsageSummaryV1")
    rows = re.findall(
        r"(?m)^\| `([A-Z_]+)` \| `([A-Z_]+)` \| (true|false) \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \| `([A-Z_]+)` \|$",
        reason_text,
    )
    reason_registry: dict[tuple[str, str], dict[str, str]] = {}
    for code, reason, retryable, required, optional, forbidden, direction, public_code in rows:
        key = (code, reason)
        if key in reason_registry:
            fail(str(INTERNAL_DOC), "ERROR_REASON_DUPLICATE", "unique", key)
        reason_registry[key] = {
            "retryable": retryable,
            "required": required.strip(),
            "optional": optional.strip(),
            "direction": direction.strip(),
            "public": public_code,
        }
    if {code for code, _ in reason_registry} != set(INTERNAL_ERRORS):
        fail(str(INTERNAL_DOC), "ERROR_REASON_REGISTRY", "all 12 codes represented", len(reason_registry))

    schema_specs = parse_field_schemas(internal)
    schemas = set(schema_specs)
    bound_total, _ = classify_all_bound_specs(schema_specs)

    for required in (RESOURCE_TYPES, PROVENANCE_CATEGORIES, LEGAL_RESULTS, ANALYSIS_TYPES, REPORT_DECISIONS, MARKETING_ASSET_TYPES):
        if not all(value in internal for value in required):
            fail(str(INTERNAL_DOC), "REGISTRY_VALUE", sorted(required), "missing")

    catalog = section(public, "## 3. Public API As-Is Matrix", "## 4. 구현 상태 구분")
    if "| 기능 | Method | 실제 Path | Request | HTTP Status | Response Envelope | 실행 방식 | 현재 UI 사용 여부 |" not in catalog:
        fail(str(PUBLIC_DOC), "PUBLIC_MATRIX_HEADER", "current As-Is matrix columns", "missing")
    endpoint_count = len(re.findall(r"(?m)^\| [^|]+ \| (?:GET|POST|PUT|DELETE|PATCH) \|", catalog))
    if endpoint_count != 48:
        fail(str(PUBLIC_DOC), "PUBLIC_AS_IS_ENDPOINT_COUNT", 48, endpoint_count)
    internal_registry, internal_invariants = parse_consistency_registry(internal)
    expected_registry = {
        "Legal Result": LEGAL_RESULTS,
        "Analysis Type": ANALYSIS_TYPES,
        "Report Decision": REPORT_DECISIONS,
        "Provenance Category": PROVENANCE_CATEGORIES,
        "Marketing Asset Type": MARKETING_ASSET_TYPES,
    }
    if internal_registry != expected_registry:
        fail("contract registry", "EXACT_ENUM_CONSISTENCY", expected_registry, internal_registry)
    expected_invariants = {
        "FINANCIAL_DETERMINISTIC_INPUT_OWNERSHIP": "SPRING_ONLY",
        "PERSONA_SYNTHETIC_DISCLOSURE": "REQUIRED",
        "MARKETING_PROBABILITY_CLAIMS": "FORBIDDEN",
        "MARKETING_STATISTICAL_AB_CLAIM": "FORBIDDEN",
    }
    if internal_invariants != expected_invariants:
        fail("contract registry", "CROSS_CONTRACT_INVARIANT", expected_invariants, internal_invariants)
    normalization = section(
        status,
        "### Internal failure normalization registry",
        "### End internal failure normalization registry",
    )
    public_errors = set(re.findall(r"(?m)^\| `([A-Z_]+)` \|", normalization))
    mapped = {rule["public"] for rule in reason_registry.values()}
    if not mapped.issubset(public_errors):
        fail("contract registry", "ERROR_MAPPING_CONSISTENCY", sorted(public_errors), sorted(mapped - public_errors))
    return reason_registry, schema_specs, schemas, bound_total


def parse_range(bounds: str) -> tuple[int, int] | None:
    match = re.search(r"(?<![0-9])([0-9][0-9,]*)[–-]([0-9][0-9,]*)(?![0-9])", bounds)
    if not match:
        return None
    return int(match.group(1).replace(",", "")), int(match.group(2).replace(",", ""))


def bare_string_literal(bounds: str) -> str | None:
    match = re.fullmatch(r"`([^`]+)`", bounds.strip())
    return match.group(1) if match else None


def classify_bound_spec(json_type: str, bounds: str) -> str:
    structural = {
        "exact object", "exactly one", "one named object", "one object",
        "object or null", "one named object", "해당 `*InputV1`", "해당 `*ResultV1`",
    }
    if bounds in structural or json_type in {"task-discriminated object", "JSON value"}:
        return "STRUCTURAL_ONLY"
    enum_marker = (
        "enum" in json_type and (
            bool(re.findall(r"`[A-Z][A-Z0-9_.-]*`", bounds))
            or any(marker in bounds for marker in (
                "Registry", "Task Registry", "Internal Error Mapping",
                "MOLEG_API/LEGAL_MCP",
            ))
        )
    )
    if enum_marker:
        return "ENUM_REFERENCE"
    executable_markers = (
        parse_range(bounds) is not None,
        "minItems" in bounds or "maxItems" in bounds,
        "maxLength" in bounds,
        "RFC 3339 UTC" in bounds,
        "SHA-256" in bounds or "sha256:" in bounds,
        "LocalKey" in bounds,
        bounds.startswith("HTTPS"),
        "uppercase snake case" in bounds,
        "uppercase ASCII letters" in bounds,
        "`[A-Za-z0-9._-]+`" in bounds or "`[A-Z][A-Z0-9_]*`" in bounds,
        "canonical decimal regex" in bounds,
        bounds == "true/false",
        json_type == "string" and bare_string_literal(bounds) is not None,
    )
    if any(executable_markers):
        return "EXECUTABLE"
    semantic_only = {
        "INPUT/CONCEPT_SELECTION LocalKey", "INPUT/CONCEPT_VERSION LocalKey",
        "INPUT/IDEA_VERSION LocalKey", "INPUT/LEGAL_REVIEW_RUN LocalKey",
        "INPUT/MARKETING_WORKSPACE_VERSION LocalKey", "INPUT/PERSONA_CARD_VERSION LocalKey",
        "INPUT/PERSONA_STUDY LocalKey", "INPUT/SHORTLIST_DECISION LocalKey",
        "output LocalKey",
    }
    if bounds in semantic_only:
        return "SEMANTIC_ONLY"
    fail(str(INTERNAL_DOC), "UNSUPPORTED_BOUND_SPEC", "classified Bounds/enum expression", f"{json_type}: {bounds}")


def classify_all_bound_specs(schemas: dict[str, dict[str, dict[str, str]]]) -> tuple[int, dict[str, int]]:
    counts: Counter[str] = Counter()
    total = 0
    for fields in schemas.values():
        for spec in fields.values():
            counts[classify_bound_spec(spec["type"], spec["bounds"])] += 1
            total += 1
    return total, dict(counts)


def validate_literal_bound_handlers(schemas: dict[str, dict[str, dict[str, str]]]) -> None:
    literals = [
        (schema_name, field, spec["bounds"], bare_string_literal(spec["bounds"]))
        for schema_name, fields in schemas.items()
        for field, spec in fields.items()
        if spec["type"] == "string" and bare_string_literal(spec["bounds"]) is not None
    ]
    if not literals:
        fail(str(INTERNAL_DOC), "UNSUPPORTED_BOUND_SPEC", "at least one executable string literal", "none")
    for schema_name, field, bounds, literal in literals:
        assert literal is not None
        validate_string_bounds(f"<literal-handler:{schema_name}.{field}>", field, literal, "string", bounds)
        try:
            validate_string_bounds(
                f"<literal-handler:{schema_name}.{field}>", field, literal + "-mismatch", "string", bounds
            )
        except ValidationFailure as exc:
            if exc.rule != "STRING_LITERAL_MISMATCH":
                fail(exc.path, "UNSUPPORTED_BOUND_SPEC", "STRING_LITERAL_MISMATCH handler", exc.rule)
        else:
            fail(
                f"<literal-handler:{schema_name}.{field}>",
                "UNSUPPORTED_BOUND_SPEC",
                "executable literal mismatch handler",
                "missing",
            )


def parse_item_bounds(bounds: str) -> tuple[int | None, int | None]:
    minimum = re.search(r"minItems ([0-9][0-9,]*)", bounds)
    maximum = re.search(r"maxItems ([0-9][0-9,]*)", bounds)
    return (
        int(minimum.group(1).replace(",", "")) if minimum else None,
        int(maximum.group(1).replace(",", "")) if maximum else None,
    )


def validate_timestamp(path: str, value: str, rule: str = "TIMESTAMP_FORMAT") -> datetime:
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z", value):
        fail(path, rule, "RFC 3339 UTC Z", "invalid")
    try:
        return datetime.fromisoformat(value[:-1] + "+00:00").astimezone(timezone.utc)
    except ValueError:
        fail(path, rule, "valid RFC 3339 UTC Z", "invalid")


def enum_values(field: str, bounds: str) -> set[str] | None:
    values = set(re.findall(r"`([A-Z][A-Z0-9_.-]*)`", bounds))
    if values:
        return values
    if "Task Registry" in bounds:
        return set(TASK_TYPES)
    if "Internal Error Mapping" in bounds:
        return set(INTERNAL_ERRORS)
    if "Request-local Resource Type Registry" in bounds:
        return set(RESOURCE_TYPES)
    if field in {"attemptedChannels", "successfulChannels", "missingChannels"}:
        return {"MOLEG_API", "LEGAL_MCP"}
    return None


def validate_string_bounds(path: str, field: str, value: str, json_type: str, bounds: str) -> None:
    if "timestamp" in json_type:
        validate_timestamp(path, value, "DEADLINE_FORMAT" if field == "deadlineAt" else "TIMESTAMP_FORMAT")
        return
    if "decimal string" == json_type:
        if not re.fullmatch(r"(?:0|[1-9]\d*)(?:\.\d+)?|-(?:0|[1-9]\d*)(?:\.\d+)?", value):
            fail(path, "DECIMAL_STRING", "canonical decimal string", "invalid")
        if "maxLength" in bounds:
            maximum = int(re.search(r"maxLength ([0-9]+)", bounds).group(1))
            if len(value) > maximum:
                fail(path, "STRING_BOUNDS", f"maxLength {maximum}", len(value))
        if bounds.startswith("0–1"):
            try:
                number = Decimal(value)
            except InvalidOperation:
                fail(path, "DECIMAL_STRING", "0..1", "invalid")
            if not Decimal(0) <= number <= Decimal(1):
                fail(path, "DECIMAL_BOUNDS", "0..1", value)
        return
    literal = bare_string_literal(bounds) if json_type == "string" else None
    if literal is not None and value != literal:
        fail(path, "STRING_LITERAL_MISMATCH", literal, value)
    length_range = parse_range(bounds)
    if length_range and not bounds.startswith("0–1 inclusive"):
        minimum, maximum = length_range
        if not minimum <= len(value) <= maximum:
            fail(path, "STRING_BOUNDS", f"{minimum}..{maximum}", len(value))
    max_length = re.search(r"maxLength ([0-9,]+)", bounds)
    if max_length and len(value) > int(max_length.group(1).replace(",", "")):
        fail(path, "STRING_BOUNDS", f"maxLength {max_length.group(1)}", len(value))
    if "blank 금지" in bounds and not value.strip():
        fail(path, "STRING_BLANK", "non-blank", "blank")
    if "LocalKey" in bounds or field.endswith("Key") or field in {"statementKey", "sourceKey"}:
        if not LOCAL_KEY_RE.fullmatch(value):
            fail(path, "LOCAL_KEY_FORMAT", "1..64 local key", "invalid")
    if "uppercase snake case" in bounds and not re.fullmatch(r"[A-Z][A-Z0-9_]{0,63}", value):
        fail(path, "UPPER_SNAKE_CASE", "uppercase snake case", "invalid")
    if "`[A-Za-z0-9._-]+`" in bounds and not re.fullmatch(r"[A-Za-z0-9._-]+", value):
        fail(path, "STRING_PATTERN", "[A-Za-z0-9._-]+", "invalid")
    if "`[A-Z][A-Z0-9_]*`" in bounds and not re.fullmatch(r"[A-Z][A-Z0-9_]*", value):
        fail(path, "STRING_PATTERN", "[A-Z][A-Z0-9_]*", "invalid")
    if "exactly 3 uppercase ASCII letters" in bounds and not re.fullmatch(r"[A-Z]{3}", value):
        fail(path, "CURRENCY_CODE", "3 uppercase ASCII letters", "invalid")
    if "SHA-256" in bounds or "sha256:" in bounds:
        if not HASH_RE.fullmatch(value):
            fail(path, "HASH_FORMAT", "sha256: + lowercase 64 hex", "invalid")
    if bounds.startswith("HTTPS") and not value.startswith("https://"):
        fail(path, "HTTPS_URL", "https://", "invalid")


def validate_schema(
    path: str,
    value: Any,
    schema_name: str,
    schemas: dict[str, dict[str, dict[str, str]]],
    coverage: set[str],
    task_type: str | None = None,
    field_path: str = "$",
) -> None:
    if schema_name not in schemas:
        fail(path, "SCHEMA_REFERENCE", "known named schema", schema_name)
    coverage.add(schema_name)
    if not isinstance(value, dict):
        fail(path, "SCHEMA_TYPE", f"{field_path}: object", type(value).__name__)
    fields = schemas[schema_name]
    required = {name for name, spec in fields.items() if spec["presence"] == "REQUIRED"}
    missing = required - set(value)
    if missing:
        fail(path, "SCHEMA_REQUIRED_FIELD", f"{field_path}: {sorted(required)}", sorted(missing))
    unknown = set(value) - set(fields)
    if unknown:
        fail(path, "SCHEMA_UNKNOWN_FIELD", f"{field_path}: exact fields", sorted(unknown))
    for field, item in value.items():
        spec = fields[field]
        child_path = f"{field_path}.{field}"
        if item is None:
            if spec["nullable"] != "YES":
                fail(path, "SCHEMA_NULLABILITY", f"{child_path}: non-null", "null")
            continue
        json_type, bounds = spec["type"], spec["bounds"]
        if json_type in {"string", "string enum", "string timestamp", "decimal string"}:
            if not isinstance(item, str):
                fail(path, "SCHEMA_TYPE", f"{child_path}: string", type(item).__name__)
            validate_string_bounds(path, field, item, json_type, bounds)
            if "enum" in json_type:
                allowed = enum_values(field, bounds)
                if allowed is not None and item not in allowed:
                    fail(path, "SCHEMA_ENUM", f"{child_path}: {sorted(allowed)}", item)
        elif json_type == "integer":
            if isinstance(item, bool) or not isinstance(item, int):
                fail(path, "SCHEMA_TYPE", f"{child_path}: integer", type(item).__name__)
            numeric_range = parse_range(bounds)
            if numeric_range and not numeric_range[0] <= item <= numeric_range[1]:
                fail(path, "INTEGER_BOUNDS", f"{child_path}: {numeric_range}", item)
        elif json_type == "boolean":
            if not isinstance(item, bool):
                fail(path, "SCHEMA_TYPE", f"{child_path}: boolean", type(item).__name__)
        elif json_type.startswith("array<"):
            if not isinstance(item, list):
                fail(path, "SCHEMA_TYPE", f"{child_path}: array", type(item).__name__)
            minimum, maximum = parse_item_bounds(bounds)
            if minimum is not None and len(item) < minimum or maximum is not None and len(item) > maximum:
                fail(path, "ARRAY_BOUNDS", f"{child_path}: {minimum}..{maximum}", len(item))
            element_type = json_type[6:-1]
            for index, element in enumerate(item):
                element_path = f"{child_path}[{index}]"
                if element_type in schemas:
                    validate_schema(path, element, element_type, schemas, coverage, task_type, element_path)
                elif element_type in {"string", "string enum"}:
                    if not isinstance(element, str):
                        fail(path, "SCHEMA_TYPE", f"{element_path}: string", type(element).__name__)
                    allowed = enum_values(field, bounds) if element_type == "string enum" else None
                    if allowed is not None and element not in allowed:
                        fail(path, "SCHEMA_ENUM", f"{element_path}: {sorted(allowed)}", element)
                    each_range = re.search(r"each ([0-9,]+)[–-]([0-9,]+)", bounds)
                    if each_range:
                        low, high = (int(v.replace(",", "")) for v in each_range.groups())
                        if not low <= len(element) <= high:
                            fail(path, "STRING_BOUNDS", f"{element_path}: {low}..{high}", len(element))
                    if "LocalKey" in bounds and not LOCAL_KEY_RE.fullmatch(element):
                        fail(path, "LOCAL_KEY_FORMAT", f"{element_path}: LocalKey", "invalid")
                    if "blank 금지" in bounds and not element.strip():
                        fail(path, "STRING_BLANK", f"{element_path}: non-blank", "blank")
            if "unique" in bounds and all(isinstance(element, (str, int)) and not isinstance(element, bool) for element in item):
                if len(item) != len(set(item)):
                    fail(path, "ARRAY_UNIQUENESS", f"{child_path}: unique", "duplicate")
        elif json_type == "task-discriminated object":
            if task_type not in TASK_SCHEMAS:
                fail(path, "TASK_DISCRIMINATOR", "known taskType", task_type)
            selected = TASK_SCHEMAS[task_type][0 if field == "input" else 1]
            validate_schema(path, item, selected, schemas, coverage, task_type, child_path)
        elif json_type == "JSON value":
            pass
        elif json_type in schemas:
            validate_schema(path, item, json_type, schemas, coverage, task_type, child_path)
        else:
            fail(path, "SCHEMA_JSON_TYPE", "supported documented type", json_type)


def collect_instantiated_schema_coverage(
    value: Any,
    root_schema: str,
    schemas: dict[str, dict[str, dict[str, str]]],
    task_type: str | None,
) -> set[str]:
    observed: set[str] = set()

    def visit(instance: Any, schema_name: str) -> None:
        if schema_name not in schemas or not isinstance(instance, dict):
            return
        observed.add(schema_name)
        for field, item in instance.items():
            if field not in schemas[schema_name] or item is None:
                continue
            json_type = schemas[schema_name][field]["type"]
            nested: str | None = None
            if json_type == "task-discriminated object" and task_type in TASK_SCHEMAS:
                nested = TASK_SCHEMAS[task_type][0 if field == "input" else 1]
                visit(item, nested)
            elif json_type.startswith("array<"):
                nested = json_type[6:-1]
                if nested in schemas and isinstance(item, list):
                    for element in item:
                        visit(element, nested)
            elif json_type in schemas:
                visit(item, json_type)

    visit(value, root_schema)
    return observed


def validate_raw_byte_limit(path: str, contract_object: str, raw_length: int) -> None:
    if raw_length <= MAX_JSON_BYTES:
        return
    if contract_object == "EXECUTION_REQUEST":
        fail(path, "REQUEST_BYTES_EXCEEDED", f"<= {MAX_JSON_BYTES} raw bytes", raw_length)
    fail(path, "RESPONSE_BYTES_EXCEEDED", f"<= {MAX_JSON_BYTES} raw bytes", raw_length)


def validate_text_contents(path: str, request: dict[str, Any]) -> None:
    contents = request.get("input", {}).get("textContents", [])
    if not 1 <= len(contents) <= 64:
        fail(path, "TEXT_CONTENT_COUNT", "1..64", len(contents))
    keys, total_chunks, total_chars = [], 0, 0
    for content in contents:
        keys.append(content.get("contentKey"))
        chunks = content.get("chunks", [])
        if not 1 <= len(chunks) <= 64:
            fail(path, "CHUNK_COUNT", "1..64", len(chunks))
        indexes = [chunk.get("index") for chunk in chunks]
        if indexes != list(range(len(chunks))):
            rule = "CHUNK_INDEX_DUPLICATE" if len(set(indexes)) != len(indexes) else "CHUNK_INDEX_GAP"
            fail(path, rule, list(range(len(chunks))), indexes)
        combined = ""
        count = 0
        for chunk in chunks:
            text = chunk.get("text", "")
            if not text or len(text) > 16_384:
                fail(path, "CHUNK_CHARACTERS", "1..16384", len(text))
            if chunk.get("characterCount") != len(text):
                fail(path, "CHARACTER_COUNT_MISMATCH", len(text), chunk.get("characterCount"))
            if chunk.get("chunkHash") != sha256_text(text):
                fail(path, "CHUNK_HASH_MISMATCH", sha256_text(text), chunk.get("chunkHash"))
            combined += text
            count += len(text)
        if content.get("totalCharacters") != count:
            fail(path, "TOTAL_CHARACTERS", count, content.get("totalCharacters"))
        if content.get("contentHash") != sha256_text(combined):
            fail(path, "CONTENT_HASH_MISMATCH", sha256_text(combined), content.get("contentHash"))
        total_chunks += len(chunks)
        total_chars += count
    if len(set(keys)) != len(keys):
        fail(path, "DUPLICATE_CONTENT_KEY", "unique", keys)
    if total_chunks > 64:
        fail(path, "CHUNK_AGGREGATE_LIMIT", "<=64", total_chunks)
    if total_chars > 500_000:
        fail(path, "TEXT_AGGREGATE_LIMIT", "<=500000", total_chars)


def validate_request(
    path: str, obj: dict[str, Any], schemas: dict[str, dict[str, dict[str, str]]], coverage: set[str],
) -> None:
    task_hint = obj.get("taskType") if isinstance(obj, dict) else None
    validate_schema(path, obj, "InternalExecutionRequestV1", schemas, coverage, task_hint)
    if set(obj) != REQUEST_FIELDS:
        fail(path, "REQUEST_FIELDS", sorted(REQUEST_FIELDS), sorted(obj))
    if obj.get("contractVersion") != "1.0" or obj.get("taskSchemaVersion") != "1.0" or obj.get("locale") != "ko-KR":
        fail(path, "REQUEST_VERSION", "1.0/1.0/ko-KR", "mismatch")
    task = obj.get("taskType")
    if task not in TASK_TYPES:
        fail(path, "TASK_TYPE", TASK_TYPES, task)
    input_fields = set(obj.get("input", {}))
    if task == "DETAILED_ANALYSIS":
        base_fields = {"conceptVersionKey", "shortlistDecisionKey", "analysisType", "sharedEvidence"}
        if not base_fields.issubset(input_fields) or not input_fields.issubset(TASK_INPUT_FIELDS[task]):
            fail(path, "TASK_INPUT_FIELDS", "Detailed base plus one named section", sorted(input_fields))
    elif input_fields != TASK_INPUT_FIELDS[task]:
        fail(path, "TASK_INPUT_FIELDS", sorted(TASK_INPUT_FIELDS[task]), sorted(input_fields))
    _, digest = canonical_hash(obj)
    if obj.get("canonicalInputHash") != digest:
        fail(path, "CANONICAL_HASH", digest, obj.get("canonicalInputHash"))
    if not HASH_RE.fullmatch(obj["canonicalInputHash"]):
        fail(path, "HASH_FORMAT", "sha256 + 64 lowercase hex", obj["canonicalInputHash"])
    deadline = validate_timestamp(path, obj["deadlineAt"], "DEADLINE_FORMAT")
    fixture_now = validate_timestamp(path, FIXTURE_NOW, "FIXTURE_CLOCK")
    if deadline <= fixture_now:
        fail(path, "DEADLINE_EXPIRED", f"> {FIXTURE_NOW}", obj["deadlineAt"])
    if "textContents" in obj["input"]:
        validate_text_contents(path, obj)
    if task == "IDEA_INTERPRETATION":
        content_keys = {item["contentKey"] for item in obj["input"]["textContents"]}
        reference_keys = {item["key"] for item in obj["input"]["sourceReferences"]}
        if content_keys != reference_keys:
            fail(path, "SOURCE_REFERENCE_RESOLUTION", content_keys, reference_keys)
    if task == "DETAILED_ANALYSIS":
        selected = {
            "MARKET": "marketInput", "BUSINESS_MODEL": "businessModelInput",
            "TECHNICAL_OPERATION": "technicalOperationInput", "FINANCIAL": "financialInput",
        }[obj["input"]["analysisType"]]
        present = [key for key in ("marketInput", "businessModelInput", "technicalOperationInput", "financialInput") if key in obj["input"]]
        if present != [selected] or obj["input"][selected] is None:
            fail(path, "DETAILED_SECTION", [selected], present)
    if task == "PERSONA_INTERVIEW":
        question_keys = [item["questionKey"] for item in obj["input"]["questions"]]
        if len(question_keys) != len(set(question_keys)):
            fail(path, "QUESTION_KEY_UNIQUENESS", "unique", question_keys)
    if task == "INTERVIEW_SYNTHESIS":
        interview_keys = [item["interviewKey"] for item in obj["input"]["includedInterviews"]]
        if len(interview_keys) != len(set(interview_keys)) or set(interview_keys) & set(obj["input"]["excludedInterviewKeys"]):
            fail(path, "INTERVIEW_REFERENCE_SET", "unique/disjoint", interview_keys)
    if task == "MARKETING_COMPARISON":
        asset_keys = [item["assetVersionKey"] for item in obj["input"]["assets"]]
        dimension_keys = [item["dimensionKey"] for item in obj["input"]["comparisonDimensions"]]
        if len(asset_keys) != len(set(asset_keys)) or len(dimension_keys) != len(set(dimension_keys)):
            fail(path, "MARKETING_KEY_UNIQUENESS", "unique", f"{asset_keys}/{dimension_keys}")
    if task == "FINAL_REPORT_GENERATION":
        if not obj["input"]["userDecisions"] or any(item["category"] != "USER_DECISION" for item in obj["input"]["userDecisions"]):
            fail(path, "FINAL_REPORT_USER_DECISIONS", "non-empty USER_DECISION items", "invalid")


def validate_usage(path: str, usage: Any) -> None:
    if usage is None:
        return
    if set(usage) != {"unit", "inputUnits", "outputUnits", "totalUnits", "estimated"}:
        fail(path, "USAGE_FIELDS", "exact UsageSummaryV1", sorted(usage))
    if usage["unit"] not in {"TOKENS", "CHARACTERS", "OTHER"} or usage["totalUnits"] != usage["inputUnits"] + usage["outputUnits"]:
        fail(path, "USAGE_TOTAL", "input+output", usage.get("totalUnits"))


def validate_success(
    path: str, obj: dict[str, Any], request: dict[str, Any],
    schemas: dict[str, dict[str, dict[str, str]]], coverage: set[str],
) -> None:
    task_hint = obj.get("taskType") if isinstance(obj, dict) else None
    validate_schema(path, obj, "InternalExecutionSuccessResponseV1", schemas, coverage, task_hint)
    if set(obj) != SUCCESS_FIELDS:
        fail(path, "SUCCESS_FIELDS", sorted(SUCCESS_FIELDS), sorted(obj))
    for field in ("contractVersion", "taskType", "taskSchemaVersion", "taskRunId", "taskAttemptId", "correlationId", "canonicalInputHash"):
        if obj.get(field) != request.get(field):
            fail(path, "SUCCESS_ECHO", request.get(field), obj.get(field))
    if obj.get("resultSchemaVersion") != "1.0":
        fail(path, "RESULT_SCHEMA_VERSION", "1.0", obj.get("resultSchemaVersion"))
    task = obj["taskType"]
    result_fields = set(obj.get("result", {}))
    if task == "DETAILED_ANALYSIS":
        base_fields = {"analysisType", "findings", "warnings", "provenance"}
        if not base_fields.issubset(result_fields) or not result_fields.issubset(TASK_RESULT_FIELDS[task]):
            fail(path, "TASK_RESULT_FIELDS", "Detailed base plus one named result", sorted(result_fields))
    elif result_fields != TASK_RESULT_FIELDS[task]:
        fail(path, "TASK_RESULT_FIELDS", sorted(TASK_RESULT_FIELDS[task]), sorted(result_fields))
    if not obj.get("provenance") or (task not in {"IDEA_INTERPRETATION", "IDEA_LEGAL_PRECHECK", "CONCEPT_LEGAL_VALIDATION"}
                                    and not obj["result"].get("provenance")):
        fail(path, "PROVENANCE_MIN", ">=1", 0)
    if task == "IDEA_INTERPRETATION":
        validate_idea_interpretation_result(path, obj["result"])
    validate_usage(path, obj.get("usage"))
    proposal_keys = [value for key, value in iter_items(obj["result"]) if key == "proposalKey"]
    if len(proposal_keys) != len(set(proposal_keys)):
        fail(path, "OUTPUT_KEY_UNIQUENESS", "unique", proposal_keys)
    if task == "DETAILED_ANALYSIS":
        analysis_type = obj["result"]["analysisType"]
        selected = {
            "MARKET": "marketResult", "BUSINESS_MODEL": "businessModelResult",
            "TECHNICAL_OPERATION": "technicalOperationResult", "FINANCIAL": "financialResult",
        }[analysis_type]
        present = [key for key in ("marketResult", "businessModelResult", "technicalOperationResult", "financialResult") if key in obj["result"]]
        if present != [selected]:
            fail(path, "DETAILED_RESULT_SECTION", [selected], present)
        if selected == "financialResult" and set(obj["result"][selected]) != {"inputSnapshotHash", "aiExplanation", "provenance"}:
            fail(path, "FINANCIAL_RESULT_FIELDS", "3 exact fields", sorted(obj["result"][selected]))
        if selected == "financialResult" and obj["result"][selected]["inputSnapshotHash"] != request["canonicalInputHash"]:
            fail(path, "FINANCIAL_SNAPSHOT_HASH_MISMATCH", request["canonicalInputHash"], obj["result"][selected]["inputSnapshotHash"])
    if task == "LEGAL_REVIEW":
        coverage = obj["result"]["sourceCoverage"]
        attempted, successful, missing = map(set, (coverage["attemptedChannels"], coverage["successfulChannels"], coverage["missingChannels"]))
        if coverage["degraded"] and not missing:
            fail(path, "LEGAL_DEGRADED_EMPTY_MISSING", "missingChannels non-empty", "empty")
        if successful | missing != attempted or successful & missing or coverage["degraded"] != bool(missing):
            fail(path, "LEGAL_SOURCE_COVERAGE", "partition and degraded iff missing", coverage)
        if any(source.get("authoritative") for source in obj["result"]["sourceReferences"]) and "MOLEG_API" not in successful:
            fail(path, "LEGAL_AUTHORITATIVE_WITHOUT_MOLEG", "MOLEG_API successful", successful)
        if not successful and obj["result"]["legalResult"] in {"PASS", "PASS_WITH_CONDITIONS"}:
            fail(path, "LEGAL_NO_SOURCE_PASS", "non-passing result", obj["result"]["legalResult"])
        for source in obj["result"]["sourceReferences"]:
            url = source.get("officialSourceUrl")
            if url and (not url.startswith("https://") or any(marker in url.lower() for marker in ("storage", "presigned", "s3"))):
                fail(path, "LEGAL_OFFICIAL_URL", "external HTTPS legal provenance", "invalid URL")
    if task == "CONCEPT_LEGAL_VALIDATION":
        text = "".join(chunk["text"] for content in request["input"]["textContents"] for chunk in content["chunks"])
        try:
            batch = json.loads(text)
            expected_keys = [item["candidateKey"] for item in batch["conceptDrafts"]]
        except (KeyError, TypeError, json.JSONDecodeError):
            fail(path, "CONCEPT_BATCH_INPUT", "canonical batch JSON", "invalid")
        actual_keys = [item["candidateKey"] for item in obj["result"]["validations"]]
        if (len(expected_keys) != len(set(expected_keys)) or len(actual_keys) != len(set(actual_keys))
                or set(actual_keys) != set(expected_keys)):
            fail(path, "CONCEPT_CANDIDATE_KEY_SET", expected_keys, actual_keys)
    if task == "PERSONA_INTERVIEW":
        if not obj["result"].get("syntheticDisclosure"):
            fail(path, "PERSONA_SYNTHETIC_DISCLOSURE", "non-empty", "missing")
        expected_questions = {item["questionKey"] for item in request["input"]["questions"]}
        actual_questions = {item["questionKey"] for item in obj["result"]["responses"]}
        if expected_questions != actual_questions:
            fail(path, "PERSONA_QUESTION_RESOLUTION", expected_questions, actual_questions)
    if task == "MARKETING_COMPARISON" and not obj["result"].get("overallCaveats"):
        fail(path, "MARKETING_CAVEAT", ">=1", 0)
    if task == "FINAL_REPORT_GENERATION" and obj["result"]["reportDecision"] != request["input"]["reportDecision"]:
        fail(path, "FINAL_REPORT_DECISION_MISMATCH", request["input"]["reportDecision"], obj["result"]["reportDecision"])


def validate_idea_interpretation_result(path: str, result: dict[str, Any]) -> None:
    origin_fields = {
        "productServiceDescription", "problem", "target", "solution", "coreValue",
        "primaryCategory", "targetRegion", "fixedValues", "confirmedValues", "assumptions",
        "pricingIntent", "revenueModelIntent", "salesChannelIntent", "knownUnitCost",
        "alternatives", "knownCompetitors", "differentiationIntent", "internalConstraints",
    }
    metadata_fields = {
        "key", "sourceType", "requiredForStages", "status", "locked", "fallbackPolicy",
    }
    question_fields = {"targetField", "requirement", "question", "reason"}
    origin = result.get("originDraft")
    if not isinstance(origin, dict) or set(origin) != origin_fields:
        fail(path, "IDEA_ORIGIN_DRAFT_FIELDS", sorted(origin_fields), sorted(origin or {}))
    if not isinstance(origin.get("confirmedValues"), dict):
        fail(path, "IDEA_CONFIRMED_VALUES", "object", type(origin.get("confirmedValues")).__name__)
    for field in ("problem", "solution", "coreValue", "fixedValues", "assumptions",
                  "alternatives", "knownCompetitors", "internalConstraints"):
        if not isinstance(origin.get(field), list):
            fail(path, "IDEA_ORIGIN_ARRAY", field, type(origin.get(field)).__name__)
    target = origin.get("target")
    if target is not None and (not isinstance(target, dict)
                               or set(target) != {"customerTypes", "segment", "situation", "needs"}):
        fail(path, "IDEA_TARGET_FIELDS", "exact target object or null", target)
    for metadata in result.get("fieldMetadata", []):
        if not isinstance(metadata, dict) or set(metadata) != metadata_fields:
            fail(path, "IDEA_FIELD_METADATA_FIELDS", sorted(metadata_fields), sorted(metadata or {}))
        if metadata["status"] == "MISSING" and (metadata["sourceType"] != "AI_PROPOSED" or metadata["locked"] is not False):
            fail(path, "IDEA_MISSING_METADATA", "AI_PROPOSED/false", "mismatch")
    for question in result.get("clarificationQuestions", []):
        if not isinstance(question, dict) or set(question) != question_fields:
            fail(path, "IDEA_QUESTION_FIELDS", sorted(question_fields), sorted(question or {}))
    questions = [item.get("question") for item in result.get("clarificationQuestions", [])]
    if result.get("openQuestions") != questions:
        fail(path, "IDEA_OPEN_QUESTION_ALIGNMENT", questions, result.get("openQuestions"))
    question_targets = {
        item.get("targetField") for item in result.get("clarificationQuestions", [])
    }
    required_values = {
        field: origin.get(field) for field in (
            "productServiceDescription", "problem", "target", "solution", "coreValue",
            "primaryCategory", "targetRegion", "fixedValues",
        )
    }
    missing_questions = sorted(
        field for field, value in required_values.items()
        if (value is None or value == "" or value == []) and field not in question_targets
    )
    if missing_questions:
        fail(path, "IDEA_REQUIRED_QUESTION", missing_questions, "missing")


def iter_items(value: Any):
    if isinstance(value, dict):
        for key, item in value.items():
            yield key, item
            yield from iter_items(item)
    elif isinstance(value, list):
        for item in value:
            yield from iter_items(item)


def parse_detail_names(cell: str) -> set[str]:
    if cell == "none":
        return set()
    return set(re.findall(r"`([A-Za-z]+)`", cell))


def validate_error(
    path: str, obj: dict[str, Any], entry: dict[str, Any],
    registry: dict[tuple[str, str], dict[str, str]],
    schemas: dict[str, dict[str, dict[str, str]]], coverage: set[str],
) -> None:
    validate_schema(path, obj, "InternalErrorResponseV1", schemas, coverage)
    if set(obj) != {"error"} or set(obj.get("error", {})) != ERROR_BODY_FIELDS:
        fail(path, "ERROR_FIELDS", "exact InternalErrorResponseV1", sorted(obj.get("error", {})))
    error = obj["error"]
    details = error.get("details", [])
    if len(details) != 1 or not set(details[0]).issubset(DETAIL_FIELDS):
        fail(path, "ERROR_DETAILS", "one safe detail", details)
    reason = details[0].get("reason")
    key = (error.get("code"), reason)
    if key not in registry:
        fail(path, "ERROR_REASON", "registered code/reason", key)
    rule = registry[key]
    if error.get("retryable") != (rule["retryable"] == "true"):
        fail(path, "ERROR_RETRYABLE", rule["retryable"], error.get("retryable"))
    required = parse_detail_names(rule["required"])
    optional = parse_detail_names(rule["optional"])
    fields = set(details[0]) - {"reason"}
    if not required.issubset(fields) or not fields.issubset(required | optional):
        fail(path, "ERROR_DETAIL_CONTRACT", f"required={required}, optional={optional}", fields)
    if reason in PREPARSE_REASONS and (error["taskRunId"] is not None or error["taskAttemptId"] is not None):
        fail(path, "PREPARSE_ID_ECHO", "null/null", f"{error['taskRunId']}/{error['taskAttemptId']}")
    if entry.get("expectedErrorCode") != error["code"] or entry.get("expectedReason") != reason:
        fail(path, "MANIFEST_ERROR_EXPECTATION", f"{error['code']}/{reason}", f"{entry.get('expectedErrorCode')}/{entry.get('expectedReason')}")


def values_for_key(value: Any, target: str) -> list[Any]:
    return [item for key, item in iter_items(value) if key == target]


def semantic_precheck(path: str, obj: Any, contract_object: str) -> None:
    if not isinstance(obj, dict):
        fail(path, "SCHEMA_TYPE", "object", type(obj).__name__)
    keys = set(walk_keys(obj))
    semantic_keys = {
        "purchaseProbability": "PERSONA_PURCHASE_PROBABILITY",
        "demographicOnly": "PERSONA_DEMOGRAPHIC_ONLY",
        "actualCustomerResearch": "PERSONA_REAL_CUSTOMER_CLAIM",
        "marketShare": "PERSONA_MARKET_SHARE",
        "populationStatistic": "PERSONA_POPULATION_STATISTIC",
        "conversionProbability": "MARKETING_CONVERSION_PROBABILITY",
        "winnerProbability": "MARKETING_WINNER_PROBABILITY",
        "statisticalAbClaim": "MARKETING_STATISTICAL_AB",
        "storageUrl": "MARKETING_STORAGE_REFERENCE",
    }
    for key, rule in semantic_keys.items():
        if key in keys:
            fail(path, rule, "forbidden field absent", key)
    task = obj.get("taskType")
    if task == "IDEA_INTERPRETATION" and contract_object == "EXECUTION_REQUEST":
        contents = obj.get("input", {}).get("textContents", [])
        if isinstance(contents, list) and len(contents) > 64:
            fail(path, "TEXT_CONTENT_COUNT_LIMIT", "<=64", len(contents))
        if isinstance(contents, list):
            total_chunks = sum(len(item.get("chunks", [])) for item in contents if isinstance(item, dict))
            if total_chunks > 64:
                fail(path, "CHUNK_AGGREGATE_LIMIT", "<=64", total_chunks)
    if "binary" in keys:
        rule = "FINAL_REPORT_BINARY_OUTPUT" if task == "FINAL_REPORT_GENERATION" else "MARKETING_BINARY_FIELD"
        fail(path, rule, "binary field absent", "binary")
    if "rawValue" in keys and contract_object == "INTERNAL_ERROR":
        fail(path, "ERROR_FORBIDDEN_DETAIL_PRESENT", "safe detail fields", "rawValue")
    if task == "PERSONA_INTERVIEW" and contract_object == "EXECUTION_REQUEST":
        if "otherPersonaCard" in keys:
            fail(path, "PERSONA_MULTIPLE_CARDS", "exactly one PersonaCardVersion", "otherPersonaCard")
        if "hiddenOtherInterview" in keys:
            fail(path, "PERSONA_HIDDEN_OTHER_INTERVIEW", "no other Persona context", "hiddenOtherInterview")
    if task == "PERSONA_INTERVIEW" and contract_object == "EXECUTION_SUCCESS":
        if "result" in obj and "syntheticDisclosure" not in obj["result"]:
            fail(path, "PERSONA_MISSING_SYNTHETIC_DISCLOSURE", "required", "missing")
    if task == "DETAILED_ANALYSIS":
        body_name = "input" if contract_object == "EXECUTION_REQUEST" else "result"
        body = obj.get(body_name, {})
        sections = (
            ("marketInput", "businessModelInput", "technicalOperationInput", "financialInput")
            if body_name == "input" else
            ("marketResult", "businessModelResult", "technicalOperationResult", "financialResult")
        )
        if any(name in body and body[name] is None for name in sections):
            fail(path, "DETAILED_NULL_SECTION", "selected section non-null; others omitted", "null")
        present = [name for name in sections if name in body]
        if len(present) > 1:
            fail(path, "DETAILED_MULTIPLE_INPUT_SECTIONS" if body_name == "input" else "DETAILED_RESULT_SECTION", "one section", present)
        selected = {
            "MARKET": sections[0], "BUSINESS_MODEL": sections[1],
            "TECHNICAL_OPERATION": sections[2], "FINANCIAL": sections[3],
        }.get(body.get("analysisType"))
        if selected and present != [selected]:
            fail(path, "DETAILED_RESULT_TYPE_MISMATCH" if body_name == "result" else "DETAILED_SECTION", [selected], present)
        financial = body.get("financialResult") if isinstance(body, dict) else None
        if isinstance(financial, dict):
            if "deterministicInputs" in financial:
                fail(path, "FINANCIAL_RESULT_DETERMINISTIC_INPUTS", "absent", "present")
            if any(name in financial for name in ("drivers", "risks", "caveats")):
                fail(path, "FINANCIAL_RESULT_OUTER_DRIVERS", "owned by aiExplanation", "outer field")
    if task == "FINAL_REPORT_GENERATION":
        if contract_object == "EXECUTION_REQUEST":
            decisions = obj.get("input", {}).get("userDecisions", [])
            if not decisions:
                fail(path, "FINAL_REPORT_MISSING_USER_DECISION", ">=1", 0)
            if any(item.get("category") != "USER_DECISION" for item in decisions):
                fail(path, "FINAL_REPORT_MIXED_PROVENANCE", "USER_DECISION only", "mixed")
    references = values_for_key(obj, "sourceReferences")
    for reference_array in references:
        if isinstance(reference_array, list):
            reference_keys = [item.get("key") for item in reference_array if isinstance(item, dict) and "key" in item]
            if len(reference_keys) != len(set(reference_keys)):
                fail(path, "REFERENCE_DUPLICATE_KEY", "unique request-local keys", reference_keys)
    if any(value == "unknown-input" for value in values_for_key(obj, "key") + values_for_key(obj, "reference")):
        fail(path, "REFERENCE_UNKNOWN_INPUT_KEY", "registered INPUT key", "unknown-input")
    resource_types = values_for_key(obj, "resourceType")
    if any(value not in RESOURCE_TYPES for value in resource_types):
        fail(path, "REFERENCE_WRONG_RESOURCE_TYPE", sorted(RESOURCE_TYPES), "unknown")
    if "observedByAdapter" in keys and any(value is False for value in values_for_key(obj, "observedByAdapter")):
        fail(path, "REFERENCE_INVENTED_LEGAL_SOURCE", "adapter-observed source", "invented")


def validate_fixture(
    path: str, obj: Any, entry: dict[str, Any],
    schemas: dict[str, dict[str, dict[str, str]]],
    reason_registry: dict[tuple[str, str], dict[str, str]], coverage: set[str],
    paired_request: dict[str, Any] | None = None,
) -> None:
    if path not in RAW_BYTE_LENGTHS:
        fail(path, "RAW_BYTE_LENGTH", "captured original bytes", "missing")
    validate_raw_byte_limit(path, entry["contractObject"], RAW_BYTE_LENGTHS[path])
    semantic_precheck(path, obj, entry["contractObject"])
    if entry["contractObject"] == "EXECUTION_REQUEST":
        validate_request(path, obj, schemas, coverage)
    elif entry["contractObject"] == "EXECUTION_SUCCESS":
        if paired_request is None:
            fail(path, "RESPONSE_PAIR", "matching positive request", "missing")
        validate_success(path, obj, paired_request, schemas, coverage)
    elif entry["contractObject"] == "INTERNAL_ERROR":
        validate_error(path, obj, entry, reason_registry, schemas, coverage)
    else:
        fail(path, "MANIFEST_CONTRACT_OBJECT", "known object", entry["contractObject"])


def main() -> int:
    try:
        internal = INTERNAL_DOC.read_text(encoding="utf-8")
        public = PUBLIC_DOC.read_text(encoding="utf-8")
        status = STATUS_DOC.read_text(encoding="utf-8")
        reason_registry, schema_specs, schemas, bound_spec_total = parse_registries(internal, public, status)
        validate_literal_bound_handlers(schema_specs)
        manifest = load_json_file(MANIFEST_PATH)
        if set(manifest) != {"manifestVersion", "fixtures"} or manifest["manifestVersion"] != "1.1":
            fail("manifest.json", "MANIFEST_ROOT", "manifestVersion/fixtures", sorted(manifest))
        entries = manifest["fixtures"]
        ids = [entry.get("fixtureId") for entry in entries]
        paths = [entry.get("path") for entry in entries]
        if len(ids) != len(set(ids)) or len(paths) != len(set(paths)):
            fail("manifest.json", "MANIFEST_DUPLICATE", "unique IDs/paths", "duplicate")
        for entry in entries:
            if set(entry) != MANIFEST_FIELDS:
                fail("manifest.json", "MANIFEST_ENTRY_FIELDS", sorted(MANIFEST_FIELDS), sorted(entry))
            if entry["category"] not in {"POSITIVE", "NEGATIVE"} or entry["contractObject"] not in {"EXECUTION_REQUEST", "EXECUTION_SUCCESS", "INTERNAL_ERROR"}:
                fail("manifest.json", "MANIFEST_ENUM", "known category/object", entry)
            if entry["expectedValid"] != (entry["category"] == "POSITIVE"):
                fail("manifest.json", "MANIFEST_VALIDITY", "POSITIVE=true; NEGATIVE=false", entry["fixtureId"])
            if not isinstance(entry["coveredSchemas"], list) or not entry["coveredSchemas"] or len(entry["coveredSchemas"]) != len(set(entry["coveredSchemas"])):
                fail("manifest.json", "MANIFEST_COVERED_SCHEMAS", "non-empty unique array", entry["fixtureId"])
            if not set(entry["coveredSchemas"]).issubset(schemas):
                fail("manifest.json", "MANIFEST_COVERED_SCHEMAS", "known schemas", sorted(set(entry["coveredSchemas"]) - schemas))
            if entry["category"] == "POSITIVE":
                if entry["expectedValidatorRule"] is not None or entry["primaryInvariant"] is not None:
                    fail("manifest.json", "MANIFEST_POSITIVE_RULE", "null/null", entry["fixtureId"])
            else:
                if not isinstance(entry["expectedValidatorRule"], str) or not entry["expectedValidatorRule"]:
                    fail("manifest.json", "MANIFEST_NEGATIVE_RULE", "stable rule", entry["fixtureId"])
                if not isinstance(entry["primaryInvariant"], str) or entry["invariants"] != [entry["primaryInvariant"]]:
                    fail("manifest.json", "MANIFEST_PRIMARY_INVARIANT", "one matching invariant", entry["fixtureId"])
            if entry["schemaName"] not in schemas and entry["schemaName"] != "CanonicalInputExpectationV1":
                fail("manifest.json", "MANIFEST_SCHEMA", "registered schema", entry["schemaName"])
            if not isinstance(entry["matchingContractSection"], str) or entry["matchingContractSection"] not in internal:
                fail("manifest.json", "MANIFEST_CONTRACT_SECTION", "existing section/registry name", entry["matchingContractSection"])
            if entry["expectedErrorCode"] is not None or entry["expectedReason"] is not None:
                if (entry["expectedErrorCode"], entry["expectedReason"]) not in reason_registry:
                    fail("manifest.json", "MANIFEST_ERROR_REASON", "registered code/reason", f"{entry['expectedErrorCode']}/{entry['expectedReason']}")
            relative = Path(entry["path"])
            if relative.is_absolute() or ".." in relative.parts or relative.as_posix() != entry["path"]:
                fail("manifest.json", "MANIFEST_PATH", "normalized relative path", entry["path"])
            resolved = (HERE / relative).resolve()
            try:
                resolved.relative_to(HERE.resolve())
            except ValueError:
                fail("manifest.json", "MANIFEST_PATH_ESCAPE", "inside fixture root", entry["path"])
            cursor = HERE
            for part in relative.parts:
                cursor /= part
                if cursor.is_symlink():
                    fail("manifest.json", "MANIFEST_SYMLINK_ESCAPE", "no symlink path", entry["path"])
        disk_json = {path.relative_to(HERE).as_posix() for path in HERE.rglob("*.json") if path != MANIFEST_PATH}
        if set(paths) != disk_json:
            fail("manifest.json", "MANIFEST_FILE_COVERAGE", sorted(disk_json), sorted(paths))

        objects: dict[str, Any] = {}
        for entry in entries:
            path = HERE / entry["path"]
            objects[entry["path"]] = load_json_file(path)
            if entry["expectedValid"] and FORBIDDEN_KEYS.intersection(walk_keys(objects[entry["path"]])):
                fail(entry["path"], "FORBIDDEN_FIELD", "none", sorted(FORBIDDEN_KEYS.intersection(walk_keys(objects[entry["path"]]))))

        canonical_request = objects["common/canonical-input.request.json"]
        canonical_expected = objects["common/canonical-input.expected.json"]
        canonical_text, digest = canonical_hash(canonical_request)
        if canonical_expected != {"canonicalJson": canonical_text, "canonicalInputHash": digest}:
            fail("common/canonical-input.expected.json", "CANONICAL_EXPECTATION", digest, canonical_expected.get("canonicalInputHash"))

        observed_by_path: dict[str, set[str]] = {}
        for entry in entries:
            if entry["schemaName"] == "CanonicalInputExpectationV1":
                observed = collect_instantiated_schema_coverage(
                    canonical_request, "InternalExecutionRequestV1", schema_specs, "IDEA_INTERPRETATION"
                )
            else:
                observed = collect_instantiated_schema_coverage(
                    objects[entry["path"]], entry["schemaName"], schema_specs, entry["taskType"]
                )
            observed_by_path[entry["path"]] = observed
            declared = set(entry["coveredSchemas"])
            if declared != observed:
                fail(entry["path"], "MANIFEST_SCHEMA_COVERAGE_MISMATCH", sorted(observed), sorted(declared))

        positive_requests: dict[str, dict[str, Any]] = {}
        actual_positive_coverage: set[str] = set()
        for entry in entries:
            obj = objects[entry["path"]]
            if not entry["expectedValid"] or entry["schemaName"] == "CanonicalInputExpectationV1":
                continue
            if entry["contractObject"] == "EXECUTION_REQUEST":
                fixture_coverage: set[str] = set()
                validate_fixture(entry["path"], obj, entry, schema_specs, reason_registry, fixture_coverage)
                actual_positive_coverage.update(fixture_coverage)
                if entry["taskType"]:
                    key = entry["path"].replace(".request.valid.json", "").replace(".request.", ".")
                    positive_requests[key] = obj
            elif entry["contractObject"] == "INTERNAL_ERROR":
                fixture_coverage = set()
                validate_fixture(entry["path"], obj, entry, schema_specs, reason_registry, fixture_coverage)
                actual_positive_coverage.update(fixture_coverage)

        for entry in entries:
            if not entry["expectedValid"] or entry["contractObject"] != "EXECUTION_SUCCESS":
                continue
            obj = objects[entry["path"]]
            key = entry["path"].replace(".response.valid.json", "").replace(".response.degraded-valid.json", "").replace(".response.", ".")
            request = positive_requests.get(key)
            if request is None:
                default_path = f"tasks/{entry['taskType'].lower().replace('_', '-')}.request.valid.json"
                request = objects.get(default_path)
            if request is None:
                fail(entry["path"], "RESPONSE_PAIR", "matching request", "missing")
            fixture_coverage = set()
            validate_fixture(entry["path"], obj, entry, schema_specs, reason_registry, fixture_coverage, request)
            actual_positive_coverage.update(fixture_coverage)

        negative_count = 0
        actual_negative_validation_coverage: set[str] = set()
        for entry in entries:
            if entry["expectedValid"]:
                continue
            obj = objects[entry["path"]]
            request = None
            if entry["contractObject"] == "EXECUTION_SUCCESS":
                task_slug = entry["taskType"].lower().replace("_", "-")
                if entry["taskType"] == "DETAILED_ANALYSIS":
                    analysis_type = obj.get("result", {}).get("analysisType", "FINANCIAL")
                    suffix = {
                        "MARKET": "-market", "BUSINESS_MODEL": "-business-model",
                        "TECHNICAL_OPERATION": "-technical-operation", "FINANCIAL": "",
                    }.get(analysis_type, "")
                    request = objects.get(f"tasks/detailed-analysis{suffix}.request.valid.json")
                else:
                    request = objects.get(f"tasks/{task_slug}.request.valid.json")
            try:
                fixture_coverage = set()
                validate_fixture(entry["path"], obj, entry, schema_specs, reason_registry, fixture_coverage, request)
            except ValidationFailure as exc:
                actual_negative_validation_coverage.update(fixture_coverage)
                if exc.rule != entry["expectedValidatorRule"]:
                    fail(entry["path"], "NEGATIVE_RULE_MISMATCH", entry["expectedValidatorRule"], exc.rule)
                negative_count += 1
            else:
                fail(entry["path"], "NEGATIVE_DID_NOT_FAIL", entry["expectedValidatorRule"], "PASS")

        task_request_coverage = {e["taskType"] for e in entries if e["expectedValid"] and e["contractObject"] == "EXECUTION_REQUEST" and e["taskType"] in TASK_TYPES}
        task_response_coverage = {e["taskType"] for e in entries if e["expectedValid"] and e["contractObject"] == "EXECUTION_SUCCESS" and e["taskType"] in TASK_TYPES}
        error_code_coverage = {e["expectedErrorCode"] for e in entries if e["expectedValid"] and e["contractObject"] == "INTERNAL_ERROR"}
        error_reason_coverage = {e["expectedReason"] for e in entries if e["expectedValid"] and e["contractObject"] == "INTERNAL_ERROR"}
        if task_request_coverage != set(TASK_TYPES) or task_response_coverage != set(TASK_TYPES):
            fail("manifest.json", "TASK_COVERAGE", "13/13", f"{len(task_request_coverage)}/{len(task_response_coverage)}")
        detailed_request_types = {
            objects[e["path"]]["input"]["analysisType"] for e in entries
            if e["expectedValid"] and e["taskType"] == "DETAILED_ANALYSIS" and e["contractObject"] == "EXECUTION_REQUEST"
        }
        detailed_response_types = {
            objects[e["path"]]["result"]["analysisType"] for e in entries
            if e["expectedValid"] and e["taskType"] == "DETAILED_ANALYSIS" and e["contractObject"] == "EXECUTION_SUCCESS"
        }
        if detailed_request_types != ANALYSIS_TYPES or detailed_response_types != ANALYSIS_TYPES:
            fail("manifest.json", "DETAILED_ANALYSIS_TYPE_COVERAGE", "4/4 request and response", f"{len(detailed_request_types)}/{len(detailed_response_types)}")
        if error_code_coverage != set(INTERNAL_ERRORS) or not error_reason_coverage.issubset({reason for _, reason in reason_registry}):
            fail("manifest.json", "ERROR_COVERAGE", "12/12 codes and registered reasons", f"{len(error_code_coverage)}/{len(error_reason_coverage)}")
        matrix = section(internal, "## 17. P2.6 fixture readiness matrix", "\0") if "\0" in internal else internal.split("## 17. P2.6 fixture readiness matrix", 1)[1]
        ready = set(re.findall(r"(?m)^\| ([A-Za-z][A-Za-z0-9]+V1) \| YES \| YES \|", matrix))
        if ready != schemas:
            fail(str(INTERNAL_DOC), "FIXTURE_READINESS", len(schemas), len(ready))
        positive_schema_coverage = {
            schema for entry in entries if entry["expectedValid"] for schema in observed_by_path[entry["path"]]
        }
        negative_schema_coverage = {
            schema for entry in entries if not entry["expectedValid"] for schema in observed_by_path[entry["path"]]
        }
        if positive_schema_coverage != schemas:
            fail("manifest.json", "SCHEMA_POSITIVE_COVERAGE", f"{len(schemas)}/{len(schemas)}", len(positive_schema_coverage))
        if actual_positive_coverage != schemas:
            fail("manifest.json", "SCHEMA_POSITIVE_EXECUTION_COVERAGE", f"{len(schemas)}/{len(schemas)}", f"{len(actual_positive_coverage)}/{len(schemas)} missing={sorted(schemas - actual_positive_coverage)}")

        loader_self_tests = {
            "DUPLICATE_JSON_KEY": b'{"a":1,"a":2}\n',
            "NORMALIZED_KEY_COLLISION": '{"é":1,"é":2}\n'.encode("utf-8"),
            "FLOAT_NUMBER_NOT_ALLOWED": b'{"value":1.5}\n',
            "LINE_ENDING_INVALID": b'{"value":1}\r\n',
        }
        for expected_rule, raw in loader_self_tests.items():
            try:
                decode_json(raw, f"<self-test:{expected_rule}>")
            except ValidationFailure as exc:
                if exc.rule != expected_rule:
                    fail("validator", "JSON_LOADER_SELF_TEST", expected_rule, exc.rule)
            else:
                fail("validator", "JSON_LOADER_SELF_TEST", expected_rule, "PASS")

        validate_raw_byte_limit("<raw-request-exact>", "EXECUTION_REQUEST", MAX_JSON_BYTES)
        validate_raw_byte_limit("<raw-response-exact>", "EXECUTION_SUCCESS", MAX_JSON_BYTES)
        for contract_object, expected_rule in (
            ("EXECUTION_REQUEST", "REQUEST_BYTES_EXCEEDED"),
            ("EXECUTION_SUCCESS", "RESPONSE_BYTES_EXCEEDED"),
        ):
            try:
                validate_raw_byte_limit("<raw-over-limit>", contract_object, MAX_JSON_BYTES + 1)
            except ValidationFailure as exc:
                if exc.rule != expected_rule:
                    fail("validator", "RAW_BYTE_LIMIT_SELF_TEST", expected_rule, exc.rule)
            else:
                fail("validator", "RAW_BYTE_LIMIT_SELF_TEST", expected_rule, "PASS")
        multibyte_raw = ("가" * ((MAX_JSON_BYTES // 3) + 1)).encode("utf-8")
        if len(multibyte_raw) <= MAX_JSON_BYTES:
            fail("validator", "RAW_BYTE_MULTIBYTE_SELF_TEST", f">{MAX_JSON_BYTES}", len(multibyte_raw))
        try:
            validate_raw_byte_limit("<raw-multibyte>", "EXECUTION_REQUEST", len(multibyte_raw))
        except ValidationFailure as exc:
            if exc.rule != "REQUEST_BYTES_EXCEEDED":
                fail("validator", "RAW_BYTE_MULTIBYTE_SELF_TEST", "REQUEST_BYTES_EXCEEDED", exc.rule)
        else:
            fail("validator", "RAW_BYTE_MULTIBYTE_SELF_TEST", "REQUEST_BYTES_EXCEEDED", "PASS")

        positives = sum(e["category"] == "POSITIVE" for e in entries)
        negatives = sum(e["category"] == "NEGATIVE" for e in entries)
        print("CONTRACT_REGISTRY=PASS")
        print(f"NAMED_SCHEMAS={len(schemas)}")
        print(f"TASK_TYPES={len(TASK_TYPES)}")
        print(f"INTERNAL_ERRORS={len(INTERNAL_ERRORS)}")
        print(f"INTERNAL_ERROR_REASONS={len(reason_registry)}")
        print(f"FIXTURES_TOTAL={len(entries)}")
        print(f"POSITIVE_FIXTURES={positives}")
        print(f"NEGATIVE_FIXTURES={negatives}")
        print("TASK_REQUEST_COVERAGE=13/13")
        print("TASK_RESPONSE_COVERAGE=13/13")
        print("DETAILED_ANALYSIS_TYPE_COVERAGE=4/4")
        print("ERROR_CODE_COVERAGE=12/12")
        print(f"ERROR_REASON_FIXTURE_COVERAGE={len(error_reason_coverage)}/{len(reason_registry)}")
        print(f"SCHEMA_POSITIVE_COVERAGE={len(schemas)}/{len(schemas)}")
        print(f"SCHEMA_NEGATIVE_COVERAGE={len(negative_schema_coverage)}/{len(schemas)}")
        print(f"SCHEMA_POSITIVE_INSTANCE_COVERAGE={len(schemas)}/{len(schemas)}")
        print(f"SCHEMA_NEGATIVE_INSTANCE_COVERAGE={len(negative_schema_coverage)}/{len(schemas)}")
        print(f"SCHEMA_NEGATIVE_EXECUTION_COVERAGE={len(actual_negative_validation_coverage)}/{len(schemas)}")
        print(f"MANIFEST_SCHEMA_COVERAGE_MATCH={len(entries)}/{len(entries)}")
        print(f"NEGATIVE_EXPECTED_RULES={negative_count}/{negative_count}")
        print(f"BOUND_SPEC_CLASSIFICATION={bound_spec_total}/{bound_spec_total}")
        print("UNSUPPORTED_BOUND_SPEC=0")
        print("STRING_LITERAL_VALIDATION=PASS")
        print("RAW_REQUEST_BYTE_LIMIT=PASS")
        print("RAW_RESPONSE_BYTE_LIMIT=PASS")
        print("PREPARSE_ID_RULES=PASS")
        print("DEADLINE_VALIDATION=PASS")
        print("JSON_DUPLICATE_KEY_SCAN=PASS")
        print("EXACT_ENUM_CONSISTENCY=PASS")
        print("LEGAL_RESULT_CONSISTENCY=PASS")
        print("ANALYSIS_TYPE_CONSISTENCY=PASS")
        print("REPORT_DECISION_CONSISTENCY=PASS")
        print("PROVENANCE_CONSISTENCY=PASS")
        print("MARKETING_ASSET_TYPE_CONSISTENCY=PASS")
        print("ERROR_MAPPING_CONSISTENCY=PASS")
        print("PUBLIC_INTERNAL_CONSISTENCY=PASS")
        print("FORBIDDEN_FIELD_SCAN=PASS")
        print("CANONICAL_HASH=PASS")
        print("CHUNK_INTEGRITY=PASS")
        print("RESULT=PASS")
        return 0
    except ValidationFailure as exc:
        print(f"fixture={exc.path}")
        print(f"rule={exc.rule}")
        print(f"expected={exc.expected}")
        print(f"actual={exc.actual}")
        print("RESULT=FAIL")
        return 1


if __name__ == "__main__":
    sys.exit(main())
