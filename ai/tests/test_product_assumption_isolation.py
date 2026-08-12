import json
import sys
from pathlib import Path

from app.research import pipeline

BLOCKS = str(Path(pipeline.RESEARCH_HOME) / "blocks")
if BLOCKS not in sys.path:
    sys.path.insert(0, BLOCKS)

import b_estimate
import runlog
import verdict
from schema import Coverage, Formula, FormulaVar, Ledger, Slot


DONOR_TOKENS = (
    "두발 미용업",
    "소상공인 SaaS",
    "서울 내 직접 영업",
    "초기 B2B SaaS",
)


def _arbitrary_concept(tmp_path: Path) -> Path:
    path = tmp_path / "food-recipe-concept.json"
    path.write_text(json.dumps({
        "concept_id": "food-recipe-community",
        "name": "재료 기반 요리법 커뮤니티 플랫폼",
        "target": "남는 재료를 활용하려는 가정 사용자",
        "problem": "보유 재료에 맞는 요리법을 찾기 어렵다",
        "solution": "재료를 기준으로 요리법을 공유하고 탐색한다",
        "_hypotheses_v2": {},
    }, ensure_ascii=False), encoding="utf-8")
    return path


def test_fixture_and_product_assumption_authorities_are_separate(monkeypatch):
    monkeypatch.delenv("RESEARCH2_ASSUMPTION_PROFILE", raising=False)
    fixture = runlog.load_rules()["assumptions"]
    assert fixture["by_role"]["침투율"]["value"] == 0.1
    assert "소상공인 SaaS" in fixture["by_role"]["침투율"]["basis"]

    monkeypatch.setenv("RESEARCH2_ASSUMPTION_PROFILE", "product")
    product = runlog.load_rules()["assumptions"]
    assert product["version"].startswith("product-v1")
    assert product["by_role"] == {}
    assert set(product["var_roles"]["allowed"]) == set(fixture["var_roles"]["allowed"])


def test_product_b_estimate_stops_without_a_numeric_assumption(monkeypatch):
    monkeypatch.setenv("RESEARCH2_ASSUMPTION_PROFILE", "product")
    rules = runlog.load_rules()
    formula = Formula(
        formula_id="F_PRODUCT",
        target="TAM",
        path="bottomup",
        template="T2",
        vars=[FormulaVar("V1", "침투율", "요리법 커뮤니티", "침투율", "2026", "비율")],
    )
    slot = Slot(
        slot_id="S1", var_id="V1", formula_id="F_PRODUCT", claim_type="TAM",
        subject="요리법 커뮤니티", metric="침투율", period="2026", unit="비율",
    )
    coverage = Coverage("S1", "공백", 0, 0, [], min_facts=2)

    inputs = b_estimate.substitute(
        formula, Ledger(), {"S1": coverage}, {"S1": slot},
        rules["assumptions"]["by_role"], rules,
    )

    assert inputs[0].assumption is None
    assert inputs[0].from_fact is None
    assert inputs[0].basis == "가정값 없음 — 계산 불가"


def test_arbitrary_product_verdict_has_no_donor_assumption_or_fabricated_som(
        monkeypatch, tmp_path):
    monkeypatch.setenv("RESEARCH2_ASSUMPTION_PROFILE", "product")
    # 원장은 donor fixture를 일부러 사용한다. Product authority가 계산 가정을 격리하지
    # 못하면 이보다 강한 오염 재현 조건은 없다.
    result = verdict.build("beauty-13", str(_arbitrary_concept(tmp_path)))
    rendered = json.dumps(result, ensure_ascii=False)

    for token in DONOR_TOKENS:
        assert token not in rendered
    som = result["판정"]["9_SOM_초기점유"]
    assert som["도장"] == "판정_불가"
    assert som["추정"] is None


def test_product_profile_reaches_product_runner_boundary(monkeypatch, tmp_path):
    from app.research import product_runner

    monkeypatch.setenv("RESEARCH2_ASSUMPTION_PROFILE", "fixture")
    monkeypatch.setenv("RESEARCH2_RUNS_DIR", str(tmp_path / "prior-runs"))
    input_path = tmp_path / "input.json"
    output_path = tmp_path / "output.json"
    workspace = tmp_path / "workspace"
    workspace.mkdir()
    input_path.write_text(_arbitrary_concept(tmp_path).read_text(encoding="utf-8"),
                          encoding="utf-8")
    seen = {}

    def fake_subprocess(_command, **_kwargs):
        seen["subprocess_profile"] = __import__("os").environ.get(
            "RESEARCH2_ASSUMPTION_PROFILE")

        class Result:
            returncode = 0
            stderr = ""
            stdout = ""
        return Result()

    def fake_full(*_args, **_kwargs):
        seen["serialization_profile"] = __import__("os").environ.get(
            "RESEARCH2_ASSUMPTION_PROFILE")
        return {"mode": "FULL", "market": {"tam": None, "sam": None, "som": None}}

    monkeypatch.setattr(product_runner.subprocess, "run", fake_subprocess)
    monkeypatch.setattr(pipeline, "_full", fake_full)
    monkeypatch.setattr("sys.argv", [
        "product_runner.py", "--input", str(input_path), "--output", str(output_path),
        "--workspace", str(workspace), "--run-id", "product-run",
        "--concept-id", "food-recipe-community", "--as-of", "2026-08-12",
    ])

    product_runner.main()

    assert seen == {"subprocess_profile": "product", "serialization_profile": "product"}
    assert json.loads(output_path.read_text(encoding="utf-8"))["market"] == {
        "tam": None, "sam": None, "som": None}
