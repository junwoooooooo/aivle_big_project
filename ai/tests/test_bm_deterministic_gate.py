from types import SimpleNamespace

from app.research import serialize
from app.validation import gate


def _cells():
    return [{"canvasCell": name, "status": "PLAN" if name in gate.PLANNED_CELLS else "UNVERIFIED",
             "sourceLabels": ["concept_snapshot"], "marketEvidenceIds": []}
            for name in (*gate.OBSERVED_CELLS, *gate.PLANNED_CELLS)]


def test_gate_causes_keep_uncollected_uncited_and_unmapped_distinct():
    cells = _cells()
    score = [{"subject": "MARKET_SIZE", "state": "MISSING"},
             {"subject": "DEMAND", "state": "FILLED"}]
    channel = next(item for item in cells if item["canvasCell"] == "CHANNELS")
    channel["marketEvidenceIds"] = ["C-SEC-channel"]

    reasons = {item["cell"]: item for item in gate.evaluate(cells, score) if item["code"] == "G1"}

    assert reasons["CUSTOMER_SEGMENTS"]["cause"] == "UNCOLLECTED"
    assert reasons["VALUE_PROPOSITIONS"]["cause"] == "UNCITED"
    assert reasons["CHANNELS"]["cause"] == "UNCITED"
    assert reasons["REVENUE_STREAMS"]["cause"] == "UNMAPPED"
    assert gate.apply_decision("PASS", list(reasons.values())) == "REVISION_REQUIRED"


def test_bm_contract_keeps_gate_reasons_and_financial_handoff_together():
    final = SimpleNamespace(decision=SimpleNamespace(value="PASS"), confidence="MEDIUM",
                            summary="s", market_fit_summary="m", consistency_summary="c",
                            strengths=[], weaknesses=[], risks=[], legal_context_used=False,
                            legal_status="UNVERIFIED", legal_summary="", legal_risks=[],
                            required_legal_actions=[])
    analysis = SimpleNamespace(market_fit_status="PASS", consistency_status="PASS")
    handoff = SimpleNamespace(concept_id="c", revenue_model="구독", price_min=None,
                              price_base=10, price_max=None, tam=None, sam=None, som=None,
                              market_growth_rate=None, expected_revenue=None, unit_cost=None,
                              fixed_cost_items=[], variable_cost_items=[],
                              missing_financial_inputs=["unit_cost"], handoff_status="PARTIAL")
    reason = {"code": "G1", "cell": "CHANNELS", "message": "missing",
              "evidenceIds": [], "cause": "UNMAPPED"}

    result = serialize.bm(final, analysis, handoff, decision="REVISION_REQUIRED",
                          gate_reasons=[reason])

    assert result["decision"] == "REVISION_REQUIRED"
    assert result["gateReasons"] == [reason]
    assert result["financialHandoff"]["handoffStatus"] == "PARTIAL"
