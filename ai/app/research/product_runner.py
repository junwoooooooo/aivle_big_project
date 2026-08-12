"""공식 Product Market 실행용 격리 어댑터.

donor research2의 A1→A2→A3→A4/B/C를 그대로 호출하되 원장은 Task 단위 임시
workspace에만 둔다. 이 프로세스는 결과 봉투를 만든 뒤 종료되므로 AI 로컬 파일이
current authority가 되지 않는다.
"""
from __future__ import annotations

import argparse
import asyncio
import io
import json
import os
import subprocess
import sys

from app.research.progress_jsonl import SafeProgressJsonl


PRODUCT_ASSUMPTION_PROFILE = "product"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--workspace", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--concept-id", required=True)
    parser.add_argument("--as-of", required=True)
    parser.add_argument("--llm-budget", type=int, default=3)
    parser.add_argument("--progress-jsonl", default="")
    args = parser.parse_args()

    progress = SafeProgressJsonl(args.progress_jsonl, truncate=True)

    os.environ["RESEARCH2_RUNS_DIR"] = os.path.join(args.workspace, "runs")
    # Product 실행 경계가 authority를 고른다. concept 문자열에서 업종을 추측하지 않는다.
    os.environ["RESEARCH2_ASSUMPTION_PROFILE"] = PRODUCT_ASSUMPTION_PROFILE
    with io.open(args.input, encoding="utf-8") as handle:
        concept = json.load(handle)
    concept_path = os.path.join(args.workspace, "concept.json")
    with io.open(concept_path, "w", encoding="utf-8") as handle:
        json.dump(concept, handle, ensure_ascii=False, sort_keys=True)

    from app.research.runner import RESEARCH_HOME
    command = [sys.executable, "-u", "run.py", "--id", args.run_id,
               "--concept", concept_path, "--as-of", args.as_of]
    if args.progress_jsonl:
        command.extend(["--progress-jsonl", args.progress_jsonl])
    process = subprocess.run(
        command,
        cwd=RESEARCH_HOME, check=False, capture_output=True, text=True,
    )
    if process.returncode:
        detail = (process.stderr or process.stdout or "research2 failed").splitlines()
        raise RuntimeError(detail[-1] if detail else "research2 failed")

    from app.research.pipeline import Budget, _full
    result = _full(args.run_id, concept_path, args.concept_id, args.run_id,
                   Budget(total=args.llm_budget), False, collection_wired=True,
                   event_sink=progress.emit)
    progress.close()
    with io.open(args.output, "w", encoding="utf-8") as handle:
        json.dump(result, handle, ensure_ascii=False, sort_keys=True)


if __name__ == "__main__":
    main()
