import json
import asyncio
import subprocess
import sys

from app.research import pipeline, product_market_join, product_runner


def test_official_full_uses_arbitrary_concept_snapshot_without_saved_run(monkeypatch):
    seen = {}

    async def fake_product_full(concept, concept_id, run_id, as_of, llm_budget, timeout_seconds):
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


def test_product_runner_invokes_full_a1_to_a3_collection_without_from_resume(monkeypatch, tmp_path):
    input_path = tmp_path / "input.json"
    output_path = tmp_path / "output.json"
    input_path.write_text(json.dumps({"concept_name": "Any concept"}), encoding="utf-8")
    calls = []

    def fake_run(command, **kwargs):
        calls.append((command, kwargs))
        return subprocess.CompletedProcess(command, 0, "", "")

    monkeypatch.setattr(subprocess, "run", fake_run)
    monkeypatch.setattr(pipeline, "_full", lambda *args, **kwargs: {"mode": "FULL", "ok": True})
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
        ],
    )
    (tmp_path / "workspace").mkdir()

    product_runner.main()

    command = calls[0][0]
    assert command[1:3] == ["-u", "run.py"]
    assert "--concept" in command
    assert "--from" not in command
    assert json.loads(output_path.read_text(encoding="utf-8"))["ok"] is True


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
