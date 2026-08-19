from __future__ import annotations

import hashlib
import subprocess
from functools import lru_cache
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


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
    for path in sorted(donor):
        _assert_main_blob(path)


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
