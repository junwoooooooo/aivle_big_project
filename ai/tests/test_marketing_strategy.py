from app.tasks.marketing_strategy import execute_marketing_strategy
from app.tasks.marketing_strategy.models import MarketingStrategyInput, MarketingStrategyResult


def test_marketing_strategy_contract_import_and_schema():
    assert callable(execute_marketing_strategy)
    value = MarketingStrategyInput.model_validate({
        "contract": "marketing-strategy-input-v1",
        "projectId": 7,
        "sourceManifestHash": "sha256:" + "a" * 64,
        "sourceManifest": [{"type": "CURRENT_CONCEPT", "id": "concept-1"}],
        "sources": {"CURRENT_CONCEPT": {"conceptName": "자전거 운영 분석"}},
    })
    schema = MarketingStrategyResult.model_json_schema()
    assert value.projectId == 7
    assert "campaignRoadmap" in schema["properties"]
    assert "budgetGuidelines" in schema["properties"]
    assert "evidenceRefs" in schema["properties"]
