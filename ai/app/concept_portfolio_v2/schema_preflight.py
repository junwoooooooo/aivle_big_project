"""V2 Provider strict structured-output schema의 로컬 호환성 검사."""

from __future__ import annotations

from typing import Any

from app.tasks.concept_candidate.models import ConceptCandidateDraft

from .models import (
    BusinessRoleSemanticBatch,
    LegalFactCompletionPatch, LegalFactDependencySemanticBatch,
    PlanDraftPool, SchemaCompatibilityItem, SchemaPreflightReport,
    SemanticArchitectureBatch, SemanticDistinctnessResult, SemanticFidelityResult,
    SemanticHypothesisBatch,
)


class StructuredOutputSchemaCompatibilityError(ValueError):
    def __init__(self, schema_name: str, failures: list[dict[str, str]]):
        first = failures[0] if failures else {"path": "$", "reason": "UNKNOWN"}
        super().__init__(f"SCHEMA_PREFLIGHT_FAILED:{schema_name}:{first['path']}:{first['reason']}")
        self.schema_name = schema_name
        self.failures = failures


ALLOWED_KEYWORDS = {
    "$defs", "$ref", "title", "description", "type", "properties", "required",
    "additionalProperties", "items", "minItems", "maxItems", "minLength", "maxLength",
    "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "enum", "const",
    "anyOf", "oneOf", "default", "format", "pattern",
}


def inspect_strict_schema(schema: dict[str, Any], schema_name: str) -> SchemaCompatibilityItem:
    failures: list[dict[str, str]] = []
    definitions = schema.get("$defs", {})

    def fail(path: str, reason: str):
        failures.append({"path": path, "reason": reason})

    def visit(node: Any, path: str, depth: int):
        if depth > 14:
            fail(path, "SCHEMA_DEPTH_EXCEEDED")
            return
        if not isinstance(node, dict):
            fail(path, "SCHEMA_NODE_NOT_OBJECT")
            return
        unsupported = sorted(set(node) - ALLOWED_KEYWORDS)
        for keyword in unsupported:
            fail(f"{path}.{keyword}", "UNSUPPORTED_SCHEMA_KEYWORD")
        if "$ref" in node:
            ref = node["$ref"]
            prefix = "#/$defs/"
            if not isinstance(ref, str) or not ref.startswith(prefix) or ref[len(prefix):] not in definitions:
                fail(path, "UNRESOLVED_SCHEMA_REFERENCE")
            return
        variants = node.get("anyOf") or node.get("oneOf")
        if variants is not None:
            if not isinstance(variants, list) or not variants:
                fail(path, "INVALID_UNION")
            else:
                for index, variant in enumerate(variants):
                    visit(variant, f"{path}.union[{index}]", depth + 1)
            return
        node_type = node.get("type")
        if node_type == "object" or "properties" in node:
            additional = node.get("additionalProperties")
            if isinstance(additional, dict) or additional is True:
                fail(path, "DYNAMIC_OBJECT_NOT_STRICT_COMPATIBLE")
            properties = node.get("properties")
            if not isinstance(properties, dict):
                fail(path, "OBJECT_PROPERTIES_REQUIRED")
                return
            if not isinstance(additional, dict) and additional is not True and additional is not False:
                fail(path, "ADDITIONAL_PROPERTIES_MUST_BE_FALSE")
            required = node.get("required")
            if not isinstance(required, list) or set(required) != set(properties):
                fail(path, "REQUIRED_PROPERTIES_MISMATCH")
            for key, child in properties.items():
                visit(child, f"{path}.properties.{key}", depth + 1)
        elif node_type == "array":
            if "items" not in node:
                fail(path, "ARRAY_ITEMS_REQUIRED")
            else:
                visit(node["items"], f"{path}.items", depth + 1)
        elif node_type not in {"string", "integer", "number", "boolean", "null"} and "enum" not in node:
            fail(path, "UNSUPPORTED_OR_MISSING_TYPE")

    if schema.get("type") != "object":
        fail("$", "SCHEMA_ROOT_MUST_BE_OBJECT")
    visit(schema, "$", 0)
    for name, definition in definitions.items():
        visit(definition, f"$defs.{name}", 1)
    unique = list({(item["path"], item["reason"]): item for item in failures}.values())
    return SchemaCompatibilityItem(schemaName=schema_name, status="PASS" if not unique else "FAIL",
                                   failures=unique)


def assert_strict_compatible(schema: dict[str, Any], schema_name: str) -> None:
    result = inspect_strict_schema(schema, schema_name)
    if result.status != "PASS":
        raise StructuredOutputSchemaCompatibilityError(schema_name, result.failures)


def v2_schema_preflight_report() -> SchemaPreflightReport:
    schemas = [
        ("PlanDraftPool", PlanDraftPool.model_json_schema()),
        ("ConceptCandidateDraft", ConceptCandidateDraft.model_json_schema()),
        ("SemanticDistinctnessResult", SemanticDistinctnessResult.model_json_schema()),
        ("SemanticFidelityResult", SemanticFidelityResult.model_json_schema()),
        ("SemanticArchitectureBatch", SemanticArchitectureBatch.model_json_schema()),
        ("SemanticHypothesisBatch", SemanticHypothesisBatch.model_json_schema()),
        ("BusinessRoleSemanticBatch", BusinessRoleSemanticBatch.model_json_schema()),
        ("LegalFactDependencySemanticBatch", LegalFactDependencySemanticBatch.model_json_schema()),
        ("LegalFactCompletionPatch", LegalFactCompletionPatch.model_json_schema()),
    ]
    results = [inspect_strict_schema(schema, name) for name, schema in schemas]
    return SchemaPreflightReport(status="PASS" if all(item.status == "PASS" for item in results) else "FAIL",
                                 schemas=results, providerCalls=0)
