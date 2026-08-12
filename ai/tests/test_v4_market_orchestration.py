import importlib
import json
import sys
from pathlib import Path

from app.research import pipeline, product_runner


def _reload_runpath(monkeypatch, tmp_path):
    monkeypatch.setenv("RESEARCH2_RUNS_DIR", str(tmp_path / "runs"))
    monkeypatch.setenv("RESEARCH2_GENERATED_RUNS_DIR", str(tmp_path / "runs-generated"))
    from app.research.research2 import runpath
    return importlib.reload(runpath)


def test_pipeline_reads_fresh_generated_ledger_through_runpath(monkeypatch, tmp_path):
    with monkeypatch.context() as scoped:
        runpath = _reload_runpath(scoped, tmp_path)
        run_dir = Path(runpath.GENERATED_RUNS_DIR) / "test-run"
        run_dir.mkdir(parents=True)
        (run_dir / "result.json").write_text('{"fresh":true}', encoding="utf-8")

        assert runpath.read_dir("test-run") == str(run_dir)
        assert runpath.complete("test-run") is True
        scoped.setitem(sys.modules, "runpath", runpath)
        assert pipeline._read_result("test-run") == {"fresh": True}
    importlib.reload(runpath)


def test_product_post_validator_blocks_unobserved_numeric_assumptions():
    result = {"market": {
        "tam": {"value": 100, "factors": [{"basis": "가정", "sourceCount": 0}]},
        "sam": {"value": 80, "factors": [{"basis": "관측", "sourceCount": 1}]},
        "som": None,
    }}

    validated = product_runner._fail_closed_unverified_product_assumptions(result)

    assert validated["market"]["tam"] is None
    assert validated["market"]["sam"]["value"] == 80
    assert validated["degradations"][0]["code"] == "UNVERIFIED_NUMERIC_ASSUMPTION"


def test_partial_generated_ledger_is_quarantined_before_collection(monkeypatch, tmp_path):
    with monkeypatch.context() as scoped:
        runpath = _reload_runpath(scoped, tmp_path)
        partial = Path(runpath.GENERATED_RUNS_DIR) / "concept-1"
        partial.mkdir(parents=True)
        (partial / "run.jsonl").write_text("{}\n", encoding="utf-8")

        moved = runpath.quarantine_partial("concept-1")

        assert moved and moved.startswith("concept-1.partial-")
        assert not partial.exists()
        assert (Path(runpath.GENERATED_RUNS_DIR) / moved / "run.jsonl").exists()
    importlib.reload(runpath)


def test_expected_preregistration_resource_is_present_and_measured():
    from app.research.research2 import runlog
    stamp = runlog.prereg_stamp(0)
    assert Path(runlog.EXPECTED_MD).is_file()
    assert stamp.get("_한계") != "미측정"
