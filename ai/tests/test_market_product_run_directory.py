import importlib
import json
import shutil
from pathlib import Path

import pytest

from app.research import product_pipeline as pipeline


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
    import runpath
    import scorecard
    from app.research.research2 import runpath as package_runpath

    with monkeypatch.context() as scoped:
        scoped.setenv("RESEARCH2_RUNS_DIR", str(runs_dir))
        scoped.setenv("RESEARCH2_GENERATED_RUNS_DIR", str(tmp_path / "runs-generated"))
        importlib.reload(runpath)
        importlib.reload(package_runpath)
        importlib.reload(bm_scorer)
        importlib.reload(scorecard)
        importlib.reload(runlog)
        yield TEMP_RUN_ID, bm_scorer, scorecard

    importlib.reload(runpath)
    importlib.reload(package_runpath)
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


def _arbitrary_product_concept(tmp_path: Path) -> str:
    source = json.loads((RESEARCH_HOME / CONCEPT).read_text(encoding="utf-8"))
    source.update({
        "concept_id": "fridge-optimizer",
        "name": "냉장고 재료 활용 최적화 서비스",
        "target": "식재료 낭비를 줄이려는 1인 가구",
        "problem": "보유 재료를 제때 활용하기 어렵다",
        "solution": "냉장고 재료를 기준으로 메뉴와 소비 순서를 추천한다",
    })
    concept_path = tmp_path / "arbitrary-product-concept.json"
    concept_path.write_text(json.dumps(source, ensure_ascii=False), encoding="utf-8")
    return str(concept_path)


def test_product_full_suppresses_fixture_only_report_note(
        product_run_override, tmp_path):
    run_id, _bm_scorer, _scorecard = product_run_override

    result = pipeline._full(
        source_run=run_id,
        concept_path=_arbitrary_product_concept(tmp_path),
        concept_id="fridge-optimizer",
        run_id="arbitrary-product-output",
        budget=pipeline.Budget(0),
        rescore=False,
        collection_wired=True,
    )

    not_found = result["market"]["notFound"]
    assert all(item.get("item") != "independent_topdown_blocked"
               for item in not_found)
    rendered = json.dumps(not_found, ensure_ascii=False)
    for fixture_token in ("카페24", "카페 SaaS", "코케비즈", "토스플레이스"):
        assert fixture_token not in rendered


def test_fixture_rescore_keeps_donor_report_note(product_run_override):
    run_id, _bm_scorer, _scorecard = product_run_override

    result = pipeline._full(
        source_run=run_id,
        concept_path=CONCEPT,
        concept_id="beauty-noshow",
        run_id="fixture-output",
        budget=pipeline.Budget(0),
        rescore=True,
        collection_wired=False,
    )

    assert any(item.get("item") == "independent_topdown_blocked"
               for item in result["market"]["notFound"])


def test_unset_override_keeps_bundled_donor_fixtures(monkeypatch):
    import bm_scorer
    import runlog
    import runpath
    import scorecard

    with monkeypatch.context() as scoped:
        scoped.delenv("RESEARCH2_RUNS_DIR", raising=False)
        scoped.delenv("RESEARCH2_GENERATED_RUNS_DIR", raising=False)
        importlib.reload(runpath)
        importlib.reload(bm_scorer)
        importlib.reload(scorecard)
        importlib.reload(runlog)
        assert Path(runpath.RUNS_DIR) == RESEARCH_HOME / "runs"
        for run_id in ("beauty-13", "ledger-05", "pet-treat-15"):
            ledger = bm_scorer.load_ledger(run_id)
            assert ledger["slots"]
            assert ledger["ledger_rows"]

    importlib.reload(runpath)
    importlib.reload(bm_scorer)
    importlib.reload(scorecard)
    importlib.reload(runlog)
