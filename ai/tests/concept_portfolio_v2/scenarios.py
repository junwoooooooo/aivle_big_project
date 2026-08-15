"""Production-path parity 검증용 입력 catalog. Core 정책은 포함하지 않는다."""

import json
from pathlib import Path


SCENARIO_FILE = (Path(__file__).resolve().parents[2] / "fixtures" /
                 "concept_portfolio_v2" / "live_scenarios.json")
SCENARIOS = json.loads(SCENARIO_FILE.read_text(encoding="utf-8"))
SCENARIO_BY_ID = {item["scenarioId"]: item for item in SCENARIOS}


def scenario_payload(scenario_id: str) -> dict:
    item = SCENARIO_BY_ID[scenario_id]
    return {key: item[key] for key in ("ideaOverview", "problem", "targetUsers")}
