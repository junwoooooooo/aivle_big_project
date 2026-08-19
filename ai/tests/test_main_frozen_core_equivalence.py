from __future__ import annotations

import hashlib
import subprocess
from functools import lru_cache
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVABILITY_DELTAS = {"ai/app/research/product_pipeline.py"}


def _git(*args: str) -> str:
    return subprocess.check_output(
        ["git", *args], cwd=ROOT, text=True, encoding="utf-8"
    ).strip()


@lru_cache(maxsize=1)
def _main_blobs() -> dict[str, str]:
    rows = _git("ls-tree", "-r", "origin/main").splitlines()
    return {
        path: metadata.split()[2]
        for row in rows
        for metadata, path in [row.split("\t", 1)]
    }


def _worktree_blob(path: str) -> str:
    content = (ROOT / path).read_bytes()
    content = content.replace(b"\r\n", b"\n")
    header = f"blob {len(content)}\0".encode("ascii")
    return hashlib.sha1(header + content).hexdigest()


def _assert_main_blob(path: str) -> None:
    assert (ROOT / path).is_file(), path
    assert _worktree_blob(path) == _main_blobs()[path], path


def test_stage2_research_tree_is_the_main_tree() -> None:
    donor = {
        line
        for line in _git(
            "ls-tree", "-r", "--name-only", "origin/main", "ai/app/research"
        ).splitlines()
        if line
    }
    current = {
        path.relative_to(ROOT).as_posix()
        for path in (ROOT / "ai/app/research").rglob("*")
        if path.is_file()
        and "__pycache__" not in path.parts
        and "runs-generated" not in path.parts
    }
    assert current == donor
    for path in sorted(donor - OBSERVABILITY_DELTAS):
        _assert_main_blob(path)


def test_product_pipeline_delta_is_only_the_outer_failure_diagnostic_hook() -> None:
    path = "ai/app/research/product_pipeline.py"
    source = (ROOT / path).read_text(encoding="utf-8").replace("\r\n", "\n")
    observed = '''            from app.market_failure_diagnostics import (
                collect_market_failure_diagnostics,
                diagnostic_log_detail,
                fallback_stderr_diagnostics,
            )
            stderr_text = stderr.decode("utf-8", "replace")
            try:
                diagnostic = (collect_market_failure_diagnostics(workspace)
                              or fallback_stderr_diagnostics(stderr_text))
            except Exception:
                diagnostic = None
            failure = _fail("EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE",
                            diagnostic_log_detail(diagnostic))
            if diagnostic:
                failure.safe_diagnostics = diagnostic
            raise failure
'''
    donor = '''            detail = stderr.decode("utf-8", "replace").strip().splitlines()
            raise _fail("EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE",
                        detail[-1] if detail else "시장조사 엔진 실패")
'''
    assert source.count(observed) == 1
    restored = source.replace(observed, donor)
    content = restored.encode("utf-8")
    header = f"blob {len(content)}\0".encode("ascii")

    assert hashlib.sha1(header + content).hexdigest() == _main_blobs()[path]


def test_stage2_validation_import_closure_is_the_main_tree() -> None:
    for path in (
        "ai/app/validation/__init__.py",
        "ai/app/validation/citation.py",
        "ai/app/validation/gate.py",
        "ai/app/validation/mapping.py",
        "ai/app/validation/runner.py",
    ):
        _assert_main_blob(path)

    # drift is a separate refinement consumer; it is not imported by the Stage 2 BM closure.
    product = (ROOT / "ai/app/research/product_pipeline.py").read_text(encoding="utf-8")
    pipeline = (ROOT / "ai/app/research/pipeline.py").read_text(encoding="utf-8")
    assert "from app.validation import gate" in product
    assert "from app.validation import citation" in product
    assert "from ..validation import citation, gate, mapping" in pipeline


def test_stage2_java_execution_core_is_main() -> None:
    for path in (
        "backend/src/main/java/com/aivle/backend/pipeline/market/MarketResearchInputFactory.java",
        "backend/src/main/java/com/aivle/backend/pipeline/market/MarketResearchWorker.java",
        "backend/src/main/java/com/aivle/backend/taskrun/contract/MarketResearchContract.java",
        "backend/src/main/java/com/aivle/backend/integration/ai/AiServerProperties.java",
    ):
        _assert_main_blob(path)


def test_stage4_execution_core_is_main() -> None:
    donor = {
        line
        for line in _git(
            "ls-tree", "-r", "--name-only", "origin/main", "ai/app/interview"
        ).splitlines()
        if line
    }
    for path in sorted(donor):
        _assert_main_blob(path)
    for path in (
        "ai/app/twin/bank.py",
        "ai/app/twin/caveats.py",
        "ai/app/twin/profile.py",
        "ai/app/twin/runner.py",
        "ai/app/twin/task_type.py",
        "ai/app/providers/__init__.py",
        "ai/app/providers/schema_compatibility.py",
        "ai/app/providers/structured.py",
        "backend/src/main/java/com/aivle/backend/pipeline/market/MarketInterviewInputFactory.java",
        "backend/src/main/java/com/aivle/backend/pipeline/market/MarketInterviewWorker.java",
        "backend/src/main/java/com/aivle/backend/taskrun/contract/MarketInterviewContract.java",
    ):
        _assert_main_blob(path)


def test_production_dispatch_delegates_to_main_interview() -> None:
    source = (ROOT / "ai/app/api/executions.py").read_text(encoding="utf-8")
    branch = source[source.index('elif body.taskType == "MARKET_INTERVIEW":') :]
    branch = branch[: branch.index('elif body.taskType == "TWIN_SURVEY":')]
    assert "from app.interview import execute_market_interview" in branch
    assert "from app.tasks.market_interview" not in branch
    assert "event_sink=" not in branch


def test_full_replacements_are_not_stage2_production_dependencies() -> None:
    assert not (ROOT / "ai/app/research/research2/section_recall.py").exists()
    assert not (ROOT / "ai/app/research/semantic_relevance.py").exists()
    source = (ROOT / "ai/app/research/pipeline.py").read_text(encoding="utf-8")
    assert "import read_sections as READ" in source
    assert "import reask_sections as REASK" in source
    assert "REASK.merge" in source


def test_full_market_interview_worker_is_not_an_execution_authority() -> None:
    assert not (
        ROOT
        / "backend/src/main/java/com/aivle/backend/pipeline/marketinterview/MarketInterviewWorker.java"
    ).exists()
