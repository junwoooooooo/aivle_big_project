from app.tasks.final_business_proposal.models import (
    FinalBusinessProposalInput, FinalBusinessProposalResult,
)
from app.tasks.final_business_proposal.review import ProposalReviewResult
from app.tasks.final_business_proposal.service import _close_evidence_vocabulary
from app.tasks.final_business_proposal.service import execute_final_business_proposal


def test_proposal_input_and_structured_output_schema():
    value = FinalBusinessProposalInput.model_validate({
        "contract": "final-business-proposal-input-v1", "projectId": 7, "version": 1,
        "sourceManifestHash": "sha256:" + "a" * 64,
        "sourceManifest": [{"type": "PROJECT", "id": "7", "metadata": {"status": "CURRENT"}},
                           {"type": "CURRENT_CONCEPT", "id": "concept-1"},
                           {"type": "MARKET", "id": "market-1"}],
        "includedSourceTypes": ["PROJECT", "CURRENT_CONCEPT", "MARKET"],
        "omittedSourceTypes": ["FINANCE"], "sources": {"PROJECT": {"name": "자전거 분석"}},
        "evidenceCatalog": [{"evidenceKey": "EV-" + "a" * 24, "sourceType": "PROJECT",
                             "sourceId": "7", "label": "프로젝트 · 사업명",
                             "summary": "자전거 분석", "sourcePath": "프로젝트 · name"}],
        "allowedEvidenceKeys": ["EV-" + "a" * 24],
    })
    schema = FinalBusinessProposalResult.model_json_schema()
    assert value.projectId == 7
    assert value.sourceManifest[0].metadata == {"status": "CURRENT"}
    assert set(schema["required"]) == {"contract", "cover", "executiveDecisionSummary", "sections",
                                       "decisionRequest", "appendix"}


def test_every_evidence_source_field_is_closed_to_manifest_types():
    schema = FinalBusinessProposalResult.model_json_schema()
    allowed_key = "EV-" + "b" * 24
    _close_evidence_vocabulary(schema, ["CURRENT_CONCEPT", "MARKET"], [allowed_key])

    enums = []
    key_enums = []
    def visit(node):
        if isinstance(node, dict):
            if "evidenceSourceTypes" in node.get("properties", {}):
                enums.append(node["properties"]["evidenceSourceTypes"]["items"]["enum"])
            if "evidenceKeys" in node.get("properties", {}):
                key_enums.append(node["properties"]["evidenceKeys"]["items"]["enum"])
            for value in node.values(): visit(value)
        elif isinstance(node, list):
            for value in node: visit(value)
    visit(schema)
    assert enums and all(value == ["CURRENT_CONCEPT", "MARKET"] for value in enums)
    assert key_enums and all(value == [allowed_key] for value in key_enums)


def test_review_contract_has_traceable_groups():
    schema = ProposalReviewResult.model_json_schema()
    assert {"wellPrepared", "needsImprovement", "requiredBeforeApproval", "followUpActions"}.issubset(
        schema["properties"],
    )


def test_invalid_proposal_input_reports_only_safe_schema_path():
    payload = {
        "contract": "final-business-proposal-input-v1", "projectId": 7, "version": 1,
        "sourceManifestHash": "sha256:" + "a" * 64,
        "sourceManifest": [{"type": "PROJECT", "id": "7", "unexpected": "secret"},
                           {"type": "CURRENT_CONCEPT", "id": "concept-1"},
                           {"type": "MARKET", "id": "market-1"}],
        "includedSourceTypes": ["PROJECT", "CURRENT_CONCEPT", "MARKET"],
        "omittedSourceTypes": [], "sources": {"PROJECT": {}},
        "evidenceCatalog": [{"evidenceKey": "EV-" + "a" * 24, "sourceType": "PROJECT",
                             "sourceId": "7", "label": "사업명", "summary": "자료",
                             "sourcePath": "프로젝트 · name"}],
        "allowedEvidenceKeys": ["EV-" + "a" * 24],
    }
    with pytest.raises(ProviderFailure) as failure:
        asyncio.run(execute_final_business_proposal(payload))
    assert failure.value.validation_fields == [{
        "path": "sourceManifest.0.unexpected", "category": "extra_forbidden",
        "expectedType": "no extra field",
    }]
    assert "secret" not in str(failure.value.validation_fields)
import asyncio

import pytest

from app.providers import ProviderFailure
