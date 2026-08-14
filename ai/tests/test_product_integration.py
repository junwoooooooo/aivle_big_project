import json
import asyncio
import os
import sys

from app.research import product_pipeline as pipeline, product_market_join, product_runner


def test_official_full_uses_arbitrary_concept_snapshot_without_saved_run(monkeypatch):
    seen = {}

    async def fake_product_full(concept, concept_id, run_id, as_of, llm_budget, timeout_seconds,
                                event_sink=None):
        seen.update(
            concept=concept,
            concept_id=concept_id,
            run_id=run_id,
            as_of=as_of,
            llm_budget=llm_budget,
            timeout_seconds=timeout_seconds,
        )
        return {"mode": "FULL", "conceptId": concept_id}

    monkeypatch.setattr(pipeline, "_product_full", fake_product_full)
    result = asyncio.run(
        pipeline.run_market_research(
            {
                "mode": "FULL",
                "conceptId": "arbitrary-selected-concept",
                "conceptSnapshotJson": json.dumps({"concept_name": "Arbitrary business"}),
                "asOf": "2026-08-11",
                "llmBudget": 7,
            },
            "task-run-1",
            42.0,
        )
    )

    assert result["conceptId"] == "arbitrary-selected-concept"
    assert seen["concept"]["concept_name"] == "Arbitrary business"
    assert seen["run_id"] == "task-run-1"
    assert seen["llm_budget"] == 7


def test_product_runner_invokes_exact_main_orchestrator(monkeypatch, tmp_path):
    input_path = tmp_path / "input.json"
    output_path = tmp_path / "output.json"
    progress_path = tmp_path / "progress.jsonl"
    input_path.write_text(json.dumps({"concept_name": "Any concept"}), encoding="utf-8")
    seen = {}
    async def fake_main(task_input, run_id, timeout_seconds):
        seen.update(task_input=task_input, run_id=run_id, timeout_seconds=timeout_seconds)
        return {"mode": "FULL", "market": {"tam": None, "sam": None, "som": None}}
    monkeypatch.setattr("app.research.pipeline.run_market_research", fake_main)
    monkeypatch.setattr("app.research.research2.runpath.exists", lambda _run_id: False)
    monkeypatch.setattr(
        sys,
        "argv",
        [
            "product_runner.py",
            "--input", str(input_path),
            "--output", str(output_path),
            "--workspace", str(tmp_path / "workspace"),
            "--run-id", "run-1",
            "--concept-id", "concept-1",
            "--as-of", "2026-08-11",
            "--progress-jsonl", str(progress_path),
        ],
    )
    (tmp_path / "workspace").mkdir()

    previous_runs_dir = os.environ.get("RESEARCH2_RUNS_DIR")
    product_runner.main()

    assert seen["run_id"] == "run-1"
    assert seen["task_input"]["conceptId"] == "concept-1"
    assert json.loads(seen["task_input"]["textContents"][0]["chunks"][0]["text"])["concept_name"] == "Any concept"
    assert json.loads(output_path.read_text(encoding="utf-8"))["mode"] == "FULL"
    events = [json.loads(line) for line in progress_path.read_text(encoding="utf-8").splitlines()]
    assert [event["stage"] for event in events] == [
        "MARKET_COLLECTION", "MARKET_COLLECTION", "MARKET_SERIALIZATION"]
    assert all(set(event) <= {"stage", "action", "status", "safeSummary",
                              "reasonCode", "decision"} for event in events)
    if previous_runs_dir is None:
        os.environ.pop("RESEARCH2_RUNS_DIR", None)
    else:
        os.environ["RESEARCH2_RUNS_DIR"] = previous_runs_dir
    import runlog
    runlog.RUNS_DIR = previous_runs_dir or os.path.join(pipeline.RESEARCH_HOME, "runs")


def test_market_join_evidence_preserves_provenance_and_failure_semantics():
    source = {
        "id": "E-17",
        "kind": "FACT",
        "metric": "market-size",
        "subject": "selected market",
        "period": "2025",
        "value": 123,
        "unit": "KRW",
        "grade": "B",
        "gradeReason": "secondary source",
        "sourceUrl": "https://example.test/evidence",
        "sourceKind": "web",
        "retrievedAt": "2026-08-11T00:00:00Z",
        "quote": "bounded excerpt",
        "caveats": ["not directly measurable"],
        "formula": "a*b",
        "inputs": {"a": 3, "b": 41},
        "materialIds": ["M-1"],
        "assumptions": ["constant price"],
    }

    joined = product_market_join._evidence(source)

    assert joined["id"] == "E-17"
    assert joined["grade_reason"] == "secondary source"
    assert joined["source_url"] == "https://example.test/evidence"
    assert joined["caveats"] == ["not directly measurable"]
    assert joined["formula"] == "a*b"
    assert joined["inputs"] == {"a": 3, "b": 41}
    assert joined["assumptions"] == ["constant price"]
