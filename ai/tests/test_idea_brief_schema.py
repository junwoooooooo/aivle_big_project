from typing import Any, get_args, get_origin, get_type_hints

from app.tasks.idea_brief.mapper import to_domain
from app.tasks.idea_brief.models import IdeaBriefProviderResult


def test_provider_schema_is_closed_and_fully_typed():
    schema = IdeaBriefProviderResult.model_json_schema()
    _assert_closed_schema(schema, schema)
    for model in _model_types(IdeaBriefProviderResult):
        assert Any not in get_type_hints(model).values()


def test_provider_to_domain_mapping_is_deterministic():
    provider = IdeaBriefProviderResult.model_validate({
        "extractedFields": [{"fieldKey": "problem", "value": "폐기", "decisionState": "OPEN", "sourceReference": "overview"}],
        "fieldSuggestions": [{"fieldKey": "targetCustomers", "value": "식당", "decisionState": "PREFERRED", "rationale": "입력 기반"}],
        "clarificationQuestions": [],
        "contradictions": [],
        "readiness": {"status": "READY_FOR_REVIEW", "score": 90, "missingFieldKeys": []},
        "userFacingSummary": "검토할 수 있습니다.",
    })
    first = to_domain(provider).model_dump(mode="json")
    second = to_domain(provider).model_dump(mode="json")
    assert first == second
    assert [field["provenance"] for field in first["fields"]] == ["SOURCE_EXTRACTED", "AI_PROPOSED"]


def _assert_closed_schema(node: dict, root: dict) -> None:
    if "$ref" in node:
        target = root
        for part in node["$ref"].removeprefix("#/").split("/"):
            target = target[part]
        _assert_closed_schema(target, root)
        return
    if node.get("type") == "object":
        assert node.get("additionalProperties") is False
        assert node.get("properties")
        for value in node["properties"].values():
            _assert_closed_schema(value, root)
    if node.get("type") == "array":
        assert node.get("items")
        _assert_closed_schema(node["items"], root)
    for choice in node.get("anyOf", []) + node.get("oneOf", []):
        _assert_closed_schema(choice, root)


def _model_types(root):
    found = {root}
    pending = [root]
    while pending:
        model = pending.pop()
        for hint in get_type_hints(model).values():
            for value in get_args(hint) or (hint,):
                origin = get_origin(value)
                candidate = get_args(value)[0] if origin is list else value
                if isinstance(candidate, type) and hasattr(candidate, "model_fields") and candidate not in found:
                    found.add(candidate)
                    pending.append(candidate)
    return found
