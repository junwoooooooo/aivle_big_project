from typing import Any, get_type_hints

import asyncio
import pytest

from app.providers import ProviderFailure
from app.tasks.concept_candidate.models import ConceptCandidateResult
from app.tasks.concept_legal_review.models import ConceptLegalReviewProviderResult
from app.tasks.concept_legal_review.service import execute_concept_legal_review
from app.tasks.concept_redesign.models import ConceptRedesignResult


MODELS = [ConceptCandidateResult, ConceptLegalReviewProviderResult, ConceptRedesignResult]


def _assert_closed(schema: dict, root: dict) -> None:
    if "$ref" in schema:
        target = root
        for part in schema["$ref"].removeprefix("#/").split("/"):
            target = target[part]
        _assert_closed(target, root)
        return
    if schema.get("type") == "object":
        assert schema.get("additionalProperties") is False
        assert schema.get("properties")
        for value in schema["properties"].values():
            _assert_closed(value, root)
    if schema.get("type") == "array":
        assert schema.get("items")
        _assert_closed(schema["items"], root)
    for branch in schema.get("anyOf", []):
        _assert_closed(branch, root)


def test_all_provider_result_schemas_are_closed_and_typed():
    for model in MODELS:
        schema = model.model_json_schema()
        _assert_closed(schema, schema)
        assert Any not in get_type_hints(model).values()


def test_legal_review_refuses_missing_official_evidence():
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(execute_concept_legal_review({"candidate": {}, "sharedContext": {}}))
    assert raised.value.retryable is False
