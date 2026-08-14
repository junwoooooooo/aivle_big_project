from __future__ import annotations

import asyncio
import hashlib
import importlib
import io
import json
import sys
import zipfile
from pathlib import Path

import httpx
import pytest

from app.research import market_ledger_artifact as ledger
from app.research import pipeline


def task_input() -> dict:
    return {
        "conceptId": "concept-v5",
        "asOf": "2026-08-13",
        "sourceRun": "concept-v5",
        "source": {
            "projectId": 41,
            "selectedConceptHash": "sha256:" + "a" * 64,
        },
    }


def context() -> dict[str, str]:
    return {
        "taskRunId": "task-v5",
        "taskAttemptId": "attempt-v5",
        "canonicalInputHash": "sha256:" + "b" * 64,
    }


def recollect_context() -> dict[str, str]:
    return {
        "taskRunId": "task-v5-b",
        "taskAttemptId": "attempt-v5-b",
        "canonicalInputHash": "sha256:" + "e" * 64,
    }


def bind_source_artifact(input_value: dict, manifest: dict) -> None:
    input_value["ledgerArtifact"] = {
        "artifactId": "artifact-v5",
        "manifestHash": manifest["manifestHash"],
        "sourceMarketResearchVersionId": 7,
        "sourceMarketTaskRunId": manifest["marketTaskRunId"],
        "sourceTaskAttemptId": manifest["taskAttemptId"],
        "sourceCanonicalInputHash": manifest["canonicalInputHash"],
        "sourceConceptSnapshotHash": manifest["conceptSnapshotHash"],
        "sourceAsOf": manifest["asOf"],
    }


def write_run(workspace: Path) -> None:
    run = workspace / "runs-generated" / "concept-v5"
    run.mkdir(parents=True)
    (run / "run.jsonl").write_text('{"event":"ok"}\n', encoding="utf-8")
    (run / "a3_bodies.json").write_text('{"a":"body"}', encoding="utf-8")
    (run / "result.json").write_text('{"mode":"FULL"}', encoding="utf-8")


def test_bundle_round_trip_survives_original_workspace_deletion(tmp_path: Path) -> None:
    original = tmp_path / "task-a"
    write_run(original)
    bundle, manifest = ledger.build_bundle(task_input(), context(), str(original), "concept-v5")
    for path in sorted(original.rglob("*"), reverse=True):
        if path.is_file():
            path.unlink()
        else:
            path.rmdir()
    original.rmdir()

    restored_manifest, files = ledger.verify_bundle(bundle, task_input(), context())

    assert restored_manifest["manifestHash"] == manifest["manifestHash"]
    assert set(files) == set(ledger.LEDGER_FILES)
    assert json.loads(files["result.json"])["mode"] == "FULL"


def test_bundle_rejects_corrupt_file_hash(tmp_path: Path) -> None:
    write_run(tmp_path)
    bundle, _ = ledger.build_bundle(task_input(), context(), str(tmp_path), "concept-v5")
    source = zipfile.ZipFile(io.BytesIO(bundle))
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as target:
        for name in source.namelist():
            content = source.read(name)
            target.writestr(name, b"corrupt" if name == "result.json" else content)

    with pytest.raises(ledger.MarketLedgerArtifactError, match="checksum"):
        ledger.verify_bundle(output.getvalue(), task_input(), context())


def test_bundle_rejects_path_traversal(tmp_path: Path) -> None:
    write_run(tmp_path)
    bundle, _ = ledger.build_bundle(task_input(), context(), str(tmp_path), "concept-v5")
    source = zipfile.ZipFile(io.BytesIO(bundle))
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as target:
        for name in source.namelist():
            target.writestr(name, source.read(name))
        target.writestr("../escape", b"x")

    with pytest.raises(ledger.MarketLedgerArtifactError, match="allowlist"):
        ledger.verify_bundle(output.getvalue(), task_input(), context())


