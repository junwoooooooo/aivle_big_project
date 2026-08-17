from app.tasks.final_business_proposal.models import (
    FinalBusinessProposalInput, FinalBusinessProposalResult,
)
from app.tasks.final_business_proposal.review import ProposalReviewResult
from app.tasks.final_business_proposal.service import _close_evidence_vocabulary


def test_proposal_input_and_structured_output_schema():
    value = FinalBusinessProposalInput.model_validate({
        "contract": "final-business-proposal-input-v1", "projectId": 7, "version": 1,
        "sourceManifestHash": "sha256:" + "a" * 64,
        "sourceManifest": [{"type": "PROJECT", "id": "7"},
                           {"type": "CURRENT_CONCEPT", "id": "concept-1"},
                           {"type": "MARKET", "id": "market-1"}],
        "includedSourceTypes": ["PROJECT", "CURRENT_CONCEPT", "MARKET"],
        "omittedSourceTypes": ["FINANCE"], "sources": {"PROJECT": {"name": "자전거 분석"}},
    })
    schema = FinalBusinessProposalResult.model_json_schema()
    assert value.projectId == 7
    assert set(schema["required"]) == {"contract", "cover", "executiveDecisionSummary", "sections",
                                       "decisionRequest", "appendix"}


def test_every_evidence_source_field_is_closed_to_manifest_types():
    schema = FinalBusinessProposalResult.model_json_schema()
    _close_evidence_vocabulary(schema, ["CURRENT_CONCEPT", "MARKET"])

    enums = []
    def visit(node):
        if isinstance(node, dict):
            if "evidenceSourceTypes" in node.get("properties", {}):
                enums.append(node["properties"]["evidenceSourceTypes"]["items"]["enum"])
            for value in node.values(): visit(value)
        elif isinstance(node, list):
            for value in node: visit(value)
    visit(schema)
    assert enums and all(value == ["CURRENT_CONCEPT", "MARKET"] for value in enums)


def test_review_contract_has_traceable_groups():
    schema = ProposalReviewResult.model_json_schema()
    assert {"wellPrepared", "needsImprovement", "requiredBeforeApproval", "followUpActions"}.issubset(
        schema["properties"],
    )
