import json
from pathlib import Path

from app.canonical_json import canonical_input_hash, canonical_json


FIXTURE = (Path(__file__).resolve().parents[2]
           / "backend/src/test/resources/canonical/numeric-canonical-fixture-v1.json")


def test_numeric_canonical_fixture_matches_java_expected_hash():
    fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))
    result = canonical_input_hash(
        contract_version="1.0",
        task_type=fixture["taskType"],
        task_schema_version=fixture["taskSchemaVersion"],
        locale=fixture["locale"],
        input_value=json.loads(fixture["inputJson"]),
    )
    assert result == fixture["expectedHash"]


def test_number_policy_removes_exponent_trailing_zero_and_negative_zero():
    values = json.loads("[0,-0,1,1.0,1.00,0.1,3.5,100000000.0,-12.3400,1e3,1E-3,-0.0]")
    assert canonical_json(values) == "[0,0,1,1,1,0.1,3.5,100000000,-12.34,1000,0.001,0]"


def test_nested_redesign_candidate_numbers_are_canonical():
    fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))
    candidate = json.loads(fixture["inputJson"])["candidate"]
    encoded = canonical_json(candidate)
    assert '"targetSharePercent":3.5' in encoded
    assert '"amount":100000000' in encoded