def test_bundle_rejects_wrong_project(tmp_path: Path) -> None:
    write_run(tmp_path)
    bundle, _ = ledger.build_bundle(task_input(), context(), str(tmp_path), "concept-v5")
    wrong = task_input()
    wrong["source"]["projectId"] = 99

    with pytest.raises(ledger.MarketLedgerArtifactError, match="lineage"):
        ledger.verify_bundle(bundle, wrong, context())


def test_restore_uses_backend_transport_and_generated_directory(tmp_path: Path,
                                                               monkeypatch) -> None:
    source_workspace = tmp_path / "source"
    write_run(source_workspace)
    bundle, manifest = ledger.build_bundle(
        task_input(), context(), str(source_workspace), "concept-v5")
    input_value = task_input()
    bind_source_artifact(input_value, manifest)

    class FakeClient:
        def __init__(self, *args, **kwargs):
            pass
        async def __aenter__(self):
            return self
        async def __aexit__(self, *args):
            return None
        async def get(self, url, headers):
            return httpx.Response(200, content=bundle,
                headers={"X-Artifact-SHA256": hashlib.sha256(bundle).hexdigest()},
                request=httpx.Request("GET", url))

    monkeypatch.setattr(ledger, "_backend_config", lambda: ("http://backend", "token"))
    monkeypatch.setattr(ledger.httpx, "AsyncClient", FakeClient)
    restored = tmp_path / "restored"

    asyncio.run(ledger.restore(input_value, recollect_context(), str(restored)))

    restored_run = restored / "runs-generated" / "concept-v5"
    assert (restored_run / "result.json").read_bytes() == b'{"mode":"FULL"}'
    assert not (restored / "runs" / "concept-v5").exists()


def test_restored_ledger_reaches_exact_recollect_orchestrator(tmp_path: Path,
                                                              monkeypatch) -> None:
    source_workspace = tmp_path / "source"
    write_run(source_workspace)
    bundle, manifest = ledger.build_bundle(
        task_input(), context(), str(source_workspace), "concept-v5")
    restored_workspace = tmp_path / "restored"
    restore_input = task_input()
    bind_source_artifact(restore_input, manifest)
    ledger.restore_bundle(bundle, restore_input, recollect_context(), str(restored_workspace))

    monkeypatch.setenv("RESEARCH2_RUNS_DIR", str(restored_workspace / "runs"))
    monkeypatch.setenv("RESEARCH2_GENERATED_RUNS_DIR",
                       str(restored_workspace / "runs-generated"))
    from app.research.research2 import runpath
    runpath = importlib.reload(runpath)
    monkeypatch.setitem(sys.modules, "runpath", runpath)
    captured = {}

    def fake_full(source_run, concept_path, concept_id, run_id, budget, rescore,
                  collect=False, as_of="", recollect=None):
        captured.update({
            "sourceRun": source_run,
            "conceptId": concept_id,
            "collect": collect,
            "recollect": recollect,
            "sourceComplete": runpath.complete(source_run),
        })
        return {"mode": "FULL", "sourceRun": source_run}

    monkeypatch.setattr(pipeline, "_full", fake_full)
    request = {
        "mode": "FULL",
        "conceptId": "concept-v5",
        "sourceRun": "concept-v5",
        "asOf": "2026-08-13",
        "textContents": [{
            "contentKey": "concept",
            "chunks": [{"text": json.dumps({"concept_id": "concept-v5"})}],
        }],
        "recollect": {"slots": "S1,S2", "from": "extract", "slotsFrom": "current"},
    }

    result = asyncio.run(pipeline.run_market_research(request, "task-b", 30))

    assert result == {"mode": "FULL", "sourceRun": "concept-v5"}
    assert captured == {
        "sourceRun": "concept-v5",
        "conceptId": "concept-v5",
        "collect": True,
        "recollect": {"slots": "S1,S2", "from": "extract", "slotsFrom": "current"},
        "sourceComplete": True,
    }
