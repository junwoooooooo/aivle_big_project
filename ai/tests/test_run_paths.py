# -*- coding: utf-8 -*-
"""원장이 어디 있는가 — **읽기는 둘 다, 쓰기는 뒤에만.**

수집이 원장을 만들기 시작하면서 자리가 둘로 갈렸다. 씨앗 `runs/` 는 컨테이너에서 `:ro`
이고(「컨테이너가 측정 원장을 덮어쓸 수 없다」), 새 원장은 `runs-generated/` 로 간다.

이 규칙이 **한 곳에만** 있어야 하는 이유는 예전에 답이 갈려 있었기 때문이다 — 쓰기는
`runlog.RUNS_DIR`(환경변수)로 옮길 수 있었는데 읽기는 `bm_scorer`·`bm_layer`·`run.py` 에
`ROOT/runs` 로 **하드코딩**돼 있었다. 그 상태로 볼륨만 붙이면 수집은 새 자리에 쓰고
채점은 옛 자리를 보게 된다 — **조용히** 「원장이 없다」가 된다.
"""
from __future__ import annotations

import io
import json
import os
import sys

import pytest

HERE = os.path.dirname(os.path.abspath(__file__))
AI_ROOT = os.path.dirname(HERE)
RESEARCH2 = os.path.join(AI_ROOT, "app", "research", "research2")
sys.path.insert(0, AI_ROOT)
sys.path.insert(0, RESEARCH2)

import runpath  # noqa: E402


@pytest.fixture()
def split(monkeypatch, tmp_path):
    """씨앗 자리와 생성 자리를 갈라 놓는다."""
    seed = tmp_path / "runs"
    generated = tmp_path / "runs-generated"
    seed.mkdir()
    generated.mkdir()
    monkeypatch.setattr(runpath, "RUNS_DIR", str(seed))
    monkeypatch.setattr(runpath, "GENERATED_RUNS_DIR", str(generated))
    monkeypatch.setattr(runpath, "SEARCH_ORDER", (str(generated), str(seed)))
    return seed, generated


def test_writes_always_go_to_the_generated_side(split):
    """씨앗은 `:ro` 다. 쓰기가 그쪽으로 가면 **컨테이너에서 그 자리에서 죽는다.**"""
    seed, generated = split

    assert runpath.write_dir("selection-42") == os.path.join(str(generated), "selection-42")
    assert runpath.harness_write_dir("store-ops") == os.path.join(
        str(generated), "harness", "store-ops")


def test_reads_see_both_sides(split):
    seed, generated = split
    (seed / "beauty-13").mkdir()
    (generated / "selection-42").mkdir()

    assert runpath.exists("beauty-13") and runpath.exists("selection-42")
    assert runpath.read_dir("beauty-13") == os.path.join(str(seed), "beauty-13")
    assert runpath.read_dir("selection-42") == os.path.join(str(generated), "selection-42")
    assert runpath.find("없는-원장") is None


def test_a_generated_ledger_wins_over_a_seed_of_the_same_name(split):
    """반대로 두면 사용자가 방금 만든 원장이 옛 씨앗에 **조용히 가려진다.**"""
    seed, generated = split
    (seed / "beauty-13").mkdir()
    (generated / "beauty-13").mkdir()

    assert runpath.read_dir("beauty-13") == os.path.join(str(generated), "beauty-13")


def test_missing_ledger_resolves_to_the_writable_side(split):
    """없을 때의 경로는 **쓸 수 있는 쪽**이어야 한다 — 그래야 만들 수 있다."""
    seed, generated = split

    assert runpath.read_dir("아직-없다") == os.path.join(str(generated), "아직-없다")


@pytest.mark.parametrize("bad", ["", "  ", ".", "..", "../etc", "a/b", "a\\b"])
def test_path_traversal_is_refused(bad):
    """`_SAFE_RUN_ID` 가 이미 막지만, 이 파일을 부르는 길이 여럿이라 여기서도 막는다."""
    with pytest.raises(ValueError):
        runpath.write_dir(bad)


def test_the_engine_writer_lands_in_the_generated_side(split, monkeypatch):
    """`runlog.Run` 이 실제로 생성 자리에 쓴다 — 상수만 맞고 쓰기가 옛 자리면 소용없다."""
    seed, generated = split
    import runlog

    monkeypatch.setattr(runlog, "runpath", runpath)
    run = runlog.Run("selection-42", rules={}, reference_date="2026-08-11")

    assert run.dir == os.path.join(str(generated), "selection-42")
    assert os.path.isdir(run.dir)
    assert not (seed / "selection-42").exists()


def test_the_scorer_reads_through_the_resolver(split):
    """채점기가 생성 자리의 원장을 읽는가. **여기가 예전에 갈려 있던 자리다.**"""
    seed, generated = split
    ledger = generated / "selection-42"
    ledger.mkdir()
    with io.open(str(ledger / "result.json"), "w", encoding="utf-8") as handle:
        json.dump({"run_id": "selection-42", "input": {"slots": []},
                   "report": {}, "reference_date": "2026-08-11"}, handle)

    sys.path.insert(0, os.path.join(RESEARCH2, "service"))
    import bm_scorer

    assert bm_scorer.load_ledger("selection-42")["run_id"] == "selection-42"
