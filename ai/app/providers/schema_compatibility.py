"""Offline preflight for provider strict JSON schemas.

The provider accepts only closed, fully-required object schemas.  This module
contains no provider or task imports so every structured task can share the
same validation before an HTTP request is attempted.
"""

from __future__ import annotations

from typing import Any


ALLOWED_KEYWORDS = {
    "$defs", "$ref", "title", "description", "type", "properties", "required",
    "additionalProperties", "items", "minItems", "maxItems", "minLength",
    "maxLength", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum",
    "enum", "const", "anyOf", "format", "pattern",
}


def strict_schema_failures(schema: dict[str, Any]) -> list[dict[str, str]]:
    failures: list[dict[str, str]] = []
    definitions = schema.get("$defs", {})

    def fail(path: str, reason: str) -> None:
        failures.append({"path": path, "reason": reason})

    def visit(node: Any, path: str, depth: int) -> None:
        if depth > 10:
            fail(path, "SCHEMA_DEPTH_EXCEEDED")
            return
        if not isinstance(node, dict):
            fail(path, "SCHEMA_NODE_NOT_OBJECT")
            return
        for keyword in sorted(set(node) - ALLOWED_KEYWORDS):
            fail(f"{path}.{keyword}", "UNSUPPORTED_SCHEMA_KEYWORD")
        if "$ref" in node:
            ref = node["$ref"]
            prefix = "#/$defs/"
            if not isinstance(ref, str) or not ref.startswith(prefix) \
                    or ref[len(prefix):] not in definitions:
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
            properties = node.get("properties")
            if not isinstance(properties, dict):
                fail(path, "OBJECT_PROPERTIES_REQUIRED")
                return
            if node.get("additionalProperties") is not False:
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
        elif node_type not in {"string", "integer", "number", "boolean", "null"} \
                and "enum" not in node:
            fail(path, "UNSUPPORTED_OR_MISSING_TYPE")

    if schema.get("type") != "object":
        fail("$", "SCHEMA_ROOT_MUST_BE_OBJECT")
    visit(schema, "$", 0)
    for name, definition in definitions.items():
        visit(definition, f"$defs.{name}", 1)
    return list({(item["path"], item["reason"]): item for item in failures}.values())
