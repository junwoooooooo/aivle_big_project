import asyncio
import json

import pytest

from app.legal import concept_validation
from app.models.journey import ConceptGenerationResult
from app.services.journey_provider import ProviderFailure


def candidate(name: str = "Companion Safety Care") -> dict:
    return {
        "conceptName": name,
        "targetSegment": {"segment": "first-time companion animal owners"},
        "positioning": "sensor-assisted care guidance",
        "featureSet": ["condition alerts"],
        "pricing": {"amount": 39000, "currency": "KRW"},
        "revenueModel": {"type": "DEVICE_SALE"},
        "channels": ["direct store"],
        "operatingModel": {"seller": "operator", "dataHandling": "user consent"},
        "newAssumptions": [],
        "newBusinessActivities": ["sensor data processing"],
        "originTrace": [
            {
                "structureKey": "problem",
                "sourceValue": ["difficulty detecting hydration"],
                "conceptValue": ["difficulty detecting hydration"],
            }
        ],
        "legalTrace": [
            {
                "guardrailType": "requiredDisclosures",
                "constraint": "disclose processing policy",
                "implementation": "show policy before activation",
            }
        ],
    }


def test_concept_generation_contract_accepts_eligibility_fields():
    value = ConceptGenerationResult.model_validate(
        {"concepts": [candidate("A"), candidate("B"), candidate("C")]}
    )
    assert len(value.concepts) == 3
    assert value.concepts[0].originTrace[0].structureKey == "problem"


def test_concept_legal_validation_contract(monkeypatch):
    async def fake_prompt(system, user, **kwargs):
        payload = json.loads(user)
        assert payload["input"]["guardrails"]["hardConstraints"] == ["consent required"]
        return {
            "status": "FAIL_LEGAL",
            "reasons": ["consent flow is missing"],
            "violatedStructureKeys": ["operatingModel.dataHandling"],
            "legalTrace": [],
        }

    monkeypatch.setattr(concept_validation, "execute_structured_prompt", fake_prompt)
    result = asyncio.run(
        concept_validation.execute_concept_legal_validation(
            {"validationMode": "GUARDRAIL"},
            json.dumps({"guardrails": {"hardConstraints": ["consent required"]}}),
        )
    )
    assert result["status"] == "FAIL_LEGAL"


def batch_input() -> dict:
    first = candidate("A")
    first["candidateKey"] = "candidate-1"
    second = candidate("B")
    second["candidateKey"] = "candidate-2"
    return {
        "guardrails": {
            "hardConstraints": ["consent required"],
            "prohibitedPatterns": [],
            "conditionalConstraints": [],
            "requiredDisclosures": [],
            "requiredOperationalControls": [],
        },
        "lockedValues": {"targetRegion": {"value": "KR"}},
        "conceptDrafts": [first, second],
    }


def test_concept_legal_validation_batch_contract(monkeypatch):
    async def fake_prompt(system, user, **kwargs):
        payload = json.loads(user)
        assert [item["candidateKey"] for item in payload["input"]["conceptDrafts"]] == [
            "candidate-1", "candidate-2"
        ]
        return {
            "validations": [
                {
                    "candidateKey": "candidate-1",
                    "status": "PASS",
                    "reasons": [],
                    "violatedStructureKeys": [],
                    "legalTrace": [{
                        "guardrailType": "hardConstraints",
                        "constraint": "consent required",
                        "implementation": "consent collected before processing",
                    }],
                },
                {
                    "candidateKey": "candidate-2",
                    "status": "FAIL_LEGAL",
                    "reasons": ["consent flow is missing"],
                    "violatedStructureKeys": ["operatingModel.dataHandling"],
                    "legalTrace": [],
                },
            ]
        }

    monkeypatch.setattr(concept_validation, "execute_structured_prompt", fake_prompt)
    result = asyncio.run(concept_validation.execute_concept_legal_validation_batch(
        {"validationMode": "GUARDRAIL_BATCH"}, json.dumps(batch_input())
    ))
    assert [item["candidateKey"] for item in result["validations"]] == [
        "candidate-1", "candidate-2"
    ]


@pytest.mark.parametrize("keys", [
    ["candidate-1"],
    ["candidate-1", "candidate-1"],
    ["candidate-1", "unknown-candidate"],
])
def test_concept_legal_validation_batch_rejects_candidate_key_mismatch(monkeypatch, keys):
    async def fake_prompt(system, user, **kwargs):
        return {
            "validations": [{
                "candidateKey": key,
                "status": "FAIL_LEGAL",
                "reasons": ["not compliant"],
                "violatedStructureKeys": ["operatingModel"],
                "legalTrace": [],
            } for key in keys]
        }

    monkeypatch.setattr(concept_validation, "execute_structured_prompt", fake_prompt)
    with pytest.raises(ProviderFailure) as failure:
        asyncio.run(concept_validation.execute_concept_legal_validation_batch(
            {"validationMode": "GUARDRAIL_BATCH"}, json.dumps(batch_input())
        ))
    assert failure.value.code == "RESULT_SCHEMA_INVALID"


def test_concept_legal_validation_batch_rejects_extra_output_field(monkeypatch):
    async def fake_prompt(system, user, **kwargs):
        return {
            "validations": [{
                "candidateKey": key,
                "status": "PASS",
                "reasons": [],
                "violatedStructureKeys": [],
                "legalTrace": [],
                "score": 100,
            } for key in ["candidate-1", "candidate-2"]]
        }

    monkeypatch.setattr(concept_validation, "execute_structured_prompt", fake_prompt)
    with pytest.raises(ProviderFailure) as failure:
        asyncio.run(concept_validation.execute_concept_legal_validation_batch(
            {"validationMode": "GUARDRAIL_BATCH"}, json.dumps(batch_input())
        ))
    assert failure.value.code == "RESULT_SCHEMA_INVALID"
