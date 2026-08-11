import importlib
import json
import shutil
from pathlib import Path

import pytest

from app.research import pipeline


RESEARCH_HOME = Path(pipeline.RESEARCH_HOME)
FIXTURE_RUN = RESEARCH_HOME / "runs" / "beauty-13"
CONCEPT = "data/concept_beauty-noshow.json"
TEMP_RUN_ID = "product-temp-run-authority"


@pytest.fixture()
def product_run_override(tmp_path, monkeypatch):
    runs_dir = tmp_path / "runs"
    run_dir = runs_dir / TEMP_RUN_ID
    run_dir.mkdir(parents=True)
    shutil.copy2(FIXTURE_RUN / "run.jsonl", run_dir / "run.jsonl")
    result = json.loads((FIXTURE_RUN / "result.json").read_text(encoding="utf-8"))
    result["run_id"] = TEMP_RUN_ID
    (run_dir / "result.json").write_text(
        json.dumps(result, ensure_ascii=False), encoding="utf-8")

    assert not (RESEARCH_HOME / "runs" / TEMP_RUN_ID).exists()

    import bm_scorer
    import runlog
    import scorecard

    with monkeypatch.context() as scoped:
        scoped.setenv("RESEARCH2_RUNS_DIR", str(runs_dir))
        importlib.reload(bm_scorer)
        importlib.reload(scorecard)
        importlib.reload(runlog)
        yield TEMP_RUN_ID, bm_scorer, scorecard

    importlib.reload(bm_scorer)
    importlib.reload(scorecard)
    importlib.reload(runlog)


def test_bm_scorer_reads_product_workspace_ledger(product_run_override):
    run_id, bm_scorer, _scorecard = product_run_override

    ledger = bm_scorer.load_ledger(run_id)

    assert ledger["run_id"] == run_id
    assert ledger["slots"]
    assert ledger["ledger_rows"]


def test_verdict_reads_product_workspace_ledger(product_run_override):
    run_id, _bm_scorer, _scorecard = product_run_override
    import verdict

    result = verdict.build(run_id, CONCEPT)

    assert result["run_id"] == run_id
    assert result["판정"]


def test_cards_reads_product_workspace_ledger(product_run_override):
    run_id, _bm_scorer, _scorecard = product_run_override
    import cards

    result = cards.build(run_id, CONCEPT)

    assert result["run_id"] == run_id
    assert result["카드"]


def test_scorecard_reads_product_workspace_ledger(product_run_override):
    run_id, _bm_scorer, scorecard = product_run_override
    import verdict

    result = scorecard.build(run_id, CONCEPT, verdict=verdict.build(run_id, CONCEPT))

    assert result["run"] == run_id
    assert result["전체_행"] > 0
    assert result["과목"]


def test_product_full_builds_envelope_from_workspace_run(product_run_override):
    run_id, _bm_scorer, _scorecard = product_run_override

    result = pipeline._full(
        source_run=run_id,
        concept_path=CONCEPT,
        concept_id="beauty-noshow",
        run_id="product-output-run",
        budget=pipeline.Budget(0),
        rescore=False,
        collection_wired=True,
    )

    assert result["runId"] == "product-output-run"
    assert result["mode"] == "FULL"
    assert result["market"]
    assert result["scorecard"]


def test_unset_override_keeps_bundled_donor_fixtures(monkeypatch):
    import bm_scorer
    import runlog
    import scorecard

    with monkeypatch.context() as scoped:
        scoped.delenv("RESEARCH2_RUNS_DIR", raising=False)
        importlib.reload(bm_scorer)
        importlib.reload(scorecard)
        importlib.reload(runlog)
        assert Path(bm_scorer.RUNS_DIR) == RESEARCH_HOME / "runs"
        for run_id in ("beauty-13", "ledger-05", "pet-treat-15"):
            ledger = bm_scorer.load_ledger(run_id)
            assert ledger["slots"]
            assert ledger["ledger_rows"]

    importlib.reload(bm_scorer)
    importlib.reload(scorecard)
    importlib.reload(runlog)
